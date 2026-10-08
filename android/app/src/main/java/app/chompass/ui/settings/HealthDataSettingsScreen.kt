package app.chompass.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppTextOpacity

/**
 * Health Connect connection screen: toggle, manage access, background sync and
 * the safety/medical disclosure. Owns the Health Connect permission flows;
 * exports/imports stay on Data & Backup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDataSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()

    var showSafetyMedicalInfo by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var healthAvailabilityActionLabel by remember { mutableStateOf<String?>(null) }
    var healthAvailabilityActionIntent by remember {
        mutableStateOf<android.content.Intent?>(null)
    }
    var pendingHealthPermissionAction by remember {
        mutableStateOf<HealthConnectPermissionAction?>(null)
    }
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthDeniedMsg = stringResource(R.string.settings_health_denied)
    val healthBackgroundDeniedMsg = stringResource(R.string.settings_health_background_denied)
    val healthBackgroundUnsupportedMsg =
        stringResource(R.string.settings_health_background_sync_unsupported)
    val healthManageFailedMsg = stringResource(R.string.settings_health_manage_failed)
    val backgroundSyncSupported = remember { container.health.isBackgroundReadAvailable() }

    val healthConnectLauncher = rememberLauncherForActivityResult(
        contract = container.health.permissionRequestContract()
    ) { granted ->
        val action = pendingHealthPermissionAction ?: HealthConnectPermissionAction.SYNC
        pendingHealthPermissionAction = null
        when (action) {
            HealthConnectPermissionAction.BACKGROUND_SYNC -> {
                if (container.health.backgroundReadPermission in granted) {
                    vm.setHealthBackgroundSyncEnabled(true)
                } else {
                    permissionDeniedMessage = healthBackgroundDeniedMsg
                    healthAvailabilityActionLabel = null
                    healthAvailabilityActionIntent = null
                }
            }
            HealthConnectPermissionAction.SYNC,
            HealthConnectPermissionAction.ENERGY_GOALS -> {
                if (granted.any { it in container.health.permissions }) {
                    when (action) {
                        HealthConnectPermissionAction.SYNC -> vm.setHealthConnectEnabled(true)
                        HealthConnectPermissionAction.ENERGY_GOALS ->
                            vm.setHealthEnergyGoalsEnabled(true)
                        HealthConnectPermissionAction.BACKGROUND_SYNC -> Unit
                    }
                } else {
                    permissionDeniedMessage = healthDeniedMsg
                    healthAvailabilityActionLabel = null
                    healthAvailabilityActionIntent = null
                }
            }
        }
    }

    fun showHealthAvailabilityMessage() {
        permissionDeniedMessage =
            activityContext.getString(container.health.unavailableMessageRes())
        val labelRes = container.health.availabilityActionLabelRes()
        healthAvailabilityActionLabel =
            labelRes?.let { activityContext.getString(it) }
        healthAvailabilityActionIntent = container.health.availabilityActionIntent()
    }

    fun onHealthConnectToggle(enabled: Boolean) {
        if (!enabled) {
            vm.setHealthConnectEnabled(false)
            return
        }
        if (!container.health.isAvailable()) {
            showHealthAvailabilityMessage()
            return
        }
        pendingHealthPermissionAction = HealthConnectPermissionAction.SYNC
        healthConnectLauncher.launch(container.health.permissions)
    }

    fun onBackgroundSyncToggle(enabled: Boolean) {
        if (!enabled) {
            vm.setHealthBackgroundSyncEnabled(false)
            return
        }
        if (!container.health.isBackgroundReadAvailable()) {
            permissionDeniedMessage = healthBackgroundUnsupportedMsg
            healthAvailabilityActionLabel = null
            healthAvailabilityActionIntent = null
            return
        }
        scope.launch {
            if (container.health.hasBackgroundRead()) {
                vm.setHealthBackgroundSyncEnabled(true)
            } else {
                pendingHealthPermissionAction = HealthConnectPermissionAction.BACKGROUND_SYNC
                healthConnectLauncher.launch(setOf(container.health.backgroundReadPermission))
            }
        }
    }

    fun openHealthConnectAccess() {
        runCatching { activityContext.startActivity(container.health.manageAccessIntent()) }
            .onFailure {
                permissionDeniedMessage = healthManageFailedMsg
                healthAvailabilityActionLabel =
                    activityContext.getString(R.string.settings_health_open_settings)
                healthAvailabilityActionIntent =
                    runCatching {
                        android.content.Intent(
                            androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }.getOrNull()
            }
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_section_health),
        onBack = onBack,
    ) {
        SettingsHealthConnectSection(
            ui = ui,
            safetyMedicalExpanded = showSafetyMedicalInfo,
            onToggleSafetyMedical = { showSafetyMedicalInfo = !showSafetyMedicalInfo },
            onHealthConnectToggle = ::onHealthConnectToggle,
            onManageHealthAccess = ::openHealthConnectAccess,
            backgroundSyncSupported = backgroundSyncSupported,
            onBackgroundSyncToggle = ::onBackgroundSyncToggle,
        )
        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_group_data)) {
                    nav.navigate(ChompassRoutes.SETTINGS_DATA)
                },
            ),
        )
    }

    permissionDeniedMessage?.let { msg ->
        val actionLabel = healthAvailabilityActionLabel
        val actionIntent = healthAvailabilityActionIntent
        ChompassDialog(
            onDismissRequest = {
                permissionDeniedMessage = null
                healthAvailabilityActionLabel = null
                healthAvailabilityActionIntent = null
            }
        ) {
            Text(stringResource(R.string.settings_permission_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary))
            if (actionLabel != null && actionIntent != null) {
                ChompassDialogActions(
                    primaryText = actionLabel,
                    onPrimary = {
                        runCatching { activityContext.startActivity(actionIntent) }
                        permissionDeniedMessage = null
                        healthAvailabilityActionLabel = null
                        healthAvailabilityActionIntent = null
                    },
                    dismissText = stringResource(R.string.action_ok),
                    onDismiss = {
                        permissionDeniedMessage = null
                        healthAvailabilityActionLabel = null
                        healthAvailabilityActionIntent = null
                    },
                )
            } else {
                ChompassDialogActions(
                    primaryText = stringResource(R.string.action_ok),
                    onPrimary = {
                        permissionDeniedMessage = null
                        healthAvailabilityActionLabel = null
                        healthAvailabilityActionIntent = null
                    }
                )
            }
        }
    }
}
