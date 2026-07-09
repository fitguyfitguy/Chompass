package org.codeberg.fitguy.nofud.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.models.BodyFatEntry
import org.codeberg.fitguy.nofud.models.BodyMeasurement
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WeightEntry
import org.codeberg.fitguy.nofud.services.health.DailyActivity
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

data class ProgressUiState(
    val entries: List<WeightEntry> = emptyList(),
    val bodyFatEntries: List<BodyFatEntry> = emptyList(),
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val profile: UserProfile? = null,
    val weightUnit: String = "kg",
    val timeRange: TimeRange = TimeRange.WEEK,
    val filteredWeights: List<WeightEntry> = emptyList(),
    val filteredBodyFats: List<BodyFatEntry> = emptyList(),
    val dailyCalories: List<Pair<LocalDate, Int>> = emptyList(),
    val macroAverages: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val weightStats: WeightSummaryStats = WeightSummaryStats(),
    val bodyFatStats: BodyFatSummaryStats = BodyFatSummaryStats(),
    val goalReached: Boolean = false
)

private data class BaseProgressData(
    val profile: UserProfile?,
    val entries: List<WeightEntry>,
    val bodyFatEntries: List<BodyFatEntry>,
    val bodyMeasurements: List<BodyMeasurement>
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

    init {
        viewModelScope.launch {
            if (container.prefs.healthConnectEnabled.first() &&
                container.health.isAvailable() &&
                container.health.hasActivityRead()
            ) {
                _activity.value = container.health.readDailyActivity(days = 7)
            }
        }
        combine(
            container.profileRepository.profile,
            container.weightRepository.entries,
            container.bodyFatRepository.entries,
            container.bodyMeasurementRepository.entries
        ) { profile, weights, bodyFats, measurements ->
            BaseProgressData(
                profile = profile,
                entries = weights,
                bodyFatEntries = bodyFats,
                bodyMeasurements = measurements
            )
        }.let { baseData ->
            combine(
                baseData,
                container.foodRepository.entries,
                container.prefs.weightUnit,
                timeRange,
                goalReached
            ) { base, foods, weightUnit, selectedRange, showGoalReached ->
                ProgressSnapshot(base, foods, weightUnit, selectedRange, showGoalReached)
            }.mapLatest { snapshot ->
                withContext(Dispatchers.Default) { snapshot.toUiState() }
            }.onEach { _ui.value = it }.launchIn(viewModelScope)
        }
    }

    fun addWeight(kg: Double) {
        addWeightAt(kg, Instant.now())
    }

    fun addWeightAt(kg: Double, at: Instant) {
        viewModelScope.launch {
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
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProgressViewModel(container) as T
    }
}

private data class DailyFoodAggregate(
    var calories: Int = 0,
    var protein: Double = 0.0,
    var carbs: Double = 0.0,
    var fat: Double = 0.0
)

private data class ProgressSnapshot(
    val base: BaseProgressData,
    val foods: List<FoodEntry>,
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
    val foodByDay = foods.groupByLocalDateInRange(
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        zone = zone
    )
    val dailyCalories = foodByDay
        .toSortedMap()
        .mapNotNull { (day, aggregate) ->
            if (aggregate.calories == 0) null else day to aggregate.calories
        }
    val macroAverages = if (foodByDay.isEmpty()) {
        Triple(0.0, 0.0, 0.0)
    } else {
        val days = foodByDay.size.toDouble()
        val protein = foodByDay.values.sumOf { it.protein } / days
        val carbs = foodByDay.values.sumOf { it.carbs } / days
        val fat = foodByDay.values.sumOf { it.fat } / days
        Triple(protein, carbs, fat)
    }
    return ProgressUiState(
        profile = base.profile,
        entries = base.entries,
        bodyFatEntries = base.bodyFatEntries,
        bodyMeasurements = base.bodyMeasurements,
        weightUnit = weightUnit,
        timeRange = selectedRange,
        filteredWeights = filteredWeights,
        filteredBodyFats = filteredBodyFats,
        dailyCalories = dailyCalories,
        macroAverages = macroAverages,
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

private fun List<FoodEntry>.groupByLocalDateInRange(
    rangeStart: Instant,
    rangeEnd: Instant,
    zone: ZoneId
): Map<LocalDate, DailyFoodAggregate> {
    val perDay = mutableMapOf<LocalDate, DailyFoodAggregate>()
    for (entry in this) {
        if (entry.timestamp < rangeStart || entry.timestamp > rangeEnd) continue
        val day = entry.timestamp.atZone(zone).toLocalDate()
        val existing = perDay.getOrPut(day) { DailyFoodAggregate() }
        existing.calories += entry.calories
        existing.protein += entry.protein
        existing.carbs += entry.carbs
        existing.fat += entry.fat
    }
    return perDay
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
): ProgressUiState {
    return ProgressSnapshot(
        base = BaseProgressData(
            profile = profile,
            entries = weights,
            bodyFatEntries = bodyFatEntries,
            bodyMeasurements = emptyList(),
        ),
        foods = foods,
        weightUnit = weightUnit,
        selectedRange = timeRange,
        showGoalReached = false,
    ).toUiState(anchorDate)
}
