package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.util.clockTimePattern
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NotificationTypeRows(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    onOpenSheet: (SettingsSheet) -> Unit,
    onOpenWater: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.settings_notification_types),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
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
    if (ui.dailySummaryEnabled) {
        val summaryTime = remember(ui.dailySummaryHour, ui.dailySummaryMinute, context) {
            DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
                .format(LocalTime.of(ui.dailySummaryHour, ui.dailySummaryMinute))
        }
        SettingRow(
            stringResource(R.string.settings_notif_daily_summary_time),
            summaryTime,
            icon = Icons.Outlined.Schedule,
        ) { onOpenSheet(SettingsSheet.DAILY_SUMMARY_TIME) }
    }
    if (ui.dailySummaryEnabled &&
        ui.healthConnectEnabled &&
        ui.healthBackgroundReadAvailable &&
        !ui.healthBackgroundReadGranted
    ) {
        Text(
            stringResource(
                R.string.settings_notif_daily_summary_needs_hc_background,
                stringResource(R.string.settings_group_data),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Calorie,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .clickable(onClick = onOpenHealth),
        )
    }
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
    // Always visible: when water tracking is off this is disabled with a link to
    // the Water screen instead of being hidden (cross-link rule: never hide a
    // dependency — disable it and point at its owner).
    ToggleRow(
        stringResource(R.string.settings_notif_water_reminder),
        ui.waterTrackingEnabled && ui.waterReminderEnabled,
        icon = Icons.Outlined.WaterDrop,
        enabled = ui.waterTrackingEnabled,
        onChange = vm::setWaterReminderEnabled,
    )
    if (!ui.waterTrackingEnabled) {
        Text(
            stringResource(R.string.settings_needs_water_tracking),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Calorie,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .clickable(onClick = onOpenWater),
        )
    }
    HorizontalDivider()
    if (ui.waterReminderEnabled) {
        SettingRow(
            stringResource(R.string.settings_water_drinking_window),
            drinkingWindowSummary(ui, context),
            icon = Icons.Outlined.Schedule,
        ) { onOpenSheet(SettingsSheet.WATER_REMINDER_PLAN) }
        HorizontalDivider()
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
    val waterReminderEffective = ui.waterTrackingEnabled && ui.waterReminderEnabled
    val noneSelected = !ui.streakReminderEnabled &&
        !ui.dailySummaryEnabled &&
        !ui.weightReminderEnabled &&
        !ui.bodyFatReminderEnabled &&
        !waterReminderEffective &&
        !ui.goalReachedNotificationsEnabled &&
        !ui.appUpdateNotificationsEnabled
    if (noneSelected) {
        SettingFootnote(stringResource(R.string.settings_notif_none_selected))
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
