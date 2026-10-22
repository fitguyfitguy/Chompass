package app.chompass.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import app.chompass.models.UnitFormat
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity

internal fun formatPercent(fraction: Double): String =
    UnitFormat.percent(fraction * 100)

internal fun formatPercentValue(percent: Double): String =
    UnitFormat.percent(percent)

internal fun formatPercentChange(deltaPercent: Double): String =
    "${UnitFormat.signedDelta(deltaPercent)}%"

/** Fixed overlay color for a series key: weight keeps the theme accent, body
 *  fat takes ChartSeries[0], measurement sites cycle the remaining hues. */
internal fun overlaySeriesColor(key: String): Color = when (key) {
    OVERLAY_KEY_WEIGHT -> AppColors.Calorie
    OVERLAY_KEY_BODY_FAT -> AppColors.ChartSeries[0]
    else -> {
        val ordinal = BodyMeasurement.Site.fromStorageId(key)?.ordinal ?: 0
        AppColors.ChartSeries[1 + ordinal % (AppColors.ChartSeries.size - 1)]
    }
}

/**
 * Unified body card: weight, body fat, and measurement sites as series chips
 * over one normalized overlay chart. Multi-select; the last chip toggled on
 * becomes the primary series whose stat badges render under the chart. The
 * legend is this card's chip row — the canvas draws no per-series labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodySection(
    weights: List<WeightEntry>,
    bodyFats: List<BodyFatEntry>,
    measurements: List<BodyMeasurement>,
    sites: List<BodyMeasurement.Site>,
    selectedKeys: Set<String>,
    onToggleSeries: (String) -> Unit,
    weightStats: WeightSummaryStats,
    bodyFatStats: BodyFatSummaryStats,
    profileBodyFatFraction: Double?,
    goalWeightKg: Double?,
    goalBodyFatFraction: Double?,
    useMetricWeight: Boolean,
    useMetricLength: Boolean,
    bodyFatAvailable: Boolean,
    chartsImmediate: Boolean = false,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> Color = { Color.Transparent },
    onLogWeight: () -> Unit = {},
    onLogBodyFat: () -> Unit = {},
) {
    // Last chip toggled on becomes primary; falls back to weight when the
    // selection changes underneath (pref restore, range without data).
    var primary by rememberSaveable { mutableStateOf(OVERLAY_KEY_WEIGHT) }

    val weightUnitSuffix = " " + stringResource(if (useMetricWeight) R.string.unit_kg else R.string.unit_lbs)
    val lengthUnitSuffix = " " + stringResource(if (useMetricLength) R.string.unit_cm else R.string.unit_in)
    val weightLabel = stringResource(R.string.progress_metric_weight)
    val bodyFatLabel = stringResource(R.string.progress_metric_body_fat)
    val siteLabels = sites.associate { it.storageId to stringResource(it.labelRes) }
    val siteShortLabels = sites.associate { it.storageId to stringResource(it.shortLabelRes) }
    val selectedSites = remember(sites, selectedKeys) { sites.filter { it.storageId in selectedKeys } }

    val model = remember(
        weights, bodyFats, measurements, selectedSites, selectedKeys,
        goalWeightKg, goalBodyFatFraction, useMetricWeight, useMetricLength,
        weightLabel, bodyFatLabel, siteLabels, weightUnitSuffix, lengthUnitSuffix,
        dayTypeByDay, untrackedDays, typeColorOf,
    ) {
        val meta = buildMap {
            for (key in selectedKeys) {
                val seriesMeta = when {
                    key == OVERLAY_KEY_WEIGHT ->
                        OverlaySeriesMeta(weightLabel, weightUnitSuffix, overlaySeriesColor(key))
                    key == OVERLAY_KEY_BODY_FAT ->
                        OverlaySeriesMeta(bodyFatLabel, "%", overlaySeriesColor(key))
                    else -> siteLabels[key]?.let {
                        OverlaySeriesMeta(it, lengthUnitSuffix, overlaySeriesColor(key))
                    }
                }
                if (seriesMeta != null) put(key, seriesMeta)
            }
        }
        buildOverlayChartModel(
            weights = weights,
            bodyFats = bodyFats,
            measurements = measurements,
            sites = selectedSites,
            goalWeightKg = goalWeightKg,
            goalBodyFatFraction = goalBodyFatFraction,
            useMetricWeight = useMetricWeight,
            useMetricLength = useMetricLength,
            meta = meta,
            dayTypeByDay = dayTypeByDay,
            untrackedDays = untrackedDays,
            typeColorOf = typeColorOf,
        )
    }
    // Last chip toggled on becomes primary; falls back through the series
    // that actually has data when the selection changes underneath (pref
    // restore, range without data) — never fabricates weight stats from a
    // series with no readings.
    val effectivePrimary = if (primary in selectedKeys) {
        primary
    } else {
        model.series.firstOrNull()?.key ?: OVERLAY_KEY_WEIGHT
    }
    val primarySeries = model.series.firstOrNull { it.key == effectivePrimary }

    CardSection {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Static header: title anchors the card, the pills add entries.
            // No collapse toggle — tapping the title used to fold the chart
            // away, which read as a broken affordance.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.progress_body_section),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    LogPill(weightLabel, onLogWeight)
                    if (bodyFatAvailable) {
                        LogPill(bodyFatLabel, onLogBodyFat)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeriesChip(
                        icon = Icons.Filled.MonitorWeight,
                        accessibilityLabel = weightLabel,
                        selected = OVERLAY_KEY_WEIGHT in selectedKeys,
                        enabled = true,
                        onClick = {
                            if (OVERLAY_KEY_WEIGHT !in selectedKeys) {
                                primary = OVERLAY_KEY_WEIGHT
                            }
                            onToggleSeries(OVERLAY_KEY_WEIGHT)
                        },
                    )
                    if (bodyFatAvailable) {
                        SeriesChip(
                            icon = Icons.Filled.Percent,
                            accessibilityLabel = bodyFatLabel,
                            selected = OVERLAY_KEY_BODY_FAT in selectedKeys,
                            enabled = true,
                            onClick = {
                                if (OVERLAY_KEY_BODY_FAT !in selectedKeys) {
                                    primary = OVERLAY_KEY_BODY_FAT
                                }
                                onToggleSeries(OVERLAY_KEY_BODY_FAT)
                            },
                        )
                    }
                    for (site in sites) {
                        val key = site.storageId
                        SeriesChip(
                            label = siteShortLabels.getValue(key),
                            accessibilityLabel = siteLabels.getValue(key),
                            selected = key in selectedKeys,
                            enabled = true,
                            onClick = {
                                if (key !in selectedKeys) primary = key
                                onToggleSeries(key)
                            },
                        )
                    }
                }
                if (model.series.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when {
                                    selectedKeys.isEmpty() -> R.string.progress_no_series_selected
                                    weights.isEmpty() -> R.string.progress_log_first_weight
                                    else -> R.string.progress_no_entries_in_range
                                }
                            ),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                } else {
                    DeferredChart(immediate = chartsImmediate) {
                        OverlayChartCanvas(
                            model = model,
                            immediate = chartsImmediate,
                            title = stringResource(R.string.progress_body_section),
                            untrackedDays = untrackedDays,
                        )
                    }
                    // Legend chips: real value + signed range delta per
                    // series. Sign coloring is neutral (no good/bad read):
                    // negative takes the accent, positive stays on-surface.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        for (series in model.series) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(series.color, CircleShape)
                                )
                                Text(
                                    "${series.label} ${series.currentValue?.let { formatTick(it) + series.unitLabel } ?: "–"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Subtle),
                                )
                                series.deltaOverRange?.let { delta ->
                                    Text(
                                        UnitFormat.signedDelta(delta) + series.unitLabel,
                                        fontSize = 12.sp,
                                        color = if (delta < 0) AppColors.Calorie
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    PrimaryStatBadges(
                        series = primarySeries,
                        primary = effectivePrimary,
                        weightStats = weightStats,
                        bodyFatStats = bodyFatStats,
                        profileBodyFatFraction = profileBodyFatFraction,
                        goalWeightKg = goalWeightKg,
                        goalBodyFatFraction = goalBodyFatFraction,
                        useMetricWeight = useMetricWeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogPill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AddCircle, null, tint = AppColors.Calorie, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Calorie,
            maxLines = 1,
        )
    }
}

@Composable
private fun SeriesChip(
    label: String? = null,
    icon: ImageVector? = null,
    accessibilityLabel: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    label.orEmpty(),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.chipAccessibility(accessibilityLabel, selected),
    )
}

/** Stat badges for the primary series: weight keeps the classic Current /
 *  Goal / Net Change / Average row, body fat mirrors it in percent, sites
 *  show Current / Δ / Average from their overlay series. */
