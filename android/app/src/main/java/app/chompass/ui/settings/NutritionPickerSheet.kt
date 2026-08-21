package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
/**
 * Wheel-picker sheet for a single macro / calorie target. Mirrors iOS
 * NutritionPickerSheet exactly: optional title, wheel picker stepped at the
 * requested step, gradient Save button, optional "Reset to Auto-balance"
 * link when the macro is currently pinned.
 *
 * Also offers "Enter custom value…": swaps the wheel for a free-entry field
 * so values outside the preset range (e.g. a therapeutic vitamin D dose of
 * 250 mcg) are settable. Parses as a non-negative integer; falls back to the
 * wheel selection when the text is not a number.
 */
@Composable
fun NutritionPickerSheet(
    label: String,
    unit: String,
    currentValue: Int,
    range: IntRange,
    step: Int,
    onSave: (Int) -> Unit,
    onResetToAuto: (() -> Unit)? = null,
    resetLabel: String? = null,
    // Live wheel-selection reporter, for hosts that need the current value
    // before Save (e.g. to convert it when a unit switcher flips).
    onValueChange: ((Int) -> Unit)? = null,
    accentColor: Color = AppColors.Calorie,
    /**
     * Optional per-value conversion line shown under the custom field
     * (e.g. vitamin D mcg → IU). Receives the live custom value so the
     * hint tracks what is being typed.
     */
    conversionHintFor: ((Int) -> String)? = null,
    /**
     * Optional upper clamp for the custom input (e.g. a nutrient's
     * maxCustomGoal); null keeps any non-negative integer.
     */
    maxCustomGoal: Int? = null,
    /** When set, saving a value below this shows a confirm dialog first. */
    confirmBelow: Int? = null,
    confirmBelowTitle: String? = null,
    confirmBelowMessage: String? = null,
    /**
     * When true, paint [label] as a large colored heading above the wheel.
     * Goal hosts already name the nutrient on the row the user tapped, so
     * the default is off. Body-measurement editors keep it on because the
     * dialog's only heading is this label.
     */
    showTitle: Boolean = false,
) {
    val items = remember(range, step) { (range.first..range.last step step).toList() }
    val snapped = (currentValue / step) * step
    val initial = snapped.coerceIn(range.first, range.last).let { v ->
        items.minByOrNull { kotlin.math.abs(it - v) } ?: items.first()
    }
    var selected by remember(initial) { mutableStateOf(initial) }
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var pendingConfirm by remember { mutableStateOf(false) }
    val clampCustom: (Int) -> Int = { v -> if (maxCustomGoal != null) v.coerceAtMost(maxCustomGoal) else v }
    val parsedCustom = customText.trim().replace(',', '.').toDoubleOrNull()?.toInt()?.coerceAtLeast(0)?.let(clampCustom)
    val saveValue = if (customMode) parsedCustom ?: selected else selected
    if (showTitle) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accentColor)
        Spacer(Modifier.height(12.dp))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        app.chompass.ui.components.WheelPicker(
            items = items,
            selected = selected,
            onSelect = { selected = it; onValueChange?.invoke(it) },
            modifier = Modifier
                .width(120.dp)
                .semantics { contentDescription = label }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
        )
    }
    if (customMode) {
        Spacer(Modifier.height(10.dp))
        FudGlassTextField(
            value = customText,
            onValueChange = { text ->
                customText = text
                text.trim().replace(',', '.').toDoubleOrNull()?.toInt()?.coerceAtLeast(0)?.let(clampCustom)?.let { onValueChange?.invoke(it) }
            },
            placeholder = stringResource(R.string.settings_picker_custom_placeholder, unit),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            accentColor = accentColor,
        )
        TextButton(
            onClick = { customMode = false; onValueChange?.invoke(selected) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.settings_picker_use_wheel),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
            )
        }
    } else {
        TextButton(
            onClick = { customMode = true; customText = selected.toString() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_picker_enter_custom), color = accentColor)
        }
    }
    if (conversionHintFor != null && (customMode || currentValue > range.last)) {
        Spacer(Modifier.height(6.dp))
        Text(
            conversionHintFor(parsedCustom ?: selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(AppRadii.Field))
            .background(accentColor)
            .clickable {
                if (confirmBelow != null && saveValue < confirmBelow) pendingConfirm = true
                else onSave(saveValue)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.action_save),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium
        )
    }
    if (onResetToAuto != null) {
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onResetToAuto,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                resetLabel ?: stringResource(R.string.settings_reset_autobalance),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    if (pendingConfirm && confirmBelow != null) {
        AlertDialog(
            onDismissRequest = { pendingConfirm = false },
            title = { Text(confirmBelowTitle ?: stringResource(R.string.settings_calorie_below_floor_title)) },
            text = { Text(confirmBelowMessage ?: stringResource(R.string.settings_calorie_below_floor_message)) },
            confirmButton = {
                TextButton(onClick = { pendingConfirm = false; onSave(saveValue) }) {
                    Text(stringResource(R.string.settings_calorie_below_floor_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
