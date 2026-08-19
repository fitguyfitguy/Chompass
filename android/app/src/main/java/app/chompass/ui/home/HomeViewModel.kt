package app.chompass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.data.QuickRelogRows
import app.chompass.data.disambiguateFoodName
import app.chompass.models.ActiveBurnShade
import app.chompass.models.ActiveCalorieSource
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.DietMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.ResolvedActiveBurn
import app.chompass.models.HomeTopNutrient
import app.chompass.models.ManualActiveEntry
import app.chompass.models.MealType
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.PendingFoodAnalysisDraft
import app.chompass.models.PendingFoodInputDraft
import app.chompass.models.ProgressiveMealDraft
import app.chompass.models.ProgressiveMealItem
import app.chompass.models.ServingUnitOption
import app.chompass.models.UserProfile
import app.chompass.models.ActivityLevel
import app.chompass.models.WaterGoalCalculator
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WaterEntry
import app.chompass.services.FoodImageComposer
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
    val todayEntries: List<FoodEntry> = emptyList(),
    val homeDisplay: HomeDisplayPreferences = HomeDisplayPreferences(),
    val homeTopNutrients: List<HomeTopNutrient> = HomeTopNutrient.DefaultSelection,
    val foodLogMacroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
    /** Measured Health Connect active kcal/day average (Energy Burn Goals). 0 = unavailable. */
    val measuredActiveAverageCalories: Int = 0,
    val activitySnapshot: HomeActivitySnapshot = HomeActivitySnapshot(date = LocalDate.now()),
    val optionalNutrientGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val foodLogSortOrder: FoodLogSortOrder = FoodLogSortOrder.STANDARD,
    val preferGramsByDefault: Boolean = false,
    val portionClarifyEnabled: Boolean = false,
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
    val pendingInputImageBytes: ByteArray? = null,
    val pendingInputNote: String? = null,
    val pendingInputConfirmedPortionGrams: Double? = null,
    /**
     * True when the pending review already received exact grams on multi-photo /
     * context note — skip the portion-clarify row on [FoodResultSheet].
     */
    val pendingPortionPreConfirmed: Boolean = false,
    val pendingInputDraftImageFilename: String? = null,
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
    /** True when the goal shown comes from the dynamic calculator (issue #3). */
    val waterGoalDynamic: Boolean = false,
    /**
     * Next planned drink from the adaptive reminder chain (one cup, capped by
     * the goal remainder, at [Plan.nextFireMillis]); null when water tracking /
     * the reminder is off, the goal is met, or the window is degenerate.
     */
    val waterNextPlan: app.chompass.services.WaterReminderPlanner.Plan? = null,
    /** In-progress weigh-as-you-go meal (photo-per-ingredient). Null when idle. */
    val progressiveMeal: ProgressiveMealDraft? = null,
    /** HomeScreen consumes this once to reopen the camera after Add next ingredient. */
    val resumeProgressiveCapture: Boolean = false,
    /** Show [ProgressiveMealSheet] when the draft has items and capture is idle. */
    val showProgressiveMealSheet: Boolean = false,
    val manualActiveKcal: Int = 0,
    /**
     * Diary entries copied via the selection bar, waiting to be pasted onto
     * the viewed day (in-memory only, cleared on app restart). Empty when the
     * clipboard is unset.
     */
    val copiedEntries: List<FoodEntry> = emptyList(),
) {
    val isEntryAnalysisBusy: Boolean get() = analyzing || analysisPhase != null || inferringUnits
    /** Progressive Log sheet: analysis running or a completed review is waiting. */
    val showFoodResultSheet: Boolean get() = pendingAnalysis != null || isEntryAnalysisBusy
    /** Fields + Log unlocked only after the AI call (and unit inference) finish. */
    val analysisReadyForEdit: Boolean get() = pendingAnalysis != null && !isEntryAnalysisBusy
    val caloriesToday: Int get() = todayEntries.sumOf { it.calories }
    val proteinToday: Double get() = todayEntries.sumOf { it.protein }
    val carbsToday: Double get() = todayEntries.sumOf { it.carbs }
    val fatToday: Double get() = todayEntries.sumOf { it.fat }
    val baseCalorieGoal: Int get() = profile?.effectiveCalories ?: 2000
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
     * burn (or debug data) plus manual entries. Mode-independent — unlike
     * [displayActiveCalories], which is 0 in STATIC mode. Feeds the hero's
     * "N active" caption so the toggle works in STATIC too.
     */
    val liveActiveBurn: Int get() =
        activitySnapshot.activeCalories.coerceAtLeast(0) + manualActiveKcal.coerceAtLeast(0)

    /** True when the home activity snapshot carries live measured/debug burn for the day. */
    val hasLiveBurn: Boolean get() {
        val s = activitySnapshot
        return (s.source == ActivityDataSource.HEALTH_CONNECT || s.source == ActivityDataSource.DEBUG) &&
            s.activeCalories > 0
    }

    /** The day's active norm: measured Health Connect 14-day average, else the PAL estimate. */
    val activeBurnTypical: Int get() {
        val p = profile ?: return 0
        return measuredActiveAverageCalories.takeIf { it > 0 } ?: p.estimatedDailyActiveCalories
    }

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
        val base = p.effectiveCalories
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
        return ActiveBurnShade(live = liveActiveBurn, typical = typical, source = source)
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
            todayEntries == other.todayEntries &&
            homeDisplay == other.homeDisplay &&
            homeTopNutrients == other.homeTopNutrients &&
            foodLogMacroChips == other.foodLogMacroChips &&
            measuredActiveAverageCalories == other.measuredActiveAverageCalories &&
            activitySnapshot == other.activitySnapshot &&
            optionalNutrientGoals == other.optionalNutrientGoals &&
            foodLogSortOrder == other.foodLogSortOrder &&
            preferGramsByDefault == other.preferGramsByDefault &&
            portionClarifyEnabled == other.portionClarifyEnabled &&
            skipPhotoNotePrompt == other.skipPhotoNotePrompt &&
            photoNoteSkipCount == other.photoNoteSkipCount &&
            photoAccuracyGuideCount == other.photoAccuracyGuideCount &&
            hasSeenCameraScaleTip == other.hasSeenCameraScaleTip &&
            weightMetric == other.weightMetric &&
            favoriteKeys == other.favoriteKeys &&
            pendingAnalysis == other.pendingAnalysis &&
            pendingFoodSource == other.pendingFoodSource &&
            pendingDraftImageFilename == other.pendingDraftImageFilename &&
            pendingReviewSource == other.pendingReviewSource &&
            pendingInputNote == other.pendingInputNote &&
            pendingInputConfirmedPortionGrams == other.pendingInputConfirmedPortionGrams &&
            pendingPortionPreConfirmed == other.pendingPortionPreConfirmed &&
            pendingInputDraftImageFilename == other.pendingInputDraftImageFilename &&
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
            waterGoalDynamic == other.waterGoalDynamic &&
            waterNextPlan == other.waterNextPlan &&
            progressiveMeal == other.progressiveMeal &&
            resumeProgressiveCapture == other.resumeProgressiveCapture &&
            showProgressiveMealSheet == other.showProgressiveMealSheet &&
            manualActiveKcal == other.manualActiveKcal &&
            copiedEntries == other.copiedEntries
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + (profile?.hashCode() ?: 0)
        result = 31 * result + todayEntries.hashCode()
        result = 31 * result + homeDisplay.hashCode()
        result = 31 * result + homeTopNutrients.hashCode()
        result = 31 * result + foodLogMacroChips.hashCode()
        result = 31 * result + measuredActiveAverageCalories
        result = 31 * result + activitySnapshot.hashCode()
        result = 31 * result + optionalNutrientGoals.hashCode()
        result = 31 * result + foodLogSortOrder.hashCode()
        result = 31 * result + preferGramsByDefault.hashCode()
        result = 31 * result + portionClarifyEnabled.hashCode()
        result = 31 * result + skipPhotoNotePrompt.hashCode()
        result = 31 * result + photoNoteSkipCount
        result = 31 * result + photoAccuracyGuideCount
        result = 31 * result + hasSeenCameraScaleTip.hashCode()
        result = 31 * result + weightMetric.hashCode()
        result = 31 * result + favoriteKeys.hashCode()
        result = 31 * result + (pendingAnalysis?.hashCode() ?: 0)
        result = 31 * result + (pendingFoodSource?.hashCode() ?: 0)
        result = 31 * result + (pendingDraftImageFilename?.hashCode() ?: 0)
        result = 31 * result + (pendingReviewSource?.hashCode() ?: 0)
        result = 31 * result + (pendingInputNote?.hashCode() ?: 0)
        result = 31 * result + (pendingInputConfirmedPortionGrams?.hashCode() ?: 0)
        result = 31 * result + pendingPortionPreConfirmed.hashCode()
        result = 31 * result + (pendingInputDraftImageFilename?.hashCode() ?: 0)
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
        result = 31 * result + waterGoalDynamic.hashCode()
        result = 31 * result + (waterNextPlan?.hashCode() ?: 0)
        result = 31 * result + (progressiveMeal?.hashCode() ?: 0)
        result = 31 * result + resumeProgressiveCapture.hashCode()
        result = 31 * result + showProgressiveMealSheet.hashCode()
        result = 31 * result + manualActiveKcal
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
        _ui.update { it.copy(
            analyzing = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            error = message,
        ) }
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
                _ui.value = next
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
                }
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

        container.prefs.portionClarifyEnabled
            .onEach { enabled ->
                _ui.update { it.copy(portionClarifyEnabled = enabled) }
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

        combine(container.waterRepository.entries, _selectedDate) { entries, day ->
            val zone = ZoneId.systemDefault()
            entries
                .filter { it.date.atZone(zone).toLocalDate() == day }
                .sumOf { it.milliliters }
        }
            .onEach { total ->
                _ui.update { it.copy(waterTodayMl = total) }
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
        // fire-time recompute).
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                refreshWaterPlan()
            }
        }

        combine(container.manualActiveRepository.entries, _selectedDate) { entries, day ->
            entries.filter { it.date == day.toString() }.sumOf { it.calories }
        }
            .onEach { total -> _ui.update { it.copy(manualActiveKcal = total) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val analysisDraft = container.prefs.pendingFoodAnalysisDraft.first()
            if (analysisDraft != null) {
                restorePendingDraft(analysisDraft)
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

    fun addWater(milliliters: Int) {
        if (milliliters <= 0) return
        viewModelScope.launch {
            container.waterRepository.add(
                WaterEntry(date = timestampForSelectedDay(), milliliters = milliliters),
            )
        }
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
                if ((!note.isNullOrBlank() || grams != null) && images.size == 1) {
                    savePendingInputDraft(
                        images.first(),
                        note.orEmpty(),
                        FoodSource.SNAP_FOOD,
                        confirmedPortionGrams = grams,
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
            val analysis = OpenFoodFactsService.lookup(barcode, container.prefs)
            savePendingDraft(analysis, imageBytes = null, source = FoodSource.BARCODE, generation = start.generation)
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
        mealType: MealType = MealType.currentMeal,
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
                val entry = analysis.toMicronutrients().scaled(effectiveScale, round1 = false).applyTo(
                    FoodEntry(
                        id = id,
                        name = resolvedName,
                        calories = s(analysis.calories),
                        protein = macro(analysis.protein),
                        carbs = macro(analysis.carbs),
                        fat = macro(analysis.fat),
                        timestamp = timestampForSelectedDay(),
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
                        constituents = if (effectiveScale == 1.0) {
                            analysis.constituents
                        } else {
                            app.chompass.services.ai.ConstituentReconcile.scaleAll(
                                analysis.constituents,
                                effectiveScale,
                            )
                        },
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
     * When [resumeCapture] is true, HomeScreen reopens the camera; otherwise
     * the progressive meal sheet is shown so the user can Log meal / Add another.
     */
    fun addToProgressiveMeal(
        name: String? = null,
        servingGrams: Double? = null,
        mealType: MealType = MealType.currentMeal,
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

    fun updateProgressiveMealMeta(name: String, mealType: MealType) {
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

    fun consumeResumeProgressiveCapture() {
        if (_ui.value.resumeProgressiveCapture) {
            _ui.update { it.copy(resumeProgressiveCapture = false) }
        }
    }

    fun showProgressiveMealSheet(show: Boolean) {
        _ui.update { it.copy(showProgressiveMealSheet = show) }
    }

    /** Start another capture while keeping the draft; hides the meal sheet until review. */
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
                val timestamp = timestampForSelectedDay()
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

    suspend fun suggestMealWhatIf(entry: FoodEntry): String {
        val snapshot = _ui.value
        val profile = snapshot.profile
            ?: return container.appContext.getString(R.string.finish_onboarding_hint)
        return container.foodAnalysis.suggestMealWhatIf(
            entry = entry,
            dayEntries = snapshot.todayEntries,
            profile = profile,
            weightMetric = snapshot.weightMetric
        )
    }

    fun dismissPending() {
        val previousDraftImage = _ui.value.pendingDraftImageFilename
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
            discardPendingDraft(previousDraftImage)
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
            val bytes = snapshot.pendingInputImageBytes ?: snapshot.pendingInputDraftImageFilename?.let { filename ->
                runCatching { container.imageStore.file(filename).readBytes() }.getOrNull()
            }
            if (bytes == null) {
                clearPendingInputDraft()
                _ui.update { it.copy(
                    error = container.appContext.getString(R.string.error_failed_input_missing)
                ) }
                return@launch
            }
            analyzePhotoWithNote(
                bytes,
                snapshot.pendingInputNote.orEmpty(),
                confirmedPortionGrams = snapshot.pendingInputConfirmedPortionGrams,
            )
        }
    }

    fun dismissFailedInput() {
        viewModelScope.launch {
            clearPendingInputDraft()
            _ui.update { it.copy(error = null) }
        }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
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

    fun updateEntry(original: FoodEntry, updated: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.updateEntry(original, updated)
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
                container.foodRepository.addEntry(template.duplicatedForLogging(timestampForSelectedDay()))
            }
        }
    }

    /** Log every ingredient of a Recipe as its own diary row, timestamped to the selected day. */
    fun logRecipe(recipe: app.chompass.models.Recipe) {
        viewModelScope.launch {
            container.recipeRepository.logRecipe(recipe, timestampForSelectedDay())
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
                val duplicated = entries.map { it.duplicatedForLogging(timestampForDate(targetDate)) }
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
        mealType: MealType = MealType.currentMeal,
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
                            timestamp = timestampForSelectedDay(),
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
     * Mirrors iOS `logDate: selectedDate` behavior. When viewing today, returns now.
     * When viewing a past or future day, combines that day with the current wall-clock
     * time so the entry shows a sensible time and lands on the correct calendar day.
     */
    private fun timestampForSelectedDay(): Instant = timestampForDate(_selectedDate.value)

    /** Same as [timestampForSelectedDay] but for an explicit day (copy target). */
    private fun timestampForDate(day: LocalDate): Instant {
        val today = LocalDate.now()
        if (day == today) return Instant.now()
        val zone = ZoneId.systemDefault()
        val nowTime = java.time.LocalTime.now()
        return day.atTime(nowTime).atZone(zone).toInstant()
    }

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
            pendingPortionPreConfirmed = portionPreConfirmed,
            pendingInputDraftImageFilename = null
        ) }
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
                pendingInputDraftImageFilename = null,
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
        imageBytes: ByteArray,
        note: String,
        source: FoodSource = FoodSource.SNAP_FOOD,
        confirmedPortionGrams: Double? = null,
    ) {
        val previousFilename = _ui.value.pendingInputDraftImageFilename
            ?: container.prefs.pendingFoodInputDraft.first()?.imageFilename
        val imageFilename = persistImage(imageBytes, UUID.randomUUID()) ?: return
        if (previousFilename != null && previousFilename != imageFilename) {
            container.imageStore.delete(previousFilename)
        }
        val grams = confirmedPortionGrams?.takeIf { it > 0 }
        container.prefs.setPendingFoodInputDraft(
            PendingFoodInputDraft(
                imageFilename = imageFilename,
                note = note,
                confirmedPortionGrams = grams,
                source = source,
                // Same day-restore intent as [PendingFoodAnalysisDraft.targetDate].
                targetDate = _selectedDate.value
            )
        )
        _ui.update { it.copy(
            pendingInputImageBytes = imageBytes,
            pendingInputNote = note,
            pendingInputConfirmedPortionGrams = grams,
            pendingInputDraftImageFilename = imageFilename
        ) }
    }

    private suspend fun restorePendingInputDraft(draft: PendingFoodInputDraft) {
        // Re-target the diary day the input sheet was opened for (same rationale
        // as [restorePendingDraft]): the resumed Log must land on that day.
        _selectedDate.value = draft.targetDate
        val bytes = runCatching { container.imageStore.file(draft.imageFilename).readBytes() }.getOrNull()
        if (bytes == null) {
            clearPendingInputDraft()
            _ui.update { it.copy(
                error = container.appContext.getString(R.string.error_failed_input_missing)
            ) }
            return
        }
        _ui.update { it.copy(
            pendingInputImageBytes = bytes,
            pendingInputNote = draft.note,
            pendingInputConfirmedPortionGrams = draft.confirmedPortionGrams?.takeIf { it > 0 },
            pendingInputDraftImageFilename = draft.imageFilename,
            error = null
        ) }
    }

    private suspend fun clearPendingInputDraft() {
        val filename = _ui.value.pendingInputDraftImageFilename
            ?: container.prefs.pendingFoodInputDraft.first()?.imageFilename
        container.prefs.setPendingFoodInputDraft(null)
        filename?.let { container.imageStore.delete(it) }
        _ui.update { it.copy(
            pendingInputImageBytes = null,
            pendingInputNote = null,
            pendingInputConfirmedPortionGrams = null,
            pendingInputDraftImageFilename = null
        ) }
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
                mealType = MealType.BREAKFAST,
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
                            canned.duplicatedForLogging(timestampForSelectedDay()),
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

    /** Portion-clarify chip (docs/UNCERTAINTY_DRIVEN_ENTRY.md bet 1): re-analyzes the
     *  still-pending (not yet saved) photo analysis with the user's portion answer injected
     *  as extra context, preserving the original custom note, then replaces pendingAnalysis
     *  so FoodResultSheet recomposes with the refined estimate. */
    suspend fun reprocessPendingAnalysis(portionAnswer: String) {
        val bytes = _ui.value.pendingImageBytes ?: return
        val current = _ui.value.pendingAnalysis
        val originalNote = current?.customNote?.trim()?.takeIf { it.isNotEmpty() }
        val description = buildString {
            current?.name?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(it)
                append(". ")
            }
            if (originalNote != null) {
                append(originalNote)
                append(". ")
            }
            append("Portion size: $portionAnswer")
        }
        // Portion clarify runs inside FoodResultSheet with its own spinner; still
        // emit progress so callers can observe streaming fields if needed.
        val result = container.foodAnalysis.analyzeFood(bytes, description) { }
            .copy(customNote = originalNote)
        _ui.update { it.copy(pendingAnalysis = result) }
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
