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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chompass.ui.components.energyUnitLabel
import androidx.compose.ui.unit.sp
import app.chompass.models.BodyFatEntry
import app.chompass.models.WeightEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.success
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

internal data class BodyFatChartModel(
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
    val showsDots: Boolean,
    val goalPercent: Double?
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

/** Format a body-fat tick value for the Y-axis label (e.g. 17.5 → "17.5%"
 *  when the tick has a fractional part, otherwise "18%" — keeps short ticks
 *  short and falls back to one decimal when the chart is zoomed in). */
internal fun formatPercentTick(value: Double): String {
    val rounded = (value * 10).toInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()}%"
    else "${LocaleFormat.decimal(rounded, 1)}%"
}

/** Pick at most [maxLabels] evenly-spaced bar indices for x-axis labelling. */
internal fun pickXLabelIndices(n: Int, maxLabels: Int = 7): List<Int> {
    if (n <= 0) return emptyList()
    if (n <= maxLabels) return (0 until n).toList()
    val step = (n - 1).toFloat() / (maxLabels - 1)
    return (0 until maxLabels).map { i -> (i * step).toInt().coerceIn(0, n - 1) }.distinct()
}

internal fun buildWeightChartModel(entries: List<WeightEntry>, goalKg: Double?, useMetric: Boolean): WeightChartModel {
    val displayKg = { kg: Double -> if (useMetric) kg else UnitFormat.kgToLbs(kg) }
    val zone = ZoneId.systemDefault()
    val trendKg = computeWeightTrend(
        weighIns = entries.map { WeightTrendInput(at = it.date, weightKg = it.weightKg) },
        zone = zone,
    )
    val trendDisplay = trendKg.map {
        TrendPoint(
            timeMs = it.day.atStartOfDay(zone).toInstant().toEpochMilli(),
            value = displayKg(it.valueKg),
        )
    }
    val displayWeights = entries.map { displayKg(it.weightKg) } +
        trendDisplay.map { it.value } +
        listOfNotNull(goalKg?.let(displayKg))
    val minW = displayWeights.min()
    val maxW = displayWeights.max()
    val pad = maxOf((maxW - minW) * 0.15, 2.0)
    val yMin = minW - pad
    val yMax = maxW + pad
    val tStart = entries.first().date.toEpochMilli()
    val tEnd = entries.last().date.toEpochMilli()
    val singleEntry = entries.size == 1
    val tRange = maxOf(1L, tEnd - tStart)
    val ticks = niceAxisTicks(yMin, yMax, count = 5)
    val spanDays = maxOf(1L, (tEnd - tStart) / 86_400_000L)
    val showsYear = spanDays > 150 &&
        Instant.ofEpochMilli(tStart).atZone(zone).year != Instant.ofEpochMilli(tEnd).atZone(zone).year
    val xLabelFmt = LocaleFormat.monthOrDayZoned(showsYear, zone)
    // Analytical trend first, then visual downsampling of raw weigh-ins.
    val points = downsampleTrend(entries.map { TrendPoint(it.date.toEpochMilli(), displayKg(it.weightKg)) })
    val trendForDraw = downsampleTrend(trendDisplay)
    val trendSegments = splitTrendSegments(trendKg).map { segment ->
        downsampleTrend(
            segment.map {
                TrendPoint(
                    timeMs = it.day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    value = displayKg(it.valueKg),
                )
            }
        )
    }.filter { it.size >= 2 }
    val showsDots = points.size <= 31
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
        points = points,
        trendPoints = trendForDraw,
        trendSegments = trendSegments,
        showsDots = showsDots,
        goalDisplayValue = goalKg?.let(displayKg)
    )
}

internal fun buildBodyFatChartModel(entries: List<BodyFatEntry>, goalFraction: Double?): BodyFatChartModel {    val percents = entries.map { it.bodyFatFraction * 100 } + listOfNotNull(goalFraction?.let { it * 100 })
    val minP = percents.min()
    val maxP = percents.max()
    val pad = maxOf((maxP - minP) * 0.15, 1.0)
    val yMin = (minP - pad).coerceAtLeast(0.0)
    val yMax = maxP + pad
    val tStart = entries.first().date.toEpochMilli()
    val tEnd = entries.last().date.toEpochMilli()
    val singleEntry = entries.size == 1
    val tRange = maxOf(1L, tEnd - tStart)
    val ticks = niceAxisTicks(yMin, yMax, count = 5)
    val zone = ZoneId.systemDefault()
    val spanDays = maxOf(1L, (tEnd - tStart) / 86_400_000L)
    val showsYear = spanDays > 150 &&
        Instant.ofEpochMilli(tStart).atZone(zone).year != Instant.ofEpochMilli(tEnd).atZone(zone).year
    val xLabelFmt = LocaleFormat.monthOrDayZoned(showsYear, zone)
    val points = downsampleTrend(entries.map { TrendPoint(it.date.toEpochMilli(), it.bodyFatFraction * 100) })
    val showsDots = points.size <= 31
    return BodyFatChartModel(
        yMin = yMin,
        yMax = yMax,
        ticks = ticks,
        tStart = tStart,
        tEnd = tEnd,
        tRange = tRange,
        singleEntry = singleEntry,
        showsYear = showsYear,
        xLabelFmt = xLabelFmt,
        points = points,
        showsDots = showsDots,
        goalPercent = goalFraction?.times(100)
    )
}

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

