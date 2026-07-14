package org.codeberg.fitguy.nofud.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.models.FoodLogMacroChip
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplay
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplayMode
import org.codeberg.fitguy.nofud.models.HomeDisplayPreferences
import org.codeberg.fitguy.nofud.models.ResolvedActiveBurn
import org.codeberg.fitguy.nofud.models.HomeTopNutrient
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.models.OptionalNutrientGoals
import org.codeberg.fitguy.nofud.models.PendingFoodAnalysisDraft
import org.codeberg.fitguy.nofud.models.PendingFoodInputDraft
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.services.FoodImageComposer
import org.codeberg.fitguy.nofud.services.OpenFoodFactsService
import org.codeberg.fitguy.nofud.services.PerfLog
import org.codeberg.fitguy.nofud.services.ai.AiError
import org.codeberg.fitguy.nofud.services.health.HomeActivitySnapshot
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
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
    STANDARD("standard", "Breakfast → Lunch → Dinner", R.string.sort_standard),
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
    val activitySnapshot: HomeActivitySnapshot = HomeActivitySnapshot(date = LocalDate.now()),
    val optionalNutrientGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val foodLogSortOrder: FoodLogSortOrder = FoodLogSortOrder.STANDARD,
    val preferGramsByDefault: Boolean = false,
    val weightMetric: Boolean = true,
    val favoriteKeys: Set<String> = emptySet(),
    val pendingAnalysis: FoodAnalysis? = null,
    val pendingImageBytes: ByteArray? = null,
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
    val pendingInputDraftImageFilename: String? = null,
    val analyzing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
) {
    val caloriesToday: Int get() = todayEntries.sumOf { it.calories }
    val proteinToday: Double get() = todayEntries.sumOf { it.protein }
    val carbsToday: Double get() = todayEntries.sumOf { it.carbs }
    val fatToday: Double get() = todayEntries.sumOf { it.fat }
    val baseCalorieGoal: Int get() = profile?.effectiveCalories ?: 2000
    val resolvedActiveBurn: ResolvedActiveBurn? get() {
        val p = profile ?: return null
        return HomeCalorieDisplay.resolveActiveBurn(
            homeDisplay.calorieDisplayMode,
            activitySnapshot,
            p.estimatedDailyActiveCalories,
        )
    }
    val effectiveCalorieMode: HomeCalorieDisplayMode get() =
        HomeCalorieDisplay.effectiveMode(homeDisplay.calorieDisplayMode, resolvedActiveBurn)
    val gaugeBaseCalorieGoal: Int get() {
        val p = profile ?: return baseCalorieGoal
        val sedentary = p.sedentaryCalorieBudget(baseCalorieGoal)
        return HomeCalorieDisplay.gaugeBaseGoal(effectiveCalorieMode, baseCalorieGoal, sedentary)
    }
    val displayActiveCalories: Int get() = resolvedActiveBurn?.calories ?: 0
    fun isFavorite(entry: FoodEntry): Boolean = entry.favoriteKey in favoriteKeys
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    @Volatile
    private var analysisInFlight = false
    private var analysisGeneration = 0

    private data class AnalysisStart(
        val generation: Int,
        val previousDraftImage: String?,
    )

    private fun beginAnalysis(configure: (HomeUiState) -> HomeUiState): AnalysisStart? =
        synchronized(this) {
            if (analysisInFlight || _ui.value.analyzing) return null
            analysisInFlight = true
            val gen = ++analysisGeneration
            val previousDraftImage = _ui.value.pendingDraftImageFilename
            container.analyzingFood.value = true
            _ui.value = configure(
                _ui.value.copy(
                    error = null,
                    pendingAnalysis = null,
                    pendingReviewSource = null,
                    analyzing = true,
                )
            )
            AnalysisStart(gen, previousDraftImage)
        }

    private fun failAnalysis(gen: Int, message: String?) {
        if (gen != analysisGeneration) return
        _ui.value = _ui.value.copy(analyzing = false, error = message)
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

        container.prefs.weightUnit
            .onEach { unit ->
                _ui.value = _ui.value.copy(weightMetric = unit == "kg")
            }
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

    fun refreshActivitySnapshot() {
        viewModelScope.launch {
            val day = _selectedDate.value
            val display = _ui.value.homeDisplay
            val needsMeasuredEnergy = when (display.calorieDisplayMode) {
                HomeCalorieDisplayMode.STATIC -> false
                HomeCalorieDisplayMode.NET -> true
                HomeCalorieDisplayMode.ADD_ACTIVE,
                HomeCalorieDisplayMode.DUAL -> container.prefs.healthConnectEnabled.first()
            }
            if (!display.showSteps && !needsMeasuredEnergy) {
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

    fun analyzeText(description: String) {
        viewModelScope.launch {
            val start = beginAnalysis { state ->
                state.copy(
                    pendingImageBytes = null,
                    pendingFoodSource = FoodSource.TEXT_INPUT,
                    pendingDraftImageFilename = null,
                )
            } ?: return@launch
            discardPendingDraft(start.previousDraftImage)
            try {
                val analysis = container.foodAnalysis.analyzeText(description)
                savePendingDraft(analysis, imageBytes = null, source = FoodSource.TEXT_INPUT, generation = start.generation)
            } catch (e: AiError) {
                failAnalysis(start.generation, e.message)
            } catch (e: Throwable) {
                failAnalysis(
                    start.generation,
                    e.localizedMessage ?: container.appContext.getString(R.string.error_analysis_failed)
                )
            } finally {
                endAnalysis(start.generation)
            }
        }
    }

    fun analyzePhoto(bytes: ByteArray) {
        viewModelScope.launch {
            val start = beginAnalysis { state ->
                state.copy(
                    pendingImageBytes = bytes,
                    pendingFoodSource = FoodSource.SNAP_FOOD,
                    pendingDraftImageFilename = null,
                )
            } ?: return@launch
            discardPendingDraft(start.previousDraftImage)
            try {
                val analysis = container.foodAnalysis.analyzeAuto(bytes)
                savePendingDraft(analysis, imageBytes = bytes, source = FoodSource.SNAP_FOOD, generation = start.generation)
            } catch (e: AiError) {
                failAnalysis(start.generation, e.message)
            } catch (e: Throwable) {
                failAnalysis(
                    start.generation,
                    e.localizedMessage ?: container.appContext.getString(R.string.error_analysis_failed)
                )
            } finally {
                endAnalysis(start.generation)
            }
        }
    }

    fun analyzePhotos(firstBytes: ByteArray, secondBytes: ByteArray) {
        viewModelScope.launch {
            val start = beginAnalysis { state ->
                state.copy(
                    pendingFoodSource = FoodSource.SNAP_FOOD,
                    pendingDraftImageFilename = null,
                )
            } ?: return@launch
            discardPendingDraft(start.previousDraftImage)
            try {
                // Both shots side by side — this composite becomes the entry's stored
                // image, so the log row and edit sheet show both photos (mirrors iOS).
                val combinedBytes = withContext(Dispatchers.Default) {
                    FoodImageComposer.sideBySide(firstBytes, secondBytes)
                }
                if (start.generation != analysisGeneration) return@launch
                _ui.value = _ui.value.copy(pendingImageBytes = combinedBytes)
                val analysis = container.foodAnalysis.analyzeFood(listOf(firstBytes, secondBytes))
                savePendingDraft(analysis, imageBytes = combinedBytes, source = FoodSource.SNAP_FOOD, generation = start.generation)
            } catch (e: AiError) {
                failAnalysis(start.generation, e.message)
            } catch (e: Throwable) {
                failAnalysis(
                    start.generation,
                    e.localizedMessage ?: container.appContext.getString(R.string.error_analysis_failed)
                )
            } finally {
                endAnalysis(start.generation)
            }
        }
    }

    /**
     * "Camera + Note" flow — analyze a photo with extra textual context the
     * user typed in (e.g. "extra cheese", "no oil"). Mirrors iOS
     * `cameraMode == .snapFoodWithContext` → `GeminiService.analyzeFood(image, description:)`.
     */
    fun analyzePhotoWithNote(bytes: ByteArray, note: String) {
        viewModelScope.launch {
            val start = beginAnalysis { state ->
                state.copy(
                    pendingImageBytes = bytes,
                    pendingFoodSource = FoodSource.SNAP_FOOD,
                    pendingDraftImageFilename = null,
                )
            } ?: return@launch
            savePendingInputDraft(bytes, note, FoodSource.SNAP_FOOD)
            discardPendingDraft(start.previousDraftImage)
            try {
                val analysis = container.foodAnalysis.analyzeFood(bytes, note.takeIf { it.isNotBlank() })
                    .copy(customNote = note.takeIf { it.isNotBlank() })
                clearPendingInputDraft()
                savePendingDraft(analysis, imageBytes = bytes, source = FoodSource.SNAP_FOOD, generation = start.generation)
            } catch (e: AiError) {
                failAnalysis(start.generation, e.message)
            } catch (e: Throwable) {
                failAnalysis(
                    start.generation,
                    e.localizedMessage ?: container.appContext.getString(R.string.error_analysis_failed)
                )
            } finally {
                endAnalysis(start.generation)
            }
        }
    }

    fun lookupBarcode(barcode: String) {
        viewModelScope.launch {
            val start = beginAnalysis { state ->
                state.copy(
                    pendingImageBytes = null,
                    pendingFoodSource = FoodSource.BARCODE,
                    pendingDraftImageFilename = null,
                )
            } ?: return@launch
            discardPendingDraft(start.previousDraftImage)
            try {
                val analysis = OpenFoodFactsService.lookup(barcode)
                savePendingDraft(analysis, imageBytes = null, source = FoodSource.BARCODE, generation = start.generation)
            } catch (e: Throwable) {
                failAnalysis(
                    start.generation,
                    e.localizedMessage ?: container.appContext.getString(R.string.error_barcode_lookup_failed)
                )
            } finally {
                endAnalysis(start.generation)
            }
        }
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
        val analysis = editedAnalysis ?: _ui.value.pendingAnalysis ?: return
        if (_ui.value.saving) return
        val reviewSource = _ui.value.pendingReviewSource
        val pendingFoodSource = _ui.value.pendingFoodSource
        val pendingDraftImageFilename = _ui.value.pendingDraftImageFilename
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.value = _ui.value.copy(saving = true)
            try {
                val imageBytes = _ui.value.pendingImageBytes
                val id = UUID.randomUUID()
                // If this analysis came from a Saved Meals review, reuse the
                // template's existing on-disk image so we don't duplicate the
                // JPEG. Otherwise (fresh AI analysis), persist the in-memory
                // bytes as a new file under the new entry id.
                val filename = reviewSource?.imageFilename
                    ?: pendingDraftImageFilename
                    ?: imageBytes?.let { persistImage(it, id) }
                fun s(v: Int) = (v * scale).roundToInt()
                fun macro(v: Double) = v * scale
                fun s(v: Double?) = v?.let { it * scale }
                val entry = FoodEntry(
                    id = id,
                    name = name?.takeIf { it.isNotBlank() } ?: analysis.name,
                    calories = s(analysis.calories),
                    protein = macro(analysis.protein),
                    carbs = macro(analysis.carbs),
                    fat = macro(analysis.fat),
                    timestamp = timestampForSelectedDay(),
                    imageFilename = filename,
                    emoji = analysis.emoji,
                    source = reviewSource?.source
                        ?: pendingFoodSource
                        ?: if (imageBytes != null) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT,
                    mealType = mealType,
                    sugar = s(analysis.sugar),
                    addedSugar = s(analysis.addedSugar),
                    fiber = s(analysis.fiber),
                    saturatedFat = s(analysis.saturatedFat),
                    monounsaturatedFat = s(analysis.monounsaturatedFat),
                    polyunsaturatedFat = s(analysis.polyunsaturatedFat),
                    cholesterol = s(analysis.cholesterol),
                    sodium = s(analysis.sodium),
                    potassium = s(analysis.potassium),
                    transFat = s(analysis.transFat),
                    calcium = s(analysis.calcium),
                    iron = s(analysis.iron),
                    magnesium = s(analysis.magnesium),
                    zinc = s(analysis.zinc),
                    vitaminA = s(analysis.vitaminA),
                    vitaminC = s(analysis.vitaminC),
                    vitaminD = s(analysis.vitaminD),
                    vitaminB12 = s(analysis.vitaminB12),
                    vitaminE = s(analysis.vitaminE),
                    vitaminK = s(analysis.vitaminK),
                    folate = s(analysis.folate),
                    omega3 = s(analysis.omega3),
                    servingSizeGrams = servingGrams ?: analysis.servingSizeGrams,
                    servingUnitOptions = analysis.servingUnitOptions,
                    selectedServingUnit = if (analysis.servingUnitOptions.isEmpty()) null else selectedServingUnit,
                    selectedServingQuantity = if (analysis.servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                    customNote = analysis.customNote
                )
                container.foodRepository.addEntry(entry)
                container.prefs.setPendingFoodAnalysisDraft(null)
                _ui.value = _ui.value.copy(
                    pendingAnalysis = null,
                    pendingImageBytes = null,
                    pendingFoodSource = null,
                    pendingDraftImageFilename = null,
                    pendingReviewSource = null
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
        _ui.value = _ui.value.copy(
            pendingAnalysis = null,
            pendingImageBytes = null,
            pendingFoodSource = null,
            pendingDraftImageFilename = null,
            pendingReviewSource = null,
            error = null
        )
        viewModelScope.launch {
            discardPendingDraft(previousDraftImage)
        }
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
            analyzePhotoWithNote(bytes, snapshot.pendingInputNote.orEmpty())
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
            pendingFoodSource = template.source,
            pendingDraftImageFilename = null,
            pendingReviewSource = template,
            error = null
        )
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.deleteEntry(entry)
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

    fun copyEntriesToSelectedDay(entries: List<FoodEntry>) {
        if (entries.isEmpty() || _ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.value = _ui.value.copy(saving = true)
            try {
                entries.forEach { entry ->
                    container.foodRepository.addEntry(
                        entry.duplicatedForLogging(
                            logDate = timestampForSelectedDayPreservingTime(entry.timestamp),
                            mealType = entry.mealType
                        )
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
        mealType: MealType = MealType.currentMeal
    ) {
        if (_ui.value.saving) return
        viewModelScope.launch {
            if (_ui.value.saving) return@launch
            _ui.value = _ui.value.copy(saving = true)
            try {
                container.foodRepository.addEntry(
                    FoodEntry(
                        name = name,
                        calories = calories,
                        protein = protein,
                        carbs = carbs,
                        fat = fat,
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

    private fun timestampForSelectedDayPreservingTime(sourceTimestamp: Instant): Instant {
        val zone = ZoneId.systemDefault()
        val sourceTime = sourceTimestamp.atZone(zone).toLocalTime()
        return _selectedDate.value.atTime(sourceTime).atZone(zone).toInstant()
    }

    private suspend fun savePendingDraft(
        analysis: FoodAnalysis,
        imageBytes: ByteArray?,
        source: FoodSource,
        generation: Int,
    ) {
        if (generation != analysisGeneration) return
        val imageFilename = imageBytes?.let { persistImage(it, UUID.randomUUID()) }
        container.prefs.setPendingFoodAnalysisDraft(
            PendingFoodAnalysisDraft(
                analysis = analysis,
                imageFilename = imageFilename,
                source = source
            )
        )
        if (generation != analysisGeneration) return
        _ui.value = _ui.value.copy(
            analyzing = false,
            pendingAnalysis = analysis,
            pendingImageBytes = imageBytes,
            pendingFoodSource = source,
            pendingDraftImageFilename = imageFilename,
            pendingReviewSource = null,
            pendingInputImageBytes = null,
            pendingInputNote = null,
            pendingInputDraftImageFilename = null
        )
    }

    private fun restorePendingDraft(draft: PendingFoodAnalysisDraft) {
        val bytes = draft.imageFilename?.let {
            runCatching { container.imageStore.file(it).readBytes() }.getOrNull()
        }
        _ui.value = _ui.value.copy(
            analyzing = false,
            pendingAnalysis = draft.analysis,
            pendingImageBytes = bytes,
            pendingFoodSource = draft.source,
            pendingDraftImageFilename = draft.imageFilename,
            pendingReviewSource = null,
            pendingInputImageBytes = null,
            pendingInputNote = null,
            pendingInputDraftImageFilename = null,
            error = null
        )
    }

    private suspend fun savePendingInputDraft(
        imageBytes: ByteArray,
        note: String,
        source: FoodSource = FoodSource.SNAP_FOOD
    ) {
        val previousFilename = _ui.value.pendingInputDraftImageFilename
            ?: container.prefs.pendingFoodInputDraft.first()?.imageFilename
        val imageFilename = persistImage(imageBytes, UUID.randomUUID()) ?: return
        if (previousFilename != null && previousFilename != imageFilename) {
            container.imageStore.delete(previousFilename)
        }
        container.prefs.setPendingFoodInputDraft(
            PendingFoodInputDraft(
                imageFilename = imageFilename,
                note = note,
                source = source
            )
        )
        _ui.value = _ui.value.copy(
            pendingInputImageBytes = imageBytes,
            pendingInputNote = note,
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

    suspend fun reprocessFoodEntry(entry: FoodEntry, updatedNote: String): FoodAnalysis {
        val imageBytes = entry.imageFilename?.let {
            runCatching { container.imageStore.file(it).readBytes() }.getOrNull()
        }
        // Compose name + serving + note so a photo-less (text / voice / emoji) entry
        // keeps its food context instead of re-analyzing the bare note; a photo entry
        // gets the name/note as extra grounding on top of the image.
        val description = reprocessDescription(entry, updatedNote)
        val result = if (imageBytes != null) {
            container.foodAnalysis.analyzeFood(imageBytes, description.takeIf { it.isNotBlank() })
        } else {
            container.foodAnalysis.analyzeText(description)
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
private fun FoodEntry.toAnalysis(): FoodAnalysis = FoodAnalysis(
    name = name,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    servingSizeGrams = servingSizeGrams ?: 100.0,
    emoji = emoji,
    sugar = sugar,
    addedSugar = addedSugar,
    fiber = fiber,
    saturatedFat = saturatedFat,
    monounsaturatedFat = monounsaturatedFat,
    polyunsaturatedFat = polyunsaturatedFat,
    cholesterol = cholesterol,
    sodium = sodium,
    potassium = potassium,
    transFat = transFat,
    calcium = calcium,
    iron = iron,
    magnesium = magnesium,
    zinc = zinc,
    vitaminA = vitaminA,
    vitaminC = vitaminC,
    vitaminD = vitaminD,
    vitaminB12 = vitaminB12,
    vitaminE = vitaminE,
    vitaminK = vitaminK,
    folate = folate,
    omega3 = omega3,
    servingUnitOptions = servingUnitOptions,
    selectedServingUnit = selectedServingUnit,
    selectedServingQuantity = selectedServingQuantity,
    customNote = customNote
)
