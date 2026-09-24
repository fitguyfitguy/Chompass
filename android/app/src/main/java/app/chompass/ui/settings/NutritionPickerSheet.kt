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
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.FudGlassTextButton
import app.chompass.ui.components.energyText
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
/**
 * Wheel-picker sheet for a single macro / calorie target. Mirrors iOS
 * NutritionPickerSheet exactly: optional title, wheel picker stepped at the
 * requested step, gradient Save button, optional "Reset to Auto-balance"
 * link when the macro is currently pinned.
 */
@Composable
fun NutritionPickerSheet(
    label: String,
    unit: String,
    currentValue: Int,
    range: IntRange,
    step: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    onResetToAuto: (() -> Unit)? = null,
    resetLabel: String? = null,
    // Live wheel-selection reporter, for hosts that need the current value
    // before Save (e.g. to convert it when a unit switcher flips).
    onValueChange: ((Int) -> Unit)? = null,
    accentColor: Color = AppColors.Calorie,
    /**
     * Optional per-value conversion line under the wheel (e.g. vitamin D
     * mcg → IU). Receives the live selection so the hint stays current.
     */
    conversionHintFor: ((Int) -> String)? = null,
    /** When set, saving a value below this shows a confirm dialog first. */
    confirmBelow: Int? = null,
    confirmBelowTitle: String? = null,
    confirmBelowMessage: String? = null,
    /** Mirror of [confirmBelow]: saving above this line asks for a generic
     *  high-amount confirmation first (optional-nutrient soft maxima). */
    confirmAbove: Int? = null,
    /**
     * When true, paint [label] as a large colored heading above the wheel.
     * Goal hosts already name the nutrient on the row the user tapped, so
     * the default is off. Body-measurement editors keep it on because the
     * dialog's only heading is this label.
     */
    showTitle: Boolean = false,
) {
    // D2 (audit M8+M9 decision): open on the stored value verbatim — no snap
    // to the wheel grid before the first touch. NumericWheelPicker injects an
    // off-grid stored value as its own row, so the center label shows the
    // exact stored number until the user scrolls onto the grid.
    var selected by remember(currentValue) { mutableStateOf(currentValue) }
    var pendingConfirm by remember { mutableStateOf(false) }
    if (showTitle) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accentColor)
        Spacer(Modifier.height(12.dp))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        app.chompass.ui.components.NumericWheelPicker(
            value = selected,
            onValueChange = { selected = it; onValueChange?.invoke(it) },
            min = range.first,
            max = range.last,
            step = step,
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
    if (conversionHintFor != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            conversionHintFor(selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FudGlassTextButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
        )
        Box(
            Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(AppRadii.Field))
                .background(accentColor)
                .clickable {
                    app.chompass.ui.components.MagnitudeDrafts.commitAll()
                    val needsBelowConfirm = confirmBelow != null && selected < confirmBelow
                    val needsAboveConfirm = confirmAbove != null && selected > confirmAbove
                    if (needsBelowConfirm || needsAboveConfirm) pendingConfirm = true
                    else onSave(selected)
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
    if (pendingConfirm && (confirmBelow != null || confirmAbove != null)) {
        val highAmount = confirmAbove != null && selected > confirmAbove
        AlertDialog(
            onDismissRequest = { pendingConfirm = false },
            title = {
                Text(
                    when {
                        highAmount -> stringResource(R.string.settings_picker_confirm_above_title)
                        else -> confirmBelowTitle ?: stringResource(R.string.settings_calorie_below_floor_title)
                    }
                )
            },
            text = {
                Text(
                    when {
                        highAmount -> stringResource(R.string.settings_picker_confirm_above_message)
                        else -> confirmBelowMessage ?: stringResource(R.string.settings_calorie_below_floor_message, energyText(1200))
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingConfirm = false; onSave(selected) }) {
                    Text(
                        stringResource(
                            if (highAmount) R.string.action_save
                            else R.string.settings_calorie_below_floor_continue
                        )
                    )
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
