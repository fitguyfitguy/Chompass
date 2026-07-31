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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.export.BodyMetricsImportResult
import app.chompass.export.BodyMetricsImporter
import app.chompass.export.DiaryImportResult
import app.chompass.export.DiaryImporter
import app.chompass.sync.SyncRepository
import app.chompass.sync.normalizeWebDavUrl
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    container: AppContainer,
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
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUsername by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var lastSyncAt by remember { mutableStateOf<String?>(null) }
    var showSafetyMedicalInfo by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var pendingHealthPermissionAction by remember {
        mutableStateOf<HealthConnectPermissionAction?>(null)
    }
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthDeniedMsg = stringResource(R.string.settings_health_denied)
    val healthUnavailableMsg = stringResource(R.string.settings_health_unavailable)

    LaunchedEffect(Unit) {
        webDavUrl = container.prefs.webDavUrl.first()
        webDavUsername = container.prefs.webDavUsername.first()
        webDavPassword = container.keyStore.webDavPassword().orEmpty()
        lastSyncAt = container.prefs.lastSyncAt.first()
    }

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

    val importSyncLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = activityContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: ""
                when (val result = container.syncRepository.importDocumentJson(text)) {
                    is SyncRepository.SyncResult.Success ->
                        syncMessage = activityContext.getString(R.string.import_sync_success)
                    is SyncRepository.SyncResult.Failed ->
                        syncMessage = activityContext.getString(R.string.import_sync_failed, result.message)
                }
            }.onFailure { t ->
                syncMessage = activityContext.getString(
                    R.string.import_sync_failed,
                    t.localizedMessage ?: "unknown error",
                )
            }
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
        pendingHealthPermissionAction = HealthConnectPermissionAction.SYNC
        healthConnectLauncher.launch(container.health.permissions)
    }

    fun openHealthConnectAccess() {
        runCatching { activityContext.startActivity(container.health.manageAccessIntent()) }
            .onFailure { permissionDeniedMessage = healthUnavailableMsg }
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_group_data),
        onBack = onBack,
    ) {
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
        SettingsSyncSection(
            webDavUrl = webDavUrl,
            webDavUsername = webDavUsername,
            webDavPassword = webDavPassword,
            lastSyncAt = lastSyncAt,
            syncStatus = syncMessage,
            onWebDavUrlChange = { webDavUrl = it },
            onWebDavUsernameChange = { webDavUsername = it },
            onWebDavPasswordChange = { webDavPassword = it },
            onSaveWebDav = {
                scope.launch {
                    val normalized = normalizeWebDavUrl(webDavUrl)
                    webDavUrl = normalized
                    container.prefs.setWebDavUrl(normalized)
                    container.prefs.setWebDavUsername(webDavUsername)
                    container.keyStore.setWebDavPassword(webDavPassword)
                    syncMessage = activityContext.getString(R.string.settings_webdav_saved)
                }
            },
            onExportSync = {
                scope.launch {
                    runCatching {
                        val content = container.syncRepository.exportDocumentJson()
                        shareExportedFile(
                            context = activityContext,
                            fileName = "Chompass-sync.json",
                            content = content,
                            mimeType = "application/json",
                            chooserTitle = activityContext.getString(R.string.export_sync_title),
                        )
                    }.onFailure { t ->
                        syncMessage = activityContext.getString(
                            R.string.export_sync_failed,
                            t.localizedMessage ?: "unknown error",
                        )
                    }
                }
            },
            onImportSync = { importSyncLauncher.launch(arrayOf("application/json", "text/plain")) },
            onSyncNow = {
                scope.launch {
                    syncMessage = "…"
                    when (val result = container.syncRepository.syncNow()) {
                        is SyncRepository.SyncResult.Success -> {
                            syncMessage = result.message
                            lastSyncAt = container.prefs.lastSyncAt.first()
                        }
                        is SyncRepository.SyncResult.Failed -> syncMessage = result.message
                    }
                }
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
