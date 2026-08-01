package app.chompass.ui.progress

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Display-only trailing moving-average for Progress weight charts.
 * Does **not** feed Adaptive Goals / forecast math.
 *
 * Shared semantics with `web/app/src/lib/chompass-core/weight-trend.js`:
 * 1. Bucket weigh-ins by calendar day in [zone]; average same-day values.
 * 2. For each day that has a weigh-in, average all daily values in the trailing
 *    [windowDays] calendar-day window that also have data.
 * 3. Emit a trend point only when that window contains at least [minDaysInWindow]
 *    distinct weigh-in days.
 */
private const val WEIGHT_TREND_WINDOW_DAYS = 7
private const val WEIGHT_TREND_MIN_DAYS = 2

data class WeightTrendInput(
    val at: Instant,
    val weightKg: Double,
)

data class WeightTrendPoint(
    val day: LocalDate,
    val valueKg: Double,
)

/** Average same-calendar-day weigh-ins. */
fun averageWeightByDay(
    weighIns: List<WeightTrendInput>,
    zone: ZoneId,
): List<WeightTrendPoint> {
    if (weighIns.isEmpty()) return emptyList()
    return weighIns
        .asSequence()
        .filter { it.weightKg > 0.0 }
        .groupBy { it.at.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (day, group) ->
            WeightTrendPoint(day = day, valueKg = group.map { it.weightKg }.average())
        }
}

/**
 * Trailing calendar-day moving average of daily weight averages.
 */
fun computeWeightTrend(
    weighIns: List<WeightTrendInput>,
    zone: ZoneId,
    windowDays: Int = WEIGHT_TREND_WINDOW_DAYS,
    minDaysInWindow: Int = WEIGHT_TREND_MIN_DAYS,
): List<WeightTrendPoint> {
    val daily = averageWeightByDay(weighIns, zone)
    if (daily.isEmpty()) return emptyList()
    val valueByDay = daily.associate { it.day to it.valueKg }
    val out = ArrayList<WeightTrendPoint>(daily.size)
    for (point in daily) {
        val end = point.day
        val start = end.minusDays((windowDays - 1).toLong())
        var sum = 0.0
        var count = 0
        var day = start
        while (!day.isAfter(end)) {
            val v = valueByDay[day]
            if (v != null) {
                sum += v
                count += 1
            }
            day = day.plusDays(1)
        }
        if (count >= minDaysInWindow) {
            out.add(WeightTrendPoint(day = end, valueKg = sum / count))
        }
    }
    return out
}

/**
 * Split trend points into contiguous segments when calendar gaps exceed [maxGapDays].
 */
fun splitTrendSegments(
    points: List<WeightTrendPoint>,
    maxGapDays: Long = WEIGHT_TREND_WINDOW_DAYS.toLong(),
): List<List<WeightTrendPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<WeightTrendPoint>>()
    var current = mutableListOf(points.first())
    for (i in 1 until points.size) {
        val gap = ChronoUnit.DAYS.between(points[i - 1].day, points[i].day)
        if (gap > maxGapDays) {
            segments.add(current)
            current = mutableListOf(points[i])
        } else {
            current.add(points[i])
        }
    }
    segments.add(current)
    return segments
}
