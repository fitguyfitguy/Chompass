package app.chompass.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.EnergyFormat
import app.chompass.models.GoalPhase
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroPlan
import app.chompass.models.PhaseIntent
import app.chompass.models.UnitFormat
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.dayTypeColor
import app.chompass.ui.theme.success

/**
 * Adherence card (Progress v2): range intake vs journaled goals — headline,
 * net, on-target band, then per-day-type and per-phase rollups. Hidden by the
 * caller when the range has no complete logged days.
 */
@Composable
internal fun ProgressAdherenceSection(
    adherence: AdherenceSummary,
    plan: MacroPlan?,
) {
    val unit = LocalEnergyUnit.current
    val avgIntakeText = LocaleFormat.integer(EnergyFormat.quantity(adherence.avgIntake ?: 0, unit))
    val avgGoalText = LocaleFormat.integer(EnergyFormat.quantity(adherence.avgGoal ?: 0, unit))
    val deltaText = UnitFormat.signedDelta(EnergyFormat.quantity(adherence.avgDelta ?: 0, unit).toDouble()) +
        " " + energyUnitLabel()
    val netText = adherence.netKcal?.let {
        UnitFormat.signedDelta(EnergyFormat.quantity(it.toInt(), unit).toDouble()) + " " + energyUnitLabel()
    }

    CardSection {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.progress_adherence_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    R.string.progress_adherence_headline_format,
                    avgIntakeText,
                    avgGoalText,
                    deltaText,
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                netText?.let { net ->
                    AdherenceFact(
                        text = stringResource(R.string.progress_adherence_net_format, net),
                        color = if (adherence.avgDelta != null && adherence.avgDelta < 0) {
                            AppColors.Calorie
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                adherence.withinBandPct?.let { pct ->
                    AdherenceFact(
                        text = stringResource(
                            R.string.progress_adherence_band_format,
                            pct,
                            adherence.underDays,
                            adherence.overDays,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (adherence.byDayType.isNotEmpty()) {
                Text(
                    stringResource(R.string.progress_adherence_by_day_type),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                adherence.byDayType.forEach { row ->
                    // Current plan wins for label/color consistency; the
                    // journal's write-time name rescues a plan-missed id.
                    val name = plan?.profileById(row.profileId)?.name
                        ?: row.profileName
                        ?: row.profileId
                    DayTypeAdherenceRow(
                        color = dayTypeColor(plan?.profileById(row.profileId)?.colorKey, row.profileId),
                        name = name,
                        detail = stringResource(
                            R.string.progress_adherence_row_format,
                            LocaleFormat.integer(EnergyFormat.quantity(row.avgIntake ?: 0, unit)),
                            UnitFormat.signedDelta(EnergyFormat.quantity(row.avgDelta ?: 0, unit).toDouble()) + " " + energyUnitLabel(),
                            row.days,
                        ),
                    )
                }
            }
            if (adherence.byPhase.any { it.overlapDays > 0 }) {
                Text(
                    stringResource(R.string.progress_adherence_by_phase),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                adherence.byPhase.filter { it.overlapDays > 0 }.forEach { row ->
                    PhaseAdherenceRow(
                        phase = row.phase,
                        avgDelta = row.avgDelta,
                        netKcal = row.netKcal,
                        overlapDays = row.overlapDays,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdherenceFact(text: String, color: Color) {
    Text(
        text,
        fontSize = 13.sp,
        color = color,
    )
}

@Composable
private fun DayTypeAdherenceRow(color: Color, name: String, detail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(
            detail,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
    }
}

@Composable
private fun PhaseAdherenceRow(
    phase: GoalPhase,
    avgDelta: Int?,
    netKcal: Long?,
    overlapDays: Int,
) {
    val unit = LocalEnergyUnit.current
    val scheme = MaterialTheme.colorScheme
    val nameColor = when (phase.intent) {
        PhaseIntent.CUT -> scheme.error
        PhaseIntent.MAINTAIN -> scheme.onSurface
        PhaseIntent.BULK -> scheme.success
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Text(phase.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nameColor)
        Spacer(Modifier.weight(1f))
        val detail = buildString {
            if (avgDelta != null) {
                append(UnitFormat.signedDelta(EnergyFormat.quantity(avgDelta, unit).toDouble()))
                append(" ")
                append(energyUnitLabel())
            }
            netKcal?.let {
                if (isNotEmpty()) append(" · ")
                append(UnitFormat.signedDelta(EnergyFormat.quantity(it.toInt(), unit).toDouble()))
                append(" ")
                append(energyUnitLabel())
            }
            append(" · ")
            append(stringResource(R.string.progress_adherence_overlap_days, overlapDays))
        }
        Text(
            detail,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
    }
}
