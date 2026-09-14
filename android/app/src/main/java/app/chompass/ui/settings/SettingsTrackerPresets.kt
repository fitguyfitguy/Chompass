package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.HabitPreset
import app.chompass.models.HabitPresetCatalog
import app.chompass.models.HabitPresetDomain
import app.chompass.models.builtinCaffeineDefaultMg
import app.chompass.models.MilkKind
import app.chompass.models.caffeineKindLabelRes
import app.chompass.ui.components.ChompassPinnedFooterSheet
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.home.SheetReviewToolbar
import app.chompass.ui.home.formatMg
import app.chompass.ui.home.CaffeineMilkSection
import app.chompass.ui.theme.AppTextOpacity

/**
 * Tracker preset manager pieces (custom / renamed caffeine + nicotine
 * presets, Codeberg #55 follow-up): resolved labels, the catalog list rows
 * with reorder handles, and the add/edit bottom sheets. Hosted pieces use
 * [ChompassPinnedFooterSheet] so Save stays measured under the IME.
 */

/** Resolved display label: non-blank override wins, else builtin locale default. */
@Composable
internal fun presetLabel(preset: HabitPreset, domain: HabitPresetDomain): String {
    preset.label.trim().takeIf { it.isNotEmpty() }?.let { return it }
    return stringResource(
        when (domain) {
            HabitPresetDomain.CAFFEINE -> caffeineKindLabelRes(preset.id)
            HabitPresetDomain.NICOTINE -> app.chompass.models.nicotineKindLabelRes(preset.id)
        }
    )
}

