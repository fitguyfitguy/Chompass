package app.chompass.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.sync.SyncRepository
import app.chompass.sync.normalizeWebDavUrl
import app.chompass.ui.navigation.ChompassRoutes

/**
 * Sync settings: WebDAV endpoint form, auto-sync, one-off export/import and
 * Sync Now. Extracted from Health & Data so the data screen stays short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
    from: String,
) {
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUsername by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var webDavAutoSync by remember { mutableStateOf(false) }
    var lastSyncAt by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        webDavUrl = container.prefs.webDavUrl.first()
        webDavUsername = container.prefs.webDavUsername.first()
        webDavPassword = container.keyStore.webDavPassword().orEmpty()
        webDavAutoSync = container.prefs.webDavEnabled.first()
        lastSyncAt = container.prefs.lastSyncAt.first()
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
                    t.localizedMessage ?: activityContext.getString(R.string.error_unknown),
                )
            }
        }
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_sync_section),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        SettingsSyncSection(
            webDavUrl = webDavUrl,
            webDavUsername = webDavUsername,
            webDavPassword = webDavPassword,
            webDavAutoSync = webDavAutoSync,
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
            onWebDavAutoSyncChange = { enabled ->
                webDavAutoSync = enabled
                scope.launch { container.prefs.setWebDavEnabled(enabled) }
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
                            t.localizedMessage ?: activityContext.getString(R.string.error_unknown),
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
                        is SyncRepository.SyncResult.Failed -> {
                            syncMessage = result.message
                        }
                    }
                }
            },
        )

        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_group_data)) {
                    nav.navigate(ChompassRoutes.SETTINGS_DATA)
                },
            ),
        )
    }
}
