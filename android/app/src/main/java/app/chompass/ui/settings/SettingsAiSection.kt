package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.models.AIProvider
import app.chompass.services.ondevice.OnDeviceDownloadState
import app.chompass.ui.navigation.ChompassRoutes

/**
 * AI & Speech provider wiring: which service, model, key and endpoint are
 * used. Entry-flow behavior (serving sizes, photo note, portion clarify,
 * constituents) lives in Food & Entry — linked from here.
 */
@Composable
internal fun SettingsAiSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_ai)) {
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
                // Phase 1 of the master AI-off switch (Codeberg #20): hide the
                // coach tab wholesale without touching any data path. The master
                // switch above covers it too, so the row only shows while AI is on.
                if (ui.aiFeaturesEnabled) {
                    ToggleRow(
                        stringResource(R.string.settings_show_coach_tab),
                        ui.coachTabEnabled,
                        icon = Icons.Outlined.Forum,
                        onChange = { vm.setCoachTabEnabled(it) }
                    )
                    SettingFootnote(stringResource(R.string.settings_show_coach_tab_footer))
                    HorizontalDivider()
                }
                SettingRow(stringResource(R.string.settings_ai_provider), stringResource(ui.selectedAI.displayNameRes), icon = Icons.Outlined.SmartToy) { onOpenSheet(SettingsSheet.AI_PROVIDER) }
                // Privacy disclosure per provider: cloud providers receive food/chat/
                // profile data; only on-device Gemma 4 keeps everything local.
                SettingFootnote(
                    when (ui.selectedAI) {
                        AIProvider.ON_DEVICE -> stringResource(R.string.settings_ai_privacy_ondevice)
                        AIProvider.OLLAMA -> stringResource(R.string.settings_ai_privacy_ollama)
                        else -> stringResource(R.string.settings_ai_privacy_cloud)
                    }
                )
                HorizontalDivider()
                SettingRow(stringResource(R.string.settings_ai_model), ui.selectedModel.ifEmpty { stringResource(R.string.settings_ai_model_unset) }, icon = Icons.Outlined.Tune) { onOpenSheet(SettingsSheet.AI_MODEL) }
                // Vision-model slot: OpenAI-compatible hosts are where text-only
                // ids can be picked/typed; curated Gemini/OpenAI/Anthropic lineups
                // are all multimodal (#195).
                if (ui.selectedAI.apiFormat == AIProvider.ApiFormat.OPENAI_COMPATIBLE) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_vision_model),
                        ui.visionModel.ifEmpty { stringResource(R.string.settings_ai_vision_model_unset) },
                        icon = Icons.Outlined.Tune,
                    ) { onOpenSheet(SettingsSheet.VISION_MODEL) }
                }
                val showReasoningEffort = ui.selectedAI == AIProvider.OPENROUTER ||
                    (ui.fallbackEnabled && ui.fallbackProvider == AIProvider.OPENROUTER)
                if (showReasoningEffort) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_reasoning_effort),
                        stringResource(ui.openRouterReasoningEffort.displayNameRes),
                        icon = Icons.Outlined.Tune,
                    ) { onOpenSheet(SettingsSheet.OPENROUTER_REASONING) }
                }
                if (ui.selectedAI.requiresApiKey) {
                    HorizontalDivider()
                    SettingRow(stringResource(R.string.settings_api_key), ui.apiKeyMasked.ifEmpty { stringResource(R.string.settings_not_set) }, icon = Icons.Outlined.Key) { onOpenSheet(SettingsSheet.API_KEY) }
                }
                if (ui.selectedAI.requiresCustomEndpoint || ui.selectedAI == AIProvider.OLLAMA) {
                    HorizontalDivider()
                    SettingRow(
                        if (ui.selectedAI.requiresCustomEndpoint) stringResource(R.string.settings_base_url) else stringResource(R.string.settings_server_url),
                        stringResource(R.string.settings_tap_to_edit),
                        icon = Icons.Outlined.Link
                    ) { onOpenSheet(SettingsSheet.CUSTOM_BASE_URL) }
                }
                if (ui.selectedAI == AIProvider.ON_DEVICE) {
                    HorizontalDivider()
                    val downloadState by vm.container.onDeviceModelDownloadManager.state(ui.selectedModel)
                        .collectAsState(initial = OnDeviceDownloadState.NotDownloaded)
                    val ready = downloadState is OnDeviceDownloadState.Downloaded
                    SettingRow(
                        stringResource(R.string.settings_on_device_model),
                        if (ready) stringResource(R.string.settings_on_device_model_ready) else stringResource(R.string.settings_on_device_model_not_downloaded),
                        icon = Icons.Outlined.Download
                    ) { onOpenSheet(SettingsSheet.ON_DEVICE_MODEL) }
                    SettingFootnote(stringResource(R.string.settings_on_device_accuracy_footer))
                }
                // Only OpenAI-compatible + Anthropic send a token cap; Gemini is left
                // uncapped and on-device dispatch doesn't take one at all, so hide this
                // for both.
                if (ui.selectedAI.apiFormat != AIProvider.ApiFormat.GEMINI && ui.selectedAI != AIProvider.ON_DEVICE) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_max_tokens),
                        ui.maxResponseTokens.toString(),
                        icon = Icons.Outlined.Numbers
                    ) { onOpenSheet(SettingsSheet.MAX_TOKENS) }
                }
                if (ui.selectedAI != AIProvider.ON_DEVICE) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_read_timeout),
                        stringResource(R.string.settings_ai_read_timeout_value, ui.aiReadTimeoutSeconds),
                        icon = Icons.Outlined.Speed
                    ) { onOpenSheet(SettingsSheet.AI_READ_TIMEOUT) }
                }
                val showGeminiSearch = ui.selectedAI == AIProvider.GEMINI ||
                    (ui.fallbackEnabled && ui.fallbackProvider == AIProvider.GEMINI)
                if (showGeminiSearch) {
                    HorizontalDivider()
                    ToggleRow(
                        stringResource(R.string.settings_gemini_google_search),
                        ui.geminiGoogleSearchEnabled,
                        icon = Icons.Outlined.Search,
                        onChange = { vm.setGeminiGoogleSearchEnabled(it) }
                    )
                    SettingFootnote(stringResource(R.string.settings_gemini_google_search_footer))
                }
                HorizontalDivider()
                // Cross-link (Rule A): serving-size behavior is edited in Food & Entry.
                SettingRow(
                    stringResource(R.string.settings_serving_unit_mode),
                    stringResource(ui.servingUnitInferenceMode.displayNameRes),
                    icon = Icons.Outlined.Tune,
                ) { nav.navigate(ChompassRoutes.SETTINGS_FOOD) }
    }
}
