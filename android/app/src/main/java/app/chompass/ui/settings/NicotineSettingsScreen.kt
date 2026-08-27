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
import app.chompass.models.HabitPreset
import app.chompass.models.HabitPresetDomain
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.warning

/**
 * Optional nicotine tracker settings: enable, daily count limit (0 = none),
 * the quick-log chips shown on the Add Food hub, and the preset manager
 * (rename builtins, add/reorder/delete custom products; Codeberg #55
 * follow-up). Nicotine stays local + WebDAV sync only (no Health Connect).
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
    var editingPreset by remember { mutableStateOf<HabitPreset?>(null) }
    var addingPreset by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HabitPreset?>(null) }

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
                    ui.nicotinePresets.presets.forEach { preset ->
                        val selected = ui.nicotineQuickKinds.contains(preset.id)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) {
                                    ui.nicotineQuickKinds.filterNot { it == preset.id }
                                } else {
                                    ui.nicotineQuickKinds + preset.id
                                }
                                vm.setNicotineQuickKinds(next.ifEmpty { HabitPresetDomain.NICOTINE.defaultQuickKindIds })
                            },
                            label = { Text(presetLabel(preset, HabitPresetDomain.NICOTINE)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.18f),
                            ),
                        )
                    }
                }
            }
        }

        TrackerPresetsSection(
            domain = HabitPresetDomain.NICOTINE,
            catalog = ui.nicotinePresets,
            onReorder = vm::setNicotinePresets,
            onEdit = { editingPreset = it },
            onDelete = { pendingDelete = it },
            onAdd = { addingPreset = true },
        )

        SettingFootnote(stringResource(R.string.settings_nicotine_privacy_note))
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = {},
            onRebalanceBlocked = {},
        )
    }

    if (addingPreset || editingPreset != null) {
        NicotinePresetEditorSheet(
            existing = editingPreset,
            onDismiss = { addingPreset = false; editingPreset = null },
            onSave = { label, defaultCount, defaultDoseMg ->
                if (addingPreset) {
                    vm.setNicotinePresets(
                        ui.nicotinePresets.addCustom(
                            HabitPresetDomain.NICOTINE,
                            label,
                            defaultCount = defaultCount,
                            defaultDoseMg = defaultDoseMg,
                        )
                    )
                } else {
                    editingPreset?.let { preset ->
                        vm.setNicotinePresets(
                            ui.nicotinePresets.withPreset(
                                preset.copy(
                                    label = label,
                                    defaultCount = defaultCount,
                                    defaultDoseMg = defaultDoseMg,
                                )
                            )
                        )
                    }
                }
            },
        )
    }

    pendingDelete?.let { preset ->
        TrackerPresetDeleteDialog(
            preset = preset,
            domain = HabitPresetDomain.NICOTINE,
            usageCount = ui.nicotineKindUsage[preset.id] ?: 0,
            onConfirm = {
                vm.deleteNicotinePreset(preset.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
