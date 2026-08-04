package app.chompass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.data.disambiguateFoodName
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.ActiveBurnArcData
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
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
import app.chompass.models.UserProfile
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WaterEntry
import app.chompass.services.FoodImageComposer
import app.chompass.services.FoodPhotoSession
import app.chompass.services.OpenFoodFactsService
import app.chompass.services.PerfLog
import app.chompass.services.grounding.DatabaseSearchResult
import app.chompass.services.grounding.GroundedEntryFeature
import app.chompass.services.ai.AiError
import app.chompass.services.health.HomeActivitySnapshot
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.applyTo
import app.chompass.services.ai.toMicronutrients
import app.chompass.models.MicronutrientValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    /** In-progress weigh-as-you-go meal (photo-per-ingredient). Null when idle. */
    val progressiveMeal: ProgressiveMealDraft? = null,
    /** HomeScreen consumes this once to reopen the camera after Add next ingredient. */
    val resumeProgressiveCapture: Boolean = false,
    /** Show [ProgressiveMealSheet] when the draft has items and capture is idle. */
    val showProgressiveMealSheet: Boolean = false,
    val manualActiveKcal: Int = 0,
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
     * Today's live active burn vs its "typical" reference, for the hero's
     * inner arc. Kept separate from [resolvedActiveBurn] (whose estimate
     * fallback conflates live with reference); null when nothing live to draw.
     */
    val activeBurnArc: ActiveBurnArcData? get() {
        val p = profile ?: return null
        return HomeCalorieDisplay.activeBurnArc(
            liveHcActive = activitySnapshot.activeCalories,
            manualActiveCalories = manualActiveKcal,
            healthConnectAverage = measuredActiveAverageCalories,
            estimatedDailyActive = p.estimatedDailyActiveCalories,
        )
    }
    fun isFavorite(entry: FoodEntry): Boolean = entry.favoriteKey in favoriteKeys
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
        _ui.value = _ui.value.copy(
            pendingAnalysis = null,
            pendingReviewSource = null,
            analyzing = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            error = null,
        )
    }

    private fun onFoodAnalysisProgress(generation: Int, progress: FoodAnalysisProgress) {
        if (generation != analysisGeneration) return
        when (progress) {
            is FoodAnalysisProgress.Phase -> {
                _ui.value = _ui.value.copy(analysisPhase = progress.phase)
            }
            is FoodAnalysisProgress.Partial -> {
                val preview = progress.partial.toPreviewAnalysis()
                _ui.value = _ui.value.copy(
                    analysisPartial = progress.partial,
                    analysisPreview = preview ?: _ui.value.analysisPreview,
                )
            }
            is FoodAnalysisProgress.Parsed -> {
                if (progress.unitsPending) {
                    _ui.value = _ui.value.copy(
                        analysisPhase = null,
                        analysisPreview = progress.analysis,
                        analysisPartial = app.chompass.services.ai.PartialFoodAnalysis.fromComplete(
                            progress.analysis,
                            streaming = false,
                        ),
                        pendingAnalysis = progress.analysis,
                        analyzing = false,
                        inferringUnits = true,
                    )
                    container.analyzingFood.value = false
                } else {
                    _ui.value = _ui.value.copy(
                        analysisPreview = progress.analysis,
                        analysisPartial = app.chompass.services.ai.PartialFoodAnalysis.fromComplete(
                            progress.analysis,
                            streaming = false,
                        ),
                    )
                }
            }
            is FoodAnalysisProgress.Complete -> {
                _ui.value = _ui.value.copy(
                    pendingAnalysis = progress.analysis,
                    inferringUnits = false,
                    analysisPreview = null,
                    analysisPartial = null,
                    analysisPhase = null,
                )
            }
        }
    }

    private fun failAnalysis(gen: Int, message: String?) {
        if (gen != analysisGeneration) return
        _ui.value = _ui.value.copy(
            analyzing = false,
            analysisPhase = null,
            analysisPreview = null,
            analysisPartial = null,
            inferringUnits = false,
            error = message,
        )
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
            failAnalysis(start.generation, e.message)
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
        combine(
            container.profileRepository.profile,
            _selectedDate.flatMapLatest { day -> container.foodRepository.entriesForDate(day) },
            container.foodRepository.favoriteKeys,
            container.prefs.foodLogSortOrder,
            _selectedDate
        ) { p, dayEntries, favKeys, sortOrder, day ->
            _ui.value.copy(
                profile = p,
                date = day,
                todayEntries = dayEntries,
                foodLogSortOrder = FoodLogSortOrder.fromStorage(sortOrder),
                favoriteKeys = favKeys
            )
        }
            .onEach { _ui.value = it }
            .launchIn(viewModelScope)

        container.prefs.homeDisplayPreferences
            .onEach { display ->
                _ui.value = _ui.value.copy(
                    homeDisplay = display,
                    homeTopNutrients = display.homeTopNutrients,
                    foodLogMacroChips = display.foodLogMacroChips,
                )
                refreshActivitySnapshot()
            }
            .launchIn(viewModelScope)

        container.prefs.healthEnergyMeasuredActive
            .onEach { measuredActive ->
                _ui.value = _ui.value.copy(measuredActiveAverageCalories = measuredActive)
            }
            .launchIn(viewModelScope)

        _selectedDate
            .onEach { refreshActivitySnapshot() }
            .launchIn(viewModelScope)

        container.prefs.optionalNutrientGoals
            .onEach { goals ->
                _ui.value = _ui.value.copy(optionalNutrientGoals = goals)
            }
            .launchIn(viewModelScope)

        container.prefs.preferGramsByDefault
            .onEach { preferGrams ->
                _ui.value = _ui.value.copy(preferGramsByDefault = preferGrams)
            }
            .launchIn(viewModelScope)

        container.prefs.portionClarifyEnabled
            .onEach { enabled ->
                _ui.value = _ui.value.copy(portionClarifyEnabled = enabled)
            }
            .launchIn(viewModelScope)

        container.prefs.skipPhotoNotePrompt
            .onEach { skip ->
                _ui.value = _ui.value.copy(skipPhotoNotePrompt = skip)
            }
            .launchIn(viewModelScope)

        container.prefs.photoNoteSkipCount
            .onEach { count ->
                _ui.value = _ui.value.copy(photoNoteSkipCount = count)
            }
            .launchIn(viewModelScope)

        container.prefs.photoAccuracyGuideCount
            .onEach { count ->
                _ui.value = _ui.value.copy(photoAccuracyGuideCount = count)
            }
            .launchIn(viewModelScope)

        container.prefs.hasSeenCameraScaleTip
            .onEach { seen ->
                _ui.value = _ui.value.copy(hasSeenCameraScaleTip = seen)
            }
            .launchIn(viewModelScope)

        container.prefs.weightUnit
            .onEach { unit ->
                _ui.value = _ui.value.copy(weightMetric = unit == "kg")
            }
            .launchIn(viewModelScope)

        container.prefs.waterTrackingEnabled
            .onEach { enabled -> _ui.value = _ui.value.copy(waterTrackingEnabled = enabled) }
            .launchIn(viewModelScope)

        container.prefs.waterDailyGoalMl
            .onEach { goal -> _ui.value = _ui.value.copy(waterDailyGoalMl = goal) }
            .launchIn(viewModelScope)

        container.prefs.waterQuickPresetsMl
            .onEach { presets -> _ui.value = _ui.value.copy(waterQuickPresetsMl = presets) }
            .launchIn(viewModelScope)

        combine(container.waterRepository.entries, _selectedDate) { entries, day ->
            val zone = ZoneId.systemDefault()
            entries
                .filter { it.date.atZone(zone).toLocalDate() == day }
                .sumOf { it.milliliters }
        }
            .onEach { total -> _ui.value = _ui.value.copy(waterTodayMl = total) }
            .launchIn(viewModelScope)

        combine(container.manualActiveRepository.entries, _selectedDate) { entries, day ->
            entries.filter { it.date == day.toString() }.sumOf { it.calories }
        }
            .onEach { total -> _ui.value = _ui.value.copy(manualActiveKcal = total) }
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
        viewModelScope.launch {
            val day = _selectedDate.value
            val display = _ui.value.homeDisplay
            val needsActivitySnapshot = display.showSteps || display.showActiveCalories
            val needsMeasuredEnergy = display.calorieDisplayMode ==
                HomeCalorieDisplayMode.ADD_ACTIVE && container.prefs.healthConnectEnabled.first()
            if (!needsActivitySnapshot && !needsMeasuredEnergy) {
                _ui.value = _ui.value.copy(activitySnapshot = HomeActivitySnapshot(date = day))
                return@launch
            }
            val snapshot = container.homeActivityReader.readForDate(day)
            _ui.value = _ui.value.copy(activitySnapshot = snapshot)
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
                _ui.value = _ui.value.copy(
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
                )
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
        _ui.value = _ui.value.copy(pendingGroundedReview = null)
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
            _ui.value = _ui.value.copy(saving = true)
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
                // Commit the confirming AI-entry draft snapshot atomically with
                // the diary row (one DataStore edit instead of a draft-write +
                // entry-write pair), then a small edit clears the draft now that
                // the row exists.
                container.foodRepository.addEntry(
                    entry,
                    draft = PendingFoodAnalysisDraft(
                        analysis = analysis,
                        imageFilename = filename,
                        source = entrySource,
                    ),
                )
                container.prefs.setPendingFoodAnalysisDraft(null)
                _ui.value = _ui.value.copy(
                    pendingAnalysis = null,
                    pendingImageBytes = null,
                    pendingAnalysisImages = emptyList(),
                    pendingFoodSource = null,
                    pendingDraftImageFilename = null,
                    pendingReviewSource = null
                )
                }
            } finally {
                _ui.value = _ui.value.copy(saving = false)
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
        _ui.value = _ui.value.copy(
            progressiveMeal = draft,
            pendingAnalysis = null,
            pendingImageBytes = null,
            pendingAnalysisImages = emptyList(),
            pendingFoodSource = null,
            pendingDraftImageFilename = null,
            pendingReviewSource = null,
            resumeProgressiveCapture = resumeCapture,
            showProgressiveMealSheet = !resumeCapture,
        )
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
        _ui.value = _ui.value.copy(progressiveMeal = draft.copy(name = name, mealType = mealType))
    }

    fun discardProgressiveMeal() {
        _ui.value = _ui.value.copy(
            progressiveMeal = null,
            showProgressiveMealSheet = false,
            resumeProgressiveCapture = false,
        )
    }

    fun consumeResumeProgressiveCapture() {
        if (_ui.value.resumeProgressiveCapture) {
            _ui.value = _ui.value.copy(resumeProgressiveCapture = false)
        }
    }

    fun showProgressiveMealSheet(show: Boolean) {
        _ui.value = _ui.value.copy(showProgressiveMealSheet = show)
    }

    /** Start another capture while keeping the draft; hides the meal sheet until review. */
    fun continueProgressiveCapture() {
        if (_ui.value.progressiveMeal?.items.isNullOrEmpty()) return
        _ui.value = _ui.value.copy(
            showProgressiveMealSheet = false,
            resumeProgressiveCapture = true,
        )
    }

    fun logProgressiveMeal() {
        val draft = _ui.value.progressiveMeal ?: return
        if (draft.items.isEmpty() || _ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.value = _ui.value.copy(saving = true)
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
                        knownKeys.add(resolved.lowercase())
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
                built.forEach { container.foodRepository.addEntry(it) }
                _ui.value = _ui.value.copy(
                    progressiveMeal = null,
                    showProgressiveMealSheet = false,
                    resumeProgressiveCapture = false,
                )
            } finally {
                _ui.value = _ui.value.copy(saving = false)
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
        _ui.value = _ui.value.copy(
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
        )
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
                _ui.value = _ui.value.copy(
                    error = container.appContext.getString(R.string.error_failed_input_missing)
                )
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
            _ui.value = _ui.value.copy(error = null)
        }
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
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
        _ui.value = _ui.value.copy(
            pendingAnalysis = analysis,
            pendingImageBytes = bytes,
            pendingAnalysisImages = listOfNotNull(bytes),
            pendingFoodSource = template.source,
            pendingDraftImageFilename = null,
            pendingReviewSource = template,
            pendingPortionPreConfirmed = false,
            error = null
        )
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
        viewModelScope.launch {
            container.foodRepository.addEntry(template.duplicatedForLogging(timestampForSelectedDay()))
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

    fun copyEntriesToSelectedDay(entries: List<FoodEntry>) {
        if (entries.isEmpty() || _ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.value = _ui.value.copy(saving = true)
            try {
                // Upstream #149 / Android 6.0: reused copies log at now + current meal
                // (not the source entry's clock time / meal bucket).
                entries.forEach { entry ->
                    container.foodRepository.addEntry(
                        entry.duplicatedForLogging(timestampForSelectedDay())
                    )
                }
            } finally {
                _ui.value = _ui.value.copy(saving = false)
            }
        }
    }

    /** Save a user-typed entry with no AI involvement (manual macro input from issue #15). */
    fun saveManualEntry(
        name: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double? = null,
        mealType: MealType = MealType.currentMeal
    ) {
        if (_ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.value = _ui.value.copy(saving = true)
            try {
                container.foodRepository.addEntry(
                    FoodEntry(
                        name = disambiguateFoodName(
                            name,
                            container.foodRepository.existingFoodIdentityKeys(),
                        ),
                        calories = calories,
                        protein = protein,
                        carbs = carbs,
                        fat = fat,
                        fiber = fiber,
                        timestamp = timestampForSelectedDay(),
                        source = FoodSource.MANUAL,
                        mealType = mealType
                    )
                )
            } finally {
                _ui.value = _ui.value.copy(saving = false)
            }
        }
    }

    /**
     * Mirrors iOS `logDate: selectedDate` behavior. When viewing today, returns now.
     * When viewing a past or future day, combines that day with the current wall-clock
     * time so the entry shows a sensible time and lands on the correct calendar day.
     */
    private fun timestampForSelectedDay(): Instant {
        val day = _selectedDate.value
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
                source = source
            )
        )
        if (generation != analysisGeneration) return
        _ui.value = _ui.value.copy(
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
        )
    }

    private fun restorePendingDraft(draft: PendingFoodAnalysisDraft) {
        val bytes = draft.imageFilename?.let {
            runCatching { container.imageStore.file(it).readBytes() }.getOrNull()
        }
        // Re-check collisions: the diary may have grown since the draft was saved.
        viewModelScope.launch {
            val unique = draft.analysis.copy(
                name = disambiguateFoodName(
                    draft.analysis.name,
                    container.foodRepository.existingFoodIdentityKeys(),
                )
            )
            _ui.value = _ui.value.copy(
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
            )
        }
    }

    /**
     * Keep [rawName] when re-logging the same Saved Meals food; otherwise
     * append (2), (3), … if the name already identifies another food.
     */
    private suspend fun resolveNewFoodName(rawName: String, relogTemplate: FoodEntry?): String {
        val trimmed = rawName.trim()
        if (relogTemplate != null && trimmed.lowercase() == relogTemplate.favoriteKey) {
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
                source = source
            )
        )
        _ui.value = _ui.value.copy(
            pendingInputImageBytes = imageBytes,
            pendingInputNote = note,
            pendingInputConfirmedPortionGrams = grams,
            pendingInputDraftImageFilename = imageFilename
        )
    }

    private suspend fun restorePendingInputDraft(draft: PendingFoodInputDraft) {
        val bytes = runCatching { container.imageStore.file(draft.imageFilename).readBytes() }.getOrNull()
        if (bytes == null) {
            clearPendingInputDraft()
            _ui.value = _ui.value.copy(
                error = container.appContext.getString(R.string.error_failed_input_missing)
            )
            return
        }
        _ui.value = _ui.value.copy(
            pendingInputImageBytes = bytes,
            pendingInputNote = draft.note,
            pendingInputConfirmedPortionGrams = draft.confirmedPortionGrams?.takeIf { it > 0 },
            pendingInputDraftImageFilename = draft.imageFilename,
            error = null
        )
    }

    private suspend fun clearPendingInputDraft() {
        val filename = _ui.value.pendingInputDraftImageFilename
            ?: container.prefs.pendingFoodInputDraft.first()?.imageFilename
        container.prefs.setPendingFoodInputDraft(null)
        filename?.let { container.imageStore.delete(it) }
        _ui.value = _ui.value.copy(
            pendingInputImageBytes = null,
            pendingInputNote = null,
            pendingInputConfirmedPortionGrams = null,
            pendingInputDraftImageFilename = null
        )
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
        _ui.value = _ui.value.copy(pendingAnalysis = result)
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
        servingSizeGrams = servingSizeGrams ?: 100.0,
        emoji = emoji,
        servingUnitOptions = servingUnitOptions,
        selectedServingUnit = selectedServingUnit,
        selectedServingQuantity = selectedServingQuantity,
        customNote = customNote,
        grounding = grounding,
        constituents = constituents,
    )
)
