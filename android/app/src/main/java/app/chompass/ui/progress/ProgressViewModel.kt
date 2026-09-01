package app.chompass.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import android.util.Log
import app.chompass.AppContainer
import app.chompass.data.aggregateFoodEntriesByDay
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.DailyFoodTotals
import app.chompass.models.HomeTopNutrient
import app.chompass.models.FoodEntry
import app.chompass.models.GoalJournalEntry
import app.chompass.models.MacroPlanResolver
import app.chompass.models.UserProfile
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.UnitFormat
import app.chompass.models.WeightEntry
import app.chompass.ui.components.splitDecimalParts
import app.chompass.services.health.DailyActivity
import app.chompass.services.health.DailyWellness
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class WeightSummaryStats(
    val currentKg: Double? = null,
    val netChangeKg: Double = 0.0,
    val averageKg: Double = 0.0
)

data class BodyFatSummaryStats(
    val currentFraction: Double? = null,
    val netChangePercent: Double = 0.0,
    val averagePercent: Double = 0.0
)

/** #75: one Progress averages row — range mean of a selected non-macro nutrient. */
data class NutrientAverage(
    val nutrient: HomeTopNutrient,
    val avg: Double,
    val goal: Int,
)

data class ProgressUiState(
    val weightCount: Int = 0,
    val bodyFatCount: Int = 0,
    val latestWeightKg: Double? = null,
    val latestBodyFatFraction: Double? = null,
    val profile: UserProfile? = null,
    val weightUnit: String = "kg",
    val timeRange: TimeRange = TimeRange.WEEK,
    val filteredWeights: List<WeightEntry> = emptyList(),
    val filteredBodyFats: List<BodyFatEntry> = emptyList(),
    /** Body-measurement snapshots inside the selected range, date-sorted (for the plot cards). */
    val filteredMeasurements: List<BodyMeasurement> = emptyList(),
    /** Sites with a Progress-tab trend plot enabled in Customize Progress; empty = plots off. */
    val measurementSites: Set<BodyMeasurement.Site> = emptySet(),
    val dailyCalories: List<Pair<LocalDate, Int>> = emptyList(),
    /** Mean of complete days in [dailyCalories] (today excluded). Null if none. */
    val calorieAverage: Int? = null,
    /**
     * Calorie goal rule for the selected range (#60 phase 3): the journaled
     * range average (MACRO-CYCLE-D) when journal days exist, else the current
     * profile target (plan-off behavior, unchanged). 2000 mirrors the old
     * profile-null fallback.
     */
    val calorieGoal: Int = 2000,
    /** Range macro goals (journal average, current-target fallback) for the % rows. */
    val proteinGoal: Int = 0,
    val carbsGoal: Int = 0,
    val fatGoal: Int = 0,
    /** Per-day calorie targets for the logged bars (journal-first; live-resolve fallback). */
    val dailyCalorieGoals: Map<LocalDate, Int> = emptyMap(),
    val macroAverages: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    /**
     * #75: one row per nutrient selected in Customize Progress, canonical
     * HomeTopNutrient order; empty when nothing is selected (card hidden).
     */
    val nutrientAverages: List<NutrientAverage> = emptyList(),
    val showNutrientAverages: Boolean = false,

    val weightStats: WeightSummaryStats = WeightSummaryStats(),
    val bodyFatStats: BodyFatSummaryStats = BodyFatSummaryStats(),
    val goalReached: Boolean = false
)