/** Preset list section shared by both tracker settings screens. */
@Composable
internal fun TrackerPresetsSection(
    domain: HabitPresetDomain,
    catalog: HabitPresetCatalog,
    onReorder: (HabitPresetCatalog) -> Unit,
    onEdit: (HabitPreset) -> Unit,
    onDelete: (HabitPreset) -> Unit,
    onAdd: () -> Unit,
) {
    val helpRes = when (domain) {
        HabitPresetDomain.CAFFEINE -> R.string.settings_tracker_presets_help_caffeine
        HabitPresetDomain.NICOTINE -> R.string.settings_tracker_presets_help_nicotine
    }
    val addRes = when (domain) {
        HabitPresetDomain.CAFFEINE -> R.string.settings_caffeine_preset_add
        HabitPresetDomain.NICOTINE -> R.string.settings_nicotine_preset_add
    }
    SectionCard(title = stringResource(R.string.settings_tracker_presets_section)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(helpRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
            Spacer(Modifier.height(4.dp))
            catalog.presets.forEachIndexed { index, preset ->
                key(preset.id) {
                    TrackerPresetRow(
                        preset = preset,
                        domain = domain,
                        canMoveUp = index > 0,
                        canMoveDown = index < catalog.presets.lastIndex,
                        onMoveUp = {
                            val ids = catalog.ids().toMutableList()
                            ids.add(index - 1, ids.removeAt(index))
                            onReorder(catalog.reordered(ids))
                        },
                        onMoveDown = {
                            val ids = catalog.ids().toMutableList()
                            ids.add(index + 1, ids.removeAt(index))
                            onReorder(catalog.reordered(ids))
                        },
                        onEdit = { onEdit(preset) },
                        onDelete = { onDelete(preset) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onAdd,
                enabled = catalog.customCount < HabitPresetCatalog.MAX_CUSTOM,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(addRes), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TrackerPresetRow(
    preset: HabitPreset,
    domain: HabitPresetDomain,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onEdit)
            .semantics { role = Role.Button }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                presetLabel(preset, domain),
                fontSize = 17.sp,
            )
            val summary = presetValueSummary(preset, domain)
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (preset.isCustom) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        } else {
            // Built-ins stay toggle-hideable via the quick-kind chips; no delete.
            Text(
                stringResource(R.string.settings_tracker_preset_builtin),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
    }
}

/** Compact "what the +1 chip logs" line: mg / count (+ dose). */
@Composable
private fun presetValueSummary(preset: HabitPreset, domain: HabitPresetDomain): String =
    when (domain) {
        HabitPresetDomain.CAFFEINE ->
            preset.defaultMg?.let { stringResource(R.string.caffeine_mg_value, formatMg(it)) } ?: ""
        HabitPresetDomain.NICOTINE -> when (preset.defaultDoseMg) {
            null -> if (preset.defaultCount > 1) preset.defaultCount.toString() else ""
            else -> stringResource(R.string.nicotine_count_with_mg, preset.defaultCount, formatMg(preset.defaultDoseMg))
        }
    }

/** Delete confirm: entries logged with the preset move to Other, amounts kept. */
@Composable
internal fun TrackerPresetDeleteDialog(
    preset: HabitPreset,
    domain: HabitPresetDomain,
    usageCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tracker_preset_delete_title)) },
        text = {
            Text(
                stringResource(R.string.settings_tracker_preset_delete_named, presetLabel(preset, domain)) + "\n\n" +
                    pluralStringResource(R.plurals.settings_tracker_preset_delete_entries, usageCount, usageCount) + " " +
                    stringResource(R.string.settings_tracker_preset_amounts_kept)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Caffeine preset add/edit sheet: name + default mg wheel (what the +1 chip
 * logs; also the custom-sheet wheel prefill). [existing] = null adds a custom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaffeinePresetEditorSheet(
    existing: HabitPreset?,
    onDismiss: () -> Unit,
    onSave: (label: String, defaultMg: Double, milkKind: String?, milkMl: Int) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    val isAdd = existing == null
    // Renames keep locale-following behavior: storing the same text as the
    // localized builtin default means "no override"; blank builtin labels
    // prefill that default so the sheet opens showing the current name.
    val builtinDefaultLabel = existing?.id?.let { stringResource(caffeineKindLabelRes(it)) } ?: ""
    var name by remember {
        mutableStateOf(
            when {
                existing == null -> ""
                existing.label.isNotBlank() -> existing.label
                else -> builtinDefaultLabel
            }
        )
    }
    val prefillMg = existing?.defaultMg
        ?: existing?.id?.let { builtinCaffeineDefaultMg(it) }
        ?: 65.0
    var mg by remember { mutableStateOf(prefillMg.toInt().coerceIn(5, 500)) }
    var milkKind by remember { mutableStateOf(MilkKind.fromStorage(existing?.milkKind)) }
    var milkMl by remember { mutableStateOf((existing?.milkMl ?: 0).coerceIn(0, MilkKind.MAX_ML)) }

    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        toolbar = {
            SheetReviewToolbar(
                title = stringResource(
                    if (isAdd) R.string.caffeine_preset_editor_title_add else R.string.caffeine_preset_editor_title_edit
                ),
                onCancel = onDismiss,
            )
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.settings_tracker_preset_name),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    FudGlassTextField(
                        value = name,
                        onValueChange = { name = it.take(HabitPresetCatalog.MAX_LABEL) },
                        placeholder = stringResource(R.string.settings_tracker_preset_name),
                        singleLine = true,
                    )
                }

                Text(
                    stringResource(R.string.caffeine_preset_default_mg),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                NumericWheelPicker(
                    value = mg,
                    onValueChange = { mg = it },
                    min = 5,
                    max = 500,
                    step = 5,
                    unit = stringResource(R.string.unit_mg),
                )

                CaffeineMilkSection(
                    milkKind = milkKind,
                    milkMl = milkMl,
                    onKindChange = { milkKind = it },
                    onMlChange = { milkMl = it },
                )
            }
        },
        footer = {
            GradientSaveButton(
                text = stringResource(if (isAdd) R.string.settings_caffeine_preset_add else R.string.action_save),
                enabled = (isAdd && name.isNotBlank()) || !isAdd,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                onClick = {
                    val label = name.trim()
                    onSave(
                        if (!isAdd && existing?.isCustom == false && label == builtinDefaultLabel) "" else label,
                        mg.toDouble(),
                        milkKind?.storageKey.takeIf { milkMl > 0 },
                        milkMl,
                    )
                    onDismiss()
                },
            )
        },
    )
}

/**
 * Nicotine preset add/edit sheet: name + default count + optional per-dose
 * mg wheel (0 = none recorded). [existing] = null adds a custom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NicotinePresetEditorSheet(
    existing: HabitPreset?,
    onDismiss: () -> Unit,
    onSave: (label: String, defaultCount: Int, defaultDoseMg: Double?) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    val isAdd = existing == null
    // Renames keep locale-following behavior: storing the same text as the
    // localized builtin default means "no override"; blank builtin labels
    // prefill that default so the sheet opens showing the current name.
    val builtinDefaultLabel = existing?.id?.let {
        stringResource(app.chompass.models.nicotineKindLabelRes(it))
    } ?: ""
    var name by remember {
        mutableStateOf(
            when {
                existing == null -> ""
                existing.label.isNotBlank() -> existing.label
                else -> builtinDefaultLabel
            }
        )
    }
    var count by remember { mutableStateOf(existing?.defaultCount ?: 1) }
    var doseMg by remember { mutableStateOf(existing?.defaultDoseMg?.toInt() ?: 0) }

    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        toolbar = {
            SheetReviewToolbar(
                title = stringResource(
                    if (isAdd) R.string.nicotine_preset_editor_title_add else R.string.nicotine_preset_editor_title_edit
                ),
                onCancel = onDismiss,
            )
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.settings_tracker_preset_name),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    FudGlassTextField(
                        value = name,
                        onValueChange = { name = it.take(HabitPresetCatalog.MAX_LABEL) },
                        placeholder = stringResource(R.string.settings_tracker_preset_name),
                        singleLine = true,
                    )
                }

                Text(
                    stringResource(R.string.nicotine_preset_default_count),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                NumericWheelPicker(
                    value = count,
                    onValueChange = { count = it },
                    min = 1,
                    max = 20,
                    step = 1,
                )

                Text(
                    stringResource(R.string.nicotine_preset_default_dose),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                NumericWheelPicker(
                    value = doseMg,
                    onValueChange = { doseMg = it },
                    min = 0,
                    max = 30,
                    step = 1,
                    unit = stringResource(R.string.unit_mg),
                )
            }
        },
        footer = {
            GradientSaveButton(
                text = stringResource(if (isAdd) R.string.settings_nicotine_preset_add else R.string.action_save),
                enabled = (isAdd && name.isNotBlank()) || !isAdd,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                onClick = {
                    val label = name.trim()
                    onSave(
                        if (!isAdd && existing?.isCustom == false && label == builtinDefaultLabel) "" else label,
                        count,
                        doseMg.takeIf { it > 0 }?.toDouble(),
                    )
                    onDismiss()
                },
            )
        },
    )
}
