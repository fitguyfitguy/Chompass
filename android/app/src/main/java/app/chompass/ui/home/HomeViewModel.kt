package app.chompass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.data.QuickRelogRows
import app.chompass.data.disambiguateFoodName
import app.chompass.data.loadLastGoalChangeSheet
import app.chompass.models.ActiveBurnShade
import app.chompass.models.ActiveCalorieSource
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.DietMode
import app.chompass.models.DayTargets
import app.chompass.models.GoalJournalEntry
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.MacroPlanResolver
import app.chompass.models.ResolvedDayTargets
import app.chompass.models.ResolvedActiveBurn
import app.chompass.models.HomeTopNutrient
import app.chompass.models.ManualActiveEntry
import app.chompass.models.MealType
import app.chompass.models.CurrentMealCatalog
import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.models.FastingPhase
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.PendingFoodAnalysisDraft
import app.chompass.models.PendingFoodInputDraft
import app.chompass.models.ProgressiveMealDraft
import app.chompass.models.ProgressiveMealItem
import app.chompass.models.QueuedAnalysis
import app.chompass.models.QueueStatus
import app.chompass.models.ServingUnitOption
import app.chompass.models.UserProfile
import app.chompass.models.ActivityLevel
import app.chompass.models.WaterGoalCalculator
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WaterEntry
import app.chompass.BuildConfig
import app.chompass.services.FoodImageComposer
import app.chompass.services.FastingAutoPlanner
import app.chompass.services.nextFastingStartMillis
import app.chompass.services.FoodPhotoSession
import app.chompass.services.OpenFoodFactsService
import app.chompass.services.PerfLog
import app.chompass.services.WaterReminderPlanner
import app.chompass.services.grounding.DatabaseSearchResult
import app.chompass.services.grounding.GroundedEntryFeature
import app.chompass.services.ai.AiError
import app.chompass.services.health.ActivityDataSource
import app.chompass.services.health.HomeActivitySnapshot
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.applyTo
import app.chompass.services.ai.toMicronutrients
import app.chompass.services.ai.userMessage
import app.chompass.models.MicronutrientValues
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class FoodLogSortOrder(val storageValue: String, val displayName: String, val displayNameRes: Int) {
    STANDARD("standard", "Breakfast → Lunch → Dinner (latest last)", R.string.sort_standard),
    LATEST_MEALS_FIRST("latestMealsFirst", "Latest Meals First", R.string.sort_latest_first);

    companion object {
        fun fromStorage(value: String?): FoodLogSortOrder =
            values().firstOrNull { it.storageValue == value } ?: STANDARD
    }
}

/**
 * Whether the home hero needs a Health Connect activity snapshot (steps or
 * active calories shown). Pure decision extracted from [HomeViewModel] so the
 * refresh path is unit-testable (Codeberg #22 race family).
 */
internal fun needsActivitySnapshotFor(display: HomeDisplayPreferences): Boolean =
    display.showSteps || display.showActiveCalories

/**
 * Whether the hero needs a measured energy read (ADD_ACTIVE mode with a live
 * measured source). Debug activity days count as a live source even when
 * Health Connect is off, so seeded demo days still reach the gauge.
 */
internal fun needsMeasuredEnergyFor(
    display: HomeDisplayPreferences,
    healthConnectEnabled: Boolean,
    hasDebugActivityDays: Boolean,
): Boolean =
    display.calorieDisplayMode == HomeCalorieDisplayMode.ADD_ACTIVE &&
        (healthConnectEnabled || hasDebugActivityDays)

