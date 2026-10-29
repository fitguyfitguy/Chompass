package app.chompass.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyCompositionEntry.CompositionMetric
import app.chompass.models.UnitFormat
import app.chompass.models.WeightEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity

/** How far a weigh-in may sit from a composition row and still supply the TBW% denominator. */
internal const val TBW_WEIGHT_JOIN_MILLIS: Long = 12L * 60 * 60 * 1000

/**
 * Total body water as a fraction of body weight (0–1 × 100 for display), or null
 * when no weigh-in sits within [TBW_WEIGHT_JOIN_MILLIS] of the reading. Derived
 * display value only — it never feeds the drinking-water goal, which is intake.
 */
internal fun totalBodyWaterPercent(
    entry: BodyCompositionEntry,
    weights: List<WeightEntry>,
): Double? {
    val water = entry.totalBodyWaterKg ?: return null
    if (!water.isFinite() || water <= 0) return null
    val nearest = weights.minByOrNull {
        kotlin.math.abs(it.date.toEpochMilli() - entry.date.toEpochMilli())
    } ?: return null
    val gap = kotlin.math.abs(nearest.date.toEpochMilli() - entry.date.toEpochMilli())
    if (gap > TBW_WEIGHT_JOIN_MILLIS || nearest.weightKg <= 0) return null
    return water / nearest.weightKg * 100.0
}

private fun formatCompositionValue(
    context: android.content.Context,
    metric: CompositionMetric,
    value: Double,
    useMetric: Boolean,
): String = when {
    !metric.isMass -> String.format(java.util.Locale.getDefault(), "%.1f", value)
    useMetric -> String.format(
        java.util.Locale.getDefault(), "%.1f %s", value, context.getString(R.string.unit_kg)
    )
    else -> String.format(
        java.util.Locale.getDefault(), "%.1f %s", UnitFormat.kgToLbs(value), context.getString(R.string.unit_lbs)
    )
}

private fun formatCompositionDelta(
    context: android.content.Context,
    metric: CompositionMetric,
    delta: Double,
    useMetric: Boolean,
): String = when {
    !metric.isMass -> UnitFormat.signedDelta(delta)
    useMetric -> "${UnitFormat.signedDelta(delta)} ${context.getString(R.string.unit_kg)}"
    else -> "${UnitFormat.signedDelta(UnitFormat.kgToLbs(delta))} ${context.getString(R.string.unit_lbs)}"
}

/**
 * Scale composition section for the Progress tab: one block per metric that
 * actually has readings in the selected range (bone mass, total body water,
 * muscle mass, lean mass, visceral-fat rating). Users without a
 * composition-reporting scale see nothing at all — no empty cards.
 *
 * Display-only: the values come from the user's scale (or a CSV import), and the
 * section says so; nothing here feeds BMR/macros (the profile lean-mass stamp does
 * that, silently, at write time).
 */
@Composable
internal fun CompositionSection(
    compositions: List<BodyCompositionEntry>,
    weights: List<WeightEntry>,
    useMetric: Boolean,
    chartsImmediate: Boolean = false,
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> Color = { Color.Transparent },
    onLog: () -> Unit = {},
) {
    val metrics = remember(compositions) {
        CompositionMetric.entries.filter { metric ->
            compositions.any { it.value(metric) != null }
        }
    }
    if (metrics.isEmpty()) return

    CardSection {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.progress_scale_metrics),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier.clickable(onClick = onLog),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AddCircle,
                        contentDescription = null,
                        tint = AppColors.Calorie,
                        modifier = Modifier.width(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.progress_log_scale_metrics),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Calorie,
                        maxLines = 1,
                    )
                }
            }
            Text(
                stringResource(R.string.progress_scale_metrics_note),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Subtle),
            )
            for (metric in metrics) {
                CompositionMetricBlock(
                    metric = metric,
                    entries = compositions,
                    weights = weights,
                    useMetric = useMetric,
                    chartsImmediate = chartsImmediate,
                    dayTypeByDay = dayTypeByDay,
                    untrackedDays = untrackedDays,
                    typeColorOf = typeColorOf,
                )
            }
        }
    }
}

/** One metric's header + Current / Net-change badges + trend chart. */
@Composable
private fun CompositionMetricBlock(
    metric: CompositionMetric,
    entries: List<BodyCompositionEntry>,
    weights: List<WeightEntry>,
    useMetric: Boolean,
    chartsImmediate: Boolean,
    dayTypeByDay: Map<String, String>,
    untrackedDays: Set<String>,
    typeColorOf: (String) -> Color,
) {
    val series = remember(entries, metric) {
        entries.mapNotNull { entry ->
            entry.value(metric)?.let { TrendPoint(entry.date.toEpochMilli(), it) }
        }
    }
    var expanded by rememberSaveable(metric) { mutableStateOf(true) }
    if (series.isEmpty()) return
    val latest = series.last().value
    val netChange = latest - series.first().value
    val context = LocalContext.current
    val label = stringResource(metric.labelRes)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.progressCollapseHeader(expanded) { expanded = !expanded },
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                formatCompositionValue(context, metric, latest, useMetric),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
            Spacer(Modifier.width(4.dp))
            CollapseChevron(expanded)
        }
        if (metric == CompositionMetric.TOTAL_BODY_WATER) {
            val percent = entries.asReversed()
                .firstNotNullOfOrNull { totalBodyWaterPercent(it, weights) }
            if (percent != null) {
                Text(
                    stringResource(R.string.progress_scale_metrics_tbw_percent, percent),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBadgeRow(
                    listOf(
                        stringResource(R.string.progress_stat_current) to
                            formatCompositionValue(context, metric, latest, useMetric),
                        stringResource(R.string.progress_stat_net_change) to
                            formatCompositionDelta(context, metric, netChange, useMetric),
                    )
                )
                DeferredChart(immediate = chartsImmediate) {
                    MeasurementChartCanvas(
                        series = series,
                        immediate = chartsImmediate,
                        tagFormatter = { formatCompositionValue(context, metric, it, useMetric) },
                        title = label,
                        dayTypeByDay = dayTypeByDay,
                        untrackedDays = untrackedDays,
                        typeColorOf = typeColorOf,
                    )
                }
            }
        }
    }
}
