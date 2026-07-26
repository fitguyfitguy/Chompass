package app.chompass.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                HorizontalDivider()
                Text(
                    stringResource(ui.selectedSpeech.descriptionRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
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
