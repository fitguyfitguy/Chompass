package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalCafe
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

/**
 * Optional caffeine tracker settings: enable, daily mg limit (0 = none), the
 * quick-log chips shown on the Add Food hub, and the preset manager (rename
 * builtins, add/reorder/delete custom drinks; Codeberg #55 follow-up). The
 * tracker total also includes caffeine from food entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeineSettingsScreen(
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
        title = stringResource(R.string.settings_caffeine_title),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        Text(
            stringResource(R.string.settings_caffeine_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )

        SectionCard(title = stringResource(R.string.settings_caffeine_section_tracking)) {
            ToggleRow(
                stringResource(R.string.settings_caffeine_tracking),
                ui.caffeineTrackingEnabled,
                icon = Icons.Outlined.LocalCafe,
                onChange = vm::setCaffeineTrackingEnabled,
            )
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.caffeine_daily_limit),
                if (ui.optionalNutrientGoals.caffeine > 0) {
                    stringResource(R.string.caffeine_daily_limit_summary, ui.optionalNutrientGoals.caffeine)
                } else {
                    stringResource(R.string.caffeine_no_limit)
                },
                icon = Icons.Outlined.LocalCafe,
            ) { sheet = SettingsSheet.CAFFEINE_LIMIT }
        }

        SectionCard(title = stringResource(R.string.settings_caffeine_section_quick)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_caffeine_quick_kinds_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.caffeinePresets.presets.forEach { preset ->
                        val selected = ui.caffeineQuickKinds.contains(preset.id)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) {
                                    ui.caffeineQuickKinds.filterNot { it == preset.id }
                                } else {
                                    ui.caffeineQuickKinds + preset.id
                                }
                                vm.setCaffeineQuickKinds(next.ifEmpty { HabitPresetDomain.CAFFEINE.defaultQuickKindIds })
                            },
                            label = { Text(presetLabel(preset, HabitPresetDomain.CAFFEINE)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            ),
                        )
                    }
                }
            }
        }

        TrackerPresetsSection(
            domain = HabitPresetDomain.CAFFEINE,
            catalog = ui.caffeinePresets,
            onReorder = vm::setCaffeinePresets,
            onEdit = { editingPreset = it },
            onDelete = { pendingDelete = it },
            onAdd = { addingPreset = true },
        )

        SettingFootnote(stringResource(R.string.settings_caffeine_privacy_note))
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
        CaffeinePresetEditorSheet(
            existing = editingPreset,
            onDismiss = { addingPreset = false; editingPreset = null },
            onSave = { label, defaultMg ->
                if (addingPreset) {
                    vm.setCaffeinePresets(
                        ui.caffeinePresets.addCustom(HabitPresetDomain.CAFFEINE, label, defaultMg = defaultMg)
                    )
                } else {
                    editingPreset?.let { preset ->
                        vm.setCaffeinePresets(
                            ui.caffeinePresets.withPreset(
                                preset.copy(
                                    label = label,
                                    defaultMg = defaultMg,
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
            domain = HabitPresetDomain.CAFFEINE,
            usageCount = ui.caffeineKindUsage[preset.id] ?: 0,
            onConfirm = {
                vm.deleteCaffeinePreset(preset.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
