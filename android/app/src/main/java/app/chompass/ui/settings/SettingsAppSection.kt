package app.chompass.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.ui.components.ChompassSegmentedTabs
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.progress.TimeRange
import app.chompass.ui.theme.AmbientIntensity
import app.chompass.ui.theme.GlassStrength

/**
 * Display settings: look & feel (appearance, theme color, language, launcher
 * icon) and what Home / Progress show. Trackers (water, nicotine, reminders)
 * live in their own hub group now; Week Starts On moved to Customize Progress.
 */
@Composable
internal fun SettingsAppSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_group_app_display)) {
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
                        "oled" -> stringResource(R.string.settings_appearance_oled)
                        else -> stringResource(R.string.settings_appearance_system)
                    },
                    icon = Icons.Outlined.Brightness6
                ) { onOpenSheet(SettingsSheet.APPEARANCE) }
                HorizontalDivider()
                BusyToggleRow(
                    label = stringResource(R.string.settings_glass_blur),
                    checked = ui.glassBlurEnabled,
                    icon = Icons.Outlined.BlurOn,
                    subtitle = stringResource(R.string.settings_glass_blur_subtitle),
                ) { vm.setGlassBlurEnabled(it) }
                if (ui.glassBlurEnabled) {
                    HorizontalDivider()
                    SegmentedSettingRow(
                        label = stringResource(R.string.settings_frost_strength),
                        options = GlassStrength.entries.toList(),
                        selected = ui.glassStrength,
                        onSelect = vm::setGlassStrength,
                    ) { stringResource(it.labelRes) }
                }
                HorizontalDivider()
                BusyToggleRow(
                    label = stringResource(R.string.settings_ambient_motion),
                    checked = ui.ambientMotionEnabled,
                    icon = Icons.Outlined.AutoAwesome,
                    subtitle = stringResource(R.string.settings_ambient_motion_subtitle),
                ) { vm.setAmbientMotionEnabled(it) }
                if (ui.ambientMotionEnabled) {
                    HorizontalDivider()
                    SegmentedSettingRow(
                        label = stringResource(R.string.settings_ambient_intensity),
                        options = AmbientIntensity.entries.toList(),
                        selected = ui.ambientIntensity,
                        onSelect = vm::setAmbientIntensity,
                    ) { stringResource(it.labelRes) }
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_language_title),
                    when (ui.appLanguage) {
                        "" -> stringResource(R.string.settings_language_system)
                        else -> ui.appLanguage
                    },
                    icon = Icons.Outlined.Language
                ) { onOpenSheet(SettingsSheet.LANGUAGE) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_theme_color),
                    stringResource(ui.appThemeColor.displayNameRes),
                    icon = Icons.Outlined.Palette,
                ) { onOpenSheet(SettingsSheet.THEME_COLOR) }
                HorizontalDivider()
                BusyToggleRow(
                    label = stringResource(R.string.settings_fixed_launcher_icon),
                    checked = ui.fixedLauncherIcon,
                    icon = Icons.Outlined.Star,
                    subtitle = stringResource(R.string.settings_fixed_launcher_icon_subtitle),
                ) { vm.setFixedLauncherIcon(it) }
                HorizontalDivider()
                BusyToggleRow(
                    label = stringResource(R.string.settings_system_date_time_pickers),
                    checked = ui.useSystemDateTimePickers,
                    icon = Icons.Outlined.Schedule,
                    subtitle = stringResource(R.string.settings_system_date_time_pickers_subtitle),
                ) { vm.setUseSystemDateTimePickers(it) }
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
    }
}

/** Inline segmented choice rendered under its toggle row (frost strength, ambient intensity). */
@Composable
private fun <T> SegmentedSettingRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: @Composable (T) -> String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        ChompassSegmentedTabs(
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = labelFor,
        )
    }
}
