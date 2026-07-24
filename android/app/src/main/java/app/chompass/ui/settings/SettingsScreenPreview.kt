package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.WeightGoal
import app.chompass.ui.theme.AppColors
import java.util.Locale
import androidx.compose.material.icons.outlined.Person

/** Static settings layout for release screenshot previews (top profile + goals sections). */
@Composable
internal fun SettingsScreenPreviewContent(
    ui: SettingsUiState,
    latestMeasurementWaistCm: Double? = null,
) {
    val profile = ui.profile

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_section_personal)) {
                profile?.let { p ->
                    SettingRow(stringResource(R.string.settings_gender), stringResource(p.gender.displayNameRes), icon = Icons.Outlined.Person, inlineMenu = true) {}
                    HorizontalDivider()
                    SettingRow(stringResource(R.string.settings_birthday), birthdayDisplay(p), icon = Icons.Outlined.Cake) {}
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_height),
                        if (ui.heightMetric) stringResource(R.string.height_cm_format, p.heightCm.toInt())
                        else feetInchesLabel(p.heightCm.toInt()),
                        icon = Icons.Outlined.Height,
                    ) {}
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_weight),
                        if (ui.weightMetric) String.format(Locale.US, "%.1f kg", p.weightKg)
                        else String.format(Locale.US, "%.1f lbs", p.weightKg * 2.20462),
                        icon = Icons.Outlined.MonitorWeight,
                    ) {}
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_body_fat),
                        p.bodyFatPercentage?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.settings_not_set),
                        icon = Icons.Outlined.Percent,
                    ) {}
                    if (p.bodyFatPercentage != null) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_goal_body_fat),
                            p.goalBodyFatPercentage?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.settings_not_set),
                            icon = Icons.Outlined.TrackChanges,
                        ) {}
                    }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.body_measurements_title),
                        latestMeasurementWaistCm?.let { waist ->
                            if (ui.heightMetric) stringResource(R.string.settings_waist_cm_format, waist)
                            else stringResource(R.string.settings_waist_in_format, waist / 2.54)
                        } ?: stringResource(R.string.settings_not_set),
                        icon = Icons.Outlined.Straighten,
                    ) {}
                }
            }

            SectionCard(title = stringResource(R.string.settings_section_goals)) {
                profile?.let { p ->
                    SettingRow(stringResource(R.string.settings_weight_goal), stringResource(p.goal.displayNameRes), icon = Icons.Outlined.Equalizer, inlineMenu = true) {}
                    HorizontalDivider()
                    SettingRow(stringResource(R.string.settings_diet_mode), stringResource(p.dietMode.displayNameRes), icon = Icons.Outlined.Restaurant) {}
                    HorizontalDivider()
                    ActivityLevelSettingRow(p.activityLevel) {}
                    if (p.goal != WeightGoal.MAINTAIN) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_weekly_change),
                            p.weeklyChangeKg?.let {
                                if (ui.weightMetric) String.format(Locale.US, "%.2f kg/wk", it)
                                else String.format(Locale.US, "%.2f lbs/wk", it * 2.20462)
                            } ?: stringResource(R.string.settings_weekly_default),
                            icon = Icons.Outlined.Speed,
                        ) {}
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_goal_weight),
                            p.goalWeightKg?.let {
                                if (ui.weightMetric) String.format(Locale.US, "%.1f kg", it)
                                else String.format(Locale.US, "%.1f lbs", it * 2.20462)
                            } ?: stringResource(R.string.settings_not_set),
                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        ) {}
                    }
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.settings_calories),
                        value = stringResource(R.string.kcal_value_format, p.effectiveCalories),
                        icon = Icons.Outlined.LocalFireDepartment,
                        locked = p.caloriesLocked,
                        lockEnabled = true,
                        onClick = {},
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_protein),
                        value = "${p.effectiveProtein}g",
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Protein,
                        locked = p.isMacroLocked(AutoBalanceMacro.PROTEIN),
                        lockEnabled = true,
                        onClick = {},
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_carbs),
                        value = "${p.effectiveCarbs}g",
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Carbs,
                        locked = p.isMacroLocked(AutoBalanceMacro.CARBS),
                        lockEnabled = true,
                        onClick = {},
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_fat),
                        value = "${p.effectiveFat}g",
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Fat,
                        locked = p.isMacroLocked(AutoBalanceMacro.FAT),
                        lockEnabled = true,
                        onClick = {},
                    )
                }
            }
        }
    }
}
