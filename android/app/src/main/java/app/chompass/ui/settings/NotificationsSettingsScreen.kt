package app.chompass.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.navigation.ChompassRoutes

/**
 * Notifications settings: master toggle (with system permission handling),
 * per-type reminder toggles, water drinking window, and battery optimization.
 * Water reminders cross-link to the Water screen when tracking is off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
    from: String,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    val activityContext = LocalContext.current
    val notifDeniedMsg = stringResource(R.string.settings_notifications_denied)

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.setNotificationsEnabled(true)
        else permissionDeniedMessage = notifDeniedMsg
    }

    fun onNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            vm.setNotificationsEnabled(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            vm.setNotificationsEnabled(true)
        } else {
            val granted = ContextCompat.checkSelfPermission(
                activityContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) vm.setNotificationsEnabled(true)
            else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openBatteryOptimizationSettings() {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${activityContext.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                add(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${activityContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        for (intent in intents) {
            if (runCatching { activityContext.startActivity(intent) }.isSuccess) return
        }
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        Text(
            stringResource(R.string.settings_notifications_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        SectionCard(title = stringResource(R.string.settings_section_notifications)) {
            ToggleRow(
                stringResource(R.string.settings_notifications),
                ui.notificationsEnabled,
                icon = Icons.Outlined.Notifications,
                onChange = ::onNotificationsToggle,
            )
            if (ui.notificationsEnabled) {
                HorizontalDivider()
                NotificationTypeRows(
                    ui = ui,
                    vm = vm,
                    onOpenSheet = { sheet = it },
                    onOpenWater = { nav.navigate(ChompassRoutes.waterRoute("notifications")) },
                )
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_battery_opt),
                    stringResource(R.string.settings_battery_opt_value),
                    icon = Icons.Outlined.BatteryAlert
                ) { openBatteryOptimizationSettings() }
            }
        }

        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_water_title)) {
                    nav.navigate(ChompassRoutes.waterRoute("notifications"))
                },
                RelatedLink(label = stringResource(R.string.settings_group_app_display)) {
                    nav.navigate(ChompassRoutes.SETTINGS_APP)
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

    permissionDeniedMessage?.let { msg ->
        FudGlassDialog(onDismissRequest = { permissionDeniedMessage = null }) {
            Text(stringResource(R.string.settings_permission_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { permissionDeniedMessage = null }
            )
        }
    }
}
