package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.AIProvider

@Composable
internal fun SettingsFallbackSection(ui: SettingsUiState, vm: SettingsViewModel, onOpenSheet: (SettingsSheet) -> Unit) {
    // The fallback is a full profile now: its provider (and on-device tradeoffs)
    // come from the referenced profile, not a separate provider slot.
    val fallbackProvider = ui.fallbackProfile?.provider
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
                stringResource(R.string.settings_profile_fallback),
                ui.fallbackProfile?.name ?: stringResource(R.string.settings_profile_fallback_none),
                icon = Icons.Outlined.SmartToy
            ) { onOpenSheet(SettingsSheet.FALLBACK_PROFILE) }
            SettingFootnote(stringResource(R.string.settings_fallback_footer))
            // Codeberg #54: an on-device fallback is a different model, and switching
            // models cold-loads the engine (the resident engine is per-model). Disclose
            // the latency tradeoff where the user decides the fallback slot.
            if (fallbackProvider == AIProvider.ON_DEVICE) {
                SettingFootnote(stringResource(R.string.settings_fallback_on_device_switch_notice))
            }
        }
    }
}
