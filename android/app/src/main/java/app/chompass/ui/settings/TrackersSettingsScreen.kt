package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.navigation.ChompassRoutes

/**
 * Trackers & Reminders: hub for the Water, Nicotine and Reminders sub-screens.
 * They used to be cross-link rows at the bottom of App & Display, mixing
 * trackers with look & feel; now each tracker is one drill-down like every
 * other settings domain, with its live state shown on the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()

    SettingsSubScreen(
        title = stringResource(R.string.settings_group_trackers),
        onBack = onBack,
    ) {
        SectionCard(title = stringResource(R.string.settings_group_trackers)) {
            SettingRow(
                stringResource(R.string.settings_water_title),
                if (ui.waterTrackingEnabled) {
                    stringResource(R.string.settings_water_goal_summary, ui.waterDailyGoalMl)
                } else {
                    stringResource(R.string.settings_off)
                },
                icon = Icons.Outlined.WaterDrop,
            ) { nav.navigate(ChompassRoutes.waterRoute("trackers")) }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_nicotine_title),
                if (ui.nicotineTrackingEnabled) {
                    if (ui.nicotineDailyLimit > 0) {
                        stringResource(R.string.nicotine_daily_limit_summary, ui.nicotineDailyLimit)
                    } else {
                        stringResource(R.string.nicotine_no_limit)
                    }
                } else {
                    stringResource(R.string.settings_off)
                },
                icon = Icons.Outlined.FilterAlt,
            ) { nav.navigate(ChompassRoutes.nicotineRoute("trackers")) }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_caffeine_title),
                if (ui.caffeineTrackingEnabled) {
                    if (ui.caffeineDailyLimitMg > 0) {
                        stringResource(R.string.caffeine_daily_limit_summary, ui.caffeineDailyLimitMg)
                    } else {
                        stringResource(R.string.caffeine_no_limit)
                    }
                } else {
                    stringResource(R.string.settings_off)
                },
                icon = Icons.Outlined.LocalCafe,
            ) { nav.navigate(ChompassRoutes.caffeineRoute("trackers")) }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_notes_title),
                if (ui.dailyNotesEnabled) {
                    stringResource(R.string.settings_on)
                } else {
                    stringResource(R.string.settings_off)
                },
                icon = Icons.Outlined.Notes,
            ) { nav.navigate(ChompassRoutes.notesRoute("trackers")) }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_fasting_title),
                if (ui.fastingEnabled) {
                    when {
                        ui.fastingGoalHours <= 0 -> stringResource(R.string.settings_off)
                        ui.fastingEatHours > 0 -> stringResource(
                            R.string.settings_fasting_goal_summary_both,
                            ui.fastingGoalHours,
                            ui.fastingEatHours,
                        )
                        else -> stringResource(R.string.settings_fasting_goal_summary, ui.fastingGoalHours)
                    }
                } else {
                    stringResource(R.string.settings_off)
                },
                icon = Icons.Outlined.Schedule,
            ) { nav.navigate(ChompassRoutes.fastingRoute("trackers")) }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_notifications),
                if (ui.notificationsEnabled) {
                    stringResource(R.string.settings_on)
                } else {
                    stringResource(R.string.settings_off)
                },
                icon = Icons.Outlined.Notifications,
            ) { nav.navigate(ChompassRoutes.notificationsRoute("trackers")) }
        }
    }
}
