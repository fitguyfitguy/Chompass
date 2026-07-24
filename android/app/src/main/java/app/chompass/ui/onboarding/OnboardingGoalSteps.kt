package app.chompass.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ActivityLevel
import app.chompass.models.DietMode
import app.chompass.models.KetoCarbMode
import app.chompass.models.WeightGoal
import app.chompass.services.KetoCarbRecommendationService
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.SplitDecimalWheelPicker
import app.chompass.ui.components.UnitToggle
import app.chompass.ui.theme.AppColors
import java.util.Locale

@Composable
internal fun ActivityStep(selected: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_activity_title),
            subtitle = stringResource(R.string.onboarding_activity_subtitle)
        )
        for (a in ActivityLevel.values()) {
            SelectionCard(
                icon = activityIcon(a),
                title = stringResource(a.displayNameRes),
                subtitle = stringResource(a.subtitleRes),
                selected = a == selected
            ) { onSelect(a) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

internal fun activityIcon(level: ActivityLevel): ImageVector = when (level) {
    ActivityLevel.SEDENTARY -> Icons.Outlined.Chair
    ActivityLevel.LIGHT -> Icons.AutoMirrored.Outlined.DirectionsWalk
    ActivityLevel.MODERATE -> Icons.AutoMirrored.Outlined.DirectionsRun
    ActivityLevel.ACTIVE -> Icons.Outlined.LocalFireDepartment
    ActivityLevel.VERY_ACTIVE -> Icons.Outlined.FitnessCenter
    ActivityLevel.EXTRA_ACTIVE -> Icons.Outlined.SportsKabaddi
}

@Composable
internal fun GoalStep(selected: WeightGoal, onSelect: (WeightGoal) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_goal_title),
            subtitle = stringResource(R.string.onboarding_goal_subtitle)
        )
        Spacer(Modifier.weight(1f))
        for (g in WeightGoal.values()) {
            SelectionCard(
                icon = goalIcon(g),
                title = stringResource(g.displayNameRes),
                selected = g == selected
            ) { onSelect(g) }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun DietModeStep(
    selected: DietMode,
    ketoCarbMode: KetoCarbMode,
    ketoCarbManualTarget: Int?,
    onSelect: (DietMode) -> Unit,
    onKetoCarbModeSelect: (KetoCarbMode) -> Unit,
    onKetoCarbManualTargetChange: (Int?) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_diet_mode_title),
            subtitle = stringResource(R.string.onboarding_diet_mode_subtitle)
        )
        Spacer(Modifier.height(8.dp))
        for (mode in DietMode.values()) {
            SelectionCard(
                icon = when (mode) {
                    DietMode.STANDARD -> Icons.Outlined.Restaurant
                    DietMode.KETO -> Icons.Outlined.LocalFireDepartment
                },
                title = stringResource(mode.displayNameRes),
                subtitle = if (mode == DietMode.KETO) stringResource(R.string.diet_mode_beta_note) else null,
                selected = mode == selected
            ) { onSelect(mode) }
            Spacer(Modifier.height(12.dp))
        }
        if (selected == DietMode.KETO) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.keto_carb_setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            for (mode in KetoCarbMode.values()) {
                SelectionCard(
                    icon = if (mode == KetoCarbMode.ADAPTIVE) Icons.Outlined.AutoAwesome else Icons.Outlined.Tune,
                    title = stringResource(mode.displayNameRes),
                    subtitle = if (mode == KetoCarbMode.ADAPTIVE) {
                        stringResource(R.string.keto_carb_mode_adaptive_subtitle)
                    } else {
                        stringResource(R.string.keto_carb_mode_manual_subtitle)
                    },
                    selected = mode == ketoCarbMode
                ) { onKetoCarbModeSelect(mode) }
                Spacer(Modifier.height(10.dp))
            }
            if (ketoCarbMode == KetoCarbMode.MANUAL) {
                val fallback = KetoCarbRecommendationService.MIN_NET_CARBS_G
                NumericWheelPicker(
                    value = ketoCarbManualTarget ?: fallback,
                    onValueChange = { onKetoCarbManualTargetChange(it) },
                    min = KetoCarbRecommendationService.MIN_NET_CARBS_G,
                    max = KetoCarbRecommendationService.MAX_NET_CARBS_G,
                    unit = stringResource(R.string.unit_g)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.keto_carb_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

internal fun goalIcon(goal: WeightGoal): ImageVector = when (goal) {
    WeightGoal.LOSE -> Icons.AutoMirrored.Outlined.TrendingDown
    WeightGoal.MAINTAIN -> Icons.AutoMirrored.Outlined.TrendingFlat
    WeightGoal.GAIN -> Icons.AutoMirrored.Outlined.TrendingUp
}

@Composable
internal fun GoalWeightStep(current: Double, goal: WeightGoal, heightMetric: Boolean, weightMetric: Boolean, onChange: (Double) -> Unit, onToggle: (Boolean) -> Unit) {
    // Same Imperial/Metric toggle as HeightWeightStep so the user can switch
    // units without backing out to change Settings first.
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_desired_weight_title),
            subtitle = stringResource(goal.displayNameRes)
        )
        UnitToggle(
            leftLabel = stringResource(R.string.onboarding_imperial),
            rightLabel = stringResource(R.string.onboarding_metric),
            isLeft = !(heightMetric && weightMetric),
            onSelect = { isLeftSel -> onToggle(!isLeftSel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        if (weightMetric) {
            SplitDecimalWheelPicker(
                value = current.coerceIn(30.0, 250.0),
                onValueChange = onChange,
                min = 30,
                max = 250,
                unit = stringResource(R.string.unit_kg)
            )
        } else {
            val lbs = (current * 2.20462).coerceIn(60.0, 500.0)
            SplitDecimalWheelPicker(
                value = lbs,
                onValueChange = { newLbs -> onChange(newLbs / 2.20462) },
                min = 60,
                max = 500,
                unit = stringResource(R.string.unit_lbs)
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun GoalSpeedStep(
    weeklyKg: Double,
    goal: WeightGoal,
    useMetric: Boolean,
    currentKg: Double,
    targetKg: Double,
    onSelect: (Double) -> Unit
) {
    // iOS goalSpeedStep: MAINTAIN shows a centered "Balanced pace set" card; LOSE/GAIN
    // show a big weekly-change readout, a tortoise/hare/bolt row, a 3-stop slider
    // (0.25/0.5/1.0 kg/wk), and an estimated-days card.
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            title = if (goal == WeightGoal.MAINTAIN) stringResource(R.string.onboarding_pace_title_maintain)
                    else stringResource(R.string.onboarding_pace_title_change),
            subtitle = when {
                goal == WeightGoal.MAINTAIN -> stringResource(R.string.onboarding_pace_subtitle_maintain)
                goal == WeightGoal.LOSE -> stringResource(R.string.onboarding_pace_subtitle_lose)
                else -> stringResource(R.string.onboarding_pace_subtitle_gain)
            }
        )
        if (goal == WeightGoal.MAINTAIN) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AppColors.Calorie,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onboarding_pace_balanced_set),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.onboarding_pace_balanced_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            val idx = when {
                kotlin.math.abs(weeklyKg - 0.25) < 0.01 -> 0
                kotlin.math.abs(weeklyKg - 1.0) < 0.01 -> 2
                else -> 1
            }
            val unit = if (useMetric) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lbs)
            val display = if (useMetric) String.format(Locale.US, "%.1f", weeklyKg)
                          else String.format(Locale.US, "%.1f", weeklyKg * 2.20462)
            val diffKg = kotlin.math.abs(targetKg - currentKg)
            val estimatedDays = if (weeklyKg > 0) (diffKg / weeklyKg * 7).toInt() else 0
            Spacer(Modifier.weight(1f))
            // Weekly change readout
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$display $unit",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.onboarding_pace_per_week),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            Spacer(Modifier.height(20.dp))
            // tortoise / hare / bolt icons with labels
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PaceIcon(Icons.AutoMirrored.Outlined.DirectionsWalk, stringResource(R.string.onboarding_pace_slow), idx == 0)
                PaceIcon(Icons.AutoMirrored.Outlined.DirectionsRun, stringResource(R.string.onboarding_pace_recommended), idx == 1)
                PaceIcon(Icons.Outlined.Bolt, stringResource(R.string.onboarding_pace_fast), idx == 2)
            }
            Spacer(Modifier.height(12.dp))
            // Slider with 3 stops
            androidx.compose.material3.Slider(
                value = idx.toFloat(),
                onValueChange = { v ->
                    val newIdx = v.toInt().coerceIn(0, 2)
                    val kg = when (newIdx) { 0 -> 0.25; 2 -> 1.0; else -> 0.5 }
                    onSelect(kg)
                },
                valueRange = 0f..2f,
                steps = 1,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = AppColors.Calorie,
                    activeTrackColor = AppColors.Calorie,
                    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
            // Estimated days card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text(
                            stringResource(R.string.onboarding_pace_reach_prefix),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.onboarding_pace_days_format, estimatedDays),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Calorie
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (idx) {
                            0 -> stringResource(R.string.onboarding_pace_caption_slow)
                            2 -> stringResource(R.string.onboarding_pace_caption_fast)
                            else -> stringResource(R.string.onboarding_pace_caption_recommended)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaceIcon(icon: ImageVector, label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) AppColors.Calorie
                   else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (selected) AppColors.Calorie
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
    }
}
