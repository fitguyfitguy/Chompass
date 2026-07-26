package app.chompass.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppThemeColor

@Composable
internal fun SettingsAppSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onShowDefaultGramsInfo: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_app)) {
                SettingRow(
                    stringResource(R.string.settings_home_display),
                    "",
                    icon = Icons.Outlined.Dashboard
                ) { nav.navigate(ChompassRoutes.HOME_DISPLAY) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_appearance),
                    when (ui.appearanceMode) {
                        "light" -> stringResource(R.string.settings_appearance_light)
                        "dark" -> stringResource(R.string.settings_appearance_dark)
                        else -> stringResource(R.string.settings_appearance_system)
                    },
                    icon = Icons.Outlined.Brightness6
                ) { onOpenSheet(SettingsSheet.APPEARANCE) }
                HorizontalDivider()
                var themeMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    SettingRow(
                        stringResource(R.string.settings_theme_color),
                        stringResource(ui.appThemeColor.displayNameRes),
                        icon = Icons.Outlined.Palette,
                        inlineMenu = true
                    ) { themeMenuExpanded = true }
                    // Zero-size anchor at the row's trailing edge so the menu drops
                    // under the value text (right side), not the row's left edge.
                    Box(Modifier.align(Alignment.BottomEnd)) {
                        DropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            AppThemeColor.values().forEach { themeColor ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(themeColor.displayNameRes)) },
                                    leadingIcon = { ThemeColorSwatch(themeColor, Modifier.size(22.dp)) },
                                    trailingIcon = if (themeColor == ui.appThemeColor) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = stringResource(R.string.sheet_selected_a11y),
                                                tint = AppColors.Calorie,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        vm.setAppThemeColor(themeColor)
                                        themeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                ToggleRowWithInfo(
                    label = stringResource(R.string.settings_default_to_grams),
                    checked = ui.preferGramsByDefault,
                    icon = Icons.Outlined.LocalDining,
                    onInfo = onShowDefaultGramsInfo,
                    onChange = vm::setPreferGramsByDefault
                )
                HorizontalDivider()
                ToggleRow(
                    stringResource(R.string.settings_water_tracking),
                    ui.waterTrackingEnabled,
                    icon = Icons.Outlined.WaterDrop,
                    onChange = vm::setWaterTrackingEnabled,
                )
                if (ui.waterTrackingEnabled) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_water_goal),
                        stringResource(R.string.settings_water_goal_summary, ui.waterDailyGoalMl),
                        icon = Icons.Outlined.WaterDrop,
                    ) { onOpenSheet(SettingsSheet.WATER_GOAL) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_water_quick_presets),
                        formatWaterQuickPresetsSummary(ui.waterQuickPresetsMl, ui.weightMetric),
                        icon = Icons.Outlined.WaterDrop,
                    ) { onOpenSheet(SettingsSheet.WATER_QUICK_PRESETS) }
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_food_log_sort),
                    stringResource(ui.foodLogSortOrder.displayNameRes),
                    icon = Icons.Filled.UnfoldMore
                ) { onOpenSheet(SettingsSheet.FOOD_LOG_SORT) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_week_starts),
                    if (ui.weekStartsOnMonday) stringResource(R.string.settings_week_monday) else stringResource(R.string.settings_week_sunday),
                    icon = Icons.Outlined.CalendarToday
                ) { onOpenSheet(SettingsSheet.WEEK_START) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_meal_times),
                    stringResource(R.string.settings_meal_times_customize),
                    icon = Icons.Outlined.Schedule,
                ) { onOpenSheet(SettingsSheet.MEAL_TIMES) }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.settings_notifications), ui.notificationsEnabled, icon = Icons.Outlined.Notifications, onChange = onNotificationsToggle)
                if (ui.notificationsEnabled) {
                    HorizontalDivider()
                    NotificationTypeRows(ui = ui, vm = vm)
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_battery_opt),
                        stringResource(R.string.settings_battery_opt_value),
                        icon = Icons.Outlined.BatteryAlert
                    ) { onOpenBatterySettings() }
                }
    }
}
