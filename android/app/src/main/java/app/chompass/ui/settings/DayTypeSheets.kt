package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.DayTargets
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroDayProfile
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
import java.util.UUID

/**
 * Profile picker sheet for the Day types schedule (#60 phase 2): one row per
 * profile, optional "Default (X)" / "Follow schedule" pseudo-entries for
 * weekday + day-override hosts. Mirrors the ListSheet row style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayTypePickerSheet(
    title: String,
    profiles: List<MacroDayProfile>,
    selectedId: String?,
    onSelect: (MacroDayProfile) -> Unit,
    onDismiss: () -> Unit,
    defaultOption: String? = null,
    onSelectDefault: (() -> Unit)? = null,
    followScheduleOption: Boolean = false,
    onSelectFollowSchedule: (() -> Unit)? = null,
) {
    ChompassBottomSheet(onDismiss = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (defaultOption != null && onSelectDefault != null) {
                val selected = selectedId == null
                DayTypePickerRow(
                    label = stringResource(R.string.settings_day_types_weekday_default, defaultOption),
                    subtitle = null,
                    selected = selected,
                    onClick = onSelectDefault,
                )
            }
            if (followScheduleOption && onSelectFollowSchedule != null) {
                DayTypePickerRow(
                    label = stringResource(R.string.settings_day_types_follow_schedule),
                    subtitle = null,
                    selected = false,
                    onClick = onSelectFollowSchedule,
                )
            }
            profiles.forEach { p ->
                DayTypePickerRow(
                    label = p.name,
                    subtitle = stringResource(
                        R.string.day_type_targets_summary,
                        LocaleFormat.integer(p.calories),
                        p.proteinG,
                        p.carbsG,
                        p.fatG,
                    ),
                    selected = p.id == selectedId,
                    onClick = { onSelect(p) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun DayTypePickerRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.Field))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.sheet_selected_a11y),
                tint = AppColors.Calorie,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Add / edit one day-type profile (#60 phase 2): name field + the same wheel
 * primitives as the goal pickers (calories floored at the user's CalorieSafety
 * floor — MACRO-CYCLE-C — with a warning when the target sits at the floor),
 * "Copy from current goals" seeding, and delete with the referenced-profile
 * replace/scrub prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayTypeProfileEditorSheet(
    existing: List<MacroDayProfile>?,
    editing: MacroDayProfile?,
    base: DayTargets?,
    calorieFloor: Int,
    calorieCeiling: Int,
    isReferenced: Boolean,
    replacementOptions: List<String>,
    onSave: (MacroDayProfile) -> Unit,
    onDelete: (replacementName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val seed = editing?.let { DayTargets(it.calories, it.proteinG, it.carbsG, it.fatG) } ?: base
    var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
    var calories by remember(editing) {
        mutableIntStateOf((seed?.calories ?: 2400).coerceIn(calorieFloor, calorieCeiling))
    }
    var protein by remember(editing) { mutableIntStateOf((seed?.proteinG ?: 150).coerceIn(0, 400)) }
    var carbs by remember(editing) { mutableIntStateOf((seed?.carbsG ?: 250).coerceIn(0, 800)) }
    var fat by remember(editing) { mutableIntStateOf((seed?.fatG ?: 70).coerceIn(10, 300)) }
    var confirmDelete by remember { mutableStateOf(false) }

    val nameTaken = existing.orEmpty().any {
        it.id != editing?.id && it.name.trim().equals(name.trim(), ignoreCase = true)
    }
    val canSave = name.isNotBlank() && !nameTaken

    ChompassBottomSheet(onDismiss = onDismiss) {
        Text(
            stringResource(
                if (editing != null) R.string.settings_day_types_title else R.string.settings_day_types_add
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))
        // Wheels scroll (4 of them exceed the sheet on small screens / large
        // font scale — the WaterQuickPresetsSheet pattern); name + Save stay
        // pinned and reachable. 18dp side padding matches the sheet title so
        // text lines never hug the screen edge.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            FudGlassTextField(
                value = name,
                onValueChange = { if (it.length <= 32) name = it },
                placeholder = stringResource(R.string.settings_day_types_name_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            if (nameTaken) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_day_types_name_taken),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Calorie,
                )
            }
            Spacer(Modifier.height(12.dp))
            EditorWheelLabel(stringResource(R.string.macro_calories), unit = stringResource(R.string.unit_kcal))
            NumericWheelPicker(
                value = calories,
                onValueChange = { calories = it },
                min = calorieFloor,
                max = calorieCeiling,
                unit = stringResource(R.string.unit_kcal),
                step = 50,
            )
            if (calories <= calorieFloor) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_day_types_floor_warning, calorieFloor),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Calorie,
                )
            }
            Spacer(Modifier.height(12.dp))
            EditorWheelLabel(stringResource(R.string.macro_protein), unit = stringResource(R.string.unit_g))
            NumericWheelPicker(
                value = protein,
                onValueChange = { protein = it },
                min = 0,
                max = 400,
                unit = stringResource(R.string.unit_g),
                step = 5,
            )
            Spacer(Modifier.height(12.dp))
            EditorWheelLabel(stringResource(R.string.macro_carbs), unit = stringResource(R.string.unit_g))
            NumericWheelPicker(
                value = carbs,
                onValueChange = { carbs = it },
                min = 0,
                max = 800,
                unit = stringResource(R.string.unit_g),
                step = 5,
            )
            Spacer(Modifier.height(12.dp))
            EditorWheelLabel(stringResource(R.string.macro_fat), unit = stringResource(R.string.unit_g))
            NumericWheelPicker(
                value = fat,
                onValueChange = { fat = it },
                min = 10,
                max = 300,
                unit = stringResource(R.string.unit_g),
                step = 5,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (base != null) {
            TextButton(
                onClick = {
                    calories = base.calories.coerceIn(calorieFloor, calorieCeiling)
                    protein = base.proteinG.coerceIn(0, 400)
                    carbs = base.carbsG.coerceIn(0, 800)
                    fat = base.fatG.coerceIn(10, 300)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(stringResource(R.string.settings_day_types_copy_current))
            }
        }
        GradientSaveButton(
            enabled = canSave,
            modifier = Modifier.padding(horizontal = 18.dp),
            onClick = {
                onSave(
                    MacroDayProfile(
                        id = editing?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        calories = calories,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat,
                    )
                )
            },
        )
        if (editing != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_day_types_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete && editing != null) {
        // Replace/scrub options render as full-width rows in the body — the
        // old AlertDialog stacked them as trailing text buttons right next to
        // Cancel, which made the scrub option hard to hit (device review,
        // phase 2 pass).
        FudGlassDialog(
            onDismissRequest = { confirmDelete = false },
            scrollable = true,
        ) {
            Text(
                stringResource(R.string.settings_day_types_delete_used_title, editing.name),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.settings_day_types_delete_used_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            if (isReferenced) {
                replacementOptions.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppRadii.Field))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onDelete(option) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_day_types_delete_replace, option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                FudGlassDialogActions(
                    primaryText = stringResource(R.string.settings_day_types_delete_scrub),
                    onPrimary = { onDelete(null) },
                    dismissText = stringResource(R.string.action_cancel),
                    onDismiss = { confirmDelete = false },
                    destructive = true,
                )
            } else {
                FudGlassDialogActions(
                    primaryText = stringResource(R.string.action_delete),
                    onPrimary = { onDelete(null) },
                    dismissText = stringResource(R.string.action_cancel),
                    onDismiss = { confirmDelete = false },
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun EditorWheelLabel(label: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
    }
}
