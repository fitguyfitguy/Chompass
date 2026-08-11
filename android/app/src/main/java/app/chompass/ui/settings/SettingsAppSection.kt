package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.models.WaterGoalBreakdown
import app.chompass.models.WaterGoalCalculator
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.progress.TimeRange
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.warning
import java.util.Locale

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
                    ToggleRow(
                        stringResource(R.string.settings_water_dynamic_goal),
                        ui.waterDynamicEnabled,
                        icon = Icons.Outlined.WaterDrop,
                        onChange = vm::setWaterDynamicEnabled,
                    )
                    if (ui.waterDynamicEnabled) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_water_dynamic_base),
                            stringResource(
                                if (ui.waterBaseSource == WaterGoalCalculator.BASE_SOURCE_WEIGHT) {
                                    R.string.settings_water_dynamic_base_weight
                                } else {
                                    R.string.settings_water_dynamic_base_manual
                                }
                            ),
                            icon = Icons.Outlined.Scale,
                        ) { onOpenSheet(SettingsSheet.WATER_DYNAMIC_BASE) }
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_water_manual_temp),
                            stringResource(R.string.settings_water_manual_temp_summary, ui.waterManualTempC),
                            icon = Icons.Outlined.Thermostat,
                        ) { onOpenSheet(SettingsSheet.WATER_MANUAL_TEMP) }
                        HorizontalDivider()
                        ToggleRow(
                            stringResource(R.string.settings_water_use_profile_activity),
                            ui.waterUseProfileActivity,
                            icon = Icons.Outlined.DirectionsRun,
                            onChange = vm::setWaterUseProfileActivity,
                        )
                        HorizontalDivider()
                        ToggleRow(
                            stringResource(R.string.settings_water_food_water),
                            ui.waterFoodWaterEnabled,
                            icon = Icons.Outlined.Restaurant,
                            onChange = vm::setWaterFoodWaterEnabled,
                        )
                        HorizontalDivider()
                        WaterDynamicGoalPreviewRow(ui.waterDynamicGoalPreview)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.warning.copy(alpha = 0.09f))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_water_dynamic_warning_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.settings_water_dynamic_warning_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }
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
                    stringResource(R.string.settings_progress_default_range),
                    stringResource(TimeRange.fromStorageId(ui.progressDefaultRangeId).labelRes),
                    icon = Icons.AutoMirrored.Outlined.ShowChart
                ) { onOpenSheet(SettingsSheet.PROGRESS_DEFAULT_RANGE) }
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
                    NotificationTypeRows(ui = ui, vm = vm, onOpenSheet = onOpenSheet)
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_battery_opt),
                        stringResource(R.string.settings_battery_opt_value),
                        icon = Icons.Outlined.BatteryAlert
                    ) { onOpenBatterySettings() }
                }
    }
}

/** Static preview of today's dynamic goal with the input breakdown (no tap target). */
@Composable
internal fun WaterDynamicGoalPreviewRow(preview: WaterGoalBreakdown?) {
    if (preview == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FudIconBubble(icon = Icons.Outlined.WaterDrop, size = 22.dp, iconSize = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.settings_water_dynamic_preview),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(
                    R.string.settings_water_dynamic_preview_breakdown,
                    preview.baseMl,
                    factorText(preview.tempFactor),
                    factorText(preview.activityFactor),
                    preview.foodWaterMl,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Text(
            stringResource(R.string.settings_water_goal_summary, preview.netGoalMl),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

private fun factorText(factor: Double): String = String.format(Locale.getDefault(), "%.1f", factor)
