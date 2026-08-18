package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.DietMode
import app.chompass.models.KetoCarbMode
import app.chompass.models.LocaleFormat
import app.chompass.models.WeightGoal
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.components.gramsText
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppColors
import java.util.Locale
import app.chompass.models.UnitFormat

@Composable
internal fun SettingsGoalsSection(
    ui: SettingsUiState,
    profile: app.chompass.models.UserProfile?,
    vm: SettingsViewModel,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
    onHealthEnergyGoalsToggle: (Boolean) -> Unit,
    onShowAdaptiveGoalsInfo: () -> Unit,
    onShowHealthEnergyGoalsInfo: () -> Unit,
    onShowAdaptiveLockHint: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_goals_how_title)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.settings_goals_how_adaptive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Text(
                stringResource(R.string.settings_goals_how_energy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Text(
                stringResource(R.string.settings_goals_how_lock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
    SectionCard(title = stringResource(R.string.settings_section_goals)) {
                profile?.let { p ->
                    SettingRow(stringResource(R.string.settings_weight_goal), stringResource(p.goal.displayNameRes), icon = Icons.Outlined.Equalizer, inlineMenu = true) { onOpenSheet(SettingsSheet.GOAL) }
                    HorizontalDivider()
                    SettingRow(stringResource(R.string.settings_diet_mode), stringResource(p.dietMode.displayNameRes), icon = Icons.Outlined.Restaurant) { onOpenSheet(SettingsSheet.DIET_MODE) }
                    if (p.dietMode == DietMode.KETO) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_keto_carb_mode),
                            stringResource(p.ketoCarbMode.displayNameRes),
                            icon = Icons.Outlined.Tune
                        ) { onOpenSheet(SettingsSheet.DIET_CARB_MODE) }
                        HorizontalDivider()
                        val carbValue = if (p.ketoCarbMode == KetoCarbMode.MANUAL) {
                            p.ketoCarbManualTarget ?: p.ketoActiveCarbTarget
                        } else p.ketoActiveCarbTarget
                        SettingRow(
                            stringResource(R.string.settings_keto_net_carbs),
                            stringResource(R.string.keto_carb_grams_format, carbValue),
                            icon = Icons.Outlined.Restaurant
                        ) { onOpenSheet(SettingsSheet.DIET_CARB_TARGET) }
                    }
                    HorizontalDivider()
                    ActivityLevelSettingRow(p.activityLevel) { onOpenSheet(SettingsSheet.ACTIVITY) }
                    if (p.goal != WeightGoal.MAINTAIN) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_weekly_change),
                            p.weeklyChangeKg?.let {
                                if (ui.weightMetric) String.format(Locale.US, "%.2f kg/wk", it)
                                else String.format(Locale.US, "%.2f lbs/wk", UnitFormat.kgToLbs(it))
                            } ?: stringResource(R.string.settings_weekly_default),
                            icon = Icons.Outlined.Speed
                        ) { onOpenSheet(SettingsSheet.GOAL_SPEED) }
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_goal_weight),
                            p.goalWeightKg?.let {
                                if (ui.weightMetric) String.format(Locale.US, "%.1f kg", it)
                                else String.format(Locale.US, "%.1f lbs", UnitFormat.kgToLbs(it))
                            } ?: stringResource(R.string.settings_not_set),
                            icon = Icons.AutoMirrored.Outlined.TrendingUp
                        ) { onOpenSheet(SettingsSheet.GOAL_WEIGHT) }
                    }
                    HorizontalDivider()
                    BusyToggleRow(
                        label = stringResource(R.string.settings_adaptive_goals),
                        checked = ui.adaptiveGoalsEnabled,
                        icon = Icons.Outlined.TrackChanges,
                        busy = ui.applyingAdaptiveGoals,
                        onInfo = onShowAdaptiveGoalsInfo,
                        onChange = vm::setAdaptiveGoalsEnabled
                    )
                    HorizontalDivider()
                    BusyToggleRow(
                        label = stringResource(R.string.settings_energy_goals),
                        checked = ui.healthEnergyGoalsEnabled,
                        icon = Icons.Outlined.LocalFireDepartment,
                        busy = ui.recalculatingGoals,
                        onInfo = onShowHealthEnergyGoalsInfo,
                        subtitle = if (!ui.healthConnectEnabled) {
                            stringResource(R.string.settings_needs_health_connect)
                        } else {
                            null
                        },
                        onSubtitleClick = if (!ui.healthConnectEnabled) {
                            { nav.navigate(ChompassRoutes.SETTINGS_DATA) }
                        } else {
                            null
                        },
                        onChange = onHealthEnergyGoalsToggle
                    )
                    HorizontalDivider()
                    // The lock glyph is read-only. Saving a value locks it; the picker's Reset
                    // releases it. While Adaptive Goals is on, tapping a row explains that it owns
                    // the targets (so editing would be overwritten weekly) instead of opening.
                    val lockEnabled = !ui.adaptiveGoalsEnabled
                    val openGoal = { target: SettingsSheet ->
                        if (ui.adaptiveGoalsEnabled) onShowAdaptiveLockHint() else onOpenSheet(target)
                    }
                    LockableGoalRow(
                        label = stringResource(R.string.settings_calories),
                        value = stringResource(R.string.kcal_value_format, LocaleFormat.integer(p.effectiveCalories)),
                        icon = Icons.Outlined.LocalFireDepartment,
                        locked = p.caloriesLocked,
                        lockEnabled = lockEnabled,
                        onClick = { openGoal(SettingsSheet.CALORIES) }
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_protein),
                        value = when {
                            p.proteinTargetMode.usesRate && p.proteinGramsPerKg != null ->
                                stringResource(
                                    R.string.protein_target_g_per_kg_format,
                                    p.proteinGramsPerKg!!,
                                    p.effectiveProtein,
                                )
                            else -> gramsText(p.effectiveProtein.toDouble())
                        },
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Protein,
                        locked = p.isMacroLocked(AutoBalanceMacro.PROTEIN) ||
                            (p.proteinTargetMode.usesRate && p.proteinGramsPerKg != null),
                        lockEnabled = lockEnabled,
                        onClick = { openGoal(SettingsSheet.PROTEIN) }
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_carbs),
                        value = gramsText(p.effectiveCarbs.toDouble()),
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Carbs,
                        locked = p.isMacroLocked(AutoBalanceMacro.CARBS),
                        lockEnabled = lockEnabled,
                        onClick = { openGoal(SettingsSheet.CARBS) }
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_fat),
                        value = gramsText(p.effectiveFat.toDouble()),
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Fat,
                        locked = p.isMacroLocked(AutoBalanceMacro.FAT),
                        lockEnabled = lockEnabled,
                        onClick = { openGoal(SettingsSheet.FAT) }
                    )
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_other_nutrient_goals),
                        optionalNutrientSummary(ui.optionalNutrientGoals),
                        icon = Icons.Outlined.DataUsage
                    ) { nav.navigate(ChompassRoutes.OPTIONAL_NUTRIENT_GOALS) }
                    HorizontalDivider()
                    // Cross-link (Rule A): the water goal is edited on the Water screen.
                    SettingRow(
                        stringResource(R.string.settings_water_goal),
                        stringResource(R.string.settings_water_goal_summary, ui.waterDailyGoalMl),
                        icon = Icons.Outlined.WaterDrop,
                    ) { nav.navigate(ChompassRoutes.waterRoute("goals")) }
                    HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !ui.recalculatingGoals) { vm.recalculateGoals() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FudIconBubble(icon = Icons.Outlined.Refresh, size = 22.dp, iconSize = 14.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(R.string.settings_recalculate_goals),
                            color = if (ui.recalculatingGoals) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                            } else {
                                AppColors.Calorie
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        if (ui.recalculatingGoals) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (ui.goalsNeedRecalc) {
                            // Soft nudge: a goal input changed since the last recalc. A CTA on the
                            // row's right edge, not a wrapped line below it.
                            Text(
                                stringResource(R.string.settings_tap_to_update),
                                color = AppColors.Calorie,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_calc_methods),
                        "",
                        icon = Icons.Outlined.Calculate
                    ) { nav.navigate(ChompassRoutes.CALCULATION_METHODS) }
                }
    }
}
