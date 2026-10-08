package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.models.AutoBalanceMacro
import app.chompass.ui.components.energyText
import app.chompass.ui.components.gramsText
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppColors

/** Editable targets: energy goals, the four macro rows, other nutrient goals. */
@Composable
internal fun SettingsNutritionTargetsSection(
    ui: SettingsUiState,
    profile: app.chompass.models.UserProfile?,
    vm: SettingsViewModel,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
    onHealthEnergyGoalsToggle: (Boolean) -> Unit,
    onShowHealthEnergyGoalsInfo: () -> Unit,
    onThirdMacroLockBlocked: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_nutrition_targets)) {
        profile?.let { p ->
                    BusyToggleRow(
                        label = stringResource(R.string.settings_energy_goals),
                        checked = ui.healthEnergyGoalsEnabled,
                        icon = Icons.Outlined.LocalFireDepartment,
                        // Only show recalc activity here when the recalculation
                        // actually consults Health Connect: measuredEnergyTdeeIfEnabled
                        // returns null (and the prompt skips the measured TDEE) unless
                        // Energy Burn is on AND Health Connect is connected. Without
                        // HC the row just sits there disabled — a spinner would lie.
                        busy = ui.recalculatingGoals &&
                            ui.healthEnergyGoalsEnabled &&
                            ui.healthConnectEnabled,
                        onInfo = onShowHealthEnergyGoalsInfo,
                        subtitle = if (!ui.healthConnectEnabled) {
                            stringResource(R.string.settings_needs_health_connect)
                        } else {
                            null
                        },
                        onSubtitleClick = if (!ui.healthConnectEnabled) {
                            { nav.navigate(ChompassRoutes.SETTINGS_HEALTH) }
                        } else {
                            null
                        },
                        onChange = onHealthEnergyGoalsToggle
                    )
                    HorizontalDivider()
                    // Chip is a real Locked / Auto toggle. Saving a picker value also locks;
                    // picker Reset snaps to auto-balance. Locked rows survive Recalculate
                    // and weekly Adaptive.
                    val openGoal = { target: SettingsSheet -> onOpenSheet(target) }
                    LockableGoalRow(
                        label = stringResource(R.string.settings_calories),
                        value = energyText(p.effectiveCalories),
                        icon = Icons.Outlined.LocalFireDepartment,
                        locked = p.caloriesLocked,
                        onClick = { openGoal(SettingsSheet.CALORIES) },
                        onToggleLock = vm::toggleCaloriesLock,
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
                        onClick = { openGoal(SettingsSheet.PROTEIN) },
                        onToggleLock = {
                            vm.toggleMacroLock(AutoBalanceMacro.PROTEIN, onThirdMacroLockBlocked)
                        },
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_carbs),
                        value = gramsText(p.effectiveCarbs.toDouble()),
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Carbs,
                        locked = p.isMacroLocked(AutoBalanceMacro.CARBS),
                        onClick = { openGoal(SettingsSheet.CARBS) },
                        onToggleLock = {
                            vm.toggleMacroLock(AutoBalanceMacro.CARBS, onThirdMacroLockBlocked)
                        },
                    )
                    HorizontalDivider()
                    LockableGoalRow(
                        label = stringResource(R.string.macro_fat),
                        value = gramsText(p.effectiveFat.toDouble()),
                        icon = Icons.Outlined.DataUsage,
                        iconTint = AppColors.Fat,
                        locked = p.isMacroLocked(AutoBalanceMacro.FAT),
                        onClick = { openGoal(SettingsSheet.FAT) },
                        onToggleLock = {
                            vm.toggleMacroLock(AutoBalanceMacro.FAT, onThirdMacroLockBlocked)
                        },
                    )
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_other_nutrient_goals),
                        optionalNutrientSummary(ui.optionalNutrientGoals),
                        icon = Icons.Outlined.DataUsage
                    ) { nav.navigate(ChompassRoutes.OPTIONAL_NUTRIENT_GOALS) }
        }
    }
}
