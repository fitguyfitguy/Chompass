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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.theme.AppTextOpacity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var invalidGoalWeightMessage by remember { mutableStateOf<String?>(null) }
    var showRebalanceBlockedAlert by remember { mutableStateOf(false) }
    var showAdaptiveLockHint by remember { mutableStateOf(false) }
    var showHealthEnergyGoalsInfo by remember { mutableStateOf(false) }
    var showAdaptiveGoalsInfo by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var healthAvailabilityActionLabel by remember { mutableStateOf<String?>(null) }
    var healthAvailabilityActionIntent by remember {
        mutableStateOf<android.content.Intent?>(null)
    }
    var pendingHealthPermissionAction by remember {
        mutableStateOf<HealthConnectPermissionAction?>(null)
    }
    val healthDeniedMsg = stringResource(R.string.settings_health_denied)
    val activityContext = LocalContext.current

    val healthConnectLauncher = rememberLauncherForActivityResult(
        contract = container.health.permissionRequestContract()
    ) { granted ->
        val action = pendingHealthPermissionAction ?: HealthConnectPermissionAction.ENERGY_GOALS
        pendingHealthPermissionAction = null
        if (granted.any { it in container.health.permissions }) {
            when (action) {
                HealthConnectPermissionAction.SYNC -> vm.setHealthConnectEnabled(true)
                HealthConnectPermissionAction.ENERGY_GOALS -> vm.setHealthEnergyGoalsEnabled(true)
                HealthConnectPermissionAction.BACKGROUND_SYNC -> Unit
            }
        } else {
            permissionDeniedMessage = healthDeniedMsg
            healthAvailabilityActionLabel = null
            healthAvailabilityActionIntent = null
        }
    }

    fun onHealthEnergyGoalsToggle(enabled: Boolean) {
        if (!enabled) {
            vm.setHealthEnergyGoalsEnabled(false)
            return
        }
        if (!container.health.isAvailable()) {
            permissionDeniedMessage =
                activityContext.getString(container.health.unavailableMessageRes())
            val labelRes = container.health.availabilityActionLabelRes()
            healthAvailabilityActionLabel =
                labelRes?.let { activityContext.getString(it) }
            healthAvailabilityActionIntent = container.health.availabilityActionIntent()
            return
        }
        pendingHealthPermissionAction = HealthConnectPermissionAction.ENERGY_GOALS
        healthConnectLauncher.launch(container.health.permissions)
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_section_goals),
        onBack = onBack,
    ) {
        Text(
            stringResource(R.string.settings_goals_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        SettingsGoalsSection(
            ui = ui,
            profile = ui.profile,
            vm = vm,
            nav = nav,
            onOpenSheet = { sheet = it },
            onHealthEnergyGoalsToggle = ::onHealthEnergyGoalsToggle,
            onShowAdaptiveGoalsInfo = { showAdaptiveGoalsInfo = true },
            onShowHealthEnergyGoalsInfo = { showHealthEnergyGoalsInfo = true },
            onShowAdaptiveLockHint = { showAdaptiveLockHint = true },
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = { invalidGoalWeightMessage = it },
            onRebalanceBlocked = { showRebalanceBlockedAlert = true },
        )
    }

    if (showRebalanceBlockedAlert) {
        FudGlassDialog(onDismissRequest = { showRebalanceBlockedAlert = false }) {
            Text(stringResource(R.string.settings_rebalance_blocked_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_rebalance_blocked_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showRebalanceBlockedAlert = false }
            )
        }
    }

    if (showAdaptiveLockHint) {
        FudGlassDialog(onDismissRequest = { showAdaptiveLockHint = false }) {
            Text(stringResource(R.string.settings_adaptive_locks_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_adaptive_locks_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showAdaptiveLockHint = false }
            )
        }
    }

    if (showHealthEnergyGoalsInfo) {
        FudGlassDialog(onDismissRequest = { showHealthEnergyGoalsInfo = false }) {
            Text(stringResource(R.string.settings_energy_goals), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_energy_goals_info),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showHealthEnergyGoalsInfo = false }
            )
        }
    }

    if (showAdaptiveGoalsInfo) {
        FudGlassDialog(onDismissRequest = { showAdaptiveGoalsInfo = false }) {
            Text(stringResource(R.string.settings_adaptive_goals), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_adaptive_goals_info),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showAdaptiveGoalsInfo = false }
            )
        }
    }

    val energyAlertTitle = ui.healthEnergyGoalAlertTitle
    val energyAlertMessage = ui.healthEnergyGoalAlertMessage
    if (energyAlertTitle != null && energyAlertMessage != null) {
        FudGlassDialog(onDismissRequest = { vm.dismissHealthEnergyGoalAlert() }) {
            Text(energyAlertTitle, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                energyAlertMessage,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { vm.dismissHealthEnergyGoalAlert() }
            )
        }
    }

    val adaptiveAlertTitle = ui.adaptiveGoalAlertTitle
    val adaptiveAlertMessage = ui.adaptiveGoalAlertMessage
    if (adaptiveAlertTitle != null && adaptiveAlertMessage != null) {
        FudGlassDialog(onDismissRequest = { vm.dismissAdaptiveGoalAlert() }) {
            Text(adaptiveAlertTitle, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                adaptiveAlertMessage,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { vm.dismissAdaptiveGoalAlert() }
            )
        }
    }

    invalidGoalWeightMessage?.let { msg ->
        FudGlassDialog(onDismissRequest = { invalidGoalWeightMessage = null }) {
            Text(stringResource(R.string.settings_invalid_goal_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { invalidGoalWeightMessage = null }
            )
        }
    }

    permissionDeniedMessage?.let { msg ->
        val actionLabel = healthAvailabilityActionLabel
        val actionIntent = healthAvailabilityActionIntent
        FudGlassDialog(
            onDismissRequest = {
                permissionDeniedMessage = null
                healthAvailabilityActionLabel = null
                healthAvailabilityActionIntent = null
            }
        ) {
            Text(stringResource(R.string.settings_permission_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            if (actionLabel != null && actionIntent != null) {
                FudGlassDialogActions(
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
                FudGlassDialogActions(
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
