package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.MealCatalog
import app.chompass.models.MealDef
import app.chompass.models.MealType
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudGlassTextButton
import app.chompass.ui.components.TimeWheelPicker
import app.chompass.ui.home.mealLabel
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

@Composable
internal fun MealTimesSheet(
    current: MealCatalog,
    onDirtyChange: (Boolean) -> Unit = {},
    onSave: (MealCatalog) -> Unit,
) {
    var catalog by remember(current) { mutableStateOf(current.validatedOrDefault()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<MealDef?>(null) }
    var saveError by remember { mutableStateOf(false) }
    // The bottom Save is the only persistence path; the host needs to know
    // when edits would be lost to a drag/back dismiss (#88 second drop path).
    val dirty = catalog != current.validatedOrDefault()
    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    val context = LocalContext.current
    val is24Hour = LocaleFormat.is24Hour(context)

    val selectedId = editingId
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
    if (selectedId == null) {
        Text(
            stringResource(R.string.settings_meals),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_meals_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(16.dp))
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = AppRadii.Container,
            padding = 0.dp,
        ) {
            Column {
                catalog.meals.forEachIndexed { index, def ->
                    key(def.id) {
                        MealCatalogRow(
                            def = def,
                            is24Hour = is24Hour,
                            canMoveUp = index > 0,
                            canMoveDown = index < catalog.meals.lastIndex,
                            onMoveUp = {
                                val ids = catalog.meals.map { it.id }.toMutableList()
                                ids.add(index - 1, ids.removeAt(index))
                                catalog = catalog.reordered(ids)
                            },
                            onMoveDown = {
                                val ids = catalog.meals.map { it.id }.toMutableList()
                                ids.add(index + 1, ids.removeAt(index))
                                catalog = catalog.reordered(ids)
                            },
                            onLabel = { catalog = catalog.withLabel(def.id, it) },
                            onTime = { editingId = def.id },
                            onRemove = { pendingRemove = def },
                        )
                    }
                    if (index != catalog.meals.lastIndex) HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.settings_meals_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(12.dp))
        FudGlassTextButton(
            text = stringResource(R.string.settings_meals_add),
            onClick = {
                if (catalog.meals.size < MealCatalog.MAX_MEALS) {
                    catalog = catalog.addCustom("", catalog.suggestedStartForNew())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Calorie,
        )
        Spacer(Modifier.height(16.dp))
        GradientSaveButton {
            if (catalog.isValid) {
                saveError = false
                onSave(catalog)
            } else {
                saveError = true
            }
        }
        if (saveError) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_meals_save_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FudGlassTextButton(
            text = stringResource(R.string.settings_restore_default_times),
            onClick = { catalog = MealCatalog.Default },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Calorie,
        )
        Spacer(Modifier.height(8.dp))
    } else {
        val def = catalog.def(selectedId) ?: return
        val minutes = def.startMinutes ?: catalog.suggestedStartForNew()
        var selectedMinutes by remember(selectedId, minutes) { mutableIntStateOf(minutes) }
        Text(
            stringResource(R.string.settings_meal_time_edit_format, mealLabel(selectedId)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        TimeWheelPicker(
            minutes = selectedMinutes,
            onChange = { selectedMinutes = it },
            is24Hour = is24Hour,
        )
        Spacer(Modifier.height(16.dp))
        GradientSaveButton {
            catalog = catalog.withStart(selectedId, selectedMinutes)
            editingId = null
        }
        FudGlassTextButton(
            text = stringResource(R.string.action_cancel),
            onClick = { editingId = null },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(8.dp))
    }
    }

    val remove = pendingRemove
    if (remove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.settings_meals_remove_title)) },
            text = { Text(stringResource(R.string.settings_meals_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    catalog = if (remove.isBuiltin) {
                        catalog.withEnabled(remove.id, false)
                    } else {
                        catalog.without(remove.id)
                    }
                    pendingRemove = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MealCatalogRow(
    def: MealDef,
    is24Hour: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLabel: (String) -> Unit,
    onTime: () -> Unit,
    onRemove: () -> Unit,
) {
    var editingName by remember(def.id) { mutableStateOf(false) }
    val shownLabel = catalogRowLabel(def)
    var draft by remember(def.id) { mutableStateOf(def.label.ifBlank { shownLabel }) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up))
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down))
            }
        }
        Column(Modifier.weight(1f)) {
            if (editingName) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        val next = it.take(MealCatalog.MAX_LABEL)
                        draft = next
                        onLabel(next)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onLabel(draft)
                        editingName = false
                    }),
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(AppColors.Calorie),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    onLabel(draft)
                    editingName = false
                }) { Text(stringResource(R.string.action_save)) }
            } else {
                Text(
                    if (def.enabled) shownLabel else stringResource(R.string.settings_meals_hidden, shownLabel),
                    fontSize = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable(onClickLabel = stringResource(R.string.cd_edit_meal_name)) {
                            draft = def.label.ifBlank { shownLabel }
                            editingName = true
                        }
                        .semantics { role = Role.Button },
                )
                val start = def.startMinutes
                Text(
                    if (start != null) formatTime(start, is24Hour) else stringResource(R.string.settings_meals_no_auto),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable(
                            enabled = def.enabled,
                            onClickLabel = stringResource(R.string.cd_set_meal_start),
                        ) { onTime() }
                        .semantics { role = Role.Button },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRemove) { Text(stringResource(R.string.action_delete)) }
    }
}

@Composable
private fun catalogRowLabel(def: MealDef): String {
    val custom = def.label.trim()
    if (custom.isNotEmpty()) return custom
    val builtin = MealType.fromId(def.id)
    return if (builtin != null) stringResource(builtin.displayNameRes) else def.id
}

private fun formatTime(minutes: Int, is24Hour: Boolean): String {
    val time = java.time.LocalTime.of(minutes / 60, minutes % 60)
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return time.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
}
