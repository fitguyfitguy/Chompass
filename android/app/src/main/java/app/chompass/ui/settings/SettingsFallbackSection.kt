package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.AIProvider

@Composable
internal fun SettingsFallbackSection(ui: SettingsUiState, vm: SettingsViewModel, onOpenSheet: (SettingsSheet) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_section_fallback)) {
                ToggleRow(
                    stringResource(R.string.settings_enable_fallback),
                    ui.fallbackEnabled,
                    icon = Icons.Outlined.Refresh,
                    onChange = { vm.setFallbackEnabled(it) }
                )
                if (ui.fallbackEnabled) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_provider),
                        stringResource(ui.fallbackProvider.displayNameRes),
                        icon = Icons.Outlined.SmartToy
                    ) { onOpenSheet(SettingsSheet.FALLBACK_PROVIDER) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_model),
                        ui.fallbackModel.ifEmpty { stringResource(R.string.settings_ai_model_unset) },
                        icon = Icons.Outlined.Tune
                    ) { onOpenSheet(SettingsSheet.FALLBACK_MODEL) }
                    if (ui.fallbackProvider.requiresApiKey) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_api_key),
                            ui.fallbackApiKeyMasked.ifEmpty { stringResource(R.string.settings_not_set) },
                            icon = Icons.Outlined.Key
                        ) { onOpenSheet(SettingsSheet.FALLBACK_KEY) }
                    }
                    if (ui.fallbackProvider.requiresCustomEndpoint || ui.fallbackProvider == AIProvider.OLLAMA) {
                        HorizontalDivider()
                        SettingRow(
                            if (ui.fallbackProvider.requiresCustomEndpoint) stringResource(R.string.settings_base_url) else stringResource(R.string.settings_server_url),
                            stringResource(R.string.settings_tap_to_edit),
                            icon = Icons.Outlined.Link
                        ) { onOpenSheet(SettingsSheet.FALLBACK_BASE_URL) }
                    }
                    SettingFootnote(stringResource(R.string.settings_fallback_footer))
                }
    }
}
