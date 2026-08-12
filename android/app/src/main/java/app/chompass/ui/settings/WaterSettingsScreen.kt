package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.WaterGoalBreakdown
import app.chompass.models.WaterGoalCalculator
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.warning
import app.chompass.ui.util.clockTimePattern
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Water settings: tracking, daily goal, dynamic goal (temp/activity/food),
 * quick presets, and a cross-link to water reminders in Notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
    from: String,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_water_title),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        Text(
            stringResource(R.string.settings_water_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        SectionCard(title = stringResource(R.string.settings_water_section_goal)) {
            ToggleRow(
                stringResource(R.string.settings_water_tracking),
                ui.waterTrackingEnabled,
                icon = Icons.Outlined.WaterDrop,
                onChange = vm::setWaterTrackingEnabled,
            )
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_water_goal),
                stringResource(R.string.settings_water_goal_summary, ui.waterDailyGoalMl),
                icon = Icons.Outlined.WaterDrop,
            ) { sheet = SettingsSheet.WATER_GOAL }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_water_quick_presets),
                formatWaterQuickPresetsSummary(ui.waterQuickPresetsMl, ui.weightMetric),
                icon = Icons.Outlined.WaterDrop,
            ) { sheet = SettingsSheet.WATER_QUICK_PRESETS }
        }

        SectionCard(title = stringResource(R.string.settings_water_section_dynamic)) {
            ToggleRow(
                stringResource(R.string.settings_water_dynamic_goal),
                ui.waterDynamicEnabled,
                icon = Icons.Outlined.WaterDrop,
                onChange = vm::setWaterDynamicEnabled,
            )
            SettingFootnote(stringResource(R.string.settings_water_dynamic_goal_help))
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
                ) { sheet = SettingsSheet.WATER_DYNAMIC_BASE }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_water_manual_temp),
                    stringResource(R.string.settings_water_manual_temp_summary, ui.waterManualTempC),
                    icon = Icons.Outlined.Thermostat,
                ) { sheet = SettingsSheet.WATER_MANUAL_TEMP }
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
        }

        SectionCard(title = stringResource(R.string.settings_water_section_reminders)) {
            SettingRow(
                stringResource(R.string.settings_water_reminders),
                waterRemindersSummary(ui),
                icon = Icons.Outlined.Notifications,
            ) { nav.navigate(ChompassRoutes.notificationsRoute("water")) }
        }

        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_section_goals)) {
                    nav.navigate(ChompassRoutes.SETTINGS_GOALS)
                },
                RelatedLink(label = stringResource(R.string.settings_notifications)) {
                    nav.navigate(ChompassRoutes.notificationsRoute("water"))
                },
            ),
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = {},
            onRebalanceBlocked = {},
        )
    }
}

@Composable
private fun waterRemindersSummary(ui: SettingsUiState): String {
    val context = LocalContext.current
    return if (ui.waterReminderEnabled) {
        val formatter = remember(context) {
            DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
        }
        fun fmt(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(formatter)
        stringResource(
            R.string.settings_water_drinking_window_summary,
            fmt(ui.waterAwakeStartMinutes),
            fmt(ui.waterAwakeEndMinutes),
        )
    } else {
        stringResource(R.string.settings_off)
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
