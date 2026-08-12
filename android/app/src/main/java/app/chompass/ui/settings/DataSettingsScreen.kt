package app.chompass.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.export.BodyMetricsImportResult
import app.chompass.export.BodyMetricsImporter
import app.chompass.export.DiaryImportResult
import app.chompass.export.DiaryImporter
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.navigation.ChompassRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val profile = ui.profile

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearFoodDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showBodyMetricsExportSheet by remember { mutableStateOf(false) }
    var importDiaryMessage by remember { mutableStateOf<String?>(null) }
    var importBodyMetricsMessage by remember { mutableStateOf<String?>(null) }
    var lastSyncAt by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(Unit) {
        lastSyncAt = container.prefs.lastSyncAt.first()
    }

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
                    t.localizedMessage ?: activityContext.getString(R.string.error_unknown)
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
                    t.localizedMessage ?: activityContext.getString(R.string.error_unknown)
                )
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
        title = stringResource(R.string.settings_group_data),
        onBack = onBack,
    ) {
        SettingsHealthDataSection(
            ui = ui,
            safetyMedicalExpanded = showSafetyMedicalInfo,
            onToggleSafetyMedical = { showSafetyMedicalInfo = !showSafetyMedicalInfo },
            onHealthConnectToggle = ::onHealthConnectToggle,
            onManageHealthAccess = ::openHealthConnectAccess,
            backgroundSyncSupported = backgroundSyncSupported,
            onBackgroundSyncToggle = ::onBackgroundSyncToggle,
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
            onOpenSync = { nav.navigate(ChompassRoutes.syncRoute("data")) },
            syncSummary = lastSyncAt?.let {
                activityContext.getString(R.string.settings_last_sync, it)
            },
        )
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
