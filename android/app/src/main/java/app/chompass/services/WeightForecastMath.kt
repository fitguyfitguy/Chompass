package app.chompass.services

import app.chompass.models.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pure forecast math extracted for unit tests and shared between forecast paths.
 * See [app.chompass.models.NutritionConstants] for energy constants.
 */
object WeightForecastMath {
    /** Below this logged-days / calendar-days ratio, intake is averaged over the full window. */
    const val SPARSE_LOGGING_FRACTION_THRESHOLD = 0.5

    const val TREND_DISAGREEMENT_KG_PER_WEEK = 0.3

    data class IntakeAverage(
        val avgDailyCalories: Int,
        val loggedDays: Int,
        val calendarDaysInWindow: Int,
        val usesCalendarDayAverage: Boolean,
    )

    fun calendarDaysInclusive(start: Instant, end: Instant, zone: ZoneId): Int {
        val startDate = start.atZone(zone).toLocalDate()
        val endDate = end.atZone(zone).toLocalDate()
        return (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)
    }

    data class CompleteDayWindow(
        val firstLogged: LocalDate?,
        val lastComplete: LocalDate,
        val calendarDays: Int,
        val loggedDates: Set<LocalDate>,
    )

    /**
     * Intake window for FCAST: from the first logged food in the lookback through yesterday.
     * Empty days before the first log do not count as sparse. Today is excluded (incomplete).
     */
    fun completeDayWindow(
        loggedDates: Collection<LocalDate>,
        today: LocalDate,
        maxLookbackDays: Int = 90,
    ): CompleteDayWindow {
        val lookbackStart = today.minusDays(maxLookbackDays.toLong())
        val yesterday = today.minusDays(1)
        val inLookback = loggedDates.filter { it in lookbackStart..yesterday }.toSet()
        if (inLookback.isEmpty()) {
            return CompleteDayWindow(
                firstLogged = null,
                lastComplete = yesterday,
                calendarDays = 1,
                loggedDates = emptySet(),
            )
        }
        val first = inLookback.min()
        val calendarDays = (ChronoUnit.DAYS.between(first, yesterday).toInt() + 1).coerceAtLeast(1)
        return CompleteDayWindow(
            firstLogged = first,
            lastComplete = yesterday,
            calendarDays = calendarDays,
            loggedDates = inLookback,
        )
    }

    /**
     * When logging is sparse (<50% of calendar days in the window), spread total intake across
     * the full lookback span so cheat-day-only logs do not inflate the forecast average.
     */
    fun averageDailyIntake(
        totalCalories: Int,
        loggedDays: Int,
        calendarDaysInWindow: Int,
    ): IntakeAverage {
        if (loggedDays <= 0) {
            return IntakeAverage(0, 0, calendarDaysInWindow.coerceAtLeast(1), usesCalendarDayAverage = false)
        }
        val calendarDays = calendarDaysInWindow.coerceAtLeast(1)
        val sparse = loggedDays.toDouble() / calendarDays < SPARSE_LOGGING_FRACTION_THRESHOLD
        val denominator = if (sparse) calendarDays else loggedDays
        return IntakeAverage(
            avgDailyCalories = totalCalories / denominator,
            loggedDays = loggedDays,
            calendarDaysInWindow = calendarDays,
            usesCalendarDayAverage = sparse,
        )
    }

    /**
     * Theil–Sen slope (median of pairwise slopes) in kg/day. More resistant to single outlier
     * weigh-ins than ordinary least-squares. Uses whole-day offsets from the earliest entry.
     */
    fun theilSenSlopePerDay(
        entries: List<WeightEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Double? {
        if (entries.size < 2) return null
        val sorted = entries.sortedBy { it.date }
        val originDate = sorted.first().date.atZone(zone).toLocalDate()
        val points = sorted.map { entry ->
            val dayOffset = ChronoUnit.DAYS.between(originDate, entry.date.atZone(zone).toLocalDate()).toDouble()
            dayOffset to entry.weightKg
        }
        val slopes = mutableListOf<Double>()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = points[j].first - points[i].first
                if (dx != 0.0) {
                    slopes.add((points[j].second - points[i].second) / dx)
                }
            }
        }
        if (slopes.isEmpty()) return null
        slopes.sort()
        return slopes[slopes.size / 2]
    }

    fun trendsDisagree(
        predictedWeeklyKg: Double,
        observedWeeklyKg: Double?,
        hasEnoughData: Boolean,
    ): Boolean = observedWeeklyKg?.let { observed ->
        hasEnoughData && abs(predictedWeeklyKg - observed) > TREND_DISAGREEMENT_KG_PER_WEEK
    } ?: false
}