@Composable
private fun PrimaryStatBadges(
    series: OverlaySeries?,
    primary: String,
    weightStats: WeightSummaryStats,
    bodyFatStats: BodyFatSummaryStats,
    profileBodyFatFraction: Double?,
    goalWeightKg: Double?,
    goalBodyFatFraction: Double?,
    useMetricWeight: Boolean,
) {
    val currentLabel = stringResource(R.string.progress_stat_current)
    val goalLabel = stringResource(R.string.progress_stat_goal)
    val netChangeLabel = stringResource(R.string.progress_stat_net_change)
    val averageLabel = stringResource(R.string.progress_stat_average)
    when {
        primary == OVERLAY_KEY_WEIGHT -> StatBadgeRow(
            buildList {
                weightStats.currentKg?.let { add(currentLabel to formatWeight(it, useMetricWeight)) }
                goalWeightKg?.let { add(goalLabel to formatWeight(it, useMetricWeight)) }
                // No in-range readings → no fabricated "+0.0 kg" rows.
                if (weightStats.currentKg != null) {
                    add(netChangeLabel to formatWeightChange(weightStats.netChangeKg, useMetricWeight))
                    add(averageLabel to formatWeight(weightStats.averageKg, useMetricWeight))
                }
            }
        )
        primary == OVERLAY_KEY_BODY_FAT -> StatBadgeRow(
            buildList {
                (bodyFatStats.currentFraction ?: profileBodyFatFraction)?.let {
                    add(currentLabel to formatPercent(it))
                }
                goalBodyFatFraction?.let { add(goalLabel to formatPercent(it)) }
                if (bodyFatStats.currentFraction != null) {
                    add(netChangeLabel to formatPercentChange(bodyFatStats.netChangePercent))
                    add(averageLabel to formatPercentValue(bodyFatStats.averagePercent))
                }
            }
        )
        series != null -> {
            val unit = series.unitLabel
            val avg = series.points.map { it.value }.average()
            StatBadgeRow(
                listOf(
                    currentLabel to (series.currentValue?.let { formatTick(it) + unit } ?: "–"),
                    netChangeLabel to (series.deltaOverRange?.let { UnitFormat.signedDelta(it) + unit } ?: "–"),
                    averageLabel to (formatTick(avg) + unit),
                )
            )
        }
    }
}
