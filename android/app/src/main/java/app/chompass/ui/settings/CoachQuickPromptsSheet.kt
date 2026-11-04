package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.CoachQuickPromptEntry
import app.chompass.models.CoachQuickPrompts
import app.chompass.ui.coach.CoachBuiltinPromptLabels
import app.chompass.ui.components.ChompassPinnedFooterSheet
import app.chompass.ui.components.ChompassTextField
import app.chompass.ui.components.LocalPinnedToolbarHeight
import app.chompass.ui.components.glassDialogColor
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.home.SheetReviewToolbar
import java.util.UUID

/**
 * Coach quick-prompt editor (Codeberg #155): active-entry rows with reorder +
 * delete, an add footer (builtin picker or custom fields), and Cancel/Save.
 * Saving an empty list is legal — it pins the strip hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachQuickPromptsSheet(
    initial: List<CoachQuickPromptEntry>,
    onDismiss: () -> Unit,
    onSave: (CoachQuickPrompts) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var entries by remember { mutableStateOf(initial) }
    var showBuiltinPicker by remember { mutableStateOf(false) }
    var showCustomEditor by remember { mutableStateOf(false) }

    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        toolbar = {
            SheetReviewToolbar(
                title = stringResource(R.string.settings_coach_quick_prompts),
                onCancel = onDismiss,
            )
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = LocalPinnedToolbarHeight.current)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.settings_coach_quick_prompts_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_coach_quick_prompts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                entries.forEachIndexed { index, entry ->
                    key(entry.id) {
                        CoachPromptRow(
                            entry = entry,
                            canMoveUp = index > 0,
                            canMoveDown = index < entries.lastIndex,
                            onMove = { delta ->
                                val target = index + delta
                                entries = entries.toMutableList().apply {
                                    add(target, removeAt(index))
                                }
                            },
                            onDelete = { entries = entries.filterNot { it.id == entry.id } },
                        )
                    }
                }
            }
        },
        footer = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { showBuiltinPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_coach_quick_prompts_add_builtin))
                    }
                    TextButton(
                        onClick = { showCustomEditor = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_coach_quick_prompts_add_custom))
                    }
                }
                Spacer(Modifier.height(8.dp))
                CancelSaveRow(onDismiss) { onSave(CoachQuickPrompts(entries)) }
                Spacer(Modifier.height(8.dp))
            }
        },
    )

    if (showBuiltinPicker) {
        CoachBuiltinPromptPickerDialog(
            usedIds = entries.mapTo(HashSet()) { it.id },
            onPick = { id ->
                entries = entries + CoachQuickPromptEntry(id = id)
                showBuiltinPicker = false
            },
            onDismiss = { showBuiltinPicker = false },
        )
    }
    if (showCustomEditor) {
        CoachCustomPromptDialog(
            onAdd = { label, prompt ->
                entries = entries + CoachQuickPromptEntry(
                    id = UUID.randomUUID().toString(),
                    label = label,
                    prompt = prompt,
                )
                showCustomEditor = false
            },
            onDismiss = { showCustomEditor = false },
        )
    }
}

/** Builtin label for a stored entry; custom label wins, id is the last resort. */
@Composable
private fun coachPromptLabel(entry: CoachQuickPromptEntry): String =
    entry.label
        ?: CoachBuiltinPromptLabels[entry.id]?.let { stringResource(it) }
        ?: entry.id

@Composable
private fun CoachPromptRow(
    entry: CoachQuickPromptEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(coachPromptLabel(entry), fontSize = 16.sp)
            if (!entry.isBuiltin) {
                Text(
                    entry.prompt ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
    }
}

/** Simple list dialog: unused builtin chips, tap to append. */
@Composable
private fun CoachBuiltinPromptPickerDialog(
    usedIds: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = CoachBuiltinPromptLabels.keys.filterNot { it in usedIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassDialogColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        title = { Text(stringResource(R.string.settings_coach_quick_prompts_add_builtin)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                available.forEach { id ->
                    TextButton(
                        onClick = { onPick(id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            coachPromptLabel(CoachQuickPromptEntry(id = id)),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Custom prompt fields: label + multiline prompt, saved verbatim. */
@Composable
private fun CoachCustomPromptDialog(
    onAdd: (label: String, prompt: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassDialogColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        title = { Text(stringResource(R.string.settings_coach_quick_prompts_add_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChompassTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = stringResource(R.string.settings_coach_quick_prompts_custom_label),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = {
                        Text(stringResource(R.string.settings_coach_quick_prompts_custom_prompt))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    // Fixed radius: percent corners scale with the box and clip once the prompt grows (FoodSheetPrimitives SheetPillCard precedent).
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(label.trim(), prompt.trim()) },
                enabled = label.isNotBlank() && prompt.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
