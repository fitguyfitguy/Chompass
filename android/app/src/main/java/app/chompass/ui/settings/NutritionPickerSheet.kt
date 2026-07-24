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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.theme.AppColors

/**
 * Wheel-picker sheet for a single macro / calorie target. Mirrors iOS
 * NutritionPickerSheet exactly: title, wheel picker stepped at the requested
 * step, gradient Save button, optional "Reset to Auto-balance" link when the
 * macro is currently pinned.
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
) {
    val items = remember(range, step) { (range.first..range.last step step).toList() }
    val snapped = (currentValue / step) * step
    val initial = snapped.coerceIn(range.first, range.last).let { v ->
        items.minByOrNull { kotlin.math.abs(it - v) } ?: items.first()
    }
    var selected by remember(initial) { mutableStateOf(initial) }
    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accentColor)
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        app.chompass.ui.components.WheelPicker(
            items = items,
            selected = selected,
            onSelect = { selected = it; onValueChange?.invoke(it) },
            modifier = Modifier.width(120.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor)
            .clickable { onSave(selected) },
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
