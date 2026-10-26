package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.AIProvider
import app.chompass.models.LocaleFormat
import app.chompass.services.FillGapsFeature
import app.chompass.services.ai.OpenAICompatibleClient
import app.chompass.services.ondevice.OnDeviceDownloadState

private fun leftoverGb(bytes: Long): String =
    "${LocaleFormat.decimal(bytes / 1_073_741_824.0, 1)} GB"

/**
 * AI provider wiring: which service, model, key and endpoint are used.
 * Entry-flow behavior (serving sizes, photo note, constituents) lives in
 * Food & Entry — linked from the AI screen footer.
 */
@Composable
internal fun SettingsAiSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    onOpenSheet: (SettingsSheet) -> Unit,
) {
    var confirmDeleteProfile by remember { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.settings_section_ai_profile)) {
                // Phase 2 of Codeberg #20: the master AI-features switch. When
                // off, nothing is sent to any LLM provider (gated at the service
                // choke points) and every AI entry point is hidden.
                ToggleRow(
                    stringResource(R.string.settings_ai_features_master),
                    ui.aiFeaturesEnabled,
                    icon = Icons.Outlined.SmartToy,
                    onChange = { vm.setAiFeaturesEnabled(it) }
                )
                SettingFootnote(stringResource(R.string.settings_ai_features_master_footer))
                HorizontalDivider()
                // Named AI profile: everything below edits the active profile; other
                // profiles keep their own provider, model, endpoint and key.
                SettingRow(
                    stringResource(R.string.settings_ai_profile),
                    "${ui.activeProfile?.name.orEmpty()} · ${stringResource(ui.activeProvider.displayNameRes)}",
                    icon = Icons.Outlined.AccountCircle
                ) { onOpenSheet(SettingsSheet.AI_PROFILE) }
                // Privacy disclosure per provider: cloud providers receive food/chat/
                // profile data; only on-device Gemma 4 keeps everything local.
                SettingFootnote(
                    when (ui.activeProvider) {
                        AIProvider.ON_DEVICE -> stringResource(R.string.settings_ai_privacy_ondevice)
                        AIProvider.OLLAMA -> stringResource(R.string.settings_ai_privacy_ollama)
                        else -> stringResource(R.string.settings_ai_privacy_cloud)
                    }
                )
                HorizontalDivider()
                // Profile management: add / rename / delete. Delete needs a second
                // profile to switch to, so it hides on the last one.
                SettingRow(stringResource(R.string.settings_profile_add), "", icon = Icons.Outlined.Add) { onOpenSheet(SettingsSheet.ADD_PROFILE_NAME) }
                SettingRow(stringResource(R.string.settings_profile_rename), ui.activeProfile?.name.orEmpty(), icon = Icons.Outlined.Edit) { onOpenSheet(SettingsSheet.PROFILE_RENAME) }
                if (ui.profiles.size > 1) {
                    HorizontalDivider()
                    SettingRow(stringResource(R.string.settings_profile_delete), "", icon = Icons.Outlined.Delete) { confirmDeleteProfile = true }
                }
                val downloadManager = vm.container.onDeviceModelDownloadManager
                val storageEpoch by downloadManager.storageEpoch.collectAsState()
                val leftoverBytes = remember(storageEpoch) { downloadManager.occupiedBytes() }
                if (ui.activeProvider == AIProvider.ON_DEVICE) {
                    HorizontalDivider()
                    val downloadState by downloadManager.state(ui.activeModel)
                        .collectAsState(initial = OnDeviceDownloadState.NotDownloaded)
                    val subtitle = when (val s = downloadState) {
                        is OnDeviceDownloadState.Downloaded ->
                            stringResource(R.string.settings_on_device_model_ready)
                        is OnDeviceDownloadState.Downloading ->
                            stringResource(R.string.on_device_model_downloading, s.progressPercent)
                        is OnDeviceDownloadState.Verifying ->
                            stringResource(R.string.on_device_model_verifying)
                        is OnDeviceDownloadState.Failed ->
                            stringResource(R.string.on_device_download_failed)
                        is OnDeviceDownloadState.NotDownloaded ->
                            if (leftoverBytes > 0L) leftoverGb(leftoverBytes)
                            else stringResource(R.string.settings_on_device_model_not_downloaded)
                    }
                    SettingRow(
                        stringResource(R.string.settings_on_device_model),
                        subtitle,
                        icon = Icons.Outlined.Download
                    ) { onOpenSheet(SettingsSheet.ON_DEVICE_MODEL) }
                    SettingFootnote(stringResource(R.string.settings_on_device_accuracy_footer))
                } else if (leftoverBytes > 0L) {
                    // Upgraders who tried a download on an older build, then
                    // switched back to a cloud provider, still need a path to
                    // the leftover `.part` / old-named files.
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_on_device_model),
                        leftoverGb(leftoverBytes),
                        icon = Icons.Outlined.Download
                    ) { onOpenSheet(SettingsSheet.ON_DEVICE_MODEL) }
                    SettingFootnote(
                        stringResource(R.string.on_device_model_leftover_footer, leftoverGb(leftoverBytes))
                    )
                }
    }
    SectionCard(title = stringResource(R.string.settings_section_ai_connection)) {
                // Provider picker: grouped Recommended / Cloud / Local / On-device
                // with one-line subtitles (ListSheet sectionHeader).
                SettingRow(
                    stringResource(R.string.settings_ai_provider),
                    stringResource(ui.activeProvider.displayNameRes),
                    icon = Icons.Outlined.SmartToy
                ) { onOpenSheet(SettingsSheet.AI_PROVIDER) }
                HorizontalDivider()
                SettingRow(stringResource(R.string.settings_ai_model), ui.activeModel.ifEmpty { stringResource(R.string.settings_ai_model_unset) }, icon = Icons.Outlined.Tune) { onOpenSheet(SettingsSheet.AI_MODEL) }
                if (ui.activeProvider.requiresApiKey) {
                    HorizontalDivider()
                    // ✓/✗ mirrors the fresh key-test result from the key sheet.
                    val keySubtitle = ui.apiKeyMasked.ifEmpty { stringResource(R.string.settings_not_set) } +
                        when (ui.apiKeyTestOk) {
                            true -> "  ✓"
                            false -> "  ✗"
                            null -> ""
                        }
                    SettingRow(stringResource(R.string.settings_api_key), keySubtitle, icon = Icons.Outlined.Key) { onOpenSheet(SettingsSheet.API_KEY) }
                }
                if (ui.activeProvider.requiresCustomEndpoint || ui.activeProvider == AIProvider.OLLAMA) {
                    HorizontalDivider()
                    SettingRow(
                        if (ui.activeProvider.requiresCustomEndpoint) stringResource(R.string.settings_base_url) else stringResource(R.string.settings_server_url),
                        ui.activeProfile?.baseUrl.orEmpty().ifEmpty { stringResource(R.string.settings_not_set) },
                        icon = Icons.Outlined.Link
                    ) { onOpenSheet(SettingsSheet.CUSTOM_BASE_URL) }
                    // Issue #8 follow-up: release builds allow cleartext only via this
                    // opt-in; the AI layer rejects http:// URLs while it is off.
                    HorizontalDivider()
                    ToggleRow(
                        stringResource(R.string.settings_ai_allow_insecure_http),
                        ui.allowInsecureHttp,
                        icon = Icons.Outlined.Warning,
                        onChange = { vm.setAllowInsecureHttp(it) }
                    )
                    SettingFootnote(stringResource(R.string.settings_ai_allow_insecure_http_footer))
                }
    }
    SectionCard(title = stringResource(R.string.settings_section_ai_advanced)) {
                // Advanced knobs are stored per profile; subtitles show the
                // effective value with "(default)" while the profile has no override.
                // Max response length is not a setting: every provider gets the
                // built-in 4096 cap (a low cap truncates JSON replies and reads
                // like a model bug), and the read timeout only applies to
                // self-hosted endpoints — cloud budgets are fixed cold-start
                // windows (AiHttp.effectiveReadTimeoutSeconds).
                val showVisionModel = ui.activeProvider != AIProvider.ON_DEVICE
                val showReasoningEffort = OpenAICompatibleClient.usesReasoningEffort(ui.activeProvider)
                val showReadTimeout = ui.activeProvider.usesConfigurableRequestTimeout
                val showGoalTier = ui.activeProvider.usesConfigurableRequestTimeout
                val showGeminiSearch = ui.activeProvider == AIProvider.GEMINI
                val showFillMissing = FillGapsFeature.ENABLED && ui.aiFeaturesEnabled
                // Vision-model slot: routing is provider-generic (resolveModelForRequest),
                // so any non-on-device provider can pin a separate vision model.
                if (showVisionModel) {
                    SettingRow(
                        stringResource(R.string.settings_ai_vision_model),
                        ui.activeProfile?.visionModel.orEmpty().ifEmpty { stringResource(R.string.settings_ai_vision_model_unset) },
                        icon = Icons.Outlined.Tune,
                    ) { onOpenSheet(SettingsSheet.VISION_MODEL) }
                }
                if (showReasoningEffort) {
                    if (showVisionModel) HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_reasoning_effort),
                        stringResource(ui.openRouterReasoningEffort.displayNameRes),
                        icon = Icons.Outlined.Tune,
                    ) { onOpenSheet(SettingsSheet.AI_REASONING_EFFORT) }
                }
                if (showReadTimeout) {
                    if (showVisionModel || showReasoningEffort) HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_read_timeout),
                        if (ui.activeProfile?.readTimeoutSeconds == null) {
                            stringResource(R.string.settings_ai_timeout_default, ui.aiReadTimeoutSeconds)
                        } else {
                            stringResource(R.string.settings_ai_read_timeout_value, ui.aiReadTimeoutSeconds)
                        },
                        icon = Icons.Outlined.Speed
                    ) { onOpenSheet(SettingsSheet.AI_READ_TIMEOUT) }
                }
                // #123: only the user-entered endpoints (Ollama / Custom
                // OpenAI-compatible) host models the AUTO tier rules can't
                // classify by name, so the manual override row shows there.
                if (showGoalTier) {
                    if (showVisionModel || showReasoningEffort || showReadTimeout) HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_goal_tier),
                        stringResource(ui.goalRecalcTier.displayNameRes),
                        icon = Icons.Outlined.Tune,
                    ) { onOpenSheet(SettingsSheet.GOAL_RECALC_TIER) }
                }
                if (showGeminiSearch) {
                    if (showVisionModel || showReasoningEffort || showReadTimeout || showGoalTier) HorizontalDivider()
                    ToggleRow(
                        stringResource(R.string.settings_gemini_google_search),
                        ui.geminiGoogleSearchEnabled,
                        icon = Icons.Outlined.Search,
                        onChange = { vm.setGeminiGoogleSearchEnabled(it) }
                    )
                    SettingFootnote(stringResource(R.string.settings_gemini_google_search_footer))
                }
                // AI fill-missing: reminders toggle (the forever opt-out) and
                // a stable entry point into the gaps sheet — always visible
                // while the feature is on, count shown as-is (incl. 0).
                if (showFillMissing) {
                    val gapScan by vm.container.fillGapsEngine.scan.collectAsState(initial = null)
                    val gapCount = gapScan?.count ?: 0
                    if (showVisionModel || showReasoningEffort || showReadTimeout || showGoalTier || showGeminiSearch) HorizontalDivider()
                    ToggleRow(
                        stringResource(R.string.settings_fill_missing_reminders),
                        ui.fillMissingRemindersEnabled,
                        icon = Icons.Outlined.Notifications,
                        onChange = { vm.setFillMissingRemindersEnabled(it) }
                    )
                    SettingFootnote(stringResource(R.string.settings_fill_missing_reminders_footer))
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_fill_missing_row),
                        gapCount.toString(),
                        icon = Icons.Outlined.Tune
                    ) { onOpenSheet(SettingsSheet.FILL_GAPS) }
                }
    }
    if (confirmDeleteProfile) {
        AlertDialog(
            onDismissRequest = { confirmDeleteProfile = false },
            title = { Text(stringResource(R.string.settings_profile_delete)) },
            text = { Text(stringResource(R.string.settings_profile_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteProfile = false
                    vm.deleteActiveProfile()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteProfile = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
