package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.util.clockTimePattern
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Optional intermittent-fasting timer settings (docs/local/PLAN_FASTING_TRACKER.md):
 * enable, the fast + eating window (presets or free-form), and two optional
 * lead-time reminders: a break-fast nudge X min before the fast ends and a
 * start nudge X min before the eating window closes. Local-only — the timer is
 * never synced or exported (unlike water/nicotine).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
    from: String,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    val context = LocalContext.current
    val timeFormatter = remember(context) {
        DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
    }
    val startReminderTime = remember(ui.fastingStartHour, ui.fastingStartMinute) {
        LocalTime.of(ui.fastingStartHour, ui.fastingStartMinute).format(timeFormatter)
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_fasting_title),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        Text(
            stringResource(R.string.settings_fasting_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )

        SectionCard(title = stringResource(R.string.settings_fasting_section_tracking)) {
            ToggleRow(
                stringResource(R.string.settings_fasting_tracking),
                ui.fastingEnabled,
                icon = Icons.Outlined.Schedule,
                onChange = vm::setFastingEnabled,
            )
            if (ui.fastingEnabled) {
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_fasting_goal),
                    fastingGoalSummary(ui),
                    icon = Icons.Outlined.Schedule,
                ) { sheet = SettingsSheet.FASTING_GOAL }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_fasting_start_time),
                    startReminderTime,
                    icon = Icons.Outlined.Schedule,
                ) { sheet = SettingsSheet.FASTING_START_TIME }
                SettingFootnote(stringResource(R.string.settings_fasting_start_time_help))
            }
        }

        if (ui.fastingEnabled) {
            SectionCard(title = stringResource(R.string.settings_fasting_section_reminders)) {
                ToggleRow(
                    stringResource(R.string.settings_fasting_end_reminder),
                    ui.fastingGoalNotificationEnabled,
                    icon = Icons.Outlined.Notifications,
                    onChange = vm::setFastingGoalNotificationEnabled,
                )
                if (ui.fastingGoalNotificationEnabled && ui.fastingGoalHours > 0) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_fasting_reminder_lead_end),
                        reminderLeadSummary(ui.fastingEndReminderLeadMinutes),
                        icon = Icons.Outlined.Schedule,
                    ) { sheet = SettingsSheet.FASTING_END_LEAD }
                }
                if (ui.fastingGoalHours > 0) {
                    HorizontalDivider()
                    ToggleRow(
                        stringResource(R.string.settings_fasting_auto_windows),
                        ui.fastingAutoWindows,
                        icon = Icons.Outlined.Schedule,
                        onChange = vm::setFastingAutoWindows,
                    )
                    SettingFootnote(stringResource(R.string.settings_fasting_auto_windows_help))
                }
                if (ui.fastingGoalHours > 0 && (ui.fastingEatHours > 0 || ui.fastingAutoWindows)) {
                    HorizontalDivider()
                    ToggleRow(
                        stringResource(R.string.settings_fasting_start_reminder),
                        ui.fastingStartReminderEnabled,
                        icon = Icons.Outlined.Notifications,
                        onChange = vm::setFastingStartReminderEnabled,
                    )
                    if (ui.fastingStartReminderEnabled) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_fasting_reminder_lead_start),
                            reminderLeadSummary(ui.fastingStartReminderLeadMinutes),
                            icon = Icons.Outlined.Schedule,
                        ) { sheet = SettingsSheet.FASTING_START_LEAD }
                    }
                }
                SettingFootnote(stringResource(R.string.settings_fasting_reminder_help))
            }
        }

        SettingFootnote(stringResource(R.string.fasting_local_only_note))
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
private fun fastingGoalSummary(ui: SettingsUiState): String = when {
    ui.fastingGoalHours <= 0 -> stringResource(R.string.settings_fasting_goal_summary_none)
    ui.fastingEatHours > 0 -> stringResource(
        R.string.settings_fasting_goal_summary_both,
        ui.fastingGoalHours,
        ui.fastingEatHours,
    )
    else -> stringResource(R.string.settings_fasting_goal_summary, ui.fastingGoalHours)
}

@Composable
private fun reminderLeadSummary(leadMinutes: Int): String =
    if (leadMinutes > 0) {
        stringResource(R.string.settings_fasting_reminder_lead_summary, leadMinutes)
    } else {
        stringResource(R.string.settings_fasting_reminder_lead_now)
    }
