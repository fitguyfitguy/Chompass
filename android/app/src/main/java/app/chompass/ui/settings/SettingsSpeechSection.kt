package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.chompass.R

@Composable
internal fun SettingsSpeechSection(ui: SettingsUiState, onOpenSheet: (SettingsSheet) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_section_speech)) {
                SettingRow(stringResource(R.string.settings_ai_provider), stringResource(ui.selectedSpeech.displayNameRes), icon = Icons.Outlined.Mic) { onOpenSheet(SettingsSheet.SPEECH_PROVIDER) }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_speech_language),
                    stringResource(ui.selectedSpeechLanguage.displayNameRes),
                    icon = Icons.Outlined.Language
                ) { onOpenSheet(SettingsSheet.SPEECH_LANGUAGE) }
                SettingFootnote(stringResource(ui.selectedSpeech.descriptionRes))
                if (ui.selectedSpeech.requiresApiKey) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_api_key),
                        ui.speechApiKeyMasked.ifEmpty { stringResource(R.string.settings_not_set) },
                        icon = Icons.Outlined.Key
                    ) { onOpenSheet(SettingsSheet.SPEECH_KEY) }
                }
    }
}