private data class BaseProgressData(
    val profile: UserProfile?,
    val entries: List<WeightEntry>,
    val bodyFatEntries: List<BodyFatEntry>,
    val bodyMeasurements: List<BodyMeasurement>,
    /** Site storage ids with a Progress-tab plot enabled (empty = off). */
    val measurementSites: Set<String> = emptySet(),
    /** Per-day goal journal (#60): frozen actual targets behind the range goals + bars. */
    val goalJournal: List<GoalJournalEntry> = emptyList(),
    val optionalGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val showNutrientAverages: Boolean = false,
    /** #75: nutrients the averages card shows, canonical order (macros excluded). */
    val averagesSelection: List<HomeTopNutrient> =
        HomeTopNutrient.averagesSelectionFromStorage(HomeTopNutrient.DefaultAveragesStorage),
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ProgressUiState())
    val ui: StateFlow<ProgressUiState> = _ui.asStateFlow()
    private val timeRange = MutableStateFlow(TimeRange.WEEK)
    private val goalReached = MutableStateFlow(false)

    /** Last 7 days of Health Connect steps + exercise (today included). Kept
     *  outside [ui] — it's a one-shot suspend read against Health Connect, not
     *  part of the reactive DataStore combine. Empty when Health Connect is off,
     *  unavailable, or activity read permissions weren't granted. */
    private val _activity = MutableStateFlow<List<DailyActivity>>(emptyList())
    val activity: StateFlow<List<DailyActivity>> = _activity.asStateFlow()

    /** Last 7 days of Health Connect sleep / resting HR / hydration (today included).
     *  Same one-shot suspend read as [activity]; empty when Health Connect is off,
     *  unavailable, or no wellness read permission was granted. */
    private val _wellness = MutableStateFlow<List<DailyWellness>>(emptyList())
    val wellness: StateFlow<List<DailyWellness>> = _wellness.asStateFlow()

    init {
        viewModelScope.launch {
            val lastViewed = container.prefs.progressLastRangeId.first()
            val defaultId = container.prefs.progressDefaultRangeId.first()
            timeRange.value = TimeRange.resolve(lastViewed, defaultId)
        }
        viewModelScope.launch {
            if (container.prefs.healthConnectEnabled.first() &&
                container.health.isAvailable()
            ) {
                if (container.health.hasActivityRead()) {
                    _activity.value = container.health.readDailyActivity(days = 7)
                }
                if (container.health.hasWellnessRead()) {
                    _wellness.value = container.health.readDailyWellness(days = 7)
                }
            }
        }
        combine(
            container.profileRepository.profile,
            container.weightRepository.entries,
            container.bodyFatRepository.entries,
            container.bodyMeasurementRepository.entries,
            container.prefs.progressMeasurementSites
        ) { profile, weights, bodyFats, measurements, measurementSites ->
            BaseProgressData(
                profile = profile,
                entries = weights,
                bodyFatEntries = bodyFats,
                bodyMeasurements = measurements,
                measurementSites = measurementSites
            )
        }.let { base ->
            combine(
                base,
                container.prefs.goalJournal,
                container.prefs.optionalNutrientGoals,
                container.prefs.progressNutrientAverages,
                container.prefs.progressNutrientAveragesSelection,
            ) { b, journal, goals, showMicros, averagesSelection ->
                b.copy(
                    goalJournal = journal,
                    optionalGoals = goals,
                    showNutrientAverages = showMicros,
                    averagesSelection =
                        HomeTopNutrient.averagesSelectionFromStorage(averagesSelection),
                )
            }

        }.let { baseData ->
            combine(
                baseData,
                container.prefs.weightUnit,
                timeRange,
                goalReached,
            ) { base, weightUnit, selectedRange, showGoalReached ->
                ProgressRangeInputs(base, weightUnit, selectedRange, showGoalReached)
            }.flatMapLatest { inputs ->
                val (start, end) = inputs.selectedRange.dateRange()
                // Daily aggregates (flippidity C.1): one small row per logged
                // day from the aggregate cache instead of the year of FoodEntry
                // rows — All-range compute never holds the full diary in memory.
                container.foodRepository.dailyTotalsBetween(start, end).map { totals ->
                    ProgressSnapshot(
                        base = inputs.base,
                        dailyTotals = totals,
                        weightUnit = inputs.weightUnit,
                        selectedRange = inputs.selectedRange,
                        showGoalReached = inputs.showGoalReached,
                    )
                }
            }.mapLatest { snapshot ->
                withContext(Dispatchers.Default) {
                    app.chompass.services.PerfLog.measure(
                        "progress",
                        "rangeChange",
                        "range=${snapshot.selectedRange.storageId} foods=${snapshot.dailyTotals.size} weights=${snapshot.base.entries.size}",
                    ) { snapshot.toUiState() }
                }
            }.onEach { _ui.value = it }.launchIn(viewModelScope)
        }
    }

    fun addWeight(kg: Double) {
        addWeightAt(kg, Instant.now())
    }

    fun addWeightAt(kg: Double, at: Instant) {
        viewModelScope.launch {
            val parts = splitDecimalParts(kg, 30, 250)
            Log.i(
                "ChompassWeight",
                "save raw=$kg bits=${kg.toBits()} stored=${UnitFormat.roundKgToHundredths(kg)} wheel=${parts.first}.${parts.second} at=$at",
            )
            val event = container.weightRepository.addEntry(WeightEntry(weightKg = kg, date = at))
            if (event != null) {
                goalReached.value = true
                if (container.prefs.notificationsEnabled.first() &&
                    container.prefs.goalReachedNotificationsEnabled.first()
                ) {
                    container.notifications.showGoalReached()
                }
            }
        }
    }

    fun deleteWeight(id: UUID) {
        viewModelScope.launch { container.weightRepository.deleteEntry(id) }
    }

    fun addBodyFat(fraction: Double) {
        addBodyFatAt(fraction, Instant.now())
    }

    fun addBodyFatAt(fraction: Double, at: Instant) {
        viewModelScope.launch {
            container.bodyFatRepository.addEntry(BodyFatEntry(bodyFatFraction = fraction, date = at))
        }
    }

    fun deleteBodyFat(id: UUID) {
        viewModelScope.launch { container.bodyFatRepository.deleteEntry(id) }
    }

    fun addBodyMeasurement(entry: BodyMeasurement) {
        viewModelScope.launch { container.bodyMeasurementRepository.addEntry(entry) }
    }

    fun deleteBodyMeasurement(id: UUID) {
        viewModelScope.launch { container.bodyMeasurementRepository.deleteEntry(id) }
    }

    fun dismissGoalReached() {
        goalReached.value = false
    }

    fun setTimeRange(range: TimeRange) {
        timeRange.value = range
        viewModelScope.launch {
            container.prefs.setProgressLastRangeId(range.storageId)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProgressViewModel(container) as T
    }
}

