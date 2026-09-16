package app.chompass.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import java.time.LocalDate

/**
 * Day picker for the meal-planning plan actions (suggestion-row Plan button):
 * one date wheel bounded to the diary's forward window. Hosted like the
 * Copy-from-day target picker — a dialog, not a destination.
 *
 * The wheel itself is only year-bounded (WheelPicker, #96), so the confirm
 * step re-clamps the pick into `today..maxDiaryNavDate(today)`; the wheel
 * bounds are never widened.
 */
@Composable
internal fun PlanForDayDialog(
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    val maxDate = maxDiaryNavDate(today)
    var pickedDate by remember { mutableStateOf(today.plusDays(1)) }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.plan_pick_day_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        DateWheelPicker(
            selected = pickedDate.coerceIn(today, maxDate),
            onSelect = { pickedDate = it },
            minYear = today.year,
            maxYear = maxDate.year,
            modifier = Modifier.fillMaxWidth()
        )
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = { onConfirm(pickedDate.coerceIn(today, maxDate)) },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )
    }
}
