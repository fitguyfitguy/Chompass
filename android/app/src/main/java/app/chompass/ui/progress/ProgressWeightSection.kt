package app.chompass.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.WeightEntry
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.theme.AppColors
import app.chompass.models.UnitFormat
import java.time.ZoneId

internal fun formatWeight(kg: Double, useMetric: Boolean): String =
    UnitFormat.weight(kg, useMetric)

internal fun formatWeightChange(deltaKg: Double, useMetric: Boolean): String {
    val displayValue = if (useMetric) deltaKg else UnitFormat.kgToLbs(deltaKg)
    val unit = if (useMetric) "kg" else "lbs"
    return "${UnitFormat.signedDelta(displayValue)} $unit"
}

@Composable
internal fun WeightSection(
    entries: List<WeightEntry>,
    stats: WeightSummaryStats,
    goalKg: Double?,
    useMetric: Boolean,
    onLogWeight: () -> Unit,
    chartsImmediate: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_weight_section), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(onClick = onLogWeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AddCircle, null, tint = AppColors.Calorie, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.progress_log_weight), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.Calorie)
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.progress_log_first_weight),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        } else {
            val currentLabel = stringResource(R.string.progress_stat_current)
            val goalLabel = stringResource(R.string.progress_stat_goal)
            val netChangeLabel = stringResource(R.string.progress_stat_net_change)
            val averageLabel = stringResource(R.string.progress_stat_average)
            StatBadgeRow(
                buildList {
                    stats.currentKg?.let {
                        add(currentLabel to formatWeight(it, useMetric))
                    }
                    goalKg?.let {
                        add(goalLabel to formatWeight(it, useMetric))
                    }
                    add(netChangeLabel to formatWeightChange(stats.netChangeKg, useMetric))
                    add(averageLabel to formatWeight(stats.averageKg, useMetric))
                }
            )
            val hasTrend = remember(entries) {
                computeWeightTrend(
                    weighIns = entries.map { WeightTrendInput(at = it.date, weightKg = it.weightKg) },
                    zone = ZoneId.systemDefault(),
                ).isNotEmpty()
            }
            WeightChartLegend(hasTrend = hasTrend)
            DeferredChart(immediate = chartsImmediate) {
                WeightChartCanvas(
                    entries = entries,
                    goalKg = goalKg,
                    useMetric = useMetric,
                    immediate = chartsImmediate,
                )
            }
        }
    }
}

@Composable
private fun WeightChartLegend(hasTrend: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch(color = AppColors.Calorie, label = stringResource(R.string.progress_weight_raw_legend))
            if (hasTrend) {
                LegendSwatch(
                    color = AppColors.Protein,
                    label = stringResource(R.string.progress_weight_trend_legend),
                    dashed = true,
                )
            }
        }
        if (!hasTrend) {
            Text(
                stringResource(R.string.progress_weight_trend_need_more),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun LegendSwatch(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    dashed: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) {
            Canvas(Modifier.width(18.dp).height(3.dp)) {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
    }
}

@Composable
internal fun WeightHistoryLink(count: Int, onClick: () -> Unit) {
    FudGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FudIconBubble(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                size = 28.dp,
                iconSize = 16.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.progress_weight_history), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.progress_history_count_format, count),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun BodyFatHistoryLink(count: Int, onClick: () -> Unit) {
    FudGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FudIconBubble(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                size = 28.dp,
                iconSize = 16.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.progress_body_fat_history), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.progress_history_count_format, count),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
