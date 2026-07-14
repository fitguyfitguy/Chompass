package org.codeberg.fitguy.nofud.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.AutoBalanceMacro
import org.codeberg.fitguy.nofud.models.DietMode
import org.codeberg.fitguy.nofud.models.KetoCarbMode
import org.codeberg.fitguy.nofud.models.ServingUnitInferenceMode
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.codeberg.fitguy.nofud.ui.components.FudIconBubble
import org.codeberg.fitguy.nofud.ui.navigation.NoFUDRoutes
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
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
                ) { nav.navigate(NoFUDRoutes.HOME_DISPLAY) }
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
