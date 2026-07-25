package app.chompass.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.export.BodyMetricsImportResult
import app.chompass.export.BodyMetricsImporter
import app.chompass.export.DiaryImportResult
import app.chompass.export.DiaryImporter
import app.chompass.ui.about.AboutSettingsRows
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.navigation.BottomNavScrollPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, nav: NavHostController) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val profile = ui.profile
    val latestMeasurement by container.bodyMeasurementRepository.latest.collectAsState(initial = null)

    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearFoodDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showBodyMetricsExportSheet by remember { mutableStateOf(false) }
    var importDiaryMessage by remember { mutableStateOf<String?>(null) }
    var importBodyMetricsMessage by remember { mutableStateOf<String?>(null) }
    var invalidGoalWeightMessage by remember { mutableStateOf<String?>(null) }
    var showMaxPinnedAlert by remember { mutableStateOf(false) }
    var showRebalanceBlockedAlert by remember { mutableStateOf(false) }
    var showAdaptiveLockHint by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var showDefaultGramsInfo by remember { mutableStateOf(false) }
    var showHealthEnergyGoalsInfo by remember { mutableStateOf(false) }
    var showAdaptiveGoalsInfo by remember { mutableStateOf(false) }
    var showSafetyMedicalInfo by remember { mutableStateOf(false) }
    var pendingHealthPermissionAction by remember { mutableStateOf<HealthConnectPermissionAction?>(null) }
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()

    // Notifications: API 33+ requires runtime POST_NOTIFICATIONS. We only flip the
    // pref to true if the user actually grants. Denial leaves the toggle off so
    // the UI never lies about whether notifications can fire.
    val notifDeniedMsg = stringResource(R.string.settings_notifications_denied)
    val healthDeniedMsg = stringResource(R.string.settings_health_denied)
    val healthUnavailableMsg = stringResource(R.string.settings_health_unavailable)

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.setNotificationsEnabled(true)
        else permissionDeniedMessage = notifDeniedMsg
    }

    // Health Connect honors partial grants: any granted permission connects the app, and
    // each direction is gated on its own permission downstream (issue #91). The SYNC toggle
    // accepts any grant; ENERGY_GOALS still needs the energy reads, which its VM re-checks.
    val healthConnectLauncher = rememberLauncherForActivityResult(
        contract = container.health.permissionRequestContract()
    ) { granted ->
        val action = pendingHealthPermissionAction ?: HealthConnectPermissionAction.SYNC
        pendingHealthPermissionAction = null
        if (granted.any { it in container.health.permissions }) {
            when (action) {
                HealthConnectPermissionAction.SYNC -> vm.setHealthConnectEnabled(true)
                HealthConnectPermissionAction.ENERGY_GOALS -> vm.setHealthEnergyGoalsEnabled(true)
            }
        } else {
            permissionDeniedMessage = healthDeniedMsg
        }
    }

    val importDiaryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = activityContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text.isNullOrBlank()) {
                    importDiaryMessage = activityContext.getString(R.string.import_diary_empty)
                    return@runCatching
                }
                when (val result = DiaryImporter.parse(text)) {
                    is DiaryImportResult.Success -> {
                        val imported = container.foodRepository.importEntries(result.entries)
                        if (imported <= 0) {
                            importDiaryMessage = activityContext.getString(R.string.import_diary_empty)
                            return@runCatching
                        }
                        importDiaryMessage = activityContext.getString(
                            R.string.import_diary_success_count,
                            imported
                        )
                    }
                    DiaryImportResult.EmptyPayload -> {
                        importDiaryMessage = activityContext.getString(R.string.import_diary_empty)
                    }
                    is DiaryImportResult.UnsupportedFormat -> {
                        importDiaryMessage = activityContext.getString(
                            R.string.import_diary_unsupported,
                            result.reason,
                        )
                    }
                    is DiaryImportResult.Malformed -> {
                        importDiaryMessage = activityContext.getString(
                            R.string.import_diary_malformed,
                            result.reason
                        )
                    }
                }
            }.onFailure { t ->
                importDiaryMessage = activityContext.getString(
                    R.string.import_diary_failed,
                    t.localizedMessage ?: "unknown error"
                )
            }
        }
    }

    val importBodyMetricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = activityContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text.isNullOrBlank()) {
                    importBodyMetricsMessage = activityContext.getString(R.string.import_body_metrics_empty)
                    return@runCatching
                }
                when (val result = BodyMetricsImporter.parse(text)) {
                    is BodyMetricsImportResult.Success -> {
                        val w = container.weightRepository.importFromFile(result.weights)
                        val f = container.bodyFatRepository.importFromFile(result.bodyFats)
                        val m = container.bodyMeasurementRepository.importFromFile(result.measurements)
                        if (w + f + m <= 0) {
                            importBodyMetricsMessage = activityContext.getString(R.string.import_body_metrics_empty)
                            return@runCatching
                        }
                        importBodyMetricsMessage = activityContext.getString(
                            R.string.import_body_metrics_success, w, f, m
                        )
                    }
                    BodyMetricsImportResult.EmptyPayload -> {
                        importBodyMetricsMessage = activityContext.getString(R.string.import_body_metrics_empty)
                    }
                    BodyMetricsImportResult.UnsupportedFormat -> {
                        importBodyMetricsMessage = activityContext.getString(R.string.import_body_metrics_unsupported)
                    }
                    is BodyMetricsImportResult.Malformed -> {
                        importBodyMetricsMessage = activityContext.getString(
                            R.string.import_body_metrics_malformed, result.reason
                        )
                    }
                }
            }.onFailure { t ->
                importBodyMetricsMessage = activityContext.getString(
                    R.string.import_diary_failed,
                    t.localizedMessage ?: "unknown error"
                )
            }
        }
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

    fun onHealthConnectToggle(enabled: Boolean) {
        if (!enabled) {
            vm.setHealthConnectEnabled(false)
            return
        }
        if (!container.health.isAvailable()) {
            permissionDeniedMessage = healthUnavailableMsg
            return
        }
        // Don't pre-check granted state — Health Connect's contract handles the
        // already-granted case by returning the full set immediately.
        pendingHealthPermissionAction = HealthConnectPermissionAction.SYNC
        healthConnectLauncher.launch(container.health.permissions)
    }

    fun onHealthEnergyGoalsToggle(enabled: Boolean) {
        if (!enabled) {
            vm.setHealthEnergyGoalsEnabled(false)
            return
        }
        if (!container.health.isAvailable()) {
            permissionDeniedMessage = healthUnavailableMsg
            return
        }
        pendingHealthPermissionAction = HealthConnectPermissionAction.ENERGY_GOALS
        healthConnectLauncher.launch(container.health.permissions)
    }

    fun openHealthConnectAccess() {
        runCatching { activityContext.startActivity(container.health.manageAccessIntent()) }
            .onFailure { permissionDeniedMessage = healthUnavailableMsg }
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


    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPersonalSection(
                ui = ui,
                profile = profile,
                latestMeasurement = latestMeasurement,
                nav = nav,
                onOpenSheet = { sheet = it },
            )
            SettingsGoalsSection(
                ui = ui,
                profile = profile,
                vm = vm,
                nav = nav,
                onOpenSheet = { sheet = it },
                onHealthEnergyGoalsToggle = ::onHealthEnergyGoalsToggle,
                onShowAdaptiveGoalsInfo = { showAdaptiveGoalsInfo = true },
                onShowHealthEnergyGoalsInfo = { showHealthEnergyGoalsInfo = true },
                onShowAdaptiveLockHint = { showAdaptiveLockHint = true },
            )
            SettingsAppSection(
                ui = ui,
                vm = vm,
                nav = nav,
                onOpenSheet = { sheet = it },
                onNotificationsToggle = ::onNotificationsToggle,
                onShowDefaultGramsInfo = { showDefaultGramsInfo = true },
                onOpenBatterySettings = ::openBatteryOptimizationSettings,
            )
            SettingsAiSection(
                ui = ui,
                vm = vm,
                onOpenSheet = { sheet = it },
            )
            SettingsCustomInstructionsSection(ui = ui, vm = vm)
            SettingsFallbackSection(
                ui = ui,
                vm = vm,
                onOpenSheet = { sheet = it },
            )
            SettingsSpeechSection(
                ui = ui,
                onOpenSheet = { sheet = it },
            )
            SettingsHealthDataSection(
                ui = ui,
                vm = vm,
                safetyMedicalExpanded = showSafetyMedicalInfo,
                onToggleSafetyMedical = { showSafetyMedicalInfo = !showSafetyMedicalInfo },
                onHealthConnectToggle = ::onHealthConnectToggle,
                onManageHealthAccess = ::openHealthConnectAccess,
                onShowExportDiary = { showExportSheet = true },
                onShowExportBodyMetrics = { showBodyMetricsExportSheet = true },
                onImportDiary = { importDiaryLauncher.launch(arrayOf("application/json", "text/plain")) },
                onImportBodyMetrics = {
                    importBodyMetricsLauncher.launch(
                        arrayOf(
                            "application/json", "text/csv", "text/comma-separated-values",
                            "text/plain", "application/octet-stream"
                        )
                    )
                },
                onShowClearFoodDialog = { showClearFoodDialog = true },
                onShowDeleteDialog = { showDeleteDialog = true },
            )
            SectionCard(title = stringResource(R.string.nav_about)) {
                AboutSettingsRows(container)
            }
            Spacer(Modifier.height(BottomNavScrollPadding))
        }
    }


    if (showExportSheet) {
        ExportDiarySheet(
            container = container,
            profile = profile,
            onDismiss = { showExportSheet = false },
        )
    }
    if (showBodyMetricsExportSheet) {
        ExportBodyMetricsSheet(
            container = container,
            onDismiss = { showBodyMetricsExportSheet = false },
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = { invalidGoalWeightMessage = it },
            onRebalanceBlocked = { showRebalanceBlockedAlert = true }
        )
    }

    if (showClearFoodDialog) {
        FudGlassDialog(onDismissRequest = { showClearFoodDialog = false }) {
            Text(stringResource(R.string.settings_clear_food_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_clear_food_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_clear),
                onPrimary = {
                    vm.clearFoodLog()
                    showClearFoodDialog = false
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { showClearFoodDialog = false },
                destructive = true
            )
        }
    }

    if (showDeleteDialog) {
        val context = LocalContext.current
        FudGlassDialog(onDismissRequest = { showDeleteDialog = false }) {
            Text(stringResource(R.string.settings_delete_all_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_delete_all_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_delete),
                onPrimary = {
                    vm.deleteAllData {
                        showDeleteDialog = false
                        (context as? android.app.Activity)?.recreate()
                    }
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { showDeleteDialog = false },
                destructive = true
            )
        }
    }

    if (showMaxPinnedAlert) {
        FudGlassDialog(onDismissRequest = { showMaxPinnedAlert = false }) {
            Text(stringResource(R.string.settings_max_pinned_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_max_pinned_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showMaxPinnedAlert = false }
            )
        }
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

    if (showDefaultGramsInfo) {
        FudGlassDialog(onDismissRequest = { showDefaultGramsInfo = false }) {
            Text(stringResource(R.string.settings_default_to_grams), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_default_to_grams_info),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showDefaultGramsInfo = false }
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
        FudGlassDialog(onDismissRequest = { permissionDeniedMessage = null }) {
            Text(stringResource(R.string.settings_permission_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { permissionDeniedMessage = null }
            )
        }
    }

    importDiaryMessage?.let { msg ->
        FudGlassDialog(onDismissRequest = { importDiaryMessage = null }) {
            Text(stringResource(R.string.import_diary_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { importDiaryMessage = null }
            )
        }
    }

    importBodyMetricsMessage?.let { msg ->
        FudGlassDialog(onDismissRequest = { importBodyMetricsMessage = null }) {
            Text(stringResource(R.string.import_body_metrics_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { importBodyMetricsMessage = null }
            )
        }
    }
}
