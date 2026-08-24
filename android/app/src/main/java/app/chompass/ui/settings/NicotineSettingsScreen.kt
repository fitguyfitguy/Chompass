package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.NicotineKind
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.warning

/**
 * Optional nicotine tracker settings: enable, daily count limit (0 = none),
 * and the quick-log kinds shown on the Add Food hub. Mirrors the water
 * settings shape; nicotine stays local + WebDAV sync only (no Health Connect).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicotineSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
    from: String,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_nicotine_title),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        Text(
            stringResource(R.string.settings_nicotine_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )

        SectionCard(title = stringResource(R.string.settings_nicotine_section_tracking)) {
            ToggleRow(
                stringResource(R.string.settings_nicotine_tracking),
                ui.nicotineTrackingEnabled,
                icon = Icons.Outlined.FilterAlt,
                onChange = vm::setNicotineTrackingEnabled,
            )
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.nicotine_daily_limit),
                if (ui.nicotineDailyLimit > 0) {
                    stringResource(R.string.nicotine_daily_limit_summary, ui.nicotineDailyLimit)
                } else {
                    stringResource(R.string.nicotine_no_limit)
                },
                icon = Icons.Outlined.FilterAlt,
            ) { sheet = SettingsSheet.NICOTINE_LIMIT }
        }

        SectionCard(title = stringResource(R.string.settings_nicotine_section_quick)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_nicotine_quick_kinds_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NicotineKind.entries.forEach { kind ->
                        val selected = ui.nicotineQuickKinds.contains(kind)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) {
                                    ui.nicotineQuickKinds.filterNot { it == kind }
                                } else {
                                    ui.nicotineQuickKinds + kind
                                }
                                vm.setNicotineQuickKinds(next.ifEmpty { NicotineKind.DefaultQuickKinds })
                            },
                            label = { Text(stringResource(kind.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.18f),
                            ),
                        )
                    }
                }
            }
        }

        SettingFootnote(stringResource(R.string.settings_nicotine_privacy_note))
    }

    when (sheet) {
        SettingsSheet.NICOTINE_LIMIT -> NicotineLimitSheet(
            current = ui.nicotineDailyLimit,
            onSave = {
                vm.setNicotineDailyLimit(it)
                sheet = null
            },
        )
        else -> Unit
    }
}
