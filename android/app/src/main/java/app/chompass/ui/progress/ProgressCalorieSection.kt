package app.chompass.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.BodyFatEntry
import app.chompass.models.EnergyFormat
import app.chompass.models.UnitFormat
import app.chompass.models.WeightEntry
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.navigation.LocalEnergyUnit
import java.time.LocalDate
import java.time.ZoneId
import app.chompass.ui.theme.AppTextOpacity

/**
 * Overlay payload for the calorie card: smoothed trend + raw daily dots in
 * display units, plus the per-day lookup the tap inspection tag appends.
 */
internal data class CalorieOverlayData(
    val trend: List<TrendPoint>,
    val dots: List<TrendPoint>,
    val color: Color,
    val unitSuffix: String,
    val valuesByDay: Map<LocalDate, Double>,
)

/**
 * Builds the calorie-card metric overlay: weight (kg→lb when imperial) or
 * body fat (fraction→percent), same daily-bucket + trailing-average math as
 * the body overlay chart. Null when the key is off or the series has no data
 * in range — the card then draws bars only.
 */
internal fun calorieOverlayData(
    key: String,
    weights: List<WeightEntry>,
    bodyFats: List<BodyFatEntry>,
    useMetricWeight: Boolean,
    /** Resolved at composable scope (" kg" / " lbs") — no hardcoded units here. */
    weightUnitSuffix: String,
): CalorieOverlayData? {
    if (key == "off") return null
    val zone = ZoneId.systemDefault()
    fun dayMs(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    return when (key) {
        OVERLAY_KEY_WEIGHT -> {
            if (weights.isEmpty()) return null
            val display = { kg: Double -> if (useMetricWeight) kg else UnitFormat.kgToLbs(kg) }
            val inputs = weights.map { MetricTrendInput(at = it.date, value = it.weightKg) }
            val trend = computeMetricTrend(inputs, zone)
            val daily = averageMetricByDay(inputs, zone)
            CalorieOverlayData(
                trend = downsampleTrend(trend.map { TrendPoint(dayMs(it.day), display(it.value)) }),
                dots = downsampleTrend(daily.map { TrendPoint(dayMs(it.day), display(it.value)) }),
                color = overlaySeriesColor(OVERLAY_KEY_WEIGHT),
                unitSuffix = weightUnitSuffix,
                valuesByDay = daily.associate { it.day to display(it.value) },
            )
        }
        OVERLAY_KEY_BODY_FAT -> {
            if (bodyFats.isEmpty()) return null
            val inputs = bodyFats.map { MetricTrendInput(at = it.date, value = it.bodyFatFraction * 100.0) }
            val trend = computeMetricTrend(inputs, zone)
            val daily = averageMetricByDay(inputs, zone)
            CalorieOverlayData(
                trend = downsampleTrend(trend.map { TrendPoint(dayMs(it.day), it.value) }),
                dots = downsampleTrend(daily.map { TrendPoint(dayMs(it.day), it.value) }),
                color = overlaySeriesColor(OVERLAY_KEY_BODY_FAT),
                unitSuffix = "%",
                valuesByDay = daily.associate { it.day to it.value },
            )
        }
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalorieSection(
    calorieSlots: List<CalorieSlot>,
    calorieGoal: Int,
    calorieAverage: Int? = null,
    dailyCalorieGoals: Map<LocalDate, Int> = emptyMap(),
    chartsImmediate: Boolean = false,
    /** Metric overlay: "off" | "weight" | "bodyfat". */
    overlayKey: String = "off",
    onOverlayKeyChange: (String) -> Unit = {},
    overlayWeights: List<WeightEntry> = emptyList(),
    overlayBodyFats: List<BodyFatEntry> = emptyList(),
    useMetricWeight: Boolean = true,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> androidx.compose.ui.graphics.Color = { androidx.compose.ui.graphics.Color.Transparent },
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val untrackedCount = calorieSlots.count { it.untracked }
    val weightSuffix = " " + stringResource(if (useMetricWeight) R.string.unit_kg else R.string.unit_lbs)
    val overlay = remember(overlayKey, overlayWeights, overlayBodyFats, useMetricWeight, weightSuffix) {
        calorieOverlayData(overlayKey, overlayWeights, overlayBodyFats, useMetricWeight, weightSuffix)
    }
    // A stored key whose series lost its in-range data reads as "off" so the
    // chips never claim an overlay the chart isn't drawing.
    val effectiveKey = when {
        overlayKey == OVERLAY_KEY_WEIGHT && overlayWeights.isEmpty() -> "off"
        overlayKey == OVERLAY_KEY_BODY_FAT && overlayBodyFats.isEmpty() -> "off"
        else -> overlayKey
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.progressCollapseHeader(expanded) { expanded = !expanded },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.progress_calories_section), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (calorieAverage != null) {
                    Text(
                        stringResource(R.string.progress_avg_format, EnergyFormat.quantity(calorieAverage, LocalEnergyUnit.current), energyUnitLabel()),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    )
                }
                Spacer(Modifier.width(8.dp))
                CollapseChevron(expanded)
            }
            if (untrackedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.untracked_days_excluded, untrackedCount, untrackedCount),
                    fontSize = 13.sp,
                    maxLines = 1,
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
                if (calorieSlots.none { it.kcal != null }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.progress_no_food),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                        )
                    }
                } else {
                    // Metric combo chips: single-select; a metric chip appears
                    // only when that series has data in range (plan §4).
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayPickChip(
                            label = stringResource(R.string.progress_overlay_off),
                            selected = effectiveKey == "off",
                            onClick = { onOverlayKeyChange("off") },
                        )
                        if (overlayWeights.isNotEmpty()) {
                            OverlayPickChip(
                                label = stringResource(R.string.progress_metric_weight),
                                selected = effectiveKey == OVERLAY_KEY_WEIGHT,
                                onClick = { onOverlayKeyChange(OVERLAY_KEY_WEIGHT) },
                            )
                        }
                        if (overlayBodyFats.isNotEmpty()) {
                            OverlayPickChip(
                                label = stringResource(R.string.progress_metric_body_fat),
                                selected = effectiveKey == OVERLAY_KEY_BODY_FAT,
                                onClick = { onOverlayKeyChange(OVERLAY_KEY_BODY_FAT) },
                            )
                        }
                    }
                    val chartSlots = remember(calorieSlots) { bucketCalorieSlots(calorieSlots) }
                    overlay?.let { data ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            LegendSwatch(
                                color = data.color,
                                label = stringResource(overlayLegendLabel(effectiveKey)) +
                                    (data.dots.lastOrNull()?.value?.let { " ${formatTick(it)}${data.unitSuffix}" } ?: ""),
                            )
                        }
                    }
                    CalorieBarChart(
                        slots = chartSlots,
                        goal = calorieGoal,
                        dailyGoals = dailyCalorieGoals,
                        immediate = chartsImmediate,
                        overlay = overlay,
                        dayTypeByDay = dayTypeByDay,
                        untrackedDays = untrackedDays,
                        typeColorOf = typeColorOf,
                    )
                    if (untrackedCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LegendSwatch(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                label = stringResource(R.string.progress_untracked_legend),
                                dashed = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun overlayLegendLabel(key: String): Int = when (key) {
    OVERLAY_KEY_BODY_FAT -> R.string.progress_metric_body_fat
    else -> R.string.progress_metric_weight
}

@Composable
private fun OverlayPickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
    )
}