private data class ProgressRangeInputs(
    val base: BaseProgressData,
    val weightUnit: String,
    val selectedRange: TimeRange,
    val showGoalReached: Boolean,
)

private data class ProgressSnapshot(
    val base: BaseProgressData,
    /** Per-day totals from the aggregates cache (flippidity C.1), range-filtered. */
    val dailyTotals: List<DailyFoodTotals>,
    val weightUnit: String,
    val selectedRange: TimeRange,
    val showGoalReached: Boolean
)

private fun ProgressSnapshot.toUiState(anchorDate: LocalDate = LocalDate.now()): ProgressUiState {
    val zone = ZoneId.systemDefault()
    val (rangeStart, rangeEnd) = selectedRange.instantRange(zone, today = anchorDate)
    val filteredWeights = base.entries
        .asSequence()
        .filter { it.date in rangeStart..rangeEnd }
        .sortedBy { it.date }
        .toList()
    val filteredBodyFats = base.bodyFatEntries
        .asSequence()
        .filter { it.date in rangeStart..rangeEnd }
        .sortedBy { it.date }
        .toList()
    val filteredMeasurements = base.bodyMeasurements
        .asSequence()
        .filter { it.date in rangeStart..rangeEnd }
        .sortedBy { it.date }
        .toList()
    val foodByDay = dailyTotals.associate { it.date to it }
    val dailyCalories = foodByDay
        .toSortedMap()
        .mapNotNull { (day, aggregate) ->
            if (aggregate.calories == 0) null else day to aggregate.calories
        }
    val completeCalorieDays = dailyCalories.filter { it.first < anchorDate }
    val calorieAverage = if (completeCalorieDays.isEmpty()) {
        null
    } else {
        completeCalorieDays.sumOf { it.second } / completeCalorieDays.size
    }
    val completeFoodByDay = foodByDay.filterKeys { it < anchorDate }
    val macroAverages: Triple<Double, Double, Double>
    if (completeFoodByDay.isEmpty()) {
        macroAverages = Triple(0.0, 0.0, 0.0)
    } else {
        val days = completeFoodByDay.size.toDouble()
        val protein = completeFoodByDay.values.sumOf { it.protein } / days
        val carbs = completeFoodByDay.values.sumOf { it.carbs } / days
        val fat = completeFoodByDay.values.sumOf { it.fat } / days
        macroAverages = Triple(protein, carbs, fat)
    }
    // #75: per-nutrient range means over the same complete days the macro
    // card uses, from the aggregate cache (never the per-entry diary).
    val nutrientAverages = base.averagesSelection.map { nutrient ->
        val avg = if (completeFoodByDay.isEmpty()) {
            0.0
        } else {
            completeFoodByDay.values.sumOf { it.amountOf(nutrient) } / completeFoodByDay.size
        }
        NutrientAverage(nutrient, avg, nutrient.goal(null, base.profile, base.optionalGoals))
    }
    // #60 phase 3: range goals = journaled average (MACRO-CYCLE-D, gaps
    // skipped) with the current profile target as fallback — no more "current
    // target painted over all history" once day types exist. Per-bar goals are
    // journal-first per day (resolver fallback for gaps), so a logged rest day
    // under a training-day rule line still colors correctly.
    val (rangeStartDay, rangeEndDay) = selectedRange.dateRange(anchorDate)
    val baseTargets = base.profile?.let { MacroPlanResolver.baseTargets(it) }
    val rangeTargets = MacroPlanResolver.journalAverage(base.goalJournal, rangeStartDay, rangeEndDay)
        ?: baseTargets
    val dailyCalorieGoals = dailyCalories.associate { (day, _) ->
        day to MacroPlanResolver.targetsForJournaled(base.goalJournal, base.profile, day, anchorDate).targets.calories
    }
    return ProgressUiState(
        profile = base.profile,
        weightCount = base.entries.size,
        bodyFatCount = base.bodyFatEntries.size,
        latestWeightKg = base.entries.maxByOrNull { it.date }?.weightKg,
        latestBodyFatFraction = base.bodyFatEntries.maxByOrNull { it.date }?.bodyFatFraction,
        weightUnit = weightUnit,
        timeRange = selectedRange,
        filteredWeights = filteredWeights,
        filteredBodyFats = filteredBodyFats,
        filteredMeasurements = filteredMeasurements,
        measurementSites = base.measurementSites.mapNotNull { BodyMeasurement.Site.fromStorageId(it) }.toSet(),
        dailyCalories = dailyCalories,
        calorieAverage = calorieAverage,
        calorieGoal = rangeTargets?.calories ?: 2000,
        proteinGoal = rangeTargets?.proteinG ?: 0,
        carbsGoal = rangeTargets?.carbsG ?: 0,
        fatGoal = rangeTargets?.fatG ?: 0,
        dailyCalorieGoals = dailyCalorieGoals,
        macroAverages = macroAverages,
        nutrientAverages = nutrientAverages,
        showNutrientAverages = base.showNutrientAverages,

        weightStats = filteredWeights.toWeightStats(),
        bodyFatStats = filteredBodyFats.toBodyFatStats(),
        goalReached = showGoalReached
    )
}

