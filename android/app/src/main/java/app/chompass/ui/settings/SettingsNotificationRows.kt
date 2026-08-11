package app.chompass.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.util.clockTimePattern
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NotificationTypeRows(ui: SettingsUiState, vm: SettingsViewModel, onOpenSheet: (SettingsSheet) -> Unit) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.settings_notification_types),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    ToggleRow(
        stringResource(R.string.settings_notif_food_reminders),
        ui.streakReminderEnabled,
        icon = Icons.Outlined.LocalDining,
        onChange = vm::setStreakReminderEnabled
    )
    HorizontalDivider()
    ToggleRow(
        stringResource(R.string.settings_notif_daily_summary),
        ui.dailySummaryEnabled,
        icon = Icons.Outlined.GraphicEq,
        onChange = vm::setDailySummaryEnabled
    )
    HorizontalDivider()
    ToggleRow(
        stringResource(R.string.settings_notif_weight_reminder),
        ui.weightReminderEnabled,
        icon = Icons.Outlined.MonitorWeight,
        onChange = vm::setWeightReminderEnabled
    )
    HorizontalDivider()
    ToggleRow(
        stringResource(R.string.settings_notif_body_fat_reminder),
        ui.bodyFatReminderEnabled,
        icon = Icons.Outlined.Percent,
        onChange = vm::setBodyFatReminderEnabled
    )
    HorizontalDivider()
    if (ui.waterTrackingEnabled) {
        ToggleRow(
            stringResource(R.string.settings_notif_water_reminder),
            ui.waterReminderEnabled,
            icon = Icons.Outlined.WaterDrop,
            onChange = vm::setWaterReminderEnabled,
        )
        HorizontalDivider()
        if (ui.waterReminderEnabled) {
            SettingRow(
                stringResource(R.string.settings_water_drinking_window),
                drinkingWindowSummary(ui, context),
                icon = Icons.Outlined.Schedule,
            ) { onOpenSheet(SettingsSheet.WATER_REMINDER_PLAN) }
            HorizontalDivider()
        }
    }
    ToggleRow(
        stringResource(R.string.settings_notif_goal_alerts),
        ui.goalReachedNotificationsEnabled,
        icon = Icons.Outlined.TrackChanges,
        onChange = vm::setGoalReachedNotificationsEnabled
    )
    HorizontalDivider()
    ToggleRow(
        stringResource(R.string.settings_notif_app_updates),
        ui.appUpdateNotificationsEnabled,
        icon = Icons.Outlined.SystemUpdate,
        onChange = vm::setAppUpdateNotificationsEnabled
    )
    val noneSelected = !ui.streakReminderEnabled &&
        !ui.dailySummaryEnabled &&
        !ui.weightReminderEnabled &&
        !ui.bodyFatReminderEnabled &&
        (!ui.waterTrackingEnabled || !ui.waterReminderEnabled) &&
        !ui.goalReachedNotificationsEnabled &&
        !ui.appUpdateNotificationsEnabled
    if (noneSelected) {
        Text(
            stringResource(R.string.settings_notif_none_selected),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun drinkingWindowSummary(ui: SettingsUiState, context: android.content.Context): String {
    val formatter = remember(context) {
        DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
    }
    fun fmt(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(formatter)
    return stringResource(
        R.string.settings_water_drinking_window_summary,
        fmt(ui.waterAwakeStartMinutes),
        fmt(ui.waterAwakeEndMinutes),
    )
}
