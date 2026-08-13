package app.chompass.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
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
import app.chompass.ui.progress.TimeRange
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppThemeColor

/**
 * App & Display settings: look & feel (appearance, theme color, home display),
 * calendar preferences, and links to the Water and Notifications sub-screens.
 * The heavy water/notification domains live in their own screens now.
 */
@Composable
internal fun SettingsAppSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
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
                SettingRow(
                    stringResource(R.string.settings_week_starts),
                    if (ui.weekStartsOnMonday) stringResource(R.string.settings_week_monday) else stringResource(R.string.settings_week_sunday),
                    icon = Icons.Outlined.CalendarToday
                ) { onOpenSheet(SettingsSheet.WEEK_START) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_customize_progress),
                    stringResource(
                        R.string.settings_customize_progress_summary,
                        stringResource(TimeRange.fromStorageId(ui.progressDefaultRangeId).labelRes),
                        stringResource(
                            if (ui.progressMeasurementSites.isEmpty()) R.string.settings_progress_plots_off
                            else R.string.settings_progress_plots_count,
                            ui.progressMeasurementSites.size
                        )
                    ),
                    icon = Icons.AutoMirrored.Outlined.ShowChart
                ) { nav.navigate(ChompassRoutes.CUSTOMIZE_PROGRESS) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_water_title),
                    if (ui.waterTrackingEnabled) {
                        stringResource(R.string.settings_water_goal_summary, ui.waterDailyGoalMl)
                    } else {
                        stringResource(R.string.settings_off)
                    },
                    icon = Icons.Outlined.WaterDrop,
                ) { nav.navigate(ChompassRoutes.waterRoute("app")) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_notifications),
                    if (ui.notificationsEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
                    icon = Icons.Outlined.Notifications,
                ) { nav.navigate(ChompassRoutes.notificationsRoute("app")) }
    }
}