data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val profile: UserProfile? = null,
    /** Per-day goal journal (#60): frozen past targets behind [resolvedDayTargets]. */
    val goalJournal: List<GoalJournalEntry> = emptyList(),
    val todayEntries: List<FoodEntry> = emptyList(),
    val homeDisplay: HomeDisplayPreferences = HomeDisplayPreferences(),
    val homeTopNutrients: List<HomeTopNutrient> = HomeTopNutrient.DefaultSelection,
    val foodLogMacroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
    /** Measured Health Connect active kcal/day average (Energy Burn Goals). 0 = unavailable. */
    val measuredActiveAverageCalories: Int = 0,
    /** Internal 60-day HC active-by-day map (never synced). */
    val healthEnergyActiveByDay: Map<String, Int> = emptyMap(),
    /** Manual active totals keyed by ISO date. */
    val manualActiveByDay: Map<String, Int> = emptyMap(),
    val activitySnapshot: HomeActivitySnapshot = HomeActivitySnapshot(date = LocalDate.now()),
    val optionalNutrientGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val foodLogSortOrder: FoodLogSortOrder = FoodLogSortOrder.STANDARD,
    val preferGramsByDefault: Boolean = false,
    /** Latest persisted goal-change explanation (hero ⓘ → recalc details); null = none yet. */
    val lastRecalcSheet: app.chompass.services.ai.RecalcSheetData? = null,
    /** Recalc details sheet currently shown (opened from the hero ⓘ budget dialog). */
    val recalcSheet: app.chompass.services.ai.RecalcSheetData? = null,
    /** When false (default), photo staging requires a text note before Analyze. */
    val skipPhotoNotePrompt: Boolean = false,
    /** Consecutive empty-note photo analyzes; at ≥3 offer “don’t ask again”. */
    val photoNoteSkipCount: Int = 0,
    /** Completed photo staging Analyzes; tip card while below [HomeViewModel.PHOTO_ACCURACY_GUIDE_COUNT]. */
    val photoAccuracyGuideCount: Int = 0,
    val hasSeenCameraScaleTip: Boolean = true,
    val weightMetric: Boolean = true,
    val favoriteKeys: Set<String> = emptySet(),
    val pendingAnalysis: FoodAnalysis? = null,
    val pendingImageBytes: ByteArray? = null,
    /** Raw photo bytes for the in-flight / pending review (for tip re-analyze / add photo). */
    val pendingAnalysisImages: List<ByteArray> = emptyList(),
    val pendingFoodSource: FoodSource? = null,
    val pendingDraftImageFilename: String? = null,
    /**
     * Set when the pendingAnalysis came from a Saved Meals tap (Recents /
     * Frequent / Favorites) instead of a fresh AI analysis. We keep the
     * original entry so saveAnalysis can reuse its imageFilename instead of
     * re-storing the image bytes as a new file on disk.
     */
    val pendingReviewSource: FoodEntry? = null,
    /**
     * Dismissed-but-not-logged review (recovered draft): shown as the Home
     * "recovered analysis" chip. Restoring reopens the review sheet without a
     * new AI call; discarding deletes the persisted draft for good.
     */
    val recoveredReview: PendingFoodAnalysisDraft? = null,
    val pendingInputImageBytes: ByteArray? = null,
    val pendingInputNote: String? = null,
    val pendingInputConfirmedPortionGrams: Double? = null,
    /**
     * Text of the in-flight text-only analysis (Codeberg #53): lets the
     * failure auto-save see the prompt even though the sheet owns the field.
     * Cleared on success / dismiss.
     */
    val pendingPromptText: String? = null,
    /**
     * Auto-saved analysis-queue entry for the current failed/retried input
     * (Codeberg #53): retries update the same entry; a successful retry marks
     * it DONE. Cleared on success / dismiss / fresh enqueue.
     */
    val pendingQueueEntryId: UUID? = null,
    /**
     * True when the pending review already received exact grams on multi-photo /
     * context note (tip strip / prior note).
     */
    val pendingPortionPreConfirmed: Boolean = false,
    val pendingInputDraftImageFilenames: List<String> = emptyList(),
    /** Analysis queue + prompt history (Codeberg #53), newest first. */
    val queueEntries: List<QueuedAnalysis> = emptyList(),
    val queueRunningId: UUID? = null,
    val showAnalysisQueue: Boolean = false,
    /** Intermediate grounded-entry review (candidate / portion picks). */
    val pendingGroundedReview: PendingGroundedReview? = null,
    val analyzing: Boolean = false,
    val analysisPhase: EntryAnalysisPhase? = null,
    val analysisPreview: FoodAnalysis? = null,
    /** Validated fields observed while the AI response is still streaming. */
    val analysisPartial: app.chompass.services.ai.PartialFoodAnalysis? = null,
    val inferringUnits: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val waterTrackingEnabled: Boolean = false,
    val waterDailyGoalMl: Int = 2_000,
    val waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    val waterTodayMl: Int = 0,
    /** Individual sips for the selected day, newest first (drives the history sheet). */
    val waterTodayEntries: List<WaterEntry> = emptyList(),
    /** Free-text note for the selected day (Codeberg #58a); null when the day has none. */
    val dailyNote: String? = null,
    /** True when the goal shown comes from the dynamic calculator (issue #3). */
    val waterGoalDynamic: Boolean = false,
    /**
     * Next planned drink from the adaptive reminder chain (one cup, capped by
     * the goal remainder, at [Plan.nextFireMillis]); null when water tracking /
     * the reminder is off, the goal is met, or the window is degenerate.
     */
    val waterNextPlan: app.chompass.services.WaterReminderPlanner.Plan? = null,
    /** Optional nicotine tracker (docs/local/PLAN_NICOTINE_TRACKER.md). */
    val nicotineTrackingEnabled: Boolean = false,
    val nicotineDailyLimit: Int = 0,
    val nicotineQuickKinds: List<NicotineKind> = NicotineKind.DefaultQuickKinds,
    val nicotineTodayCount: Int = 0,
    /** Individual logs for the selected day, newest first (drives the history sheet). */
    val nicotineTodayEntries: List<NicotineEntry> = emptyList(),
    /** Optional daily notes (docs/local/PLAN_DAILY_NOTES.md); default off. */
    val dailyNotesEnabled: Boolean = false,
    /** Suggest meals by time of day; default on. */
    val mealTimesEnabled: Boolean = true,
    /** Optional caffeine tracker (device-pass revision); default off. */
    val caffeineTrackingEnabled: Boolean = false,
    val caffeineQuickKinds: List<CaffeineKind> = CaffeineKind.DefaultQuickKinds,
    /** Selected-day caffeine total: tracker logs + food-entry caffeine, mg. */
    val caffeineTodayMg: Double = 0.0,
    /** Tracker logs for the selected day, newest first (history sheet). */
    val caffeineTodayEntries: List<CaffeineEntry> = emptyList(),
    /** Optional intermittent-fasting timer (docs/local/PLAN_FASTING_TRACKER.md); local-only. */
    val fastingEnabled: Boolean = false,
    val fastingGoalHours: Int = 16,
    val fastingEatHours: Int = 8,
    val fastingAutoWindows: Boolean = true,
    val fastingPhase: FastingPhase = FastingPhase.IDLE,
    /** Elapsed millis of the running fast; ticked each minute by the VM. */
    val fastingElapsedMillis: Long = 0L,
    /** Wall-clock "now" of the latest tick; lets the bar compute remaining. */
    val fastingNowMillis: Long = 0L,
    /** Elapsed millis inside the open eating window; ticked each minute. */
    val fastingEatingElapsedMillis: Long = 0L,
    /** Millis of the next fast start (schedule T in auto mode, eating-window
     *  end in manual); null while fasting or with no anchor. Drives the bar. */
    val fastingNextFastStartMillis: Long? = null,
    val fastingGoalReached: Boolean = false,
    val fastingAutoStarted: Boolean = false,
    val fastingLastEndedAtMillis: Long? = null,
    val fastingLastFastStartedAtMillis: Long? = null,
    /** In-progress weigh-as-you-go meal (photo-per-ingredient). Null when idle. */
    val progressiveMeal: ProgressiveMealDraft? = null,
    /** HomeScreen consumes this once to reopen the Add Food hub after Add another. */
    val resumeProgressiveCapture: Boolean = false,
    /** Show [ProgressiveMealSheet] when the draft has items and capture is idle. */
    val showProgressiveMealSheet: Boolean = false,
    val manualActiveKcal: Int = 0,
    /** Manual active logs for the selected day, newest first. */
    val manualActiveTodayEntries: List<ManualActiveEntry> = emptyList(),
    /**
     * Codeberg #77 part 3: when set, new food logs use this clock time on the
     * selected day instead of now. Null = wall clock. Session-only.
     */
    val logTimeOverride: LocalTime? = null,
    /**
     * Diary entries copied via the selection bar, waiting to be pasted onto
     * the viewed day (in-memory only, cleared on app restart). Empty when the
     * clipboard is unset.
     */
    val copiedEntries: List<FoodEntry> = emptyList(),
) {
    val isEntryAnalysisBusy: Boolean get() = analyzing || analysisPhase != null || inferringUnits
    /** Pending (runnable) queue items — badge count for the Add Food hub row. */
    val queuePendingCount: Int get() = queueEntries.count { it.status == QueueStatus.PENDING }
    /** Progressive Log sheet: analysis running or a completed review is waiting. */
    val showFoodResultSheet: Boolean get() = pendingAnalysis != null || isEntryAnalysisBusy
    /** Fields + Log unlocked only after the AI call (and unit inference) finish. */
    val analysisReadyForEdit: Boolean get() = pendingAnalysis != null && !isEntryAnalysisBusy
    val caloriesToday: Int get() = todayEntries.sumOf { it.calories }
    val proteinToday: Double get() = todayEntries.sumOf { it.protein }
    val carbsToday: Double get() = todayEntries.sumOf { it.carbs }
    val fatToday: Double get() = todayEntries.sumOf { it.fat }
    val baseCalorieGoal: Int get() = resolvedDayTargets.targets.calories

    /**
     * MACRO-CYCLE-A (#60): the viewed day's targets. Past days read the goal
     * journal first (frozen history — schedule edits never rewrite it), gaps
     * and today/future resolve live from the plan; plan off = base effective
     * targets, so behavior is unchanged while disabled.
     */
    val resolvedDayTargets: ResolvedDayTargets get() {
        val p = profile
            ?: return ResolvedDayTargets(DayTargets(2000, 150, 220, 70), null, null)
        val day = date
        if (day.isBefore(LocalDate.now())) {
            val key = day.toString()
            val entry = goalJournal.firstOrNull { it.date == key }
            if (entry != null) {
                return ResolvedDayTargets(
                    targets = DayTargets(entry.calories, entry.proteinG, entry.carbsG, entry.fatG),
                    profileId = entry.profileId,
                    profileName = entry.profileName,
                )
            }
        }
        return MacroPlanResolver.targetsFor(p, day)
    }

    /**
     * Hero day-type chip label (#60): today's profile name, null when the plan
     * is off / resolved to base, or while browsing another day (the quick
     * switch sheet only edits today).
     */
    val dayTypeLabel: String? get() =
        if (date == LocalDate.now()) resolvedDayTargets.profileName else null
    val resolvedActiveBurn: ResolvedActiveBurn? get() {
        val p = profile ?: return null
        val estimate = measuredActiveAverageCalories.takeIf { it > 0 } ?: p.estimatedDailyActiveCalories
        return HomeCalorieDisplay.resolveActiveBurn(
            homeDisplay.calorieDisplayMode,
            activitySnapshot,
            estimate,
            manualActiveKcal,
        )
    }
    val effectiveCalorieMode: HomeCalorieDisplayMode get() =
        HomeCalorieDisplay.effectiveMode(homeDisplay.calorieDisplayMode, resolvedActiveBurn)
    val gaugeBaseCalorieGoal: Int get() {
        val p = profile ?: return baseCalorieGoal
        val sedentary = measuredActiveAverageCalories.takeIf { it > 0 }
            ?.let { (baseCalorieGoal - it).coerceAtLeast(0) }
            ?: p.sedentaryCalorieBudget(baseCalorieGoal)
        return HomeCalorieDisplay.gaugeBaseGoal(effectiveCalorieMode, baseCalorieGoal, sedentary)
    }
    val displayActiveCalories: Int get() = resolvedActiveBurn?.calories ?: 0
    /**
     * Today's live active burn regardless of gauge mode: measured Health Connect
     * burn (or debug data) plus manual entries. Mode-independent — in STATIC
     * mode [displayActiveCalories] carries manual burns only, so the measured
     * part needs this separate sum. Feeds the hero's "N active" caption so the
     * toggle works in STATIC too.
     */
    val liveActiveBurn: Int get() =
        activitySnapshot.activeCalories.coerceAtLeast(0) + manualActiveKcal.coerceAtLeast(0)

    /** True when the home activity snapshot carries live measured/debug burn for the day. */
    val hasLiveBurn: Boolean get() {
        val s = activitySnapshot
        return (s.source == ActivityDataSource.HEALTH_CONNECT || s.source == ActivityDataSource.DEBUG) &&
            s.activeCalories > 0
    }

    val dayTypeActiveStats: app.chompass.models.DayTypeActiveStats.Result get() {
        val totals = app.chompass.models.DayTypeActiveStats.mergeDayTotals(
            healthEnergyActiveByDay,
            manualActiveByDay,
        )
        return app.chompass.models.DayTypeActiveStats.compute(goalJournal, totals, date)
    }

    val typicalResolution: app.chompass.models.DayTypeActiveStats.TypicalResolution get() {
        val p = profile
        val pal = p?.estimatedDailyActiveCalories ?: 0
        return app.chompass.models.DayTypeActiveStats.resolveTypical(
            viewedProfileId = resolvedDayTargets.profileId,
            stats = dayTypeActiveStats,
            blendedMeasured = measuredActiveAverageCalories,
            palEstimate = pal,
        )
    }

    /** The day's active norm: per-type average, else 14-day blended, else PAL. */
    val activeBurnTypical: Int get() = typicalResolution.kcal

    /**
     * The hero ring's displayed calorie goal — ADD_ACTIVE: base + active burn,
     * growing to the expected-day target when live burn exceeds the norm;
     * STATIC: the base goal. Macro cards scale against this so they can never
     * disagree with the ring (#38).
     */
    val heroCalorieGoal: Int get() {
        val base = gaugeBaseCalorieGoal
        val mode = effectiveCalorieMode
        val shade = activeBurnShade
        return if (mode == HomeCalorieDisplayMode.ADD_ACTIVE && shade != null && shade.typical > 0) {
            HomeCalorieDisplay.expectedTarget(base, shade.typical, shade.live)
        } else {
            HomeCalorieDisplay.effectiveGoal(mode, base, displayActiveCalories)
        }
    }

    /**
     * Display scale for P/C/F goals (#38): 1 on typical days (the ring shows
     * the stored base), >1 only when the ring projects above the base
     * (over-typical live burn, or manual kcal on top of the estimate). Keto is
     * excluded — its macro targets are fixed by design.
     */
    val macroGoalScale: Float get() {
        val p = profile ?: return 1f
        if (p.dietMode == DietMode.KETO) return 1f
        // #60: the denominator is the viewed day's resolved target, so the
        // cards scale against the ring exactly as before the day plan existed.
        val base = resolvedDayTargets.targets.calories
        if (base <= 0) return 1f
        return (heroCalorieGoal.toFloat() / base).coerceAtLeast(1f)
    }

    /**
     * Hero burn shades: only in ADD_ACTIVE when the day's active norm is known
     * and a live measured source exists (Health Connect energy, or debug data)
     * — including the measured-0 morning, so the projected day (base + typical)
     * is visible before the first sync. Manual-only and PAL-estimate-only days
     * stay on the legacy budget tail so the drawing never fabricates a burn
     * story. Intrinsic to ADD_ACTIVE: not gated by the "show active calories"
     * toggle, which now only controls the STATIC caption.
     */
    val activeBurnShade: ActiveBurnShade? get() {
        if (effectiveCalorieMode != HomeCalorieDisplayMode.ADD_ACTIVE) return null
        if (!activitySnapshot.energyLive) return null
        val typical = activeBurnTypical
        if (typical <= 0) return null
        val source = if (measuredActiveAverageCalories > 0) {
            ActiveCalorieSource.MEASURED
        } else {
            ActiveCalorieSource.ESTIMATED
        }
        return ActiveBurnShade(
            live = liveActiveBurn,
            typical = typical,
            source = source,
            typicalIsDayType = typicalResolution.typicalIsDayType,
            typicalDayTypeName = resolvedDayTargets.profileName,
            blendedTypical = measuredActiveAverageCalories,
        )
    }

    /**
     * Resting (basal) burn so far: measured HC total minus active when the snapshot
     * carries a total, else BMR prorated to the elapsed fraction of the day. Null
     * when no live burn exists. Feeds the optional resting shade in the hero.
     */
    val restingBurnToday: Int? get() {
        val s = activitySnapshot
        return when {
            s.totalCalories != null -> (s.totalCalories - s.activeCalories).coerceAtLeast(0)
            hasLiveBurn && profile != null ->
                (profile.bmr * elapsedDayFraction(date)).roundToInt()
            else -> null
        }
    }

    fun isFavorite(entry: FoodEntry): Boolean = entry.favoriteKey in favoriteKeys

    /**
     * Ignore in-flight photo [ByteArray] identity so a water/saving/`copy`
     * that keeps the same pixels does not bust every Home collector. Image
     * updates always change another field (`pendingAnalysis`, `analyzing`, …)
     * so StateFlow still emits when the review sheet needs a new bitmap.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HomeUiState) return false
        return date == other.date &&
            profile == other.profile &&
            goalJournal == other.goalJournal &&
            todayEntries == other.todayEntries &&
            homeDisplay == other.homeDisplay &&
            homeTopNutrients == other.homeTopNutrients &&
            foodLogMacroChips == other.foodLogMacroChips &&
            measuredActiveAverageCalories == other.measuredActiveAverageCalories &&
            healthEnergyActiveByDay == other.healthEnergyActiveByDay &&
            manualActiveByDay == other.manualActiveByDay &&
            activitySnapshot == other.activitySnapshot &&
            optionalNutrientGoals == other.optionalNutrientGoals &&
            foodLogSortOrder == other.foodLogSortOrder &&
            preferGramsByDefault == other.preferGramsByDefault &&
            lastRecalcSheet == other.lastRecalcSheet &&
            recalcSheet == other.recalcSheet &&
            skipPhotoNotePrompt == other.skipPhotoNotePrompt &&
            photoNoteSkipCount == other.photoNoteSkipCount &&
            photoAccuracyGuideCount == other.photoAccuracyGuideCount &&
            hasSeenCameraScaleTip == other.hasSeenCameraScaleTip &&
            weightMetric == other.weightMetric &&
            favoriteKeys == other.favoriteKeys &&
            pendingReviewSource == other.pendingReviewSource &&
            recoveredReview == other.recoveredReview &&
            pendingFoodSource == other.pendingFoodSource &&
            pendingDraftImageFilename == other.pendingDraftImageFilename &&
            pendingReviewSource == other.pendingReviewSource &&
            pendingInputNote == other.pendingInputNote &&
            pendingInputConfirmedPortionGrams == other.pendingInputConfirmedPortionGrams &&
            pendingPromptText == other.pendingPromptText &&
            pendingQueueEntryId == other.pendingQueueEntryId &&
            pendingPortionPreConfirmed == other.pendingPortionPreConfirmed &&
            pendingInputDraftImageFilenames == other.pendingInputDraftImageFilenames &&
            queueEntries == other.queueEntries &&
            queueRunningId == other.queueRunningId &&
            showAnalysisQueue == other.showAnalysisQueue &&
            pendingGroundedReview == other.pendingGroundedReview &&
            analyzing == other.analyzing &&
            analysisPhase == other.analysisPhase &&
            analysisPreview == other.analysisPreview &&
            analysisPartial == other.analysisPartial &&
            inferringUnits == other.inferringUnits &&
            saving == other.saving &&
            error == other.error &&
            waterTrackingEnabled == other.waterTrackingEnabled &&
            waterDailyGoalMl == other.waterDailyGoalMl &&
            waterQuickPresetsMl == other.waterQuickPresetsMl &&
            waterTodayMl == other.waterTodayMl &&
            waterTodayEntries == other.waterTodayEntries &&
            dailyNote == other.dailyNote &&
            waterGoalDynamic == other.waterGoalDynamic &&
            waterNextPlan == other.waterNextPlan &&
            nicotineTrackingEnabled == other.nicotineTrackingEnabled &&
            nicotineDailyLimit == other.nicotineDailyLimit &&
            nicotineQuickKinds == other.nicotineQuickKinds &&
            nicotineTodayCount == other.nicotineTodayCount &&
            nicotineTodayEntries == other.nicotineTodayEntries &&
            dailyNotesEnabled == other.dailyNotesEnabled &&
            mealTimesEnabled == other.mealTimesEnabled &&
            caffeineTrackingEnabled == other.caffeineTrackingEnabled &&
            caffeineQuickKinds == other.caffeineQuickKinds &&
            caffeineTodayMg == other.caffeineTodayMg &&
            caffeineTodayEntries == other.caffeineTodayEntries &&
            fastingEnabled == other.fastingEnabled &&
            fastingGoalHours == other.fastingGoalHours &&
            fastingEatHours == other.fastingEatHours &&
            fastingAutoWindows == other.fastingAutoWindows &&
            fastingPhase == other.fastingPhase &&
            fastingElapsedMillis == other.fastingElapsedMillis &&
            fastingNowMillis == other.fastingNowMillis &&
            fastingEatingElapsedMillis == other.fastingEatingElapsedMillis &&
            fastingNextFastStartMillis == other.fastingNextFastStartMillis &&
            fastingGoalReached == other.fastingGoalReached &&
            fastingAutoStarted == other.fastingAutoStarted &&
            fastingLastEndedAtMillis == other.fastingLastEndedAtMillis &&
            fastingLastFastStartedAtMillis == other.fastingLastFastStartedAtMillis &&
            progressiveMeal == other.progressiveMeal &&
            resumeProgressiveCapture == other.resumeProgressiveCapture &&
            showProgressiveMealSheet == other.showProgressiveMealSheet &&
            manualActiveKcal == other.manualActiveKcal &&
            manualActiveTodayEntries == other.manualActiveTodayEntries &&
            copiedEntries == other.copiedEntries
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + (profile?.hashCode() ?: 0)
        result = 31 * result + goalJournal.hashCode()
        result = 31 * result + todayEntries.hashCode()
        result = 31 * result + homeDisplay.hashCode()
        result = 31 * result + homeTopNutrients.hashCode()
        result = 31 * result + foodLogMacroChips.hashCode()
        result = 31 * result + measuredActiveAverageCalories
        result = 31 * result + healthEnergyActiveByDay.hashCode()
        result = 31 * result + manualActiveByDay.hashCode()
        result = 31 * result + activitySnapshot.hashCode()
        result = 31 * result + optionalNutrientGoals.hashCode()
        result = 31 * result + foodLogSortOrder.hashCode()
        result = 31 * result + preferGramsByDefault.hashCode()
        result = 31 * result + (lastRecalcSheet?.hashCode() ?: 0)
        result = 31 * result + (recalcSheet?.hashCode() ?: 0)
        result = 31 * result + skipPhotoNotePrompt.hashCode()
        result = 31 * result + photoNoteSkipCount
        result = 31 * result + photoAccuracyGuideCount
        result = 31 * result + hasSeenCameraScaleTip.hashCode()
        result = 31 * result + weightMetric.hashCode()
        result = 31 * result + favoriteKeys.hashCode()
        result = 31 * result + (pendingReviewSource?.hashCode() ?: 0)
        result = 31 * result + (recoveredReview?.hashCode() ?: 0)
        result = 31 * result + (pendingFoodSource?.hashCode() ?: 0)
        result = 31 * result + (pendingDraftImageFilename?.hashCode() ?: 0)
        result = 31 * result + (pendingReviewSource?.hashCode() ?: 0)
        result = 31 * result + (pendingInputNote?.hashCode() ?: 0)
        result = 31 * result + (pendingInputConfirmedPortionGrams?.hashCode() ?: 0)
        result = 31 * result + (pendingPromptText?.hashCode() ?: 0)
        result = 31 * result + (pendingQueueEntryId?.hashCode() ?: 0)
        result = 31 * result + pendingPortionPreConfirmed.hashCode()
        result = 31 * result + pendingInputDraftImageFilenames.hashCode()
        result = 31 * result + queueEntries.hashCode()
        result = 31 * result + (queueRunningId?.hashCode() ?: 0)
        result = 31 * result + showAnalysisQueue.hashCode()
        result = 31 * result + (pendingGroundedReview?.hashCode() ?: 0)
        result = 31 * result + analyzing.hashCode()
        result = 31 * result + (analysisPhase?.hashCode() ?: 0)
        result = 31 * result + (analysisPreview?.hashCode() ?: 0)
        result = 31 * result + (analysisPartial?.hashCode() ?: 0)
        result = 31 * result + inferringUnits.hashCode()
        result = 31 * result + saving.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + waterTrackingEnabled.hashCode()
        result = 31 * result + waterDailyGoalMl
        result = 31 * result + waterQuickPresetsMl.hashCode()
        result = 31 * result + waterTodayMl
        result = 31 * result + waterTodayEntries.hashCode()
        result = 31 * result + (dailyNote?.hashCode() ?: 0)
        result = 31 * result + waterGoalDynamic.hashCode()
        result = 31 * result + (waterNextPlan?.hashCode() ?: 0)
        result = 31 * result + nicotineTrackingEnabled.hashCode()
        result = 31 * result + nicotineDailyLimit
        result = 31 * result + nicotineQuickKinds.hashCode()
        result = 31 * result + nicotineTodayCount
        result = 31 * result + nicotineTodayEntries.hashCode()
        result = 31 * result + dailyNotesEnabled.hashCode()
        result = 31 * result + mealTimesEnabled.hashCode()
        result = 31 * result + caffeineTrackingEnabled.hashCode()
        result = 31 * result + caffeineQuickKinds.hashCode()
        result = 31 * result + caffeineTodayMg.hashCode()
        result = 31 * result + caffeineTodayEntries.hashCode()
        result = 31 * result + fastingEnabled.hashCode()
        result = 31 * result + fastingGoalHours
        result = 31 * result + fastingEatHours
        result = 31 * result + fastingAutoWindows.hashCode()
        result = 31 * result + fastingPhase.hashCode()
        result = 31 * result + fastingElapsedMillis.hashCode()
        result = 31 * result + fastingNowMillis.hashCode()
        result = 31 * result + fastingEatingElapsedMillis.hashCode()
        result = 31 * result + (fastingNextFastStartMillis?.hashCode() ?: 0)
        result = 31 * result + fastingGoalReached.hashCode()
        result = 31 * result + fastingAutoStarted.hashCode()
        result = 31 * result + (fastingLastEndedAtMillis?.hashCode() ?: 0)
        result = 31 * result + (fastingLastFastStartedAtMillis?.hashCode() ?: 0)
        result = 31 * result + (progressiveMeal?.hashCode() ?: 0)
        result = 31 * result + resumeProgressiveCapture.hashCode()
        result = 31 * result + showProgressiveMealSheet.hashCode()
        result = 31 * result + manualActiveKcal
        result = 31 * result + manualActiveTodayEntries.hashCode()
        result = 31 * result + copiedEntries.hashCode()
        return result
    }
}

private fun elapsedDayFraction(day: LocalDate): Float {
    if (!day.isEqual(LocalDate.now())) return 1f
    return (LocalTime.now().toSecondOfDay() / 86_400f).coerceIn(0f, 1f)
}

/**
 * Last-requested-wins guard for [HomeViewModel.refreshActivitySnapshot].
 * Health Connect reads are slow and day-dependent (one aggregate call per day
 * back), so an older day's read can land after a newer one and overwrite the
 * snapshot — the "active calories of the previous day stick" bug (Codeberg
 * #22). Each refresh calls [begin]; only the read holding the current token
 * may write the snapshot ([isCurrent]). Mirrors the analysisGeneration idiom
 * used for food analysis.
 */
internal class ActivitySnapshotRefreshGuard {
    private var generation = 0

    /** Claims the current generation for a new refresh; invalidates prior ones. */
    fun begin(): Int = ++generation

    /** True only for the most recently begun refresh. */
    fun isCurrent(gen: Int): Boolean = gen == generation
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    companion object {
        /** After this many empty-note photo analyzes, offer “don’t ask again”. */
        const val PHOTO_NOTE_SKIP_OFFER_THRESHOLD = 3
        /** Show the prominent accuracy tip card for the first N photo staging Analyzes. */
        const val PHOTO_ACCURACY_GUIDE_COUNT = 3
    }

    @Volatile
    private var analysisInFlight = false
    private var analysisGeneration = 0
    /** Last-requested-wins guard for [refreshActivitySnapshot] (Codeberg #22). */
    private val activitySnapshotGuard = ActivitySnapshotRefreshGuard()

    /**
     * Same-day hub-chip cache so reopening the Log sheet is instant after the
     * first open. Invalidated on every diary/favorites emission (see the init
     * combine) so a fresh save always shows the new recents.
     */
    private var quickRelogCache: QuickRelogRows? = null
    private var quickRelogCacheDay: LocalDate? = null
    private var quickRelogCacheEpoch = 0
    /** Bumped on any diary/favorites change (init combine) — invalidates the cache. */
    private var quickRelogEpoch = 0
    /** Shared in-flight load so the FAB prefetch and the sheet LaunchedEffect never run twice. */
    private var quickRelogLoad: CompletableDeferred<QuickRelogRows>? = null
    @Volatile private var daySwitchStartedAtNs = 0L
    @Volatile private var relogAckAtNs = 0L
    @Volatile private var relogAckPriorCount = -1
    private var uiAckWaiter: CompletableDeferred<Long>? = null
    private var waterAckWaiter: CompletableDeferred<Long>? = null
    @Volatile private var waterAckAtNs = 0L
    @Volatile private var waterAckPriorMl = -1

    /** Warm hub recents while the Log sheet animates open (called from the FAB tap). */
    fun prefetchQuickRelog() {
        viewModelScope.launch { loadQuickRelogCached() }
    }

    /** Instant chip row if the same-day cache is still valid; null means show placeholders. */
    fun peekQuickRelogCache(): QuickRelogRows? {
        val today = LocalDate.now()
        return if (quickRelogCacheDay == today && quickRelogCacheEpoch == quickRelogEpoch) {
            quickRelogCache
        } else {
            null
        }
    }

    /** Hub chips for the AddFoodSheet; cached per day, refreshed after any diary change. */
    suspend fun quickRelogRowsCached(): QuickRelogRows = loadQuickRelogCached()

    private suspend fun loadQuickRelogCached(): QuickRelogRows {
        val today = LocalDate.now()
        if (quickRelogCacheDay == today && quickRelogCacheEpoch == quickRelogEpoch) {
            quickRelogCache?.let { return it }
        }
        // Dedupe concurrent loads (FAB prefetch + sheet LaunchedEffect overlap).
        quickRelogLoad?.let { return it.await() }
        val deferred = CompletableDeferred<QuickRelogRows>()
        quickRelogLoad = deferred
        val startEpoch = quickRelogEpoch
        viewModelScope.launch {
            // Degrade to empty chip rows on failure — never hang the sheet.
            val fresh = runCatching {
                withContext(Dispatchers.Default) {
                    container.foodRepository.quickRelogRows(perRow = 10)
                }
            }.getOrDefault(QuickRelogRows.Empty)
            quickRelogLoad = null
            if (startEpoch == quickRelogEpoch) {
                quickRelogCache = fresh
                quickRelogCacheDay = today
                quickRelogCacheEpoch = startEpoch
            }
            deferred.complete(fresh)
        }
        return deferred.await()
    }

    private data class AnalysisStart(
        val generation: Int,
        val previousDraftImage: String?,
    )

    private fun beginAnalysis(
        phased: Boolean = false,
        configure: (HomeUiState) -> HomeUiState,
    ): AnalysisStart? =
        synchronized(this) {
            if (analysisInFlight || _ui.value.isEntryAnalysisBusy) return null
            analysisInFlight = true
            val gen = ++analysisGeneration
            val previousDraftImage = _ui.value.pendingDraftImageFilename
            container.analyzingFood.value = true
            _ui.value = configure(
                _ui.value.copy(
                    error = null,
                    pendingAnalysis = null,
                    pendingReviewSource = null,
                    pendingPortionPreConfirmed = false,
                    analyzing = true,
                    analysisPhase = if (phased) EntryAnalysisPhase.Preparing else null,
                    analysisPreview = null,
                    analysisPartial = null,
                    inferringUnits = false,
                )
            )
            AnalysisStart(gen, previousDraftImage)
        }

    /**
     * Invalidate any in-flight analysis so a tip / add-photo restart can call
     * [beginAnalysis] immediately. Keeps staged image bytes for re-analyze.
     */
    private fun cancelInFlightAnalysisKeepInput() {
        synchronized(this) {
            ++analysisGeneration
            analysisInFlight = false
        }
        container.analyzingFood.value = false
        _ui.update { it.copy(
            pendingAnalysis = null,
            pendingReviewSource = null,
            analyzing = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            error = null,
        ) }
    }

    private fun onFoodAnalysisProgress(generation: Int, progress: FoodAnalysisProgress) {
        if (generation != analysisGeneration) return
        when (progress) {
            is FoodAnalysisProgress.Phase -> {
                _ui.update { it.copy(analysisPhase = progress.phase) }
            }
            is FoodAnalysisProgress.Partial -> {
                val preview = progress.partial.toPreviewAnalysis()
                _ui.update { it.copy(
                    analysisPartial = progress.partial,
                    analysisPreview = preview ?: _ui.value.analysisPreview,
                ) }
            }
            is FoodAnalysisProgress.Parsed -> {
                if (progress.unitsPending) {
                    _ui.update { it.copy(
                        analysisPhase = null,
                        analysisPreview = progress.analysis,
                        analysisPartial = app.chompass.services.ai.PartialFoodAnalysis.fromComplete(
                            progress.analysis,
                            streaming = false,
                        ),
                        pendingAnalysis = progress.analysis,
                        analyzing = false,
                        inferringUnits = true,
                    ) }
                    container.analyzingFood.value = false
                } else {
                    _ui.update { it.copy(
                        analysisPreview = progress.analysis,
                        analysisPartial = app.chompass.services.ai.PartialFoodAnalysis.fromComplete(
                            progress.analysis,
                            streaming = false,
                        ),
                    ) }
                }
            }
            is FoodAnalysisProgress.Complete -> {
                _ui.update { it.copy(
                    pendingAnalysis = progress.analysis,
                    inferringUnits = false,
                    analysisPreview = null,
                    analysisPartial = null,
                    analysisPhase = null,
                ) }
            }
        }
    }

    private fun failAnalysis(gen: Int, message: String?) {
        if (gen != analysisGeneration) return
        autoSaveFailedInput(message)
        _ui.update { it.copy(
            analyzing = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            error = message,
        ) }
    }

    /**
     * Codeberg #53: a failed AI call must not discard the photos + description.
     * Every failed prompt (photo or text) is persisted into the analysis queue
     * (PENDING, runnable later — e.g. against a home-PC local HTTP model).
     * Retries of the same input update the same entry (via
     * [HomeUiState.pendingQueueEntryId]); a successful retry marks it DONE.
     * Barcode/search failures carry no prompt input and are not recorded.
     */
    private fun autoSaveFailedInput(message: String?) {
        val s = _ui.value
        val images = s.pendingAnalysisImages.filter { it.isNotEmpty() }
        val text = s.pendingPromptText?.trim().orEmpty()
        val note = text.ifEmpty { s.pendingInputNote?.trim().orEmpty() }
        if (images.isEmpty() && note.isEmpty()) return
        viewModelScope.launch {
            val store = container.analysisQueue
            val existingId = s.pendingQueueEntryId
            if (existingId != null && store.item(existingId) != null) {
                // Same input failed again (retry / queued run): update in place.
                store.markFailed(existingId, message)
                return@launch
            }
            val id = UUID.randomUUID()
            val filenames = store.storeImages(id, images)
            store.upsert(
                QueuedAnalysis(
                    id = id,
                    createdAt = Instant.now(),
                    targetDate = _selectedDate.value,
                    imageFilenames = filenames,
                    note = note.ifEmpty { null },
                    confirmedPortionGrams = s.pendingInputConfirmedPortionGrams,
                    singleIngredient = s.progressiveMeal?.items?.isNotEmpty() == true,
                    source = s.pendingFoodSource
                        ?: if (images.isNotEmpty()) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT,
                    status = QueueStatus.PENDING,
                    error = message,
                )
            )
            _ui.update { it.copy(pendingQueueEntryId = id) }
            // Link the persisted input draft (if any) so a successful retry
            // resolves this entry instead of leaving it PENDING forever.
            val draft = container.prefs.pendingFoodInputDraft.first()
            if (draft != null && draft.queueEntryId == null) {
                container.prefs.setPendingFoodInputDraft(draft.copy(queueEntryId = id))
            }
        }
    }

    private fun endAnalysis(gen: Int) {
        synchronized(this) {
            if (gen != analysisGeneration) return
            analysisInFlight = false
        }
        if (gen == analysisGeneration) {
            container.analyzingFood.value = false
        }
    }

    /**
     * Shared begin → discard draft → try/catch AiError → end envelope for food
     * analysis entry points. Call from an existing coroutine via [withFoodAnalysis]
     * when prep work must run before [beginAnalysis].
     */
    private fun runFoodAnalysis(
        phased: Boolean = false,
        defaultErrorRes: Int = R.string.error_analysis_failed,
        shouldEnd: () -> Boolean = { true },
        configure: (HomeUiState) -> HomeUiState,
        block: suspend (AnalysisStart) -> Unit,
    ) {
        viewModelScope.launch {
            withFoodAnalysis(phased, defaultErrorRes, shouldEnd, configure, block)
        }
    }

    private suspend fun withFoodAnalysis(
        phased: Boolean = false,
        defaultErrorRes: Int = R.string.error_analysis_failed,
        shouldEnd: () -> Boolean = { true },
        configure: (HomeUiState) -> HomeUiState,
        block: suspend (AnalysisStart) -> Unit,
    ) {
        val start = beginAnalysis(phased = phased, configure = configure) ?: return
        discardPendingDraft(start.previousDraftImage)
        // A fresh analysis supersedes any recovered review chip.
        _ui.update { it.copy(recoveredReview = null) }
        try {
            block(start)
        } catch (e: AiError) {
            failAnalysis(start.generation, e.userMessage(container.appContext))
        } catch (e: Throwable) {
            failAnalysis(
                start.generation,
                e.localizedMessage ?: container.appContext.getString(defaultErrorRes),
            )
        } finally {
            if (shouldEnd()) endAnalysis(start.generation)
        }
    }

    init {
        observePerfBench()
        // Debug-only Codeberg #84 repro: seed the meal-builder sheet with
        // canned ingredients so the pinned-footer layout is verifiable on
        // device (consume-and-clear, same semantics as the perf-bench inbox).
        container.progressiveSeedInbox
            .onEach { count ->
                if (count == null) return@onEach
                container.progressiveSeedInbox.value = null
                seedProgressiveMealDemo(count)
            }
            .launchIn(viewModelScope)
        // Analysis queue + prompt history (Codeberg #53): load once (with
        // retention pruning) and mirror mutations into ui state for the badge
        // and the queue sheet.
        viewModelScope.launch {
            runCatching { container.analysisQueue.ensureLoaded() }
        }
        viewModelScope.launch {
            container.analysisQueue.entries.collect { entries ->
                _ui.update { it.copy(queueEntries = entries) }
            }
        }
        viewModelScope.launch {
            // Hero ⓘ → recalc details: restore the latest goal-change explanation
            // (AI Recalculate or Adaptive) so the link can open the sheet on demand.
            container.prefs.loadLastGoalChangeSheet()?.let { stored ->
                _ui.update { it.copy(lastRecalcSheet = stored) }
            }
        }
        combine(
            container.profileRepository.profile,
            _selectedDate.flatMapLatest { day -> container.foodRepository.entriesForDate(day) },
            container.foodRepository.favoriteKeys,
            container.prefs.foodLogSortOrder,
            _selectedDate
        ) { p, dayEntries, favKeys, sortOrder, day ->
            // Date or favorites change makes chips stale. A same-day food write
            // does not — relog/save prepends the template so the next hub open
            // stays instant instead of rescanning 90 days.
            if (day != _ui.value.date || favKeys != _ui.value.favoriteKeys) {
                quickRelogEpoch++
                quickRelogCache = null
            }
            _ui.value.copy(
                profile = p,
                date = day,
                todayEntries = dayEntries,
                foodLogSortOrder = FoodLogSortOrder.fromStorage(sortOrder),
                favoriteKeys = favKeys
            )
        }
            .onEach { next ->
                // Merge, never replace: the combine transform snapshots `_ui.value` in a
                // separate coroutine (5-flow combine uses an internal channel), so a
                // wholesale `_ui.value = next` can clobber fields written meanwhile
                // (e.g. the hero ⓘ recalc-sheet restore in init). Only the fields this
                // chain owns are copied onto the current state.
                _ui.update { cur ->
                    cur.copy(
                        profile = next.profile,
                        date = next.date,
                        todayEntries = next.todayEntries,
                        foodLogSortOrder = next.foodLogSortOrder,
                        favoriteKeys = next.favoriteKeys,
                    )
                }
                if (PerfLog.enabled) {
                    val switchAt = daySwitchStartedAtNs
                    if (switchAt != 0L && next.date == _selectedDate.value) {
                        daySwitchStartedAtNs = 0L
                        val ms = (System.nanoTime() - switchAt) / 1_000_000
                        PerfLog.event(
                            "op=daySwitch phase=listReady ms=$ms date=${next.date} entries=${next.todayEntries.size}",
                        )
                    }
                    val ackAt = relogAckAtNs
                    if (ackAt != 0L && next.todayEntries.size > relogAckPriorCount) {
                        relogAckAtNs = 0L
                        val ms = (System.nanoTime() - ackAt) / 1_000_000
                        PerfLog.event(
                            "op=relog phase=uiAck ms=$ms entries=${next.todayEntries.size}",
                        )
                        uiAckWaiter?.complete(ms)
                        uiAckWaiter = null
                    }
                    // Codeberg #56 repro instrumentation (TEMP, debug-only): log
                    // every home-list emission with ids+meals so logcat can tell a
                    // state drop (entry missing here) from a render drop (present
                    // here, not on screen) when compared against
                    // op=homeList phase=renderGroups.
                    val listView = next.todayEntries.joinToString(",") { e ->
                        "${e.id.toString().take(8)}:${e.mealType}:${e.timestamp.epochSecond}"
                    }
                    PerfLog.event(
                        "op=homeList phase=emission date=${next.date} sort=${next.foodLogSortOrder} n=${next.todayEntries.size} entries=[$listView]",
                    )
                }
            }
            .launchIn(viewModelScope)

        container.prefs.goalJournal
            .onEach { entries ->
                _ui.update { it.copy(goalJournal = entries) }
            }
            .launchIn(viewModelScope)

        container.prefs.homeDisplayPreferences
            .onEach { display ->
                _ui.update { it.copy(
                    homeDisplay = display,
                    homeTopNutrients = display.homeTopNutrients,
                    foodLogMacroChips = display.foodLogMacroChips,
                ) }
                refreshActivitySnapshot()
            }
            .launchIn(viewModelScope)

        container.prefs.healthEnergyMeasuredActive
            .onEach { measuredActive ->
                _ui.update { it.copy(measuredActiveAverageCalories = measuredActive) }
            }
            .launchIn(viewModelScope)

        container.prefs.healthEnergyActiveByDay
            .onEach { map ->
                _ui.update { it.copy(healthEnergyActiveByDay = map) }
            }
            .launchIn(viewModelScope)

        _selectedDate
            .onEach { refreshActivitySnapshot() }
            .launchIn(viewModelScope)

        container.prefs.optionalNutrientGoals
            .onEach { goals ->
                _ui.update { it.copy(optionalNutrientGoals = goals) }
            }
            .launchIn(viewModelScope)

        container.prefs.preferGramsByDefault
            .onEach { preferGrams ->
                _ui.update { it.copy(preferGramsByDefault = preferGrams) }
            }
            .launchIn(viewModelScope)

        container.prefs.skipPhotoNotePrompt
            .onEach { skip ->
                _ui.update { it.copy(skipPhotoNotePrompt = skip) }
            }
            .launchIn(viewModelScope)

        container.prefs.photoNoteSkipCount
            .onEach { count ->
                _ui.update { it.copy(photoNoteSkipCount = count) }
            }
            .launchIn(viewModelScope)

        container.prefs.photoAccuracyGuideCount
            .onEach { count ->
                _ui.update { it.copy(photoAccuracyGuideCount = count) }
            }
            .launchIn(viewModelScope)

        container.prefs.hasSeenCameraScaleTip
            .onEach { seen ->
                _ui.update { it.copy(hasSeenCameraScaleTip = seen) }
            }
            .launchIn(viewModelScope)

        container.prefs.weightUnit
            .onEach { unit ->
                _ui.update { it.copy(weightMetric = unit == "kg") }
            }
            .launchIn(viewModelScope)

        container.prefs.waterTrackingEnabled
            .onEach { enabled -> _ui.update { it.copy(waterTrackingEnabled = enabled) } }
            .launchIn(viewModelScope)

        // Effective water goal: the stored manual goal, or the dynamic calculator's
        // result when the feature is on (issue #3). Recomputes on any input change
        // (profile weight/activity, temperature, food diary, dynamic toggles).
        combine(
            combine(
                container.prefs.waterDailyGoalMl,
                container.prefs.waterDynamicEnabled,
                container.prefs.waterBaseSource,
                container.weatherRepository.state,
                container.prefs.waterUseProfileActivity,
            ) { manualGoal, dyn, source, weather, useAct ->
                WaterDynamicPrefs(manualGoal, dyn, source, weather.effectiveHighC, useAct)
            },
            container.prefs.waterFoodWaterEnabled,
            container.profileRepository.profile,
            container.foodRepository.entriesForDate(LocalDate.now()),
        ) { prefs, foodWater, profile, todayEntries ->
            if (!prefs.dynamicEnabled) {
                prefs.manualGoalMl to false
            } else {
                val todayFoodGrams = if (foodWater) {
                    WaterGoalCalculator.estimateDiaryGrams(todayEntries)
                } else {
                    0
                }
                val goal = WaterGoalCalculator.dailyNetGoalMl(
                    baseSource = prefs.baseSource,
                    weightKg = profile?.weightKg,
                    manualBaseMl = prefs.manualGoalMl,
                    expectedHighC = prefs.tempC,
                    activityLevel = profile?.activityLevel ?: ActivityLevel.SEDENTARY,
                    useProfileActivity = prefs.useProfileActivity,
                    foodGramsToday = todayFoodGrams,
                    foodWaterEnabled = foodWater,
                )
                goal to true
            }
        }
            .onEach { (goal, dynamic) ->
                _ui.update { it.copy(waterDailyGoalMl = goal, waterGoalDynamic = dynamic) }
                refreshWaterPlan()
            }
            .launchIn(viewModelScope)

        container.prefs.waterQuickPresetsMl
            .onEach { presets -> _ui.update { it.copy(waterQuickPresetsMl = presets) } }
            .launchIn(viewModelScope)

        container.prefs.nicotineTrackingEnabled
            .onEach { enabled -> _ui.update { it.copy(nicotineTrackingEnabled = enabled) } }
            .launchIn(viewModelScope)

        container.prefs.dailyNotesEnabled
            .onEach { enabled -> _ui.update { it.copy(dailyNotesEnabled = enabled) } }
            .launchIn(viewModelScope)

        container.prefs.mealTimesEnabled
            .onEach { enabled -> _ui.update { it.copy(mealTimesEnabled = enabled) } }
            .launchIn(viewModelScope)

        container.prefs.nicotineDailyLimit
            .onEach { limit -> _ui.update { it.copy(nicotineDailyLimit = limit) } }
            .launchIn(viewModelScope)

        container.prefs.nicotineQuickKinds
            .onEach { kinds -> _ui.update { it.copy(nicotineQuickKinds = kinds) } }
            .launchIn(viewModelScope)

        combine(container.nicotineRepository.entries, _selectedDate) { entries, day ->
            val zone = ZoneId.systemDefault()
            val dayEntries = entries.filter { it.date.atZone(zone).toLocalDate() == day }
            dayEntries.sumOf { it.count } to dayEntries
        }
            .onEach { (total, dayEntries) ->
                _ui.update { it.copy(nicotineTodayCount = total, nicotineTodayEntries = dayEntries) }
            }
            .launchIn(viewModelScope)

        container.prefs.caffeineTrackingEnabled
            .onEach { enabled -> _ui.update { it.copy(caffeineTrackingEnabled = enabled) } }
            .launchIn(viewModelScope)

        container.prefs.caffeineQuickKinds
            .onEach { kinds -> _ui.update { it.copy(caffeineQuickKinds = kinds) } }
            .launchIn(viewModelScope)

        // Hero total = tracker logs + food-entry caffeine for the selected day
        // (one "today's caffeine" number; the limit applies to both sources).
        combine(
            container.caffeineRepository.entries,
            _selectedDate.flatMapLatest { day -> container.foodRepository.entriesForDate(day) },
            _selectedDate,
        ) { entries, foodEntries, day ->
            val zone = ZoneId.systemDefault()
            val dayEntries = entries.filter { it.date.atZone(zone).toLocalDate() == day }
            val trackedMg = dayEntries.sumOf { it.mg }
            val foodMg = foodEntries.sumOf { it.caffeine ?: 0.0 }
            (trackedMg + foodMg) to dayEntries
        }
            .onEach { (total, dayEntries) ->
                _ui.update { it.copy(caffeineTodayMg = total, caffeineTodayEntries = dayEntries) }
            }
            .launchIn(viewModelScope)

        container.prefs.fastingEnabled
            .onEach { enabled -> _ui.update { it.copy(fastingEnabled = enabled) } }
            .launchIn(viewModelScope)

        container.prefs.fastingGoalHours
            .onEach { goal -> _ui.update { it.copy(fastingGoalHours = goal) } }
            .launchIn(viewModelScope)

        container.prefs.fastingEatHours
            .onEach { eat -> _ui.update { it.copy(fastingEatHours = eat) } }
            .launchIn(viewModelScope)

        container.prefs.fastingAutoWindows
            .onEach { auto -> _ui.update { it.copy(fastingAutoWindows = auto) } }
            .launchIn(viewModelScope)

        // Session changes (start/stop/cancel) re-derive the display state; the
        // minute ticker below keeps the elapsed label rolling without any write.
        container.fastingRepository.session
            .onEach { refreshFastingTick(it) }
            .launchIn(viewModelScope)

        combine(container.waterRepository.entries, _selectedDate) { entries, day ->
            val zone = ZoneId.systemDefault()
            val dayEntries = entries.filter { it.date.atZone(zone).toLocalDate() == day }
            dayEntries.sumOf { it.milliliters } to dayEntries
        }
            .onEach { (total, dayEntries) ->
                _ui.update { it.copy(waterTodayMl = total, waterTodayEntries = dayEntries) }
                refreshWaterPlan()
                val sipAt = waterAckAtNs
                if (sipAt != 0L && total > waterAckPriorMl) {
                    waterAckAtNs = 0L
                    val ms = (System.nanoTime() - sipAt) / 1_000_000
                    PerfLog.event("op=waterSip phase=uiAck ms=$ms ml=$total")
                    waterAckWaiter?.complete(ms)
                    waterAckWaiter = null
                }
            }
            .launchIn(viewModelScope)

        combine(container.notesRepository.notes, _selectedDate) { notes, day ->
            notes.firstOrNull { it.date == day }?.text
        }
            .onEach { note -> _ui.update { it.copy(dailyNote = note) } }
            .launchIn(viewModelScope)

        combine(
            combine(
                container.prefs.waterTrackingEnabled,
                container.prefs.waterReminderEnabled,
                container.prefs.waterCupSizeMl,
            ) { tracking, reminder, cup -> Triple(tracking, reminder, cup) },
            combine(
                container.prefs.waterAwakeStartHour,
                container.prefs.waterAwakeStartMinute,
                container.prefs.waterAwakeEndHour,
                container.prefs.waterAwakeEndMinute,
            ) { sh, sm, eh, em -> WaterWindowPrefs(sh, sm, eh, em) },
        ) { _: Triple<Boolean, Boolean, Int>, _: WaterWindowPrefs -> Unit }
            .onEach { refreshWaterPlan() }
            .launchIn(viewModelScope)

        // A fired reminder (or simply time passing) changes the next fire
        // without any pref/entry emission — roll the caption over each minute
        // (one in-memory DataStore read, same cost as the reminder chain's own
        // fire-time recompute). Also ticks the fasting elapsed label.
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                refreshWaterPlan()
                refreshFastingTick()
            }
        }

        combine(container.manualActiveRepository.entries, _selectedDate) { entries, day ->
            val byDay = app.chompass.models.DayTypeActiveStats.sumManualByDay(entries)
            val today = entries.filter { it.date == day.toString() }.reversed()
            Triple(today.sumOf { it.calories }, byDay, today)
        }
            .onEach { (total, byDay, today) ->
                _ui.update {
                    it.copy(
                        manualActiveKcal = total,
                        manualActiveByDay = byDay,
                        manualActiveTodayEntries = today,
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val analysisDraft = container.prefs.pendingFoodAnalysisDraft.first()
            if (analysisDraft != null) {
                if (analysisDraft.awaitingReview) {
                    // Dismissed by the user: surface the recovered-review chip
                    // instead of reopening the sheet uninvited.
                    _ui.update { it.copy(recoveredReview = analysisDraft) }
                } else {
                    restorePendingDraft(analysisDraft)
                }
            } else {
                container.prefs.pendingFoodInputDraft.first()?.let { restorePendingInputDraft(it) }
            }
        }
    }

    fun setSelectedDate(date: LocalDate) {
        if (PerfLog.enabled && date != _selectedDate.value) {
            daySwitchStartedAtNs = System.nanoTime()
        }
        _selectedDate.value = date
    }

    /** Codeberg #77 part 3: session clock for new food logs. Null = now. */
    fun setLogTimeOverride(time: LocalTime?) {
        _ui.update { it.copy(logTimeOverride = time?.withSecond(0)?.withNano(0)) }
    }

    fun addWater(milliliters: Int) {
        if (milliliters <= 0) return
        viewModelScope.launch {
            container.waterRepository.add(
                WaterEntry(date = timestampForSelectedDay(), milliliters = milliliters),
            )
        }
    }

    /** Edits an existing sip's amount in place (keeps its time of day). */
    fun updateWater(id: UUID, milliliters: Int) {
        if (milliliters <= 0) return
        viewModelScope.launch { container.waterRepository.update(id, milliliters) }
    }

    /** Removes an individual sip (history sheet delete). */
    fun deleteWater(id: UUID) {
        viewModelScope.launch { container.waterRepository.delete(id) }
    }

    /** Saves (or, when blank, clears) the note for the selected day. */
    fun setDailyNote(text: String) {
        val day = _selectedDate.value
        viewModelScope.launch { container.notesRepository.setNote(day, text) }
    }

    /** Clears the note for the selected day. */
    fun clearDailyNote() {
        val day = _selectedDate.value
        viewModelScope.launch { container.notesRepository.deleteNote(day) }
    }

    fun addNicotine(kind: NicotineKind, count: Int = 1, mg: Double? = null) {
        if (count <= 0) return
        viewModelScope.launch {
            container.nicotineRepository.add(
                NicotineEntry.forNow(kind = kind, count = count, mg = mg),
            )
        }
    }

    fun addCaffeine(kind: CaffeineKind, mg: Double? = null) {
        val effective = mg ?: kind.defaultMg
        if (effective == null || effective <= 0) return
        viewModelScope.launch {
            container.caffeineRepository.add(CaffeineEntry.forNow(kind = kind, mg = mg))
        }
    }

    fun updateCaffeine(id: UUID, kind: CaffeineKind, mg: Double) {
        viewModelScope.launch { container.caffeineRepository.update(id, kind, mg) }
    }

    fun deleteCaffeine(id: UUID) {
        viewModelScope.launch { container.caffeineRepository.delete(id) }
    }

    // -- Intermittent fasting timer (local-only) -------------------------

    fun startFast() {
        viewModelScope.launch { container.fastingRepository.start() }
    }

    fun stopFast() {
        viewModelScope.launch { container.fastingRepository.stop() }
    }

    /** Launcher quick action (#182): start when idle, stop when fasting. */
    fun toggleFast() {
        viewModelScope.launch {
            val s = container.fastingRepository.current()
            if (s.isFasting) container.fastingRepository.stop() else container.fastingRepository.start()
        }
    }

    /**
     * Re-derives the fasting cycle display fields (phase, elapsed, goal
     * reached) from the session + windows; called on session change and each
     * minute. When auto windows are on, first drives any transition whose
     * boundary already passed (missed-alarm catch-up, same heal as the
     * planners) so the cycle stays on schedule.
     */
    private fun refreshFastingTick(session: app.chompass.models.FastingSession? = null) {
        viewModelScope.launch {
            if (session == null) FastingAutoPlanner.heal(container)
            val s = session ?: container.fastingRepository.current()
            val now = System.currentTimeMillis()
            val goal = container.prefs.fastingGoalHours.first()
            val eat = container.prefs.fastingEatHours.first()
            val auto = container.prefs.fastingAutoWindows.first()
            val windowEnds = s.eatingWindowEndsAtMillis(eat, now)
            val phase = when {
                s.isFasting -> FastingPhase.FASTING
                // Auto mode: the schedule is the boundary, so any stop
                // opens the eating phase until the next start time.
                auto && s.lastEndedAtMillis != null -> FastingPhase.EATING
                windowEnds != null -> FastingPhase.EATING
                else -> FastingPhase.IDLE
            }
            val nextFastStart = when {
                s.isFasting -> null
                // Auto cycle needs a goal length to be meaningful (the fast
                // would never end otherwise); without one, fall back to the
                // relative eating-window anchor so the bar still counts down.
                auto && goal > 0 -> nextFastingStartMillis(
                    container.prefs.fastingStartHour.first(),
                    container.prefs.fastingStartMinute.first(),
                    now,
                )
                else -> windowEnds
            }
            // Eating-window elapsed is anchored to the last stop whenever the
            // eating phase is active (auto mode included) — mirrors the PWA
            // fasting card instead of zeroing out outside the manual window.
            val lastEnded = s.lastEndedAtMillis
            val eatElapsed = if (phase == FastingPhase.EATING && lastEnded != null) {
                (now - lastEnded).coerceAtLeast(0L)
            } else {
                0L
            }
            _ui.update {
                it.copy(
                    fastingPhase = phase,
                    fastingElapsedMillis = s.elapsedMillis(now),
                    fastingNowMillis = now,
                    fastingEatingElapsedMillis = eatElapsed,
                    fastingNextFastStartMillis = nextFastStart,
                    fastingGoalReached = s.goalReached(goal, now),
                    fastingAutoStarted = s.autoStarted,
                    fastingLastEndedAtMillis = lastEnded,
                    fastingLastFastStartedAtMillis = s.lastFastStartedAtMillis,
                )
            }
        }
    }

    /** Edits an existing log's kind/count/mg in place (keeps its timestamp). */
    fun updateNicotine(id: UUID, kind: NicotineKind, count: Int, mg: Double?) {
        if (count <= 0) return
        viewModelScope.launch { container.nicotineRepository.update(id, kind, count, mg) }
    }

    /** Removes an individual log (history sheet delete). */
    fun deleteNicotine(id: UUID) {
        viewModelScope.launch { container.nicotineRepository.delete(id) }
    }

    /**
     * Re-derives the next planned drink (quantity + fire time) from live prefs
     * and diary — the same "pure state → plan" re-derivation the reminder
     * chain itself performs, so the Home caption always matches the armed
     * alarm. Cheap: the planner reads current DataStore values once.
     */
    private fun refreshWaterPlan() {
        viewModelScope.launch {
            _ui.update { it.copy(waterNextPlan = WaterReminderPlanner.next(container)) }
        }
    }

    fun addManualActive(name: String, calories: Int) {
        if (calories <= 0) return
        viewModelScope.launch {
            container.manualActiveRepository.add(
                ManualActiveEntry.forDay(_selectedDate.value, name, calories),
            )
            container.widgetSnapshotWriter.refresh()
        }
    }

    fun updateManualActive(id: String, name: String, calories: Int) {
        if (calories <= 0) return
        viewModelScope.launch {
            container.manualActiveRepository.update(id, name, calories)
            container.widgetSnapshotWriter.refresh()
        }
    }

    fun deleteManualActive(id: String) {
        viewModelScope.launch {
            container.manualActiveRepository.delete(id)
            container.widgetSnapshotWriter.refresh()
        }
    }

    fun refreshActivitySnapshot() {
        val gen = activitySnapshotGuard.begin()
        viewModelScope.launch {
            val day = _selectedDate.value
            val display = _ui.value.homeDisplay
            val needsActivitySnapshot = needsActivitySnapshotFor(display)
            val needsMeasuredEnergy = needsMeasuredEnergyFor(
                display = display,
                healthConnectEnabled = container.prefs.healthConnectEnabled.first(),
                hasDebugActivityDays = !container.prefs.debugActivityDaysJson().isNullOrEmpty(),
            )
            if (!needsActivitySnapshot && !needsMeasuredEnergy) {
                if (activitySnapshotGuard.isCurrent(gen)) {
                    _ui.update { it.copy(activitySnapshot = HomeActivitySnapshot(date = day)) }
                }
                return@launch
            }
            val snapshot = container.homeActivityReader.readForDate(day)
            if (activitySnapshotGuard.isCurrent(gen)) {
                _ui.update { it.copy(activitySnapshot = snapshot) }
            }
        }
    }

    fun setFoodLogSortOrder(order: FoodLogSortOrder) {
        viewModelScope.launch {
            container.prefs.setFoodLogSortOrder(order.storageValue)
        }
    }

    fun setHomeTopNutrients(selection: List<HomeTopNutrient>) {
        viewModelScope.launch {
            val cardCount = container.prefs.homeNutrientCardCount.first()
            container.prefs.setHomeTopNutrients(HomeTopNutrient.toStorage(selection, cardCount))
        }
    }

    fun dismissCameraScaleTip() {
        viewModelScope.launch {
            container.prefs.setHasSeenCameraScaleTip(true)
        }
    }

    fun analyzeText(description: String) {
        runFoodAnalysis(phased = true, configure = { state ->
            state.copy(
                pendingImageBytes = null,
                pendingAnalysisImages = emptyList(),
                pendingFoodSource = FoodSource.TEXT_INPUT,
                pendingDraftImageFilename = null,
                pendingPromptText = description,
            )
        }) { start ->
            val analysis = container.foodAnalysis.analyzeText(description) { progress ->
                onFoodAnalysisProgress(start.generation, progress)
            }
            savePendingDraft(analysis, imageBytes = null, source = FoodSource.TEXT_INPUT, generation = start.generation)
        }
    }

    fun analyzePhoto(bytes: ByteArray) {
        runFoodAnalysis(phased = true, configure = { state ->
            state.copy(
                pendingImageBytes = bytes,
                pendingAnalysisImages = listOf(bytes),
                pendingFoodSource = FoodSource.SNAP_FOOD,
                pendingDraftImageFilename = null,
            )
        }) { start ->
            val singleIngredient = _ui.value.progressiveMeal?.items?.isNotEmpty() == true
            val analysis = if (singleIngredient) {
                container.foodAnalysis.analyzeFood(
                    bytes,
                    description = null,
                    singleIngredient = true,
                ) { progress ->
                    onFoodAnalysisProgress(start.generation, progress)
                }
            } else {
                container.foodAnalysis.analyzeAuto(bytes) { progress ->
                    onFoodAnalysisProgress(start.generation, progress)
                }
            }
            savePendingDraft(analysis, imageBytes = bytes, source = FoodSource.SNAP_FOOD, generation = start.generation)
        }
    }

    fun analyzePhotos(
        imageBytesList: List<ByteArray>,
        note: String? = null,
        confirmedPortionGrams: Double? = null,
    ) {
        viewModelScope.launch {
            val images = imageBytesList.filter { it.isNotEmpty() }.take(10)
            if (images.isEmpty()) return@launch
            val displayBytes = when {
                images.size >= 2 -> withContext(Dispatchers.Default) {
                    FoodImageComposer.sideBySide(images[0], images[1])
                }
                else -> images.first()
            }
            val grams = confirmedPortionGrams?.takeIf { it > 0 }
            val singleIngredient = _ui.value.progressiveMeal?.items?.isNotEmpty() == true
            withFoodAnalysis(phased = true, configure = { state ->
                state.copy(
                    pendingImageBytes = displayBytes,
                    pendingAnalysisImages = images,
                    pendingFoodSource = FoodSource.SNAP_FOOD,
                    pendingDraftImageFilename = null,
                )
            }) { start ->
                if (images.isNotEmpty()) {
                    savePendingInputDraft(
                        images,
                        note.orEmpty(),
                        FoodSource.SNAP_FOOD,
                        confirmedPortionGrams = grams,
                        queueEntryId = _ui.value.pendingQueueEntryId,
                    )
                }
                val analysis = container.foodAnalysis.analyzeFood(
                    images,
                    note?.takeIf { it.isNotBlank() },
                    singleIngredient = singleIngredient,
                    confirmedPortionGrams = grams,
                ) { progress ->
                    onFoodAnalysisProgress(start.generation, progress)
                }.copy(customNote = note?.takeIf { it.isNotBlank() })
                clearPendingInputDraft()
                savePendingDraft(
                    analysis,
                    imageBytes = displayBytes,
                    source = FoodSource.SNAP_FOOD,
                    generation = start.generation,
                    portionPreConfirmed = grams != null,
                )
            }
        }
    }

    /**
     * Photo staging Analyze: update skip / don’t-ask prefs, then run [analyzePhotos].
     * [dontAskAgain] persists opt-out when the user checked the offer after ≥3 skips.
     */
    fun analyzePhotosFromStaging(
        imageBytesList: List<ByteArray>,
        note: String?,
        confirmedPortionGrams: Double?,
        dontAskAgain: Boolean = false,
    ) {
        viewModelScope.launch {
            val trimmed = note?.trim().orEmpty()
            if (dontAskAgain) {
                container.prefs.setSkipPhotoNotePrompt(true)
                container.prefs.setPhotoNoteSkipCount(0)
            } else if (!_ui.value.skipPhotoNotePrompt) {
                if (trimmed.isEmpty()) {
                    container.prefs.setPhotoNoteSkipCount(_ui.value.photoNoteSkipCount + 1)
                } else {
                    container.prefs.setPhotoNoteSkipCount(0)
                }
            }
            val guideCount = _ui.value.photoAccuracyGuideCount
            if (guideCount < PHOTO_ACCURACY_GUIDE_COUNT) {
                container.prefs.setPhotoAccuracyGuideCount(guideCount + 1)
            }
        }
        analyzePhotos(imageBytesList, note, confirmedPortionGrams)
    }

    fun analyzePhotos(firstBytes: ByteArray, secondBytes: ByteArray) {
        analyzePhotos(listOf(firstBytes, secondBytes))
    }

    /**
     * "Camera + Note" flow — analyze a photo with extra textual context the
     * user typed in (e.g. "extra cheese", "no oil"). Mirrors iOS
     * `cameraMode == .snapFoodWithContext` → `GeminiService.analyzeFood(image, description:)`.
     * Optional [confirmedPortionGrams] is passed as a controlled ground-truth instruction,
     * separate from the free-form note.
     */
    fun analyzePhotoWithNote(
        bytes: ByteArray,
        note: String,
        confirmedPortionGrams: Double? = null,
    ) {
        analyzePhotos(listOf(bytes), note, confirmedPortionGrams)
    }

    fun lookupBarcode(barcode: String) {
        runFoodAnalysis(
            defaultErrorRes = R.string.error_barcode_lookup_failed,
            configure = { state ->
                state.copy(
                    pendingImageBytes = null,
                    pendingAnalysisImages = emptyList(),
                    pendingFoodSource = FoodSource.BARCODE,
                    pendingDraftImageFilename = null,
                )
            },
        ) { start ->
            val lookup = OpenFoodFactsService.lookupWithImage(barcode, container.prefs)
            savePendingDraft(
                analysis = lookup.analysis,
                imageBytes = lookup.productImageBytes,
                source = FoodSource.BARCODE,
                generation = start.generation,
            )
        }
    }

    /**
     * Add Food "Search food" database pick: resolve the hit to a full
     * [FoodAnalysis] (OFF barcode lookup for micros, or offline USDA/Swiss row)
     * and prefill the review sheet with its provenance badge.
     */
    fun selectFoodSearchResult(result: DatabaseSearchResult) {
        runFoodAnalysis(
            defaultErrorRes = R.string.error_search_food_failed,
            configure = { state ->
                state.copy(
                    pendingImageBytes = null,
                    pendingAnalysisImages = emptyList(),
                    pendingFoodSource = FoodSource.SEARCH,
                    pendingDraftImageFilename = null,
                )
            },
        ) { start ->
            val analysis = container.foodDatabaseSearch.toAnalysis(result)
            savePendingDraft(analysis, imageBytes = null, source = FoodSource.SEARCH, generation = start.generation)
        }
    }

    /**
     * Optional grounded entry: recognize components with the selected model provider,
     * then ground nutrients against history / USDA / Open Food Facts.
     */
    fun analyzeGrounded(description: String?, imageBytes: ByteArray?) {
        if (!GroundedEntryFeature.ENABLED) return
        runFoodAnalysis(
            phased = true,
            shouldEnd = { _ui.value.pendingGroundedReview == null },
            configure = { state ->
                state.copy(
                    pendingImageBytes = imageBytes,
                    pendingFoodSource = FoodSource.GROUNDED,
                    pendingDraftImageFilename = null,
                    pendingGroundedReview = null,
                )
            },
        ) { start ->
            val images = listOfNotNull(imageBytes?.takeIf { it.isNotEmpty() })
            val result = container.groundedFoodEntry.analyze(
                description = description,
                imageBytesList = images,
                onProgress = { progress -> onFoodAnalysisProgress(start.generation, progress) },
            )
            val needsReview = result.resolutions.any { it.needsUserChoice }
            if (needsReview) {
                if (start.generation != analysisGeneration) return@runFoodAnalysis
                synchronized(this@HomeViewModel) {
                    analysisInFlight = false
                }
                container.analyzingFood.value = false
                _ui.update { it.copy(
                    pendingGroundedReview = PendingGroundedReview(
                        result = result,
                        description = description,
                        imageBytes = imageBytes,
                    ),
                    pendingAnalysis = null,
                    analyzing = false,
                    analysisPhase = null,
                    inferringUnits = false,
                    analysisPreview = null,
                    analysisPartial = null,
                ) }
            } else {
                savePendingDraft(
                    result.analysis,
                    imageBytes = imageBytes,
                    source = FoodSource.GROUNDED,
                    generation = start.generation,
                )
            }
        }
    }

    fun resolveGroundedChoices(
        selectedSourceIds: Map<Int, String>,
        gramOverrides: Map<Int, Double>,
    ) {
        val pending = _ui.value.pendingGroundedReview ?: return
        runFoodAnalysis(phased = true, configure = { state ->
            state.copy(
                pendingGroundedReview = null,
                pendingImageBytes = pending.imageBytes,
                pendingFoodSource = FoodSource.GROUNDED,
                pendingDraftImageFilename = null,
            )
        }) { start ->
            val result = container.groundedFoodEntry.analyze(
                description = pending.description,
                imageBytesList = listOfNotNull(pending.imageBytes),
                onProgress = { progress -> onFoodAnalysisProgress(start.generation, progress) },
                selectedSourceIds = selectedSourceIds,
                gramOverrides = gramOverrides,
                priorRecognition = pending.result.recognition,
            )
            savePendingDraft(
                result.analysis,
                imageBytes = pending.imageBytes,
                source = FoodSource.GROUNDED,
                generation = start.generation,
            )
        }
    }

    fun dismissGroundedReview() {
        _ui.update { it.copy(pendingGroundedReview = null) }
    }

    fun saveAnalysis(
        name: String? = null,
        servingGrams: Double? = null,
        scale: Double = 1.0,
        mealType: String = MealType.currentMealId,
        selectedServingUnit: String? = null,
        selectedServingQuantity: Double? = null,
        editedAnalysis: FoodAnalysis? = null
    ) {
        val pendingAnalysis = _ui.value.pendingAnalysis
        val analysis = editedAnalysis ?: pendingAnalysis ?: return
        if (_ui.value.saving) return
        val reviewSource = _ui.value.pendingReviewSource
        val pendingFoodSource = _ui.value.pendingFoodSource
        val pendingDraftImageFilename = _ui.value.pendingDraftImageFilename
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.update { it.copy(saving = true) }
            try {
                withContext(Dispatchers.Default) {
                val imageBytes = _ui.value.pendingImageBytes
                val id = UUID.randomUUID()
                // If this analysis came from a Saved Meals review, reuse the
                // template's existing on-disk image so we don't duplicate the
                // JPEG. Otherwise (fresh AI analysis), persist the in-memory
                // bytes as a new file under the new entry id.
                val filename = reviewSource?.imageFilename
                    ?: pendingDraftImageFilename
                    ?: imageBytes?.let { persistImage(it, id) }
                // FoodResultSheet's editedAnalysis already has serving scale
                // applied — only scale again when saving the raw pending analysis.
                val effectiveScale = if (editedAnalysis != null) 1.0 else scale
                fun s(v: Int) = (v * effectiveScale).roundToInt()
                fun macro(v: Double) = v * effectiveScale
                val entrySource = reviewSource?.source
                    ?: pendingFoodSource
                    ?: if (imageBytes != null) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT
                val rawName = name?.takeIf { it.isNotBlank() } ?: analysis.name
                // The pending draft's name was already disambiguated against the
                // diary when it was saved (savePendingDraft), so an unedited name
                // is still unique — skip the O(history) existing-name lookup at
                // confirm time. Saved-meals relogs keep resolveNewFoodName's
                // relog short-circuit so servings still merge.
                val resolvedName = if (reviewSource == null && rawName == pendingAnalysis?.name) {
                    rawName
                } else {
                    resolveNewFoodName(rawName, relogTemplate = reviewSource)
                }
                val constituents = if (effectiveScale == 1.0) {
                    analysis.constituents
                } else {
                    app.chompass.services.ai.ConstituentReconcile.scaleAll(
                        analysis.constituents,
                        effectiveScale,
                    )
                }
                val entry = analysis.toMicronutrients().scaled(effectiveScale, round1 = false).applyTo(
                    FoodEntry(
                        id = id,
                        name = resolvedName,
                        calories = s(analysis.calories),
                        protein = macro(analysis.protein),
                        carbs = macro(analysis.carbs),
                        fat = macro(analysis.fat),
                        timestamp = timestampForFoodLog(),
                        imageFilename = filename,
                        emoji = analysis.emoji,
                        source = entrySource,
                        mealType = mealType,
                        servingSizeGrams = servingGrams ?: analysis.servingSizeGrams,
                        servingUnitOptions = analysis.servingUnitOptions,
                        selectedServingUnit = if (analysis.servingUnitOptions.isEmpty()) null else selectedServingUnit,
                        selectedServingQuantity = if (analysis.servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                        customNote = analysis.customNote,
                        grounding = analysis.grounding,
                        constituents = constituents,
                        productMetadata = analysis.productMetadata,
                        microsCompositionSignature = app.chompass.models.microsCompositionSignature(constituents),
                    )
                )
                // Commit the diary row and clear the consumed pending draft in
                // one DataStore edit (crash before the edit restores the review
                // sheet from the pre-commit draft; crash after leaves the row and
                // no stale draft — strictly better than the old two-edit window
                // that could restore a review which double-logs on re-save).
                container.foodRepository.addEntry(entry, clearDraft = true, writeHealth = false)
                promoteQuickRelog(entry)
                // Health Connect mirroring is the slowest save step (IPC). Run it
                // in the background so the review sheet can dismiss as soon as the
                // diary row is on disk instead of after the HC round-trip.
                viewModelScope.launch { container.foodRepository.mirrorEntryToHealth(entry) }
                _ui.update { it.copy(
                    pendingAnalysis = null,
                    pendingImageBytes = null,
                    pendingAnalysisImages = emptyList(),
                    pendingFoodSource = null,
                    pendingDraftImageFilename = null,
                    pendingReviewSource = null
                ) }
                }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    /**
     * Commits the current pending review into the weigh-as-you-go draft.
     * When [resumeCapture] is true, HomeScreen reopens the Add Food hub; otherwise
     * the progressive meal sheet is shown so the user can Log meal / Add another.
     */
    fun addToProgressiveMeal(
        name: String? = null,
        servingGrams: Double? = null,
        mealType: String = MealType.currentMealId,
        selectedServingUnit: String? = null,
        selectedServingQuantity: Double? = null,
        editedAnalysis: FoodAnalysis,
        resumeCapture: Boolean,
    ) {
        val imageBytes = _ui.value.pendingImageBytes
        val source = _ui.value.pendingReviewSource?.source
            ?: _ui.value.pendingFoodSource
            ?: if (imageBytes != null) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT
        val analysis = editedAnalysis.copy(
            name = name?.takeIf { it.isNotBlank() } ?: editedAnalysis.name,
            servingSizeGrams = servingGrams ?: editedAnalysis.servingSizeGrams,
        )
        val item = ProgressiveMealItem(
            analysis = analysis,
            imageBytes = imageBytes,
            mealType = mealType,
            source = source,
            selectedServingUnit = selectedServingUnit,
            selectedServingQuantity = selectedServingQuantity,
        )
        val existing = _ui.value.progressiveMeal
        val draft = ProgressiveMealDraft(
            name = existing?.name.orEmpty(),
            mealType = existing?.mealType ?: mealType,
            items = (existing?.items ?: emptyList()) + item,
        )
        val previousDraftImage = _ui.value.pendingDraftImageFilename
        _ui.update { it.copy(
            progressiveMeal = draft,
            pendingAnalysis = null,
            pendingImageBytes = null,
            pendingAnalysisImages = emptyList(),
            pendingFoodSource = null,
            pendingDraftImageFilename = null,
            pendingReviewSource = null,
            resumeProgressiveCapture = resumeCapture,
            showProgressiveMealSheet = !resumeCapture,
        ) }
        viewModelScope.launch {
            discardPendingDraft(previousDraftImage)
        }
    }

    fun removeProgressiveMealItem(id: UUID) {
        val draft = _ui.value.progressiveMeal ?: return
        val remaining = draft.items.filterNot { it.id == id }
        _ui.value = if (remaining.isEmpty()) {
            _ui.value.copy(
                progressiveMeal = null,
                showProgressiveMealSheet = false,
                resumeProgressiveCapture = false,
            )
        } else {
            _ui.value.copy(progressiveMeal = draft.copy(items = remaining))
        }
    }

    fun updateProgressiveMealMeta(name: String, mealType: String) {
        val draft = _ui.value.progressiveMeal ?: return
        _ui.update { it.copy(progressiveMeal = draft.copy(name = name, mealType = mealType)) }
    }

    fun discardProgressiveMeal() {
        _ui.update { it.copy(
            progressiveMeal = null,
            showProgressiveMealSheet = false,
            resumeProgressiveCapture = false,
        ) }
    }

    /**
     * Debug-only (Codeberg #84 repro): open the meal-builder sheet with
     * [count] canned ingredients. Not [addToProgressiveMeal] — that mutates
     * pending-analysis state per call.
     */
    fun seedProgressiveMealDemo(count: Int) {
        if (!BuildConfig.DEBUG) return
        val items = (1..count).map { i ->
            ProgressiveMealItem(
                analysis = FoodAnalysis(
                    name = "Demo ingredient $i",
                    calories = 60 * i,
                    protein = 4.0 * i,
                    carbs = 5.0 * i,
                    fat = 2.0 * i,
                    servingSizeGrams = null,
                    emoji = "🥗",
                ),
            )
        }
        _ui.update { it.copy(
            progressiveMeal = ProgressiveMealDraft(items = items),
            showProgressiveMealSheet = true,
        ) }
    }

    fun consumeResumeProgressiveCapture() {
        if (_ui.value.resumeProgressiveCapture) {
            _ui.update { it.copy(resumeProgressiveCapture = false) }
        }
    }

    fun showProgressiveMealSheet(show: Boolean) {
        _ui.update { it.copy(showProgressiveMealSheet = show) }
    }

    /** Hide the meal sheet and reopen the Add Food hub for the next ingredient. */
    fun continueProgressiveCapture() {
        if (_ui.value.progressiveMeal?.items.isNullOrEmpty()) return
        _ui.update { it.copy(
            showProgressiveMealSheet = false,
            resumeProgressiveCapture = true,
        ) }
    }

    fun logProgressiveMeal() {
        val draft = _ui.value.progressiveMeal ?: return
        if (draft.items.isEmpty() || _ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.update { it.copy(saving = true) }
            try {
                val recipeLogId = UUID.randomUUID()
                val timestamp = timestampForFoodLog()
                val knownKeys = container.foodRepository.existingFoodIdentityKeys().toMutableSet()
                val built = draft.items.map { item ->
                    val entryId = UUID.randomUUID()
                    val filename = item.imageBytes?.let { persistImage(it, entryId) }
                    val analysis = item.analysis
                    val resolvedName = run {
                        val resolved = disambiguateFoodName(analysis.name, knownKeys)
                        knownKeys.add(resolved.lowercase(Locale.ROOT))
                        resolved
                    }
                    analysis.toMicronutrients().applyTo(
                        FoodEntry(
                            id = entryId,
                            name = resolvedName,
                            calories = analysis.calories,
                            protein = analysis.protein,
                            carbs = analysis.carbs,
                            fat = analysis.fat,
                            timestamp = timestamp,
                            imageFilename = filename,
                            emoji = analysis.emoji,
                            source = item.source,
                            mealType = draft.mealType,
                            servingSizeGrams = analysis.servingSizeGrams,
                            servingUnitOptions = analysis.servingUnitOptions,
                            selectedServingUnit = if (analysis.servingUnitOptions.isEmpty()) {
                                null
                            } else {
                                item.selectedServingUnit
                            },
                            selectedServingQuantity = if (analysis.servingUnitOptions.isEmpty()) {
                                null
                            } else {
                                item.selectedServingQuantity
                            },
                            customNote = analysis.customNote,
                            grounding = analysis.grounding,
                            recipeLogId = recipeLogId,
                            constituents = analysis.constituents,
                            productMetadata = analysis.productMetadata,
                            microsCompositionSignature = app.chompass.models.microsCompositionSignature(analysis.constituents),
                        )
                    )
                }
                // One batched DataStore edit for the whole meal instead of one
                // full-file write per ingredient; Health Connect mirrors in the
                // background so the sheet dismisses right after the local commit.
                container.foodRepository.addEntries(built, writeHealth = false)
                viewModelScope.launch {
                    built.forEach { container.foodRepository.mirrorEntryToHealth(it) }
                }
                _ui.update { it.copy(
                    progressiveMeal = null,
                    showProgressiveMealSheet = false,
                    resumeProgressiveCapture = false,
                ) }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    /**
     * Quick day-type switch (#60 phase 1, hero chip sheet): write or clear
     * today's per-day override ([MacroPlan.dayAssignments]) and re-journal
     * today with MANUAL_SWITCH provenance. `profileId == null` clears the
     * override, falling back to the schedule's resolution for today.
     *
     * Tapping the type already in effect today is a no-op — re-selecting the
     * resolved type must not create an override (which would only surface the
     * "clear today's override" action without changing any target).
     */
    fun switchTodayDayType(profileId: String?) {
        viewModelScope.launch {
            val current = container.profileRepository.current() ?: return@launch
            val plan = current.macroPlan?.takeIf { it.enabled } ?: return@launch
            val today = LocalDate.now()
            val todayKey = today.toString()
            if (profileId != null &&
                MacroPlanResolver.targetsFor(current, today).profileId == profileId
            ) {
                return@launch
            }
            val merged = if (profileId == null) {
                plan.dayAssignments - todayKey
            } else {
                plan.dayAssignments + (todayKey to profileId)
            }
            val updated = plan.copy(dayAssignments = merged)
                .let { it.copy(dayAssignments = it.prunedAssignments(today)) }
            container.profileRepository.save(current.copy(macroPlan = updated))
            // Journal with MANUAL_SWITCH provenance; the app-scope observer's
            // follow-up write skips because the values now match.
            runCatching { container.goalJournalService.recordManualSwitch() }
        }
    }

    suspend fun suggestMealWhatIf(entry: FoodEntry): String {
        val snapshot = _ui.value
        val profile = snapshot.profile
            ?: return container.appContext.getString(R.string.finish_onboarding_hint)
        return container.foodAnalysis.suggestMealWhatIf(
            entry = entry,
            dayEntries = snapshot.todayEntries,
            profile = profile,
            weightMetric = snapshot.weightMetric,
            resolved = snapshot.resolvedDayTargets,
        )
    }

    fun dismissPending() {
        val snapshot = _ui.value
        val previousDraftImage = snapshot.pendingDraftImageFilename
        // A completed, unsaved AI review is worth an API call: keep the
        // persisted draft for the recovered-review chip instead of discarding.
        // Saved Meals / favorites reviews cost no AI call and stay dismissible.
        val keepForRecovery = snapshot.analysisReadyForEdit && snapshot.pendingReviewSource == null
        synchronized(this) {
            ++analysisGeneration
            analysisInFlight = false
        }
        _ui.update { it.copy(
            pendingAnalysis = null,
            pendingImageBytes = null,
            pendingAnalysisImages = emptyList(),
            pendingFoodSource = null,
            pendingDraftImageFilename = null,
            pendingReviewSource = null,
            pendingGroundedReview = null,
            pendingPortionPreConfirmed = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            analyzing = false,
            error = null
        ) }
        container.analyzingFood.value = false
        viewModelScope.launch {
            if (keepForRecovery) {
                val recovered = parkRecoveredDraft(snapshot)
                if (recovered != null) {
                    _ui.update { it.copy(recoveredReview = recovered) }
                }
            } else {
                discardPendingDraft(previousDraftImage)
            }
        }
    }

    /**
     * Cancel the current (or completed) photo analysis and re-run with an optional
     * tip note and/or exact grams. Used from the progressive Log sheet tip strip.
     */
    fun reanalyzeWithTip(note: String?, confirmedPortionGrams: Double?) {
        val images = _ui.value.pendingAnalysisImages.ifEmpty {
            listOfNotNull(_ui.value.pendingImageBytes)
        }
        if (images.isEmpty()) return
        cancelInFlightAnalysisKeepInput()
        analyzePhotos(images, note, confirmedPortionGrams)
    }

    /**
     * Append photo(s) to the current pending set and re-analyze (label + plate, etc.).
     * Preserves an optional tip note / grams so adding a photo does not wipe context.
     */
    fun appendPhotosAndReanalyze(
        newImages: List<ByteArray>,
        note: String? = null,
        confirmedPortionGrams: Double? = null,
    ) {
        val added = newImages.filter { it.isNotEmpty() }
        if (added.isEmpty()) return
        val existing = _ui.value.pendingAnalysisImages.ifEmpty {
            listOfNotNull(_ui.value.pendingImageBytes)
        }
        val merged = (existing + added).take(FoodPhotoSession.MAX_IMAGES)
        if (merged.isEmpty()) return
        val tip = note?.takeIf { it.isNotBlank() }
            ?: _ui.value.pendingAnalysis?.customNote?.takeIf { it.isNotBlank() }
            ?: _ui.value.pendingInputNote?.takeIf { it.isNotBlank() }
        val grams = confirmedPortionGrams?.takeIf { it > 0 }
            ?: _ui.value.pendingInputConfirmedPortionGrams?.takeIf { it > 0 }
        cancelInFlightAnalysisKeepInput()
        analyzePhotos(merged, tip, grams)
    }

    fun retryFailedInput() {
        viewModelScope.launch {
            val snapshot = _ui.value
            val draft = container.prefs.pendingFoodInputDraft.first()
            // Multi-photo retries replay the persisted draft files (the real
            // images); single-photo-without-draft falls back to the in-memory
            // bytes captured at attempt start.
            val bytes = draft?.resolvedImageFilenames
                ?.mapNotNull { filename ->
                    runCatching { container.imageStore.file(filename).readBytes() }.getOrNull()
                }
                ?.takeIf { it.isNotEmpty() }
                ?: snapshot.pendingInputImageBytes?.let { listOf(it) }
                ?: emptyList()
            if (bytes.isEmpty()) {
                clearPendingInputDraft()
                _ui.update { it.copy(
                    pendingQueueEntryId = null,
                    error = container.appContext.getString(R.string.error_failed_input_missing)
                ) }
                return@launch
            }
            // Keep the auto-saved queue entry linked so this retry resolves it.
            _ui.update {
                it.copy(pendingQueueEntryId = draft?.queueEntryId ?: snapshot.pendingQueueEntryId)
            }
            analyzePhotos(
                bytes,
                snapshot.pendingInputNote.orEmpty(),
                snapshot.pendingInputConfirmedPortionGrams,
            )
        }
    }

    fun dismissFailedInput() {
        viewModelScope.launch {
            clearPendingInputDraft()
            _ui.update { it.copy(error = null, pendingQueueEntryId = null, pendingPromptText = null) }
        }
    }

    fun clearError() {
        // No retryable input left: the failed prompt stays in the analysis
        // queue (Codeberg #53), but the retry link is dropped so the next
        // failure creates a fresh entry.
        _ui.update { it.copy(error = null, pendingQueueEntryId = null, pendingPromptText = null) }
    }

    // -- Analysis queue (Codeberg #53) ------------------------------------

    /**
     * Manual enqueue from the photo staging sheet: store time + photos +
     * description WITHOUT an AI call, runnable later from the queue sheet.
     */
    fun queueStaged(images: List<ByteArray>, note: String?, confirmedPortionGrams: Double?) {
        viewModelScope.launch {
            val filtered = images.filter { it.isNotEmpty() }
            if (filtered.isEmpty()) return@launch
            val id = UUID.randomUUID()
            val filenames = container.analysisQueue.storeImages(id, filtered)
            if (filenames.isEmpty()) return@launch
            val trimmed = note?.trim().orEmpty()
            container.analysisQueue.upsert(
                QueuedAnalysis(
                    id = id,
                    createdAt = Instant.now(),
                    targetDate = _selectedDate.value,
                    imageFilenames = filenames,
                    note = trimmed.takeIf { it.isNotEmpty() },
                    confirmedPortionGrams = confirmedPortionGrams?.takeIf { it > 0 },
                    source = FoodSource.SNAP_FOOD,
                    status = QueueStatus.PENDING,
                )
            )
            _ui.update { it.copy(pendingQueueEntryId = null, showAnalysisQueue = true) }
        }
    }

    fun runQueuedItem(id: UUID) {
        viewModelScope.launch { runQueuedItemInternal(id) }
    }

    /**
     * Runs each PENDING item in order; stops at the first success (the review
     * sheet opens). Failures stay PENDING with the error recorded and the next
     * item is tried.
     */
    fun runAllQueued() {
        viewModelScope.launch {
            val pending = _ui.value.queueEntries.filter { it.status == QueueStatus.PENDING }
            for (item in pending) {
                if (runQueuedItemInternal(item.id)) break
            }
        }
    }

    /**
     * Runs one queued item through the shared analysis envelope (provider
     * dispatch + fallback, streaming partials, error mapping — identical to a
     * live analysis). A run result opens the FoodResultSheet review, logging
     * to the entry's target day. Returns true when the review is ready.
     */
    private suspend fun runQueuedItemInternal(id: UUID): Boolean {
        val item = container.analysisQueue.item(id) ?: return false
        if (item.status != QueueStatus.PENDING) return false
        if (_ui.value.isEntryAnalysisBusy) return false
        _ui.update { it.copy(queueRunningId = id, showAnalysisQueue = false) }
        try {
            _selectedDate.value = item.targetDate
            val images = item.imageFilenames.mapNotNull { filename ->
                runCatching { container.analysisQueue.loadImage(filename) }.getOrNull()
            }
            val note = item.note?.trim()?.takeIf { it.isNotEmpty() }
            withFoodAnalysis(
                phased = true,
                configure = { state ->
                    state.copy(
                        pendingImageBytes = images.firstOrNull(),
                        pendingAnalysisImages = images,
                        pendingFoodSource = item.source,
                        pendingDraftImageFilename = null,
                        pendingPromptText = null,
                        // Failure auto-save updates this same entry (no duplicates);
                        // success resolves it via savePendingDraft.
                        pendingQueueEntryId = id,
                    )
                },
            ) { start ->
                if (images.isEmpty() && note.isNullOrBlank()) {
                    throw AiError.InvalidResponse
                }
                val analysis = container.foodAnalysis.analyzeFood(
                    images,
                    note,
                    singleIngredient = item.singleIngredient,
                    confirmedPortionGrams = item.confirmedPortionGrams,
                ) { progress ->
                    onFoodAnalysisProgress(start.generation, progress)
                }.copy(customNote = note)
                savePendingDraft(
                    analysis,
                    imageBytes = images.firstOrNull(),
                    source = item.source,
                    generation = start.generation,
                )
            }
            return _ui.value.pendingAnalysis != null
        } finally {
            _ui.update { it.copy(queueRunningId = null) }
        }
    }

    /** Edits note / confirmed grams / target day of a queued entry. */
    fun updateQueued(
        id: UUID,
        note: String?,
        confirmedPortionGrams: Double?,
        targetDate: LocalDate,
    ) {
        viewModelScope.launch {
            val item = container.analysisQueue.item(id) ?: return@launch
            val trimmed = note?.trim().orEmpty()
            container.analysisQueue.upsert(
                item.copy(
                    note = trimmed.takeIf { it.isNotEmpty() },
                    confirmedPortionGrams = confirmedPortionGrams?.takeIf { it > 0 },
                    targetDate = targetDate,
                )
            )
        }
    }

    fun addQueuedPhotos(id: UUID, bytes: List<ByteArray>) {
        viewModelScope.launch {
            val item = container.analysisQueue.item(id) ?: return@launch
            val filtered = bytes.filter { it.isNotEmpty() }
            if (filtered.isEmpty()) return@launch
            val newFiles = container.analysisQueue.storeImages(id, filtered)
            if (newFiles.isEmpty()) return@launch
            container.analysisQueue.upsert(
                item.copy(
                    imageFilenames = (item.imageFilenames + newFiles)
                        .take(FoodPhotoSession.MAX_IMAGES),
                )
            )
        }
    }

    fun removeQueuedPhoto(id: UUID, filename: String) {
        viewModelScope.launch {
            val item = container.analysisQueue.item(id) ?: return@launch
            if (filename !in item.imageFilenames) return@launch
            container.analysisQueue.upsert(
                item.copy(imageFilenames = item.imageFilenames - filename)
            )
            container.analysisQueue.deleteImages(listOf(filename))
        }
    }

    fun deleteQueued(id: UUID) {
        viewModelScope.launch { container.analysisQueue.delete(id) }
    }

    fun clearQueueHistory() {
        viewModelScope.launch { container.analysisQueue.clearHistory() }
    }

    fun openQueue() {
        _ui.update { it.copy(showAnalysisQueue = true) }
    }

    fun dismissQueue() {
        _ui.update { it.copy(showAnalysisQueue = false) }
    }

    /**
     * Tap a row in Saved Meals (Recents / Frequent / Favorites) → open the
     * FoodResultSheet for review instead of logging immediately. The user
     * can edit name / serving / meal type, then tap "Log" to commit. Mirrors
     * iOS RecentsView's `onReview` callback path.
     */
    fun reviewSavedMeal(template: FoodEntry) {
        val analysis = template.toAnalysis()
        val bytes = template.imageFilename?.let {
            runCatching { container.imageStore.file(it).readBytes() }.getOrNull()
        }
        _ui.update { it.copy(
            pendingAnalysis = analysis,
            pendingImageBytes = bytes,
            pendingAnalysisImages = listOfNotNull(bytes),
            pendingFoodSource = template.source,
            pendingDraftImageFilename = null,
            pendingReviewSource = template,
            pendingPortionPreConfirmed = false,
            error = null
        ) }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.deleteEntry(entry)
        }
    }

    fun restoreEntry(entry: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.addEntry(entry)
        }
    }

    fun toggleFavorite(entry: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.toggleFavorite(entry)
        }
    }

    /**
     * Permanently edit a stored favorite (Codeberg #66): the saved-foods
     * library row is updated in place; diary rows are untouched.
     */
    fun updateFavorite(original: FoodEntry, updated: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.updateFavorite(original, updated)
        }
    }

    fun updateEntry(
        original: FoodEntry,
        updated: FoodEntry,
        applyTimeToMeal: Boolean = false,
    ) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val siblings = if (applyTimeToMeal && original.timestamp != updated.timestamp) {
                siblingEntriesForTimeApply(_ui.value.todayEntries, original, zone)
            } else {
                emptyList()
            }
            container.foodRepository.updateEntry(original, updated)
            for (sibling in siblings) {
                container.foodRepository.updateEntry(
                    sibling,
                    sibling.copy(timestamp = updated.timestamp),
                )
            }
        }
    }

    /** Re-log a saved meal (from Saved Meals sheet) as a new entry timestamped to the selected day. */
    fun relogMeal(template: FoodEntry) {
        if (PerfLog.enabled) {
            relogAckAtNs = System.nanoTime()
            relogAckPriorCount = _ui.value.todayEntries.size
        }
        promoteQuickRelog(template)
        viewModelScope.launch {
            PerfLog.measure("relog", "addEntry", "name=${template.name}") {
                container.foodRepository.addEntry(
                    template.duplicatedForLogging(timestampForFoodLog(), loggingMealId(template.mealType)),
                )
            }
        }
    }

    /** Log every ingredient of a Recipe as its own diary row, timestamped to the selected day. */
    fun logRecipe(recipe: app.chompass.models.Recipe) {
        viewModelScope.launch {
            container.recipeRepository.logRecipe(recipe, timestampForFoodLog())
        }
    }

    fun saveRecipe(recipe: app.chompass.models.Recipe) {
        viewModelScope.launch {
            container.recipeRepository.saveRecipe(recipe)
        }
    }

    /** Copy rows onto the currently viewed day (selection-bar paste). */
    fun copyEntriesToSelectedDay(entries: List<FoodEntry>) =
        copyEntriesToDate(entries, _selectedDate.value)

    /**
     * Copy [entries] onto [targetDate] (Copy-from-day sheet; the target is the
     * viewed day by default but is now pickable inside the sheet).
     */
    fun copyEntriesToDate(entries: List<FoodEntry>, targetDate: LocalDate) {
        if (entries.isEmpty() || _ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.update { it.copy(saving = true) }
            try {
                // Upstream #149 / Android 6.0: reused copies log at now + current meal
                // (not the source entry's clock time / meal bucket). One batched
                // DataStore edit instead of one full-file write per copied row;
                // Health Connect mirrors in the background.
                val duplicated = entries.map {
                    it.duplicatedForLogging(timestampForFoodLog(targetDate), loggingMealId(it.mealType))
                }
                container.foodRepository.addEntries(duplicated, writeHealth = false)
                viewModelScope.launch {
                    duplicated.forEach { container.foodRepository.mirrorEntryToHealth(it) }
                }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    /** Remember selected diary rows for a later paste (selection-bar Copy). */
    fun setCopiedEntries(entries: List<FoodEntry>) {
        _ui.update { it.copy(copiedEntries = entries) }
    }

    /** Drop the paste clipboard (chip dismiss / app state reset). */
    fun clearCopiedEntries() {
        if (_ui.value.copiedEntries.isNotEmpty()) {
            _ui.update { it.copy(copiedEntries = emptyList()) }
        }
    }

    /** Save a user-typed entry with no AI involvement (manual macro input from issue #15). */
    fun saveManualEntry(
        name: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        micronutrients: MicronutrientValues = MicronutrientValues(),
        mealType: String = MealType.currentMealId,
        servingSizeGrams: Double = 0.0,
        servingUnitOptions: List<ServingUnitOption> = emptyList(),
        selectedServingUnit: String? = null,
        selectedServingQuantity: Double? = null,
    ) {
        if (_ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.update { it.copy(saving = true) }
            try {
                container.foodRepository.addEntry(
                    micronutrients.applyTo(
                        FoodEntry(
                            name = disambiguateFoodName(
                                name,
                                container.foodRepository.existingFoodIdentityKeys(),
                            ),
                            calories = calories,
                            protein = protein,
                            carbs = carbs,
                            fat = fat,
                            timestamp = timestampForFoodLog(),
                            source = FoodSource.MANUAL,
                            mealType = mealType,
                            servingSizeGrams = servingSizeGrams,
                            servingUnitOptions = servingUnitOptions,
                            selectedServingUnit = if (servingUnitOptions.isEmpty()) null else selectedServingUnit,
                            selectedServingQuantity = if (servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                        )
                    )
                )
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    /**
     * Water / non-food: selected day + wall clock. Food logs use
     * [timestampForFoodLog] so a session stamp does not move sips.
     */
    private fun timestampForSelectedDay(): Instant = timestampForDate(_selectedDate.value)

    private fun timestampForDate(day: LocalDate): Instant =
        timestampForLogging(day, Instant.now(), ZoneId.systemDefault(), timeOverride = null)

    private fun timestampForFoodLog(day: LocalDate = _selectedDate.value): Instant =
        timestampForLogging(
            day,
            Instant.now(),
            ZoneId.systemDefault(),
            _ui.value.logTimeOverride,
        )

    private fun loggingMealId(templateMealType: String): String =
        loggingSlotFor(
            templateMealType,
            _ui.value.mealTimesEnabled,
            CurrentMealCatalog.value,
            _ui.value.logTimeOverride,
            LocalTime.now(),
        )

    private suspend fun savePendingDraft(
        analysis: FoodAnalysis,
        imageBytes: ByteArray?,
        source: FoodSource,
        generation: Int,
        portionPreConfirmed: Boolean = false,
    ) {
        if (generation != analysisGeneration) return
        val uniqueAnalysis = analysis.copy(
            name = disambiguateFoodName(
                analysis.name,
                container.foodRepository.existingFoodIdentityKeys(),
            )
        )
        val imageFilename = imageBytes?.let { persistImage(it, UUID.randomUUID()) }
        container.prefs.setPendingFoodAnalysisDraft(
            PendingFoodAnalysisDraft(
                analysis = uniqueAnalysis,
                imageFilename = imageFilename,
                source = source,
                // Codeberg #16 family: keep the diary day the sheet was opened
                // for, so a process-death restore still logs to that day.
                targetDate = _selectedDate.value
            )
        )
        if (generation != analysisGeneration) return
        resolveQueueEntryOnSuccess(uniqueAnalysis, source)
        _ui.update { it.copy(
            analyzing = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            pendingAnalysis = uniqueAnalysis,
            pendingImageBytes = imageBytes,
            pendingFoodSource = source,
            pendingDraftImageFilename = imageFilename,
            pendingReviewSource = null,
            pendingInputImageBytes = null,
            pendingInputNote = null,
            pendingInputConfirmedPortionGrams = null,
            pendingPromptText = null,
            pendingQueueEntryId = null,
            pendingPortionPreConfirmed = portionPreConfirmed,
            pendingInputDraftImageFilenames = emptyList()
        ) }
    }

    /**
     * Codeberg #53 success path: a successful analysis resolves the auto-saved
     * queue entry for this input (markDone), or — for a fresh prompt that never
     * failed — records a DONE history row so the prompt history is complete
     * and past prompts stay re-runnable. Barcode/search/grounded flows carry
     * no prompt input and are not recorded.
     */
    private suspend fun resolveQueueEntryOnSuccess(analysis: FoodAnalysis, source: FoodSource) {
        val s = _ui.value
        val images = s.pendingAnalysisImages.filter { it.isNotEmpty() }
        val text = s.pendingPromptText?.trim().orEmpty()
        if (images.isEmpty() && text.isEmpty()) return
        val store = container.analysisQueue
        val linkedId = s.pendingQueueEntryId
        if (linkedId != null) {
            store.markDone(linkedId, analysis)
        } else {
            val id = UUID.randomUUID()
            val filenames = store.storeImages(id, images)
            store.upsert(
                QueuedAnalysis(
                    id = id,
                    createdAt = Instant.now(),
                    targetDate = _selectedDate.value,
                    imageFilenames = filenames,
                    note = text.ifEmpty { analysis.customNote?.trim()?.takeIf { it.isNotEmpty() } },
                    confirmedPortionGrams = s.pendingInputConfirmedPortionGrams,
                    singleIngredient = s.progressiveMeal?.items?.isNotEmpty() == true,
                    source = source,
                    status = QueueStatus.DONE,
                    result = analysis,
                )
            )
        }
    }

    private fun restorePendingDraft(draft: PendingFoodAnalysisDraft) {
        val bytes = draft.imageFilename?.let {
            runCatching { container.imageStore.file(it).readBytes() }.getOrNull()
        }
        // Re-check collisions: the diary may have grown since the draft was saved.
        viewModelScope.launch {
            // Restore the diary day the draft was opened for — a fresh ViewModel
            // starts on today, and the modal review sheet hides the day strip, so
            // without this the Log button would silently land on today (Codeberg
            // #16 family: "entry landed on today's log" after process death).
            _selectedDate.value = draft.targetDate
            val unique = draft.analysis.copy(
                name = disambiguateFoodName(
                    draft.analysis.name,
                    container.foodRepository.existingFoodIdentityKeys(),
                )
            )
            _ui.update { it.copy(
                analyzing = false,
                analysisPhase = null,
                analysisPreview = null,
                analysisPartial = null,
                inferringUnits = false,
                pendingAnalysis = unique,
                pendingImageBytes = bytes,
                pendingAnalysisImages = listOfNotNull(bytes),
                pendingFoodSource = draft.source,
                pendingDraftImageFilename = draft.imageFilename,
                pendingReviewSource = null,
                pendingInputImageBytes = null,
                pendingInputNote = null,
                pendingInputConfirmedPortionGrams = null,
                pendingPortionPreConfirmed = false,
                pendingInputDraftImageFilenames = emptyList(),
                pendingQueueEntryId = null,
                pendingPromptText = null,
                error = null
            ) }
        }
    }

    /**
     * Keep [rawName] when re-logging the same Saved Meals food; otherwise
     * append (2), (3), … if the name already identifies another food.
     */
    private suspend fun resolveNewFoodName(rawName: String, relogTemplate: FoodEntry?): String {
        val trimmed = rawName.trim()
        if (relogTemplate != null && trimmed.lowercase(Locale.ROOT) == relogTemplate.favoriteKey) {
            return trimmed.ifEmpty { rawName }
        }
        return disambiguateFoodName(rawName, container.foodRepository.existingFoodIdentityKeys())
    }

    private suspend fun savePendingInputDraft(
        imageBytesList: List<ByteArray>,
        note: String,
        source: FoodSource = FoodSource.SNAP_FOOD,
        confirmedPortionGrams: Double? = null,
        queueEntryId: UUID? = null,
    ) {
        // Multi-photo since Codeberg #53: every staged photo survives a failed
        // AI call, so the failure dialog's Retry covers all photo cases.
        val previousFilenames = _ui.value.pendingInputDraftImageFilenames
            .ifEmpty { container.prefs.pendingFoodInputDraft.first()?.resolvedImageFilenames.orEmpty() }
        val filenames = imageBytesList.mapNotNull { persistImage(it, UUID.randomUUID()) }
        if (filenames.isEmpty()) return
        for (previous in previousFilenames) {
            if (previous !in filenames) container.imageStore.delete(previous)
        }
        val grams = confirmedPortionGrams?.takeIf { it > 0 }
        container.prefs.setPendingFoodInputDraft(
            PendingFoodInputDraft(
                imageFilenames = filenames,
                note = note,
                confirmedPortionGrams = grams,
                source = source,
                // Same day-restore intent as [PendingFoodAnalysisDraft.targetDate].
                targetDate = _selectedDate.value,
                queueEntryId = queueEntryId,
            )
        )
        _ui.update { it.copy(
            pendingInputImageBytes = imageBytesList.firstOrNull(),
            pendingInputNote = note,
            pendingInputConfirmedPortionGrams = grams,
            pendingInputDraftImageFilenames = filenames
        ) }
    }

    private suspend fun restorePendingInputDraft(draft: PendingFoodInputDraft) {
        // Re-target the diary day the input sheet was opened for (same rationale
        // as [restorePendingDraft]): the resumed Log must land on that day.
        _selectedDate.value = draft.targetDate
        val bytes = draft.resolvedImageFilenames.mapNotNull { filename ->
            runCatching { container.imageStore.file(filename).readBytes() }.getOrNull()
        }
        if (bytes.isEmpty()) {
            clearPendingInputDraft()
            _ui.update { it.copy(
                error = container.appContext.getString(R.string.error_failed_input_missing)
            ) }
            return
        }
        _ui.update { it.copy(
            pendingInputImageBytes = bytes.firstOrNull(),
            pendingInputNote = draft.note,
            pendingInputConfirmedPortionGrams = draft.confirmedPortionGrams?.takeIf { it > 0 },
            pendingInputDraftImageFilenames = draft.resolvedImageFilenames,
            pendingQueueEntryId = draft.queueEntryId,
            error = null
        ) }
    }

    private suspend fun clearPendingInputDraft() {
        // Note: deliberately keeps [HomeUiState.pendingQueueEntryId] — the
        // success path clears the input draft before savePendingDraft resolves
        // the linked queue entry. Dismiss clears the link explicitly.
        val filenames = _ui.value.pendingInputDraftImageFilenames
            .ifEmpty { container.prefs.pendingFoodInputDraft.first()?.resolvedImageFilenames.orEmpty() }
        container.prefs.setPendingFoodInputDraft(null)
        filenames.forEach { container.imageStore.delete(it) }
        _ui.update { it.copy(
            pendingInputImageBytes = null,
            pendingInputNote = null,
            pendingInputConfirmedPortionGrams = null,
            pendingInputDraftImageFilenames = emptyList()
        ) }
    }

    /**
     * Marks the persisted draft of a dismissed completed review as awaiting
     * review (the Home recovered-analysis chip). [snapshot] was captured
     * before the dismiss cleared the pending fields.
     */
    private suspend fun parkRecoveredDraft(snapshot: HomeUiState): PendingFoodAnalysisDraft? {
        val analysis = snapshot.pendingAnalysis ?: return null
        val existing = container.prefs.pendingFoodAnalysisDraft.first()
        val imageFilename = snapshot.pendingDraftImageFilename
            ?: existing?.imageFilename
            ?: snapshot.pendingImageBytes?.let { persistImage(it, UUID.randomUUID()) }
        val draft = PendingFoodAnalysisDraft(
            analysis = analysis,
            imageFilename = imageFilename,
            source = snapshot.pendingFoodSource,
            targetDate = _selectedDate.value,
            awaitingReview = true,
        )
        container.prefs.setPendingFoodAnalysisDraft(draft)
        return draft
    }

    /** Chip tap: reopen the dismissed review sheet (no new AI call). */
    fun restoreRecoveredReview() {
        val draft = _ui.value.recoveredReview ?: return
        _ui.update { it.copy(recoveredReview = null) }
        viewModelScope.launch {
            // Un-mark first so a process death while the restored sheet is
            // open keeps the pre-recovery auto-restore behavior.
            container.prefs.pendingFoodAnalysisDraft.first()
                ?.takeIf { it.awaitingReview }
                ?.let { container.prefs.setPendingFoodAnalysisDraft(it.copy(awaitingReview = false)) }
        }
        restorePendingDraft(draft)
    }

    /** Chip close: drop the recovered review and its photo for good. */
    fun discardRecoveredReview() {
        val draft = _ui.value.recoveredReview ?: return
        _ui.update { it.copy(recoveredReview = null) }
        viewModelScope.launch {
            discardPendingDraft(draft.imageFilename)
        }
    }

    private suspend fun discardPendingDraft(imageFilename: String? = _ui.value.pendingDraftImageFilename) {
        val filename = imageFilename ?: container.prefs.pendingFoodAnalysisDraft.first()?.imageFilename
        container.prefs.setPendingFoodAnalysisDraft(null)
        filename?.let { container.imageStore.delete(it) }
    }

    private suspend fun persistImage(bytes: ByteArray, entryId: UUID): String? =
        withContext(Dispatchers.IO) {
            PerfLog.measure("save", "imageWrite", "bytes=${bytes.size}") {
                container.imageStore.storeBytes(bytes, entryId)
            }
        }

    private fun promoteQuickRelog(template: FoodEntry) {
        val cache = quickRelogCache ?: return
        val recents = (listOf(template) +
            cache.recents.filter { it.favoriteKey != template.favoriteKey }).take(10)
        val frequents = cache.frequents.filter { it.favoriteKey != template.favoriteKey }
        quickRelogCache = QuickRelogRows(recents, frequents)
    }

    /** Hero ⓘ → recalc details: open the persisted goal-change sheet. */
    fun openRecalcDetails() {
        _ui.update { it.copy(recalcSheet = it.lastRecalcSheet) }
    }

    fun dismissRecalcSheet() {
        _ui.update { it.copy(recalcSheet = null) }
    }

    private fun observePerfBench() {
        if (!PerfLog.enabled) return
        container.perfBenchInbox
            .onEach { req ->
                if (req == null) return@onEach
                container.perfBenchInbox.value = null
                runCatching { handlePerfBench(req) }
                    .onFailure {
                        android.util.Log.w(PerfLog.TAG, "op=perfBench phase=fail err=${it.message}")
                    }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun handlePerfBench(req: app.chompass.services.PerfBenchRequest) {
        when (req) {
            is app.chompass.services.PerfBenchRequest.Relog -> benchRelog(req.count)
            is app.chompass.services.PerfBenchRequest.LocalEntry -> benchLocalEntry(req.count)
            is app.chompass.services.PerfBenchRequest.WaterSip -> benchWaterSip(req.count)
            is app.chompass.services.PerfBenchRequest.DaySwitch -> benchDaySwitch(req.count)
            is app.chompass.services.PerfBenchRequest.HubOpen -> benchHubOpen(req.count)
            is app.chompass.services.PerfBenchRequest.Flip -> {
                android.util.Log.i(PerfLog.TAG, "op=flipBench phase=start relog=${req.relog} local=${req.local} sips=${req.sips}")
                benchHubOpen(1)
                benchRelog(req.relog)
                benchLocalEntry(req.local)
                benchWaterSip(req.sips)
                benchDaySwitch(1)
                android.util.Log.i(PerfLog.TAG, "op=flipBench phase=done")
            }
        }
    }

    private suspend fun benchHubOpen(count: Int) {
        repeat(count) { i ->
            quickRelogCache = null
            quickRelogEpoch++
            val rows = PerfLog.measure("hubOpen", "benchLoad", "i=$i") {
                loadQuickRelogCached()
            }
            android.util.Log.i(
                PerfLog.TAG,
                "op=hubOpen phase=benchRows i=$i recents=${rows.recents.size} frequents=${rows.frequents.size}",
            )
        }
    }

    private suspend fun benchRelog(count: Int) {
        android.util.Log.i(PerfLog.TAG, "op=relogBench phase=start count=$count")
        val rows = loadQuickRelogCached()
        val template = rows.recents.firstOrNull() ?: rows.frequents.firstOrNull()
        if (template == null) {
            android.util.Log.w(PerfLog.TAG, "op=relogBench phase=done count=0 ok=0 fail=0 err=no-hub-rows")
            return
        }
        var ok = 0
        repeat(count) { i ->
            val ms = awaitUiAck {
                relogMeal(template)
            }
            android.util.Log.i(
                PerfLog.TAG,
                "op=relogBench phase=uiAck i=$i ms=$ms name=${template.name}",
            )
            ok++
        }
        android.util.Log.i(PerfLog.TAG, "op=relogBench phase=done count=$count ok=$ok fail=0")
    }

    private suspend fun benchLocalEntry(count: Int) {
        android.util.Log.i(PerfLog.TAG, "op=entryLocal phase=start count=$count")
        var ok = 0
        repeat(count) { i ->
            val canned = FoodEntry(
                name = "Bench Oats $i",
                calories = 350,
                protein = 12.0,
                carbs = 55.0,
                fat = 8.0,
                source = FoodSource.MANUAL,
                mealType = MealType.BREAKFAST.id,
            )
            val ms = awaitUiAck {
                if (PerfLog.enabled) {
                    relogAckAtNs = System.nanoTime()
                    relogAckPriorCount = _ui.value.todayEntries.size
                }
                promoteQuickRelog(canned)
                viewModelScope.launch {
                    PerfLog.measure("entryLocal", "addEntry", "i=$i") {
                        container.foodRepository.addEntry(
                            canned.duplicatedForLogging(timestampForFoodLog(), loggingMealId(canned.mealType)),
                        )
                    }
                }
            }
            android.util.Log.i(PerfLog.TAG, "op=entryLocal phase=uiAck i=$i ms=$ms")
            ok++
        }
        android.util.Log.i(PerfLog.TAG, "op=entryLocal phase=done count=$count ok=$ok fail=0")
    }

    private suspend fun benchWaterSip(count: Int) {
        android.util.Log.i(PerfLog.TAG, "op=waterSip phase=start count=$count")
        var ok = 0
        repeat(count) { i ->
            val deferred = CompletableDeferred<Long>()
            waterAckWaiter = deferred
            waterAckAtNs = System.nanoTime()
            waterAckPriorMl = _ui.value.waterTodayMl
            addWater(250)
            val ms = kotlinx.coroutines.withTimeoutOrNull(20_000) { deferred.await() } ?: -1L
            android.util.Log.i(PerfLog.TAG, "op=waterSip phase=uiAck i=$i ms=$ms")
            ok++
        }
        android.util.Log.i(PerfLog.TAG, "op=waterSip phase=done count=$count ok=$ok fail=0")
    }

    private suspend fun benchDaySwitch(count: Int) {
        repeat(count) { i ->
            val today = _selectedDate.value
            setSelectedDate(today.minusDays(1))
            kotlinx.coroutines.delay(50)
            val start = System.nanoTime()
            setSelectedDate(today)
            var spins = 0
            while (daySwitchStartedAtNs != 0L && spins < 200) {
                kotlinx.coroutines.delay(10)
                spins++
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            android.util.Log.i(PerfLog.TAG, "op=daySwitch phase=bench i=$i ms=$ms")
        }
    }

    private suspend fun awaitUiAck(block: () -> Unit): Long {
        val deferred = CompletableDeferred<Long>()
        uiAckWaiter = deferred
        block()
        return kotlinx.coroutines.withTimeoutOrNull(20_000) { deferred.await() } ?: -1L
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(container) as T
    }

    suspend fun reprocessFoodEntry(
        entry: FoodEntry,
        updatedNote: String,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): FoodAnalysis {
        val imageBytes = entry.imageFilename?.let {
            runCatching { container.imageStore.file(it).readBytes() }.getOrNull()
        }
        // Compose name + serving + note so a photo-less (text / voice / emoji) entry
        // keeps its food context instead of re-analyzing the bare note; a photo entry
        // gets the name/note as extra grounding on top of the image.
        val description = reprocessDescription(entry, updatedNote)
        val result = if (imageBytes != null) {
            container.foodAnalysis.analyzeFood(
                imageBytes,
                description.takeIf { it.isNotBlank() },
                onProgress = onProgress,
            )
        } else {
            container.foodAnalysis.analyzeText(description, onProgress = onProgress)
        }
        return result.copy(customNote = updatedNote.takeIf { it.isNotBlank() })
    }

    private fun reprocessDescription(entry: FoodEntry, note: String): String {
        val parts = mutableListOf<String>()
        entry.name.trim().takeIf { it.isNotEmpty() }?.let { parts += it }
        val qty = entry.selectedServingQuantity
        val unit = entry.selectedServingUnit?.trim()
        if (qty != null && qty > 0 && !unit.isNullOrEmpty()) {
            val q = if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
            parts += "$q $unit"
        } else {
            entry.servingSizeGrams?.takeIf { it > 0 }?.let { parts += "${it.toInt()} g" }
        }
        val base = parts.joinToString(", ")
        val trimmed = note.trim()
        return when {
            base.isEmpty() -> trimmed
            trimmed.isEmpty() -> base
            else -> "$base. $trimmed"
        }
    }
}

/**
 * Map a logged FoodEntry back into a FoodAnalysis so the FoodResultSheet
 * (which only knows how to render a FoodAnalysis) can review a saved meal
 * before re-logging. The serving size defaults to 100g if the original entry
 * didn't record one — same fallback as EditFoodEntrySheet.
 */
private fun FoodEntry.toAnalysis(): FoodAnalysis = MicronutrientValues.from(this).applyTo(
    FoodAnalysis(
        name = name,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        // Null when the entry never recorded a serving: the review sheet then
        // treats macros as absolute portion totals (no scaling on weight edits,
        // Codeberg #10 follow-up) instead of inventing a 100 g base.
        servingSizeGrams = servingSizeGrams,
        emoji = emoji,
        servingUnitOptions = servingUnitOptions,
        selectedServingUnit = selectedServingUnit,
        selectedServingQuantity = selectedServingQuantity,
        customNote = customNote,
        grounding = grounding,
        constituents = constituents,
        productMetadata = productMetadata,
    )
)

/** Dynamic-water inputs bundled for the nested combine in [HomeViewModel]. */
private data class WaterDynamicPrefs(
    val manualGoalMl: Int,
    val dynamicEnabled: Boolean,
    val baseSource: String,
    val tempC: Int,
    val useProfileActivity: Boolean,
)

/** Drinking-window prefs bundled for the water-plan trigger combine. */
private data class WaterWindowPrefs(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
)