private fun TimeRange.instantRange(zone: ZoneId, today: LocalDate = LocalDate.now()): Pair<Instant, Instant> {
    val (startDate, endDate) = dateRange(today)
    val start = startDate.atStartOfDay(zone).toInstant()
    val end = endDate.atTime(23, 59, 59).atZone(zone).toInstant()
    return start to end
}

private fun List<WeightEntry>.toWeightStats(): WeightSummaryStats {
    if (isEmpty()) return WeightSummaryStats()
    val first = first()
    val last = last()
    return WeightSummaryStats(
        currentKg = last.weightKg,
        netChangeKg = last.weightKg - first.weightKg,
        averageKg = map { it.weightKg }.average()
    )
}

private fun List<BodyFatEntry>.toBodyFatStats(): BodyFatSummaryStats {
    if (isEmpty()) return BodyFatSummaryStats()
    val first = first()
    val last = last()
    return BodyFatSummaryStats(
        currentFraction = last.bodyFatFraction,
        netChangePercent = last.bodyFatPercent - first.bodyFatPercent,
        averagePercent = map { it.bodyFatPercent }.average()
    )
}

/** Builds Progress UI state for screenshot previews with a fixed anchor date. */
internal fun buildProgressPreviewUiState(
    profile: UserProfile?,
    weights: List<WeightEntry>,
    bodyFatEntries: List<BodyFatEntry>,
    foods: List<FoodEntry>,
    timeRange: TimeRange,
    anchorDate: LocalDate,
    weightUnit: String = "kg",
    bodyMeasurements: List<BodyMeasurement> = emptyList(),
    measurementSites: Set<BodyMeasurement.Site> = emptySet(),
    goalJournal: List<GoalJournalEntry> = emptyList(),
    /** #75: which nutrients the averages card shows; default = the original trio. */
    averagesSelection: Collection<String> = HomeTopNutrient.DefaultAveragesStorage,
): ProgressUiState {
    // Same range filter the old per-entry grouping applied inside toUiState:
    // previews receive full-history lists and must not count days outside
    // the selected range, so scope before aggregating.
    val (rangeStart, rangeEnd) = timeRange.instantRange(ZoneId.systemDefault(), today = anchorDate)
    return ProgressSnapshot(
        base = BaseProgressData(
            profile = profile,
            entries = weights,
            bodyFatEntries = bodyFatEntries,
            bodyMeasurements = bodyMeasurements,
            measurementSites = measurementSites.map { it.storageId }.toSet(),
            goalJournal = goalJournal,
            averagesSelection = HomeTopNutrient.averagesSelectionFromStorage(averagesSelection),
        ),
        dailyTotals = aggregateFoodEntriesByDay(
            foods.filter { it.timestamp in rangeStart..rangeEnd }
        ),
        weightUnit = weightUnit,
        selectedRange = timeRange,
        showGoalReached = false,
    ).toUiState(anchorDate)
}
