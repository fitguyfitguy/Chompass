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

/** Generic (unit-agnostic) daily sample for Progress trend math. */
data class MetricTrendInput(
    val at: Instant,
    val value: Double,
)

/** Generic (unit-agnostic) daily-average trend point. */
data class MetricTrendPoint(
    val day: LocalDate,
    val value: Double,
)

/** Average same-calendar-day weigh-ins. */
fun averageWeightByDay(
    weighIns: List<WeightTrendInput>,
    zone: ZoneId,
): List<WeightTrendPoint> =
    averageMetricByDay(
        weighIns.map { MetricTrendInput(at = it.at, value = it.weightKg) },
        zone,
    ).map { WeightTrendPoint(day = it.day, valueKg = it.value) }

/**
 * Trailing calendar-day moving average of daily weight averages.
 */
fun computeWeightTrend(
    weighIns: List<WeightTrendInput>,
    zone: ZoneId,
    windowDays: Int = WEIGHT_TREND_WINDOW_DAYS,
    minDaysInWindow: Int = WEIGHT_TREND_MIN_DAYS,
): List<WeightTrendPoint> =
    computeMetricTrend(
        weighIns.map { MetricTrendInput(at = it.at, value = it.weightKg) },
        zone,
        windowDays = windowDays,
        minDaysInWindow = minDaysInWindow,
    ).map { WeightTrendPoint(day = it.day, valueKg = it.value) }

/**
 * Split trend points into contiguous segments when calendar gaps exceed [maxGapDays].
 */
fun splitTrendSegments(
    points: List<WeightTrendPoint>,
    maxGapDays: Long = WEIGHT_TREND_WINDOW_DAYS.toLong(),
): List<List<WeightTrendPoint>> =
    splitMetricSegments(
        points.map { MetricTrendPoint(day = it.day, value = it.valueKg) },
        maxGapDays,
    ).map { segment -> segment.map { WeightTrendPoint(day = it.day, valueKg = it.value) } }

/** Average same-calendar-day samples; non-positive values are ignored. */
fun averageMetricByDay(
    inputs: List<MetricTrendInput>,
    zone: ZoneId,
): List<MetricTrendPoint> {
    if (inputs.isEmpty()) return emptyList()
    return inputs
        .asSequence()
        .filter { it.value > 0.0 }
        .groupBy { it.at.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (day, group) ->
            MetricTrendPoint(day = day, value = group.map { it.value }.average())
        }
}

/**
 * Trailing calendar-day moving average of daily metric averages.
 * Mirrors [computeWeightTrend]; shared by weight, body fat, and measurements.
 */
fun computeMetricTrend(
    inputs: List<MetricTrendInput>,
    zone: ZoneId,
    windowDays: Int = WEIGHT_TREND_WINDOW_DAYS,
    minDaysInWindow: Int = WEIGHT_TREND_MIN_DAYS,
): List<MetricTrendPoint> {
    val daily = averageMetricByDay(inputs, zone)
    if (daily.isEmpty()) return emptyList()
    val valueByDay = daily.associate { it.day to it.value }
    val out = ArrayList<MetricTrendPoint>(daily.size)
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
            out.add(MetricTrendPoint(day = end, value = sum / count))
        }
    }
    return out
}

/**
 * Split trend points into contiguous segments when calendar gaps exceed [maxGapDays].
 */
fun splitMetricSegments(
    points: List<MetricTrendPoint>,
    maxGapDays: Long = WEIGHT_TREND_WINDOW_DAYS.toLong(),
): List<List<MetricTrendPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<MetricTrendPoint>>()
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
