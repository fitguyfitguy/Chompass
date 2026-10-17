package app.chompass.ui.progress

import app.chompass.models.LocaleFormat
import app.chompass.models.EnergyFormat
import app.chompass.R

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chompass.ui.components.energyUnitLabel
import androidx.compose.ui.unit.sp
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.navigation.LocalEnergyUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import java.time.temporal.TemporalAdjusters
import app.chompass.models.UnitFormat

/** One plotted point on a trend chart — either a raw entry or the average of
 *  a date bucket when the range is too dense to draw every reading. Mirrors
 *  the iOS TrendPoint/downsampled helpers in ProgressComponents.swift. */
internal data class TrendPoint(val timeMs: Long, val value: Double)

internal data class WeightChartModel(
    val yMin: Double,
    val yMax: Double,
    val ticks: List<Double>,
    val tStart: Long,
    val tEnd: Long,
    val tRange: Long,
    val singleEntry: Boolean,
    val showsYear: Boolean,
    val xLabelFmt: DateTimeFormatter,
    val points: List<TrendPoint>,
    /** Analytical 7-day MA (display units); empty when sparse. */
    val trendPoints: List<TrendPoint>,
    val trendSegments: List<List<TrendPoint>>,
    val showsDots: Boolean,
    val goalDisplayValue: Double?
)

/** One day's marker under a chart: day-type dot, untracked dash (UI-UX §10).
 *  [partialUntracked] marks a week bucket holding untracked days below the
 *  majority — drawn as a fainter dash so stray untracked days stay visible
 *  at 6M/1Y/All. */
internal data class DayMarker(
    val date: LocalDate,
    val typeColor: Color?,
    val untracked: Boolean,
    val partialUntracked: Boolean = false,
)

/**
 * Dense marker list for the trend charts: per-day while the span fits
 * [maxSlots] days, otherwise ISO-week buckets (Monday start — same grouping
 * as [bucketCalorieSlots]) with the majority type and a majority-untracked
 * dash, so dots stay readable at 6M/1Y ranges. Weeks holding untracked days
 * below the majority carry [DayMarker.partialUntracked].
 */
internal fun buildMarkerLane(
    start: LocalDate,
    end: LocalDate,
    types: Map<String, String>,
    untracked: Set<String>,
    typeColorOf: (String) -> Color,
    maxSlots: Int = 90,
): List<DayMarker> {
    if (start.isAfter(end)) return emptyList()
    val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
    fun dayMarker(day: LocalDate) =
        DayMarker(day, types[day.toString()]?.let(typeColorOf), day.toString() in untracked)
    if (totalDays <= maxSlots) return (0 until totalDays).map { dayMarker(start.plusDays(it.toLong())) }
    return (0 until totalDays).map { start.plusDays(it.toLong()) }
        .groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .map { (weekStart, days) ->
            val majorityType = days.mapNotNull { types[it.toString()] }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key
            val untrackedCount = days.count { it.toString() in untracked }
            val majority = untrackedCount * 2 > days.size
            DayMarker(
                date = weekStart,
                typeColor = majorityType?.let(typeColorOf),
                untracked = majority,
                partialUntracked = !majority && untrackedCount > 0,
            )
        }
}

/** One untracked backdrop band. [partial] marks a week bucket holding
 *  untracked days below the majority — drawn at half alpha so stray
 *  untracked days stay visible at 6M/1Y/All. */
internal data class UntrackedBand(val range: ClosedRange<Long>, val partial: Boolean)

/**
 * Epoch-ms day-boundary bands (start-of-day inclusive, system zone) of
 * untracked days inside [start, end] — the muted backdrop bands behind the
 * trend plots. Ranges longer than [maxSlots] days bucket per ISO week
 * (Monday start — same grouping as [buildMarkerLane]): a majority-untracked
 * week yields a full band, a week holding any untracked days below the
 * majority yields a [UntrackedBand.partial] band, and adjacent same-tier
 * weeks merge into one band covering the full final week.
 */
