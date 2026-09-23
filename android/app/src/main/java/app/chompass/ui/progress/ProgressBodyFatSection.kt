package app.chompass.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.BodyFatEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.models.UnitFormat

internal fun formatPercent(fraction: Double): String =
    UnitFormat.percent(fraction * 100)

internal fun formatPercentValue(percent: Double): String =
    UnitFormat.percent(percent)

internal fun formatPercentChange(deltaPercent: Double): String =
    "${UnitFormat.signedDelta(deltaPercent)}%"

@Composable
internal fun BodyFatSection(
    entries: List<BodyFatEntry>,
    stats: BodyFatSummaryStats,
    profileBodyFatFraction: Double?,
    goalFraction: Double?,
    onLogBodyFat: () -> Unit,
    chartsImmediate: Boolean = false,
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> androidx.compose.ui.graphics.Color = { androidx.compose.ui.graphics.Color.Transparent },
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.progressCollapseHeader(expanded) { expanded = !expanded },
        ) {
            Text(stringResource(R.string.progress_metric_body_fat), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(onClick = onLogBodyFat),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AddCircle, null, tint = AppColors.Calorie, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.progress_log_body_fat), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.Calorie)
            }
            Spacer(Modifier.width(8.dp))
            CollapseChevron(expanded)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val currentBodyFat = stats.currentFraction ?: profileBodyFatFraction
                if (entries.isEmpty() && currentBodyFat == null) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.progress_log_first_body_fat),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                        )
                    }
                } else {
                    val currentLabel = stringResource(R.string.progress_stat_current)
                    val goalLabel = stringResource(R.string.progress_stat_goal)
                    val netChangeLabel = stringResource(R.string.progress_stat_net_change)
                    val averageLabel = stringResource(R.string.progress_stat_average)
                    StatBadgeRow(
                        buildList {
                            currentBodyFat?.let {
                                add(currentLabel to formatPercent(it))
                            }
                            goalFraction?.let {
                                add(goalLabel to formatPercent(it))
                            }
                            if (entries.isNotEmpty()) {
                                add(netChangeLabel to formatPercentChange(stats.netChangePercent))
                                add(averageLabel to formatPercentValue(stats.averagePercent))
                            }
                        }
                    )
                    if (entries.isNotEmpty()) {
                        DeferredChart(immediate = chartsImmediate) {
                            BodyFatChartCanvas(
                                entries = entries,
                                goalFraction = goalFraction,
                                immediate = chartsImmediate,
                                dayTypeByDay = dayTypeByDay,
                                untrackedDays = untrackedDays,
                                typeColorOf = typeColorOf,
                            )
                        }
                    }
                }
            }
        }
    }
}
