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
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.theme.AppTextOpacity

/**
 * Calories + macro targets, split out of Goals so that screen stays about the
 * goal profile (weight, diet, activity). Owns the Health Connect permission
 * flow for Energy goals and the macro-lock cap alert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionTargetsSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var showThirdMacroLockAlert by remember { mutableStateOf(false) }
    var showHealthEnergyGoalsInfo by remember { mutableStateOf(false) }
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
        title = stringResource(R.string.settings_section_nutrition_targets),
        onBack = onBack,
    ) {
        SettingsNutritionTargetsSection(
            ui = ui,
            profile = ui.profile,
            vm = vm,
            nav = nav,
            onOpenSheet = { sheet = it },
            onHealthEnergyGoalsToggle = ::onHealthEnergyGoalsToggle,
            onShowHealthEnergyGoalsInfo = { showHealthEnergyGoalsInfo = true },
            onThirdMacroLockBlocked = { showThirdMacroLockAlert = true },
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

    if (showThirdMacroLockAlert) {
        ChompassDialog(onDismissRequest = { showThirdMacroLockAlert = false }) {
            Text(stringResource(R.string.settings_max_pinned_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_max_pinned_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showThirdMacroLockAlert = false }
            )
        }
    }

    if (showHealthEnergyGoalsInfo) {
        ChompassDialog(onDismissRequest = { showHealthEnergyGoalsInfo = false }) {
            Text(stringResource(R.string.settings_energy_goals), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_energy_goals_info),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showHealthEnergyGoalsInfo = false }
            )
        }
    }

    val energyAlertTitle = ui.healthEnergyGoalAlertTitle
    val energyAlertMessage = ui.healthEnergyGoalAlertMessage
    if (energyAlertTitle != null && energyAlertMessage != null) {
        ChompassDialog(onDismissRequest = { vm.dismissHealthEnergyGoalAlert() }) {
            Text(energyAlertTitle, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                energyAlertMessage,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { vm.dismissHealthEnergyGoalAlert() }
            )
        }
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