@Composable
internal fun WeightChartCanvas(
    entries: List<WeightEntry>,
    goalKg: Double?,
    useMetric: Boolean,
    immediate: Boolean = false,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> Color = { Color.Transparent },
) {
    val chartModel = remember(entries, goalKg, useMetric) {
        buildWeightChartModel(entries = entries, goalKg = goalKg, useMetric = useMetric)
    }
    val untrackedSpans = rememberUntrackedSpans(chartModel.tStart, chartModel.tRange, untrackedDays)
    val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val rawDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val goalLineColor = MaterialTheme.colorScheme.success.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    // 450ms draw-in: grid/goal/bands are instant, the trend sweeps in
    // left-to-right, dots/labels/latest chip fade at the end. Keyed on the
    // model so switching ranges re-runs the reveal; `immediate` (screenshot
    // path) starts fully drawn for deterministic goldens.
    val reveal = remember(chartModel, immediate) { Animatable(if (immediate) 1f else 0f) }
    LaunchedEffect(chartModel, immediate) {
        if (!immediate) reveal.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }
    val tailAlpha = ((reveal.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val pathMeasure = remember { PathMeasure() }
    val trimDst = remember { Path() }
    val tagPaint = remember { Paint() }

    var inspectedPoint by remember(chartModel) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val chipBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Strong)
    val chipForeground = MaterialTheme.colorScheme.surface
    val weightUnit = stringResource(if (useMetric) R.string.unit_kg else R.string.unit_lbs)
    val metricTitle = stringResource(R.string.progress_weight_section)
    val inspectedNow = inspectedPoint?.let { chartModel.points.getOrNull(it) }
    val chartDescription = if (inspectedNow != null) {
        stringResource(
            R.string.a11y_chart_inspection,
            metricTitle,
            chartModel.xLabelFmt.format(Instant.ofEpochMilli(inspectedNow.timeMs)),
            "${formatTick(inspectedNow.value)} $weightUnit",
        )
    } else {
        metricTitle
    }
    val inspectedLabel = inspectedPoint?.let { index ->
        chartModel.points.getOrNull(index)?.let { point ->
            "${chartModel.xLabelFmt.format(Instant.ofEpochMilli(point.timeMs))} · ${formatTick(point.value)} $weightUnit"
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
    Row(Modifier.fillMaxWidth().height(180.dp)) {
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
            chartModel.goalDisplayValue?.let { gv ->
                val y = h - (((gv - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, y), end = Offset(w, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
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
                // Raw weigh-ins stay honest as muted dots; the solid line is the
                // labeled 7-day trend (dashes read as uncertainty, not style).
                for (segment in chartModel.trendSegments) {
                    val trendOffsets = segment.map(::pointOffset)
                    drawPath(
                        trendFillPath(trendOffsets, h),
                        AppColors.Calorie.copy(alpha = 0.08f * reveal.value),
                    )
                    if (trendOffsets.size == 1) {
                        drawCircle(AppColors.Calorie, radius = 3.dp.toPx(), center = trendOffsets.first())
                    } else {
                        drawPath(
                            trimmedStraightPath(trendOffsets, reveal.value, pathMeasure, trimDst),
                            AppColors.Calorie,
                            style = Stroke(width = 4f),
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
            // Latest-value chip: the trend's end is the headline reading.
            val trendTail = chartModel.trendSegments.lastOrNull()?.lastOrNull()
                ?: chartModel.points.lastOrNull()?.takeIf { chartModel.trendSegments.isEmpty() }
            if (inspectedPoint == null && trendTail != null && tailAlpha > 0f) {
                drawIntoCanvas { canvas ->
                    tagPaint.alpha = tailAlpha
                    canvas.saveLayer(Rect(0f, 0f, w, h), tagPaint)
                    drawInspectionTag(
                        "${formatTick(trendTail.value)} $weightUnit",
                        textMeasurer,
                        pointOffset(trendTail),
                        w,
                        chipBackground,
                        chipForeground,
                    )
                    canvas.restore()
                }
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

@Composable
internal fun BodyFatChartCanvas(
    entries: List<BodyFatEntry>,
    goalFraction: Double?,
    immediate: Boolean = false,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> Color = { Color.Transparent },
) {
    val chartModel = remember(entries, goalFraction) {
        buildBodyFatChartModel(entries = entries, goalFraction = goalFraction)
    }
    val untrackedSpans = rememberUntrackedSpans(chartModel.tStart, chartModel.tRange, untrackedDays)
    val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val rawDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val goalLineColor = MaterialTheme.colorScheme.success.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val reveal = remember(chartModel, immediate) { Animatable(if (immediate) 1f else 0f) }
    LaunchedEffect(chartModel, immediate) {
        if (!immediate) reveal.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }
    val tailAlpha = ((reveal.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val pathMeasure = remember { PathMeasure() }
    val trimDst = remember { Path() }
    val tagPaint = remember { Paint() }

    var inspectedPoint by remember(chartModel) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val chipBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Strong)
    val chipForeground = MaterialTheme.colorScheme.surface
    val metricTitle = stringResource(R.string.progress_metric_body_fat)
    val inspectedNow = inspectedPoint?.let { chartModel.points.getOrNull(it) }
    val chartDescription = if (inspectedNow != null) {
        stringResource(
            R.string.a11y_chart_inspection,
            metricTitle,
            chartModel.xLabelFmt.format(Instant.ofEpochMilli(inspectedNow.timeMs)),
            UnitFormat.percent(inspectedNow.value),
        )
    } else {
        metricTitle
    }
    val inspectedLabel = inspectedPoint?.let { index ->
        chartModel.points.getOrNull(index)?.let { point ->
            "${chartModel.xLabelFmt.format(Instant.ofEpochMilli(point.timeMs))} · ${UnitFormat.percent(point.value)}"
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
    Row(Modifier.fillMaxWidth().height(180.dp)) {
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
            chartModel.goalPercent?.let { gPct ->
                val y = h - (((gPct - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, y), end = Offset(w, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
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
                // Readings path straight, not smoothed — the curve implied a
                // calculated trend; the muted dots carry the raw cadence.
                if (chartModel.points.isNotEmpty()) {
                    drawPath(
                        trendFillPath(offsets, h),
                        AppColors.Calorie.copy(alpha = 0.08f * reveal.value),
                    )
                    if (offsets.size == 1) {
                        drawCircle(AppColors.Calorie, radius = 4f, center = offsets.first())
                    } else {
                        drawPath(
                            trimmedStraightPath(offsets, reveal.value, pathMeasure, trimDst),
                            AppColors.Calorie,
                            style = Stroke(width = 4f),
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
            // Latest-value chip at the newest reading.
            if (inspectedPoint == null && chartModel.points.isNotEmpty() && tailAlpha > 0f) {
                val tail = chartModel.points.last()
                drawIntoCanvas { canvas ->
                    tagPaint.alpha = tailAlpha
                    canvas.saveLayer(Rect(0f, 0f, w, h), tagPaint)
                    drawInspectionTag(
                        formatPercentTick(tail.value),
                        textMeasurer,
                        pointOffset(tail),
                        w,
                        chipBackground,
                        chipForeground,
                    )
                    canvas.restore()
                }
            }
        }
        Column(
            Modifier.width(40.dp).fillMaxSize().padding(start = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            chartModel.ticks.reversed().forEach { tick ->
                Text(
                    formatPercentTick(tick),
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
        Spacer(Modifier.width(40.dp))
    }
    Box(Modifier.alpha(tailAlpha)) {
        TrendXAxisLabels(
            chartModel.tStart,
            chartModel.tEnd,
            chartModel.showsYear,
            chartModel.singleEntry,
            chartModel.xLabelFmt,
            secondaryColor,
            endPadding = 40.dp
        )
    }
}

/** Y-axis / time-range model for a body-measurement trend plot. Reuses the
 *  weight chart model shape — trend overlay and goal rule fields stay empty.
 *  Series values are plain cm (or inches once converted by the caller). */
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
        trendPoints = emptyList(),
        trendSegments = emptyList(),
        showsDots = series.size <= 31,
        goalDisplayValue = null
    )
}

/** Compact per-site circumference trend: grid, straight line, dots, x labels.
 *  No goal rule and no dashed analytical overlay — measurement cadence is far
 *  too sparse for either to mean anything. Mirrors the weight chart's render
 *  phases so several plot cards don't land on the first frame. */
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
                if (offsets.isNotEmpty()) {
                    drawPath(
                        trendFillPath(offsets, h),
                        AppColors.Calorie.copy(alpha = 0.08f * reveal.value),
                    )
                    if (offsets.size == 1) {
                        drawCircle(AppColors.Calorie, radius = 5.5f, center = offsets.first())
                    } else {
                        drawPath(
                            trimmedStraightPath(offsets, reveal.value, pathMeasure, trimDst),
                            AppColors.Calorie,
                            style = Stroke(width = 5f),
                        )
                    }
                }
                if (chartModel.showsDots && tailAlpha > 0f) {
                    offsets.forEach { drawCircle(AppColors.Calorie, radius = 5.5f, center = it) }
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
            "${xLabelFmt.format(slot.day)} · ${LocaleFormat.integer(EnergyFormat.quantity(slot.kcal ?: 0, energyUnit))} ${energyUnitLabel()}"
        }
    }
    // One marker per plotted slot (bucket day after roll-up), so dashes and
    // dots align with bars at every range.
    val dayMarkers = slots.map { slot ->
        DayMarker(slot.day, dayTypeByDay[slot.day.toString()]?.let(typeColorOf), slot.untracked)
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
