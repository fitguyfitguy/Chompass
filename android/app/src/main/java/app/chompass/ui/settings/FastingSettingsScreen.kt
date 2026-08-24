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
 * enable, goal length (0 = none) with popular-protocol quick picks, the
 * goal-reached notification, and an optional daily start-fast reminder.
 * Local-only — the timer is never synced or exported (unlike water/nicotine).
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
    val startReminderTime = remember(ui.fastingStartReminderHour, ui.fastingStartReminderMinute) {
        LocalTime.of(ui.fastingStartReminderHour, ui.fastingStartReminderMinute).format(timeFormatter)
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
                    if (ui.fastingGoalHours > 0) {
                        stringResource(R.string.settings_fasting_goal_summary, ui.fastingGoalHours)
                    } else {
                        stringResource(R.string.settings_fasting_goal_summary_none)
                    },
                    icon = Icons.Outlined.Schedule,
                ) { sheet = SettingsSheet.FASTING_GOAL }
            }
        }

        if (ui.fastingEnabled) {
            SectionCard(title = stringResource(R.string.settings_fasting_section_reminders)) {
                if (ui.fastingGoalHours > 0) {
                    ToggleRow(
                        stringResource(R.string.settings_fasting_notification),
                        ui.fastingGoalNotificationEnabled,
                        icon = Icons.Outlined.Notifications,
                        onChange = vm::setFastingGoalNotificationEnabled,
                    )
                    HorizontalDivider()
                }
                ToggleRow(
                    stringResource(R.string.settings_fasting_start_reminder),
                    ui.fastingStartReminderEnabled,
                    icon = Icons.Outlined.Notifications,
                    onChange = vm::setFastingStartReminderEnabled,
                )
                if (ui.fastingStartReminderEnabled) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_fasting_start_reminder_time),
                        startReminderTime,
                        icon = Icons.Outlined.Schedule,
                    ) { sheet = SettingsSheet.FASTING_START_TIME }
                    SettingFootnote(stringResource(R.string.settings_fasting_start_reminder_help))
                }
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
