package app.chompass.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.services.ondevice.OnDeviceDownloadState

import app.chompass.models.AIProvider
@Composable
internal fun SettingsAiSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    onOpenSheet: (SettingsSheet) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_ai)) {
                SettingRow(stringResource(R.string.settings_ai_provider), stringResource(ui.selectedAI.displayNameRes), icon = Icons.Outlined.SmartToy) { onOpenSheet(SettingsSheet.AI_PROVIDER) }
                HorizontalDivider()
                SettingRow(stringResource(R.string.settings_ai_model), ui.selectedModel.ifEmpty { stringResource(R.string.settings_ai_model_unset) }, icon = Icons.Outlined.Tune) { onOpenSheet(SettingsSheet.AI_MODEL) }
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_on_device_accuracy_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_gemini_google_search_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                HorizontalDivider()
                ToggleRow(
                    stringResource(R.string.settings_portion_clarify),
                    ui.portionClarifyEnabled,
                    icon = Icons.Outlined.Straighten,
                    onChange = { vm.setPortionClarifyEnabled(it) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_portion_clarify_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                HorizontalDivider()
                val constituentsAvailable = ui.selectedAI != AIProvider.ON_DEVICE
                ToggleRow(
                    stringResource(R.string.settings_meal_constituents),
                    checked = constituentsAvailable && ui.mealConstituentsEnabled,
                    icon = Icons.Outlined.Restaurant,
                    enabled = constituentsAvailable,
                    onChange = { vm.setMealConstituentsEnabled(it) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (constituentsAvailable) {
                            R.string.settings_meal_constituents_footer
                        } else {
                            R.string.settings_meal_constituents_footer_on_device
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_serving_unit_mode),
                    stringResource(ui.servingUnitInferenceMode.displayNameRes),
                    icon = Icons.Outlined.Straighten
                ) { onOpenSheet(SettingsSheet.SERVING_UNIT_MODE) }
                if (ui.servingUnitInferenceMode == ServingUnitInferenceMode.HEURISTIC) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_serving_unit_heuristics),
                        stringResource(R.string.settings_tap_to_edit),
                        icon = Icons.Outlined.Tune
                    ) { onOpenSheet(SettingsSheet.SERVING_UNIT_HEURISTICS) }
                }
    }
}
