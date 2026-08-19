package app.chompass.ui.progress

import app.chompass.models.LocaleFormat
import app.chompass.R

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.models.BodyFatEntry
import app.chompass.models.WeightEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.success
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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

/**
 * Cap calorie-bar draw calls. 1W/1M stay daily. Longer ranges roll up to ISO
 * weeks (iOS weekly bars); if that still exceeds [maxBars] (All over years),
 * fold weeks into equal-width buckets. Badge totals must use the unbucketed
 * series — this is canvas-only.
 */
internal fun downsampleCalorieBars(
    dailyCalories: List<Pair<LocalDate, Int>>,
    maxBars: Int = 90,
): List<Pair<LocalDate, Int>> {
    if (dailyCalories.size <= maxBars) return dailyCalories
    val weekly = dailyCalories
        .groupBy { it.first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .map { (weekStart, days) -> weekStart to days.sumOf { it.second } }
    if (weekly.size <= maxBars) return weekly
    val first = weekly.first().first
    val last = weekly.last().first
    val spanDays = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1)
    val bucketDays = ((spanDays + maxBars - 1) / maxBars).coerceAtLeast(7)
    return weekly
        .groupBy { ChronoUnit.DAYS.between(first, it.first) / bucketDays }
        .toSortedMap()
        .values
        .map { bucket -> bucket.first().first to bucket.sumOf { it.second } }
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

/** Catmull-Rom smoothed path through [points] — same curve the iOS charts
 *  get from interpolationMethod(.catmullRom). */
internal fun smoothTrendPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    // Lower tension than the default Catmull-Rom handles to avoid exaggerated
    // bends when adjacent dates have sharp value changes.
    val smoothing = 0.42f
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val p0 = points[maxOf(i - 2, 0)]
        val p1 = points[i - 1]
        val p2 = points[i]
        val p3 = points[minOf(i + 1, points.size - 1)]
        val handleScale = smoothing / 6f
        path.cubicTo(
            p1.x + (p2.x - p0.x) * handleScale, p1.y + (p2.y - p0.y) * handleScale,
            p2.x - (p3.x - p1.x) * handleScale, p2.y - (p3.y - p1.y) * handleScale,
            p2.x, p2.y
        )
    }
    return path
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
) {
    val chartModel = remember(entries, goalKg, useMetric) {
        buildWeightChartModel(entries = entries, goalKg = goalKg, useMetric = useMetric)
    }
    val goalLineColor = MaterialTheme.colorScheme.success.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    var chartRenderPhase by remember(entries, goalKg, useMetric, immediate) {
        mutableStateOf(if (immediate) 2 else 0)
    }
    if (!immediate) {
        LaunchedEffect(entries, goalKg, useMetric) {
            chartRenderPhase = 0
            withFrameNanos { }
            chartRenderPhase = 1
            withFrameNanos { }
            chartRenderPhase = 2
        }
    }

    Row(Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(Modifier.weight(1f).fillMaxSize()) {
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
            val offsets = chartModel.points.map { p ->
                Offset(
                    if (chartModel.singleEntry) w / 2f
                    else ((p.timeMs - chartModel.tStart).toDouble() / chartModel.tRange * w).toFloat(),
                    h - (((p.value - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
                )
            }
            fun pointOffset(p: TrendPoint): Offset = Offset(
                if (chartModel.singleEntry) w / 2f
                else ((p.timeMs - chartModel.tStart).toDouble() / chartModel.tRange * w).toFloat(),
                h - (((p.value - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
            )
            clipRect {
                // Raw weigh-ins: straight path. Catmull–Rom was cosmetic only and
                // looked like a calculated trend. Trend overlay uses a dashed stroke.
                if (chartRenderPhase >= 1) {
                    for (segment in chartModel.trendSegments) {
                        val trendOffsets = segment.map(::pointOffset)
                        drawPath(
                            straightTrendPath(trendOffsets),
                            AppColors.Protein,
                            style = Stroke(
                                width = 4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                            ),
                        )
                    }
                }
                drawPath(straightTrendPath(offsets), AppColors.Calorie, style = Stroke(width = 5f))
                if (chartRenderPhase >= 2 && chartModel.showsDots) {
                    offsets.forEach { drawCircle(AppColors.Calorie, radius = 5.5f, center = it) }
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
    if (chartRenderPhase >= 1) {
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
) {
    val chartModel = remember(entries, goalFraction) {
        buildBodyFatChartModel(entries = entries, goalFraction = goalFraction)
    }
    val goalLineColor = MaterialTheme.colorScheme.success.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    var chartRenderPhase by remember(entries, goalFraction, immediate) {
        mutableStateOf(if (immediate) 2 else 0)
    }
    if (!immediate) {
        LaunchedEffect(entries, goalFraction) {
            chartRenderPhase = 0
            withFrameNanos { }
            chartRenderPhase = 1
            withFrameNanos { }
            chartRenderPhase = 2
        }
    }

    Row(Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(Modifier.weight(1f).fillMaxSize()) {
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
            val offsets = chartModel.points.map { p ->
                Offset(
                    if (chartModel.singleEntry) w / 2f
                    else ((p.timeMs - chartModel.tStart).toDouble() / chartModel.tRange * w).toFloat(),
                    h - (((p.value - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
                )
            }
            clipRect {
                val trendPath = if (chartRenderPhase >= 1) smoothTrendPath(offsets) else straightTrendPath(offsets)
                drawPath(trendPath, AppColors.Calorie, style = Stroke(width = 5f))
                if (chartRenderPhase >= 2 && chartModel.showsDots) {
                    offsets.forEach { drawCircle(AppColors.Calorie, radius = 5.5f, center = it) }
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
    if (chartRenderPhase >= 1) {
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
) {
    val chartModel = remember(series) { buildMeasurementChartModel(series) }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    var chartRenderPhase by remember(series, immediate) {
        mutableStateOf(if (immediate) 2 else 0)
    }
    if (!immediate) {
        LaunchedEffect(series) {
            chartRenderPhase = 0
            withFrameNanos { }
            chartRenderPhase = 1
            withFrameNanos { }
            chartRenderPhase = 2
        }
    }

    Row(Modifier.fillMaxWidth().height(140.dp)) {
        Canvas(Modifier.weight(1f).fillMaxSize()) {
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
            val offsets = chartModel.points.map { p ->
                Offset(
                    if (chartModel.singleEntry) w / 2f
                    else ((p.timeMs - chartModel.tStart).toDouble() / chartModel.tRange * w).toFloat(),
                    h - (((p.value - chartModel.yMin) / (chartModel.yMax - chartModel.yMin)).toFloat() * h)
                )
            }
            clipRect {
                drawPath(straightTrendPath(offsets), AppColors.Calorie, style = Stroke(width = 5f))
                if (chartRenderPhase >= 2 && chartModel.showsDots) {
                    offsets.forEach { drawCircle(AppColors.Calorie, radius = 5.5f, center = it) }
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
    if (chartRenderPhase >= 1) {
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
internal fun CalorieBarChart(dailyCalories: List<Pair<LocalDate, Int>>, goal: Int) {
    val maxValue = dailyCalories.maxOf { it.second }.coerceAtLeast(goal).toDouble()
    val gradientStart = AppColors.CalorieStart
    val gradientEnd = AppColors.CalorieEnd
    val overColor = MaterialTheme.colorScheme.error
    val overColorSoft = overColor.copy(alpha = 0.75f)
    val goalColor = AppColors.Calorie.copy(alpha = 0.4f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val density = LocalDensity.current
    val ticks = niceAxisTicks(0.0, maxValue, count = 5)
    val yTop = ticks.last().coerceAtLeast(maxValue)
    val xLabelFmt = LocaleFormat.shortDate()

    Column {
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                val barAreaWidthPx = with(density) { maxWidth.toPx() }
                val n = dailyCalories.size
                val gap = 4f
                val maxBarPx = with(density) { 60.dp.toPx() }
                val rawWidth = (barAreaWidthPx - gap * (n - 1)) / n
                val barWidth = rawWidth.coerceIn(2f, maxBarPx)
                val totalGroupW = barWidth * n + gap * (n - 1)
                val startX = ((barAreaWidthPx - totalGroupW) / 2f).coerceAtLeast(0f)

                Canvas(Modifier.fillMaxSize()) {
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
                    dailyCalories.forEachIndexed { i, (_, cals) ->
                        val barH = ((cals / yTop).toFloat() * pxH)
                        val x = startX + i * (barWidth + gap)
                        val y = pxH - barH
                        val brush = if (cals > goal) {
                            Brush.verticalGradient(
                                colors = listOf(overColorSoft, overColor),
                                startY = y, endY = pxH
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(gradientEnd, gradientStart),
                                startY = y, endY = pxH
                            )
                        }
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
            }
            Column(
                Modifier.width(44.dp).fillMaxSize().padding(start = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ticks.reversed().forEach { tick ->
                    Text(formatTick(tick), fontSize = 11.sp, color = secondaryColor)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val areaWidthDp = maxWidth
                val areaWidthPx = with(density) { areaWidthDp.toPx() }
                val n = dailyCalories.size
                val gap = 4f
                val maxBarPx = with(density) { 60.dp.toPx() }
                val rawWidth = (areaWidthPx - gap * (n - 1)) / n
                val barWidth = rawWidth.coerceIn(2f, maxBarPx)
                val totalGroupW = barWidth * n + gap * (n - 1)
                val startX = ((areaWidthPx - totalGroupW) / 2f).coerceAtLeast(0f)
                val slotPx = barWidth + gap
                val slotDp = with(density) { slotPx.toDp() }
                val minLabelDp = 40.dp
                val slotStep = if (slotDp >= minLabelDp) 1
                    else Math.ceil((minLabelDp.value / slotDp.value).toDouble()).toInt().coerceAtLeast(1)
                val pickedIndices = buildList {
                    var i = 0
                    while (i < n) { add(i); i += slotStep }
                    if (last() != n - 1) add(n - 1)
                }.distinct()
                val labelBoxWidth = if (slotStep == 1) slotDp else minLabelDp.coerceAtLeast(slotDp)
                pickedIndices.forEach { i ->
                    val cxPx = startX + i * (barWidth + gap) + barWidth / 2f
                    val cxDp = with(density) { cxPx.toDp() }
                    Box(
                        Modifier
                            .width(labelBoxWidth)
                            .offset(x = cxDp - labelBoxWidth / 2),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            xLabelFmt.format(dailyCalories[i].first),
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