internal fun buildUntrackedSpans(
    start: LocalDate,
    end: LocalDate,
    untracked: Set<String>,
    maxSlots: Int = 90,
): List<UntrackedBand> {
    if (start.isAfter(end) || untracked.isEmpty()) return emptyList()
    val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
    val weekly = totalDays > maxSlots
    val step = if (weekly) 7L else 1L
    // Marked dates with their tier: full for untracked days (daily mode) or
    // majority-untracked weeks, partial for weeks below the majority.
    val marked: List<Pair<LocalDate, Boolean>> = (0 until totalDays)
        .map { start.plusDays(it.toLong()) }
        .let { days ->
            if (!weekly) days.filter { it.toString() in untracked }.map { it to false }
            else days.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                .toSortedMap()
                .flatMap { (weekStart, daysInWeek) ->
                    val count = daysInWeek.count { it.toString() in untracked }
                    if (count == 0) emptyList()
                    else listOf(weekStart to (count * 2 <= daysInWeek.size))
                }
        }
    if (marked.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    fun epochMs(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val tail = if (weekly) 6L else 0L
    val bands = mutableListOf<UntrackedBand>()
    var runStart = marked.first()
    var runEnd = marked.first()
    for (markedDay in marked.drop(1)) {
        val (day, partial) = markedDay
        if (partial == runStart.second && day.toEpochDay() - runEnd.first.toEpochDay() <= step) {
            runEnd = markedDay
        } else {
            bands += UntrackedBand(
                epochMs(runStart.first)..epochMs(runEnd.first.plusDays(tail).coerceAtMost(end)),
                runStart.second,
            )
            runStart = markedDay
            runEnd = markedDay
        }
    }
    bands += UntrackedBand(
        epochMs(runStart.first)..epochMs(runEnd.first.plusDays(tail).coerceAtMost(end)),
        runStart.second,
    )
    return bands
}

/** One calendar day on the calorie axis: a logged value, an untracked dash,
 *  or nothing — the day keeps its place instead of collapsing the bar row.
 *  Public: exposed through [ProgressUiState]. */
data class CalorieSlot(
    val day: LocalDate,
    val kcal: Int?,
    val untracked: Boolean,
)

/** One [CalorieSlot] per calendar day in [start, end]; untracked days always
 *  carry `kcal = null` even if somehow logged, logged-at-zero stays 0. */
internal fun buildCalorieSlots(
    start: LocalDate,
    end: LocalDate,
    logged: Map<LocalDate, Int>,
    untracked: Set<String>,
): List<CalorieSlot> {
    if (start.isAfter(end)) return emptyList()
    val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
    return (0 until totalDays).map { i ->
        val day = start.plusDays(i.toLong())
        val untrackedDay = day.toString() in untracked
        CalorieSlot(
            day = day,
            kcal = if (untrackedDay) null else logged[day],
            untracked = untrackedDay,
        )
    }
}

/**
 * Overlay value behind a calorie slot: the direct day hit, else — slot days
 * are ISO-week starts once `bucketCalorieSlots` rolls up — the average of the
 * readings inside that week `[slotDay, slotDay+6]`, else null.
 */
internal fun overlayValueForSlot(valuesByDay: Map<LocalDate, Double>, slotDay: LocalDate): Double? {
    valuesByDay[slotDay]?.let { return it }
    var sum = 0.0
    var count = 0
    for (offset in 0..6) {
        valuesByDay[slotDay.plusDays(offset.toLong())]?.let {
            sum += it
            count++
        }
    }
    return if (count == 0) null else sum / count
}

/**
 * Cap calorie-slot draw calls: within [maxSlots] the calendar-day slots pass
 * through untouched; longer ranges roll up to ISO weeks (Monday start), summing
 * logged kcal (null when nothing was logged that week) with a majority-untracked
 * dash. Slot totals must use the unbucketed series — this is canvas-only.
 */
internal fun bucketCalorieSlots(
    slots: List<CalorieSlot>,
    maxSlots: Int = 90,
): List<CalorieSlot> {
    if (slots.size <= maxSlots) return slots
    return slots
        .groupBy { it.day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .map { (weekStart, days) ->
            CalorieSlot(
                day = weekStart,
                kcal = days.mapNotNull { slot -> slot.kcal }
                    .takeIf { days.any { slot -> slot.kcal != null } }
                    ?.sum(),
                untracked = days.count { it.untracked } * 2 > days.size,
            )
        }
}

/** Untracked backdrop bands for the chart window [tStart, tStart + tRange]
 *  (same day-window derivation as the marker lane). */
@Composable
private fun rememberUntrackedSpans(
    tStart: Long,
    tRange: Long,
    untrackedDays: Set<String>,
): List<UntrackedBand> {
    val zone = remember { ZoneId.systemDefault() }
    return remember(tStart, tRange, untrackedDays, zone) {
        buildUntrackedSpans(
            start = Instant.ofEpochMilli(tStart).atZone(zone).toLocalDate(),
            end = Instant.ofEpochMilli(tStart + tRange).atZone(zone).toLocalDate(),
            untracked = untrackedDays,
        )
    }
}

/** Full-height muted rects behind the plot data; partial weeks draw at half
 *  alpha so a stray untracked day marks its week without overstating it. */
private fun DrawScope.drawUntrackedBands(
    bands: List<UntrackedBand>,
    tStart: Long,
    tRange: Long,
    color: Color,
) {
    if (tRange <= 0L) return
    val dayMs = 86_400_000L
    bands.forEach { band ->
        val tierColor = if (band.partial) color.copy(alpha = color.alpha * 0.5f) else color
        val x0 = ((band.range.start - tStart).toFloat() / tRange * size.width).coerceIn(0f, size.width)
        val x1 = ((band.range.endInclusive + dayMs - tStart).toFloat() / tRange * size.width)
            .coerceIn(0f, size.width)
        if (x1 > x0) drawRect(tierColor, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
    }
}

/** [straightTrendPath] closed down to the canvas bottom — the flat trend fill
 *  (flat alpha, no gradient — restyle D1/D10). */
internal fun trendFillPath(points: List<Offset>, height: Float): Path {
    if (points.isEmpty()) return Path()
    val path = straightTrendPath(points)
    path.lineTo(points.last().x, height)
    path.lineTo(points.first().x, height)
    path.close()
    return path
}

/** Left-to-right partial of the straight path through [points] at [fraction]
 *  (1f = full path) for the animated chart reveal, written into [dst]. */
internal fun trimmedStraightPath(
    points: List<Offset>,
    fraction: Float,
    measure: PathMeasure,
    dst: Path,
): Path {
    dst.reset()
    if (points.isEmpty()) return dst
    measure.setPath(straightTrendPath(points), false)
    val length = measure.length
    if (length <= 0f) return dst
    measure.getSegment(0f, length * fraction.coerceIn(0f, 1f), dst, true)
    return dst
}

/** 8dp marker strip: 3dp type dots / 6dp untracked dashes at slot fractions. */
@Composable
internal fun MarkerLane(
    markers: List<DayMarker>,
    xFraction: (Int) -> Float,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty()) return
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val laneDescription = stringResource(R.string.a11y_marker_lane)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics { contentDescription = laneDescription }
    ) {
        val w = size.width
        val midY = size.height / 2f
        markers.forEachIndexed { i, marker ->
            val x = xFraction(i) * w
            when {
                marker.untracked -> drawLine(
                    color = onVariant.copy(alpha = 0.6f),
                    start = Offset(x - 3.dp.toPx(), midY),
                    end = Offset(x + 3.dp.toPx(), midY),
                    strokeWidth = 1.5.dp.toPx(),
                )
                // Week bucket with untracked days below the majority.
                marker.partialUntracked -> drawLine(
                    color = onVariant.copy(alpha = 0.3f),
                    start = Offset(x - 3.dp.toPx(), midY),
                    end = Offset(x + 3.dp.toPx(), midY),
                    strokeWidth = 1.5.dp.toPx(),
                )
                marker.typeColor != null -> drawCircle(
                    color = marker.typeColor,
                    radius = 1.5.dp.toPx(),
                    center = Offset(x, midY),
                )
            }
        }
    }
}

/** Shared bar geometry for the calorie chart canvas, marker lane, and labels. */
internal data class CalorieBarGeometry(val barWidth: Float, val gap: Float, val startX: Float) {
    fun slotCenter(index: Int): Float = startX + index * (barWidth + gap) + barWidth / 2f
}

internal fun calorieBarGeometry(areaWidthPx: Float, n: Int, density: Density): CalorieBarGeometry {
    val gap = 4f
    val maxBarPx = with(density) { 60.dp.toPx() }
    val rawWidth = (areaWidthPx - gap * (n - 1)) / n
    val barWidth = rawWidth.coerceIn(2f, maxBarPx)
    val totalGroupW = barWidth * n + gap * (n - 1)
    val startX = ((areaWidthPx - totalGroupW) / 2f).coerceAtLeast(0f)
    return CalorieBarGeometry(barWidth, gap, startX)
}

/**
 * X position for a combo-overlay timestamp on the calorie chart: the linear
 * time→slot mapping snapped to the **centered bar slot geometry** the bars
 * themselves use, so trend line/dots sit on their bars' centers at every
 * range (daily slots and ISO-week buckets alike).
 */
internal fun overlaySlotCenterX(
    timeMs: Long,
    startMs: Long,
    endMs: Long,
    slotCount: Int,
    startX: Float,
    slotPitch: Float,
    barWidth: Float,
): Float {
    val span = (endMs - startMs).coerceAtLeast(1L)
    val slot = (((timeMs - startMs).toDouble() / span).coerceIn(0.0, 1.0) * slotCount)
        .coerceIn(0.0, (slotCount - 1).toDouble())
    return startX + (slot * slotPitch).toFloat() + barWidth / 2f
}

/** X-axis label picks for the calorie chart: every [slotStep]-th slot, with
 *  the end slot always labeled — replacing the last uniform pick rather than
 *  appending, so label boxes stay [slotStep] slots apart instead of
 *  overlapping at the right edge. */
internal fun calorieLabelIndices(n: Int, slotStep: Int): List<Int> {
    if (n <= 0) return emptyList()
    val picks = mutableListOf<Int>()
    var i = 0
    while (i < n) {
        picks.add(i)
        i += slotStep
    }
    picks[picks.size - 1] = n - 1
    return picks
}

/** Averages a date-sorted series into equal date buckets once it outgrows
 *  [maxPoints]. Hundreds of raw readings drew every dot on top of its
 *  neighbours and turned the line into a solid band — ~60 bucket averages
 *  keep the trend shape readable. Sparse series pass through untouched. */
internal fun downsampleTrend(points: List<TrendPoint>, maxPoints: Int = 60): List<TrendPoint> {
    if (points.size <= maxPoints) return points
    val dayMs = 86_400_000L
    val first = points.first().timeMs
    val spanDays = maxOf(1L, (points.last().timeMs - first) / dayMs)
    val bucketMs = Math.ceil(spanDays.toDouble() / maxPoints).toLong().coerceAtLeast(1L) * dayMs
    return points
        .groupBy { (it.timeMs - first) / bucketMs }
        .toSortedMap()
        .values
        .map { bucket ->
            TrendPoint(
                timeMs = bucket.map { it.timeMs }.average().toLong(),
                value = bucket.map { it.value }.average()
            )
        }
}

internal fun straightTrendPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

/** Compute "nice" axis tick values across [min, max] with approx [count] divisions. */
internal fun niceAxisTicks(min: Double, max: Double, count: Int): List<Double> {
    val range = max - min
    if (range <= 0) return listOf(min)
    val rawStep = range / (count - 1)
    val mag = Math.pow(10.0, Math.floor(Math.log10(rawStep)))
    val normalized = rawStep / mag
    val niceStep = when {
        normalized < 1.5 -> 1.0
        normalized < 3.0 -> 2.0
        normalized < 7.0 -> 5.0
        else -> 10.0
    } * mag
    val firstTick = Math.ceil(min / niceStep) * niceStep
    val out = mutableListOf<Double>()
    var v = firstTick
    while (v <= max + 1e-9) {
        out.add(v)
        v += niceStep
    }
    return out
}

internal fun formatTick(value: Double): String =
    if (value >= 1000) LocaleFormat.integer(value.toInt())
    else if (value == value.toInt().toDouble()) value.toInt().toString()
    else LocaleFormat.decimal(value, 1)

/** Pick at most [maxLabels] evenly-spaced bar indices for x-axis labelling. */
internal fun pickXLabelIndices(n: Int, maxLabels: Int = 7): List<Int> {
    if (n <= 0) return emptyList()
    if (n <= maxLabels) return (0 until n).toList()
    val step = (n - 1).toFloat() / (maxLabels - 1)
    return (0 until maxLabels).map { i -> (i * step).toInt().coerceIn(0, n - 1) }.distinct()
}

// region Unified body overlay chart (Progress v2)

internal const val OVERLAY_KEY_WEIGHT = "weight"
internal const val OVERLAY_KEY_BODY_FAT = "bodyfat"

/** Per-series display metadata, resolved by the caller (string resources,
 *  units, palette); keeps [buildOverlayChartModel] pure. [unitLabel] is the
 *  value suffix (" kg", "%", " cm") used by the legend and inspection tag. */
internal data class OverlaySeriesMeta(
    val label: String,
    val unitLabel: String,
    val color: Color,
)

/** One plotted series in the unified body overlay chart: weight, body fat, or
 *  a measurement site. Points are display-unit daily buckets; the draw-time
 *  normalization is per series (no dual axis — legend chips + tap inspection
 *  carry the real values). */
internal data class OverlaySeries(
    /** `"weight" | "bodyfat" | `BodyMeasurement.Site.storageId`. */
    val key: String,
    val label: String,
    val color: Color,
    val unitLabel: String,
    /** Daily-averaged raw readings, display units. */
    val points: List<TrendPoint>,
    /** Analytical trailing moving average ([computeMetricTrend]), display units. */
    val trendPoints: List<TrendPoint>,
    val trendSegments: List<List<TrendPoint>>,
    val showsDots: Boolean,
    val currentValue: Double?,
    val deltaOverRange: Double?,
    val goalDisplayValue: Double?,
)

/** Day → seriesKey → display value, for the tap inspection tag. */
internal data class OverlayChartModel(
    val series: List<OverlaySeries>,
    val markers: List<DayMarker>,
    val xFromMs: Long,
    val xToMs: Long,
    val dayValues: Map<String, Map<String, Double>>,
) {
    val tRange: Long get() = maxOf(1L, xToMs - xFromMs)

    /** All points inside one calendar day → center the lone column of data. */
    val singleEntry: Boolean get() = series.isNotEmpty() && xToMs - xFromMs < 86_400_000L
}

/**
 * Per-series draw scale: min–max over that series' own data. A goal outside
 * the data range extends the scale so the goal rule sits 5% inset from the
 * edge instead of on it; a constant series maps to the vertical mid-line
 * ([fraction] guards the divide-by-zero).
 */
internal data class OverlayScale(val min: Double, val max: Double) {
    fun fraction(value: Double): Float {
        val range = max - min
        if (range <= 1e-9) return 0.5f
        return ((value - min) / range).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        fun of(
            values: List<Double>,
            goal: Double?,
            goalPadFraction: Double = 0.05,
        ): OverlayScale {
            if (values.isEmpty()) return OverlayScale(0.0, 1.0)
            var lo = values.min()
            var hi = values.max()
            if (goal != null) {
                if (goal < lo) lo = goal - (hi - lo) * goalPadFraction
                if (goal > hi) hi = goal + (hi - lo) * goalPadFraction
            }
            return OverlayScale(lo, hi)
        }
    }
}

/**
 * Builds the unified body overlay model: one series per source that has data
 * in range (empty-after-filter sources are dropped; an all-empty result yields
 * an empty model the caller renders as a placeholder). Pure so the
 * normalization invariants stay JVM-testable. Values are converted to display
 * units here (kg→lb, cm→in, fraction→percent), mirroring the retired
 * weight/body-fat chart builders.
 */
internal fun buildOverlayChartModel(
    weights: List<WeightEntry>,
    bodyFats: List<BodyFatEntry>,
    measurements: List<BodyMeasurement>,
    sites: List<BodyMeasurement.Site>,
    goalWeightKg: Double?,
    goalBodyFatFraction: Double?,
    useMetricWeight: Boolean,
    useMetricLength: Boolean,
    meta: Map<String, OverlaySeriesMeta>,
    dayTypeByDay: Map<String, String>,
    untrackedDays: Set<String>,
    typeColorOf: (String) -> Color,
): OverlayChartModel {
    val zone = ZoneId.systemDefault()
    fun dayMs(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun kgDisplay(kg: Double) = if (useMetricWeight) kg else UnitFormat.kgToLbs(kg)
    fun cmDisplay(cm: Double) = if (useMetricLength) cm else UnitFormat.cmToInches(cm)

    /** Series plus its full-resolution daily values (pre-downsampling) for the tap tag. */
    fun buildSeries(key: String, inputs: List<MetricTrendInput>, goal: Double?): Pair<OverlaySeries, Map<String, Double>>? {
        val seriesMeta = meta[key] ?: return null
        val daily = averageMetricByDay(inputs, zone)
        if (daily.isEmpty()) return null
        val points = downsampleTrend(daily.map { TrendPoint(dayMs(it.day), it.value) })
        val trend = computeMetricTrend(inputs, zone)
        val trendPoints = downsampleTrend(trend.map { TrendPoint(dayMs(it.day), it.value) })
        val trendSegments = splitMetricSegments(trend)
            .map { segment -> downsampleTrend(segment.map { TrendPoint(dayMs(it.day), it.value) }) }
        return OverlaySeries(
            key = key,
            label = seriesMeta.label,
            color = seriesMeta.color,
            unitLabel = seriesMeta.unitLabel,
            points = points,
            trendPoints = trendPoints,
            trendSegments = trendSegments,
            showsDots = points.size <= 31,
            currentValue = points.lastOrNull()?.value,
            deltaOverRange = if (points.size >= 2) {
                points.last().value - points.first().value
            } else {
                null
            },
            goalDisplayValue = goal,
        ) to daily.associate { it.day.toString() to it.value }
    }

    val built = buildList {
        buildSeries(
            OVERLAY_KEY_WEIGHT,
            weights.map { MetricTrendInput(at = it.date, value = kgDisplay(it.weightKg)) },
            goalWeightKg?.let(::kgDisplay),
        )?.let { add(it) }
        buildSeries(
            OVERLAY_KEY_BODY_FAT,
            bodyFats.map { MetricTrendInput(at = it.date, value = it.bodyFatFraction * 100.0) },
            goalBodyFatFraction?.times(100.0),
        )?.let { add(it) }
        for (site in sites) {
            buildSeries(
                site.storageId,
                measurements.mapNotNull { entry ->
                    entry.value(site)?.let { cm -> MetricTrendInput(at = entry.date, value = cmDisplay(cm)) }
                },
                null,
            )?.let { add(it) }
        }
    }
    val seriesList = built.map { it.first }
    if (seriesList.isEmpty()) {
        return OverlayChartModel(emptyList(), emptyList(), 0L, 1L, emptyMap())
    }

    // Shared x axis: day-start of the earliest bucket through the end of the
    // latest day, so the newest point sits one slot short of the right edge
    // instead of the range collapsing to entry timestamps.
    val xFromMs = seriesList.minOf { s -> s.points.minOf { it.timeMs } }
    val xToMs = seriesList.maxOf { s -> s.points.maxOf { it.timeMs } } + 86_400_000L - 1
    val startDay = Instant.ofEpochMilli(xFromMs).atZone(zone).toLocalDate()
    val endDay = Instant.ofEpochMilli(xToMs).atZone(zone).toLocalDate()
    val markers = buildMarkerLane(startDay, endDay, dayTypeByDay, untrackedDays, typeColorOf)
    val dayValues = HashMap<String, MutableMap<String, Double>>()
    // Full-resolution daily values: buckets dropped by downsampling still
    // resolve in the tap inspection tag.
    for ((s, dailyValues) in built) {
        for ((day, value) in dailyValues) {
            dayValues.getOrPut(day) { HashMap() }[s.key] = value
        }
    }
    return OverlayChartModel(
        series = seriesList,
        markers = markers,
        xFromMs = xFromMs,
        xToMs = xToMs,
        dayValues = dayValues,
    )
}

// endregion

/** X-axis labels under a trend chart, matching the label density of the iOS
 *  charts: five dates aligned with the canvas' quarter gridlines, or
 *  first/middle/last with the year on multi-year spans (wider "MMM yyyy"
 *  labels need the extra room). */
@Composable
internal fun TrendXAxisLabels(
    tStart: Long,
    tEnd: Long,
    showsYear: Boolean,
    singleEntry: Boolean,
    fmt: DateTimeFormatter,
    color: Color,
    endPadding: Dp
) {
    val labels = when {
        singleEntry -> listOf(fmt.format(Instant.ofEpochMilli(tStart)))
        showsYear -> listOf(tStart, (tStart + tEnd) / 2, tEnd)
            .map { fmt.format(Instant.ofEpochMilli(it)) }
        else -> (0..4)
            .map { i -> fmt.format(Instant.ofEpochMilli(tStart + (tEnd - tStart) * i / 4)) }
            // Spans of a couple days format to repeating dates — drop the dupes.
            .let { all -> all.filterIndexed { i, label -> i == 0 || label != all[i - 1] } }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, end = endPadding),
        horizontalArrangement = if (labels.size == 1) Arrangement.Center else Arrangement.SpaceBetween
    ) {
        labels.forEach { Text(it, fontSize = 11.sp, color = color) }
    }
}

/**
 * Unified body overlay chart: one normalized trend line per series over a
 * shared x axis. Per-series min–max normalization happens here ([OverlayScale])
 * — real values live in the legend chips and the tap inspection tag, which
 * lists every series' value for the tapped day ("–" when a series has no
 * reading that day). The legend itself is the chip row in the body section.
 */
@Composable
internal fun OverlayChartCanvas(
    model: OverlayChartModel,
    immediate: Boolean = false,
    /** Section title for the TalkBack inspection description. */
    title: String,
    untrackedDays: Set<String> = emptySet(),
) {
    val zone = remember { ZoneId.systemDefault() }
    val untrackedSpans = rememberUntrackedSpans(model.xFromMs, model.tRange, untrackedDays)
    val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val rawDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val reveal = remember(model, immediate) { Animatable(if (immediate) 1f else 0f) }
    LaunchedEffect(model, immediate) {
        if (!immediate) reveal.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }
    val tailAlpha = ((reveal.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val pathMeasure = remember { PathMeasure() }
    val trimDst = remember { Path() }

    // Per-series draw scales (min–max ∪ goal clamp), keyed for O(1) lookups.
    val scales = remember(model) {
        model.series.associate { s ->
            s.key to OverlayScale.of(
                values = s.trendPoints.map { it.value } + s.points.map { it.value },
                goal = s.goalDisplayValue,
            )
        }
    }
    // Tap hit-testing runs on the merged day buckets of every series, so days
    // that only a non-weight series covers stay inspectable.
    val hitTimes = remember(model) {
        model.series.flatMap { s -> s.points.map { it.timeMs } }.distinct().sorted()
    }
    var inspectedIndex by remember(model) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val chipBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Strong)
    val chipForeground = MaterialTheme.colorScheme.surface
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val spanDays = model.tRange / 86_400_000L
    val showsYear = spanDays > 150 &&
        Instant.ofEpochMilli(model.xFromMs).atZone(zone).year !=
        Instant.ofEpochMilli(model.xToMs).atZone(zone).year
    val xLabelFmt = remember(showsYear, zone) { LocaleFormat.monthOrDayZoned(showsYear, zone) }

    val inspectedTime = inspectedIndex?.let { hitTimes.getOrNull(it) }
    val inspectedDayIso = inspectedTime
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
    val inspectedValues = inspectedDayIso?.let { model.dayValues[it] }
    fun valueText(series: OverlaySeries, value: Double?) =
        if (value == null) "–" else "${formatTick(value)}${series.unitLabel}"
    val inspectedSummary = inspectedValues?.let { values ->
        model.series.joinToString(" · ") { s -> valueText(s, values[s.key]) }
    }
    val chartDescription = if (inspectedTime != null && inspectedSummary != null) {
        stringResource(
            R.string.a11y_chart_inspection,
            title,
            xLabelFmt.format(Instant.ofEpochMilli(inspectedTime)),
            inspectedSummary,
        )
    } else {
        title
    }
    val inspectedLabel = inspectedTime?.let { time ->
        if (inspectedSummary != null) {
            "${xLabelFmt.format(Instant.ofEpochMilli(time))} · $inspectedSummary"
        } else {
            null
        }
    }
    val laneMarkers = model.markers
    val laneXFraction: (Int) -> Float = { i ->
        val marker = laneMarkers.getOrNull(i)
        if (marker == null || model.singleEntry || model.tRange <= 0L) {
            0.5f
        } else {
            ((marker.date.atStartOfDay(zone).toInstant().toEpochMilli() - model.xFromMs)
                .toFloat() / model.tRange).coerceIn(0f, 1f)
        }
    }
    Row(Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .semantics { contentDescription = chartDescription }
                .pointerInput(model) {
                    detectTapGestures { tap ->
                        if (hitTimes.isEmpty()) return@detectTapGestures
                        val index = if (model.singleEntry || hitTimes.size == 1) {
                            0
                        } else {
                            var best = 0
                            var bestDx = Float.MAX_VALUE
                            for ((index, time) in hitTimes.withIndex()) {
                                val px = ((time - model.xFromMs).toDouble() / model.tRange * size.width).toFloat()
                                val dx = abs(px - tap.x)
                                if (dx < bestDx) {
                                    bestDx = dx
                                    best = index
                                }
                            }
                            best
                        }
                        inspectedIndex = if (inspectedIndex == index) null else index
                    }
                }
        ) {
            val w = size.width; val h = size.height
            fun xOf(timeMs: Long): Float =
                if (model.singleEntry) w / 2f
                else ((timeMs - model.xFromMs).toDouble() / model.tRange * w).toFloat()
            fun yOf(series: OverlaySeries, value: Double): Float =
                h - (scales.getValue(series.key).fraction(value) * h)
            fun offsetOf(series: OverlaySeries, p: TrendPoint): Offset =
                Offset(xOf(p.timeMs), yOf(series, p.value))
            // Structure: 5 horizontal divisions + dashed quarter columns
            // (no y tick labels — per-series scales make shared labels wrong;
            // the legend chips carry the real values).
            for (i in 0..4) {
                val y = (i.toFloat() / 4f) * h
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            for (i in 0..4) {
                val x = (i.toFloat() / 4f) * w
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f), end = Offset(x, h),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
                )
            }
            // Untracked bands sit behind every plot layer (#106).
            drawUntrackedBands(untrackedSpans, model.xFromMs, model.tRange, bandColor)
            // Per-series goal rules, right above the data.
            for (s in model.series) {
                val goal = s.goalDisplayValue ?: continue
                val y = yOf(s, goal)
                drawLine(
                    color = s.color.copy(alpha = 0.7f),
                    start = Offset(0f, y), end = Offset(w, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                )
            }
            clipRect {
                for (s in model.series) {
                    // Smoothed trend is the line; when the series is too sparse
                    // for the moving average, fall back to the raw path so the
                    // series still reads.
                    val segments = s.trendSegments.ifEmpty {
                        if (s.points.size >= 2) listOf(s.points) else emptyList()
                    }
                    for (segment in segments) {
                        val offsets = segment.map { offsetOf(s, it) }
                        drawPath(
                            trendFillPath(offsets, h),
                            s.color.copy(alpha = 0.08f * reveal.value),
                        )
                        if (offsets.size == 1) {
                            drawCircle(s.color, radius = 3.dp.toPx(), center = offsets.first())
                        } else {
                            drawPath(
                                trimmedStraightPath(offsets, reveal.value, pathMeasure, trimDst),
                                s.color,
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }
                    }
                    if (s.showsDots && tailAlpha > 0f) {
                        s.points.forEach { p ->
                            drawCircle(
                                rawDotColor.copy(alpha = 0.75f * tailAlpha),
                                radius = 3.dp.toPx(),
                                center = offsetOf(s, p),
                            )
                        }
                    }
                }
            }
            // Tap inspection: guide line + per-series halos at the tapped day.
            if (inspectedTime != null) {
                val x = xOf(inspectedTime)
                drawLine(
                    color = guideColor,
                    start = Offset(x, 0f), end = Offset(x, h),
                    strokeWidth = 1f,
                )
                for (s in model.series) {
                    val p = s.points.firstOrNull { it.timeMs == inspectedTime } ?: continue
                    val center = offsetOf(s, p)
                    drawCircle(s.color.copy(alpha = 0.25f), radius = 16f, center = center)
                    drawCircle(s.color, radius = 7f, center = center)
                }
                if (inspectedLabel != null) {
                    drawInspectionTag(
                        inspectedLabel,
                        textMeasurer,
                        Offset(x, h / 2f),
                        w,
                        chipBackground,
                        chipForeground,
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        MarkerLane(
            markers = laneMarkers,
            xFraction = laneXFraction,
            modifier = Modifier.weight(1f),
        )
    }
    Box(Modifier.alpha(tailAlpha)) {
        TrendXAxisLabels(
            model.xFromMs,
            model.xToMs,
            showsYear,
            model.singleEntry,
            xLabelFmt,
            secondaryColor,
            endPadding = 4.dp,
        )
    }
}

/** Y-axis / time-range model for a body-measurement trend plot. Reuses the
 *  weight chart model shape. Series values are plain cm (or inches once
 *  converted by the caller); the trend is the same Step-1 trailing moving
 *  average the weight/body-fat overlay uses ([computeMetricTrend]) — sparse
 *  site cadence yields an empty trend and the canvas falls back to the raw
 *  readings path. */
internal fun buildMeasurementChartModel(series: List<TrendPoint>): WeightChartModel {
    if (series.isEmpty()) {
        return WeightChartModel(
            yMin = 0.0, yMax = 1.0, ticks = listOf(0.0),
            tStart = 0L, tEnd = 0L, tRange = 1L,
            singleEntry = true, showsYear = false,
            xLabelFmt = LocaleFormat.monthOrDayZoned(false, ZoneId.systemDefault()),
            points = emptyList(), trendPoints = emptyList(), trendSegments = emptyList(),
            showsDots = false, goalDisplayValue = null,
        )
    }
    val values = series.map { it.value }
    val minV = values.min()
    val maxV = values.max()
    val pad = maxOf((maxV - minV) * 0.15, 1.0)
    val yMin = minV - pad
    val yMax = maxV + pad
    val tStart = series.first().timeMs
    val tEnd = series.last().timeMs
    val singleEntry = series.size == 1
    val tRange = maxOf(1L, tEnd - tStart)
    val ticks = niceAxisTicks(yMin, yMax, count = 5)
    val zone = ZoneId.systemDefault()
    val spanDays = maxOf(1L, (tEnd - tStart) / 86_400_000L)
    val showsYear = spanDays > 150 &&
        Instant.ofEpochMilli(tStart).atZone(zone).year != Instant.ofEpochMilli(tEnd).atZone(zone).year
    val xLabelFmt = LocaleFormat.monthOrDayZoned(showsYear, zone)
    fun dayMs(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val trend = computeMetricTrend(
        series.map { MetricTrendInput(at = Instant.ofEpochMilli(it.timeMs), value = it.value) },
        zone,
    )
    return WeightChartModel(
        yMin = yMin,
        yMax = yMax,
        ticks = ticks,
        tStart = tStart,
        tEnd = tEnd,
        tRange = tRange,
        singleEntry = singleEntry,
        showsYear = showsYear,
        xLabelFmt = xLabelFmt,
        points = downsampleTrend(series),
        trendPoints = downsampleTrend(trend.map { TrendPoint(dayMs(it.day), it.value) }),
        trendSegments = splitMetricSegments(trend)
            .map { segment -> downsampleTrend(segment.map { TrendPoint(dayMs(it.day), it.value) }) },
        showsDots = series.size <= 31,
        goalDisplayValue = null
    )
}

/** Compact per-site circumference trend: grid, smoothed trend line (raw
 *  fallback when the cadence is too sparse for the moving average), muted raw
 *  dots, x labels. No goal rule. Mirrors the weight chart's render phases so
 *  several plot cards don't land on the first frame. */
@Composable
internal fun MeasurementChartCanvas(
    series: List<TrendPoint>,
    immediate: Boolean = false,
    /** Display-unit formatter for the tap tag (cm → "12.5 cm"); null → bare tick format. */
    tagFormatter: ((Double) -> String)? = null,
    /** Metric name for the TalkBack inspection description (the section header text). */
    title: String,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> Color = { Color.Transparent },
) {
    val chartModel = remember(series) { buildMeasurementChartModel(series) }
    val untrackedSpans = rememberUntrackedSpans(chartModel.tStart, chartModel.tRange, untrackedDays)
    val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val rawDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val reveal = remember(chartModel, immediate) { Animatable(if (immediate) 1f else 0f) }
    LaunchedEffect(chartModel, immediate) {
        if (!immediate) reveal.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }
    val tailAlpha = ((reveal.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val pathMeasure = remember { PathMeasure() }
    val trimDst = remember { Path() }

    var inspectedPoint by remember(chartModel) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val chipBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Strong)
    val chipForeground = MaterialTheme.colorScheme.surface
    val metricTitle = title
    val inspectedNow = inspectedPoint?.let { chartModel.points.getOrNull(it) }
    val chartDescription = if (inspectedNow != null) {
        stringResource(
            R.string.a11y_chart_inspection,
            metricTitle,
            chartModel.xLabelFmt.format(Instant.ofEpochMilli(inspectedNow.timeMs)),
            tagFormatter?.invoke(inspectedNow.value) ?: formatTick(inspectedNow.value),
        )
    } else {
        metricTitle
    }
    val inspectedLabel = inspectedPoint?.let { index ->
        chartModel.points.getOrNull(index)?.let { point ->
            val value = tagFormatter?.invoke(point.value) ?: formatTick(point.value)
            "${chartModel.xLabelFmt.format(Instant.ofEpochMilli(point.timeMs))} · $value"
        }
    }
    val laneZone = remember { ZoneId.systemDefault() }
    val laneMarkers = remember(chartModel, dayTypeByDay, untrackedDays, typeColorOf) {
        buildMarkerLane(
            start = Instant.ofEpochMilli(chartModel.tStart).atZone(laneZone).toLocalDate(),
            end = Instant.ofEpochMilli(chartModel.tStart + chartModel.tRange).atZone(laneZone).toLocalDate(),
            types = dayTypeByDay,
            untracked = untrackedDays,
            typeColorOf = typeColorOf,
        )
    }
    val laneXFraction: (Int) -> Float = { i ->
        val marker = laneMarkers.getOrNull(i)
        if (marker == null || chartModel.singleEntry || chartModel.tRange <= 0L) {
            0.5f
        } else {
            ((marker.date.atStartOfDay(laneZone).toInstant().toEpochMilli() - chartModel.tStart)
                .toFloat() / chartModel.tRange).coerceIn(0f, 1f)
        }
    }
    Row(Modifier.fillMaxWidth().height(140.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .semantics { contentDescription = chartDescription }
                .pointerInput(chartModel) {
                    detectTapGestures { tap ->
                        val index = nearestTrendIndex(
                            chartModel.points, chartModel.tStart, chartModel.tRange,
                            chartModel.singleEntry, tap.x, size.width.toFloat(),
                        )
                        inspectedPoint = if (inspectedPoint == index) null else index
                    }
                }
        ) {
            val w = size.width; val h = size.height
            chartModel.ticks.forEach { tick ->
                val y = h - (((tick - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y), end = Offset(w, y),
                    strokeWidth = 1f
                )
            }
            for (i in 0..4) {
                val x = (i.toFloat() / 4f) * w
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f), end = Offset(x, h),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )
            }
            fun pointOffset(p: TrendPoint): Offset = Offset(
                if (chartModel.singleEntry) w / 2f
                else ((p.timeMs - chartModel.tStart).toDouble() / chartModel.tRange * w).toFloat(),
                h - (((p.value - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
            )
            val offsets = chartModel.points.map(::pointOffset)
            // Untracked bands sit behind every plot layer (#106).
            drawUntrackedBands(untrackedSpans, chartModel.tStart, chartModel.tRange, bandColor)
            clipRect {
                // Smoothed trend is the line; too-few-in-window cadences fall
                // back to the raw readings path so the site still reads.
                val segments = chartModel.trendSegments.ifEmpty {
                    if (chartModel.points.size >= 2) listOf(chartModel.points) else emptyList()
                }
                for (segment in segments) {
                    val trendOffsets = segment.map(::pointOffset)
                    drawPath(
                        trendFillPath(trendOffsets, h),
                        AppColors.Calorie.copy(alpha = 0.08f * reveal.value),
                    )
                    if (trendOffsets.size == 1) {
                        drawCircle(AppColors.Calorie, radius = 5.5f, center = trendOffsets.first())
                    } else {
                        drawPath(
                            trimmedStraightPath(trendOffsets, reveal.value, pathMeasure, trimDst),
                            AppColors.Calorie,
                            style = Stroke(width = 5f),
                        )
                    }
                }
                if (chartModel.showsDots && tailAlpha > 0f) {
                    offsets.forEach {
                        drawCircle(rawDotColor.copy(alpha = 0.75f * tailAlpha), radius = 3.dp.toPx(), center = it)
                    }
                }
            }
            drawInspectedDot(offsets, inspectedPoint)
            val inspectedOffset = inspectedPoint?.takeIf { it < offsets.size }?.let { offsets[it] }
            if (inspectedOffset != null && inspectedLabel != null) {
                drawInspectionTag(inspectedLabel, textMeasurer, inspectedOffset, w, chipBackground, chipForeground)
            }
        }
        Column(
            Modifier.width(36.dp).fillMaxSize().padding(start = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            chartModel.ticks.reversed().forEach { tick ->
                Text(
                    formatTick(tick),
                    fontSize = 11.sp,
                    color = secondaryColor
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        MarkerLane(
            markers = laneMarkers,
            xFraction = laneXFraction,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(36.dp))
    }
    Box(Modifier.alpha(tailAlpha)) {
        TrendXAxisLabels(
            chartModel.tStart,
            chartModel.tEnd,
            chartModel.showsYear,
            chartModel.singleEntry,
            chartModel.xLabelFmt,
            secondaryColor,
            endPadding = 36.dp
        )
    }
}

/**
 * Calendar-day calorie bars against a goal rule line (#60 phase 3): [goal] is
 * the range average (MACRO-CYCLE-D); each bar colors over/under against its
 * own day's target from [dailyGoals] (journal-first; missing entries — e.g.
 * bucketed week slots — fall back to [goal]). Untracked days draw a baseline
 * dash; days with nothing logged leave a real gap on the axis.
 */
@Composable
internal fun CalorieBarChart(
    slots: List<CalorieSlot>,
    goal: Int,
    dailyGoals: Map<LocalDate, Int> = emptyMap(),
    immediate: Boolean = false,
    /** Metric overlay (weight/body-fat trend), null = bars only (plan §4). */
    overlay: CalorieOverlayData? = null,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> Color = { Color.Transparent },
) {
    val maxValue = slots.mapNotNull { it.kcal }.maxOrNull()?.coerceAtLeast(goal)?.toDouble()
        ?: goal.toDouble()
    val barColor = AppColors.Calorie
    // Over-goal bars are flat error (no gradient — restyle D1/D10).
    val overColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    val dashColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val goalColor = AppColors.Calorie.copy(alpha = 0.4f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val density = LocalDensity.current
    val ticks = niceAxisTicks(0.0, maxValue, count = 5)
    val yTop = ticks.last().coerceAtLeast(maxValue)
    val xLabelFmt = LocaleFormat.shortDate()
    // Same 450ms draw-in as the trend charts: bars grow from the baseline,
    // untracked dashes and x labels fade at the end; `immediate` (screenshot
    // path) starts fully drawn for deterministic goldens.
    val reveal = remember(slots, immediate) { Animatable(if (immediate) 1f else 0f) }
    LaunchedEffect(slots, immediate) {
        if (!immediate) reveal.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }
    val tailAlpha = ((reveal.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val pathMeasure = remember { PathMeasure() }
    val trimDst = remember { Path() }

    var inspectedBar by remember(slots) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val chipBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Strong)
    val chipForeground = MaterialTheme.colorScheme.surface
    val energyUnit = LocalEnergyUnit.current
    val inspectedTitle = stringResource(R.string.progress_calories_section)
    val inspectedNow = inspectedBar?.let { slots.getOrNull(it) }?.takeIf { it.kcal != null }
    val chartDescription = if (inspectedNow != null) {
        stringResource(
            R.string.a11y_chart_inspection,
            inspectedTitle,
            xLabelFmt.format(inspectedNow.day),
            "${LocaleFormat.integer(EnergyFormat.quantity(inspectedNow.kcal ?: 0, energyUnit))} ${energyUnitLabel()}",
        )
    } else {
        inspectedTitle
    }
    val inspectedLabel = inspectedBar?.let { index ->
        slots.getOrNull(index)?.takeIf { it.kcal != null }?.let { slot ->
            val kcalText = "${xLabelFmt.format(slot.day)} · ${LocaleFormat.integer(EnergyFormat.quantity(slot.kcal ?: 0, energyUnit))} ${energyUnitLabel()}"
            // Combo mode: append the overlaid metric's real value for that day
            // (week-start buckets fall back to the week's average).
            val overlayText = overlay?.valuesByDay
                ?.let { values -> overlayValueForSlot(values, slot.day) }
                ?.let { " · ${formatTick(it)}${overlay.unitSuffix}" } ?: ""
            kcalText + overlayText
        }
    }
    // One marker per plotted slot (bucket day after roll-up), so dashes and
    // dots align with bars at every range.
    val dayMarkers = slots.map { slot ->
        DayMarker(slot.day, dayTypeByDay[slot.day.toString()]?.let(typeColorOf), slot.untracked)
    }
    val goalStepColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    // Overlay draw domain: the plotted slot span in epoch-ms plus the series'
    // own min–max scale (plan §4 — no dual axis; legend + tap tag carry real
    // values). Null when off, no data, or no slots.
    val overlayDraw = overlay
        ?.takeIf { it.trend.isNotEmpty() || it.dots.isNotEmpty() }
        ?.takeIf { slots.isNotEmpty() }
        ?.let { data ->
            val zone = ZoneId.systemDefault()
            val startMs = slots.first().day.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = slots.last().day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(data, startMs, maxOf(startMs + 1, endMs))
        }
    Column {
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                val barAreaWidthPx = with(density) { maxWidth.toPx() }
                val n = slots.size
                val geometry = calorieBarGeometry(barAreaWidthPx, n, density)
                val gap = geometry.gap
                val barWidth = geometry.barWidth
                val startX = geometry.startX

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = chartDescription }
                        .pointerInput(slots, startX, barWidth, gap) {
                            detectTapGestures { tap ->
                                if (n == 0) return@detectTapGestures
                                val raw = ((tap.x - startX) / (barWidth + gap)).toInt()
                                // Taps past the last bar's right edge (label gutter,
                                // y-axis spacer) do nothing instead of selecting it;
                                // only kcal-bearing slots are selectable.
                                if (raw < 0 || raw >= n || slots[raw].kcal == null) return@detectTapGestures
                                inspectedBar = if (inspectedBar == raw) null else raw
                            }
                        }
                ) {
                    val pxW = size.width; val pxH = size.height
                    ticks.forEach { tick ->
                        val y = pxH - ((tick / yTop).toFloat() * pxH)
                        drawLine(gridColor, Offset(0f, y), Offset(pxW, y), strokeWidth = 1f)
                    }
                    for (i in 0 until n) {
                        val cx = startX + i * (barWidth + gap) + barWidth / 2f
                        drawLine(
                            color = gridColor,
                            start = Offset(cx, 0f), end = Offset(cx, pxH),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                        )
                    }
                    val goalY = pxH - ((goal / yTop).toFloat() * pxH)
                    drawLine(
                        color = goalColor,
                        start = Offset(0f, goalY), end = Offset(pxW, goalY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                    slots.forEachIndexed { i, slot ->
                        val x = startX + i * (barWidth + gap)
                        val kcal = slot.kcal
                        when {
                            kcal != null -> {
                                val barH = ((kcal / yTop).toFloat() * pxH) * reveal.value
                                if (barH > 0f) {
                                    val y = pxH - barH
                                    val overGoal = kcal > (dailyGoals[slot.day] ?: goal)
                                    drawRoundRect(
                                        color = if (overGoal) overColor else barColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barH),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                }
                            }
                            // Untracked day: baseline dash, never a zero bar.
                            slot.untracked && tailAlpha > 0f -> {
                                val cx = x + barWidth / 2f
                                drawLine(
                                    color = dashColor.copy(alpha = 0.5f * tailAlpha),
                                    start = Offset(cx - 3.dp.toPx(), pxH - 2.dp.toPx()),
                                    end = Offset(cx + 3.dp.toPx(), pxH - 2.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        }
                    }
                    // Metric combo (plan §4): per-day goal step-line over the
                    // slots, then the normalized body trend on the same span.
                    overlayDraw?.let { (data, startMs, endMs) ->
                        val scale = OverlayScale.of(
                            values = data.trend.map { it.value } + data.dots.map { it.value },
                            goal = null,
                        )
                        fun oX(timeMs: Long): Float = overlaySlotCenterX(
                            timeMs, startMs, endMs, slots.size, startX, barWidth + gap, barWidth,
                        )
                        fun oY(value: Double): Float = pxH - (scale.fraction(value) * pxH)
                        slots.forEachIndexed { i, slot ->
                            val target = dailyGoals[slot.day] ?: goal
                            val y = pxH - ((target / yTop).toFloat() * pxH)
                            val x0 = startX + i * (barWidth + gap)
                            drawLine(
                                color = goalStepColor,
                                start = Offset(x0, y),
                                end = Offset(x0 + barWidth, y),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                            )
                        }
                        clipRect {
                            val offsets = data.trend.map { Offset(oX(it.timeMs), oY(it.value)) }
                            if (offsets.isNotEmpty()) {
                                if (offsets.size == 1) {
                                    drawCircle(data.color, radius = 3.dp.toPx(), center = offsets.first())
                                } else {
                                    drawPath(
                                        trimmedStraightPath(offsets, reveal.value, pathMeasure, trimDst),
                                        data.color,
                                        style = Stroke(width = 3.dp.toPx()),
                                    )
                                }
                            }
                            if (data.dots.size <= 31 && tailAlpha > 0f) {
                                data.dots.forEach { p ->
                                    drawCircle(
                                        data.color.copy(alpha = 0.55f * tailAlpha),
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(oX(p.timeMs), oY(p.value)),
                                    )
                                }
                            }
                        }
                    }
                    inspectedBar?.takeIf { it < n }?.let { index ->
                        val slot = slots.getOrNull(index) ?: return@let
                        val kcal = slot.kcal ?: return@let
                        val cx = startX + index * (barWidth + gap) + barWidth / 2f
                        val topY = pxH - ((kcal / yTop).toFloat() * pxH)
                        drawCircle(AppColors.Calorie.copy(alpha = 0.25f), radius = 16f, center = Offset(cx, topY))
                        if (inspectedLabel != null) {
                            drawInspectionTag(inspectedLabel, textMeasurer, Offset(cx, topY), pxW, chipBackground, chipForeground)
                        }
                    }
                }
            }
            Column(
                Modifier.width(44.dp).fillMaxSize().padding(start = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ticks.reversed().forEach { tick ->
                    Text(formatTick(EnergyFormat.quantity(tick.toInt(), LocalEnergyUnit.current).toDouble()), fontSize = 11.sp, color = secondaryColor)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val areaWidthPx = with(density) { maxWidth.toPx() }
                val geometry = calorieBarGeometry(areaWidthPx, slots.size, density)
                MarkerLane(
                    markers = dayMarkers,
                    xFraction = { i -> geometry.slotCenter(i) / areaWidthPx },
                )
            }
            Spacer(Modifier.width(44.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val areaWidthDp = maxWidth
                val areaWidthPx = with(density) { areaWidthDp.toPx() }
                val n = slots.size
                val geometry = calorieBarGeometry(areaWidthPx, n, density)
                val gap = geometry.gap
                val barWidth = geometry.barWidth
                val startX = geometry.startX
                val slotPx = barWidth + gap
                val slotDp = with(density) { slotPx.toDp() }
                val minLabelDp = 40.dp
                val slotStep = if (slotDp >= minLabelDp) 1
                    else Math.ceil((minLabelDp.value / slotDp.value).toDouble()).toInt().coerceAtLeast(1)
                val pickedIndices = calorieLabelIndices(n, slotStep)
                // Boxes tile the slot pitch exactly: adjacent labels meet at
                // their box edges without overlapping, and clamping the
                // first/last box inward keeps every date fully visible.
                val labelBoxWidth = slotDp * slotStep
                pickedIndices.forEach { i ->
                    val cxPx = startX + i * (barWidth + gap) + barWidth / 2f
                    val cxDp = with(density) { cxPx.toDp() }
                    // Keep the box inside the chart area — the first/last slot
                    // centers sit within half a box of the edges.
                    val offsetX = (cxDp - labelBoxWidth / 2).coerceIn(
                        0.dp,
                        (areaWidthDp - labelBoxWidth).coerceAtLeast(0.dp),
                    )
                    Box(
                        Modifier
                            .width(labelBoxWidth)
                            .offset(x = offsetX)
                            .alpha(tailAlpha),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            xLabelFmt.format(slots[i].day),
                            fontSize = 11.sp,
                            color = secondaryColor,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
internal fun DeferredChart(immediate: Boolean = false, content: @Composable () -> Unit) {
    if (immediate) {
        content()
    } else {
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            ready = true
        }
        if (ready) content() else ChartPlaceholder()
    }
}

@Composable
internal fun ChartPlaceholder(height: Dp = 180.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.progress_chart_loading),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
        )
    }
}

/** Tap-to-read for trend charts (audit M10): index of the plotted point whose
 *  x is nearest the tap — the Android twin of the PWA's per-point hit circles
 *  (web/app/src/lib/charts.js). */
internal fun nearestTrendIndex(
    points: List<TrendPoint>,
    tStart: Long,
    tRange: Long,
    singleEntry: Boolean,
    x: Float,
    width: Float,
): Int? {
    if (points.isEmpty()) return null
    if (singleEntry || points.size == 1) return 0
    var best = 0
    var bestDx = Float.MAX_VALUE
    for ((index, point) in points.withIndex()) {
        val px = ((point.timeMs - tStart).toDouble() / tRange * width).toFloat()
        val dx = abs(px - x)
        if (dx < bestDx) {
            bestDx = dx
            best = index
        }
    }
    return best
}

private fun DrawScope.drawInspectedDot(offsets: List<Offset>, index: Int?) {
    val center = index?.takeIf { it < offsets.size }?.let { offsets[it] } ?: return
    drawCircle(AppColors.Calorie.copy(alpha = 0.25f), radius = 16f, center = center)
    drawCircle(AppColors.Calorie, radius = 7f, center = center)
}

/** Value tag pinned above the inspected point, clamped to the canvas. */
private fun DrawScope.drawInspectionTag(
    label: String,
    textMeasurer: TextMeasurer,
    point: Offset,
    canvasWidth: Float,
    chipBackground: Color,
    chipForeground: Color,
) {
    if (label.isEmpty()) return
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val textLayout = textMeasurer.measure(
        label,
        TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = chipForeground),
    )
    val tagWidth = textLayout.size.width + padH * 2
    val tagHeight = textLayout.size.height + padV * 2
    val left = (point.x - tagWidth / 2f).coerceIn(0f, (canvasWidth - tagWidth).coerceAtLeast(0f))
    val top = (point.y - tagHeight - 14f).coerceAtLeast(0f)
    drawRoundRect(
        color = chipBackground,
        topLeft = Offset(left, top),
        size = Size(tagWidth, tagHeight),
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    drawText(textLayout, topLeft = Offset(left + padH, top + padV))
}
