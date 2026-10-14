package app.chompass.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.CalorieSafety
import app.chompass.models.EnergyFormat
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.ui.theme.AppColors

/** The four plan values a user can hand-edit (onboarding Plan Ready step and
 *  the Recalculate confirm sheet). */
internal enum class PlanField(@get:androidx.annotation.StringRes val titleRes: Int) {
    CALORIES(R.string.onboarding_plan_field_calories),
    PROTEIN(R.string.onboarding_plan_field_protein),
    CARBS(R.string.onboarding_plan_field_carbs),
    FAT(R.string.onboarding_plan_field_fat)
}

/**
 * Tap-a-number → wheel dialog shared by onboarding's PlanReadyStep and the
 * Recalculate confirm sheet. Matches the in-app Settings nutrition pickers:
 * range + step per field, scroll to a value, no keyboard. Saves on the
 * picker's currently-selected value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanEditDialog(
    field: PlanField,
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: (() -> Unit)?,
    calorieFloor: Int = CalorieSafety.ABSOLUTE_FLOOR_KCAL,
) {
    val unit = LocalEnergyUnit.current
    val (min, max, step) = when (field) {
        PlanField.CALORIES -> Triple(calorieFloor, CalorieSafety.PARSER_CEILING_KCAL, 50)
        PlanField.PROTEIN  -> Triple(10, 500, 5)
        PlanField.CARBS    -> Triple(0, 800, 5)
        PlanField.FAT      -> Triple(10, 300, 5)
    }
    var picked by remember(currentValue) {
        mutableStateOf(currentValue.coerceIn(min, max))
    }
    val accent = when (field) {
        PlanField.CALORIES -> AppColors.Calorie
        PlanField.PROTEIN -> AppColors.Protein
        PlanField.CARBS -> AppColors.Carbs
        PlanField.FAT -> AppColors.Fat
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(field.titleRes), color = accent) },
        text = {
            NumericWheelPicker(
                value = EnergyFormat.quantity(picked, unit),
                onValueChange = { picked = EnergyFormat.toKcal(it, unit) },
                min = EnergyFormat.quantity(min, unit),
                max = EnergyFormat.quantity(max, unit),
                unit = if (field == PlanField.CALORIES) energyUnitLabel() else stringResource(R.string.unit_g),
                step = EnergyFormat.wheelStep(step, unit),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                // Typed mode keeps the wheel draft until Done / flip-to-wheel;
                // Save must flush it before reading `picked` (#63 family).
                MagnitudeDrafts.commitAll()
                onSave(picked)
            }) {
                Text(stringResource(R.string.action_save), color = accent)
            }
        },
        dismissButton = {
            Row {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
