package app.chompass.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.chompass.R
import app.chompass.models.MealSchedule
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudGlassTextButton
import app.chompass.ui.components.WheelPicker
import app.chompass.ui.theme.AppColors
import app.chompass.ui.util.clockTimePattern

private enum class MealBoundary {
    BREAKFAST, LUNCH, DINNER, SNACK
}

@Composable
internal fun MealTimesSheet(current: MealSchedule, onSave: (MealSchedule) -> Unit) {
    var schedule by remember(current) { mutableStateOf(current.validatedOrDefault()) }
    var editing by remember { mutableStateOf<MealBoundary?>(null) }
    val context = LocalContext.current
    val formatter = remember(context) { DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault()) }
    fun formattedTime(minutes: Int): String =
        LocalTime.of(minutes / 60, minutes % 60).format(formatter)

    val selectedBoundary = editing
    if (selectedBoundary == null) {
        Text(
            stringResource(R.string.settings_meal_times),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_meal_times_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(16.dp))
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            padding = 0.dp,
        ) {
            Column {
                MealBoundary.entries.forEachIndexed { index, boundary ->
                    SettingRow(
                        label = stringResource(boundary.labelRes()),
                        value = formattedTime(boundary.valueIn(schedule)),
                    ) { editing = boundary }
                    if (index != MealBoundary.entries.lastIndex) HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.settings_meal_times_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
        )
        Spacer(Modifier.height(16.dp))
        GradientSaveButton { onSave(schedule) }
        FudGlassTextButton(
            text = stringResource(R.string.settings_restore_default_times),
            onClick = { schedule = MealSchedule.Default },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Calorie,
        )
        Spacer(Modifier.height(8.dp))
    } else {
        val allowed = selectedBoundary.allowedRange(schedule)
        var selectedMinutes by remember(selectedBoundary, schedule) {
            mutableIntStateOf(selectedBoundary.valueIn(schedule))
        }
        val options = remember(allowed, selectedMinutes) {
            ((allowed.first..allowed.last step 15).toList() + selectedMinutes)
                .filter { it in allowed }
                .distinct()
                .sorted()
        }
        val label = stringResource(selectedBoundary.labelRes())
        Text(
            stringResource(R.string.settings_meal_time_edit_format, label),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        WheelPicker(
            items = options,
            selected = selectedMinutes,
            onSelect = { selectedMinutes = it },
            label = { formattedTime(it) },
        )
        Spacer(Modifier.height(16.dp))
        GradientSaveButton {
            schedule = selectedBoundary.updatedSchedule(schedule, selectedMinutes)
            editing = null
        }
        FudGlassTextButton(
            text = stringResource(R.string.action_cancel),
            onClick = { editing = null },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun MealBoundary.labelRes(): Int = when (this) {
    MealBoundary.BREAKFAST -> R.string.settings_breakfast_starts
    MealBoundary.LUNCH -> R.string.settings_lunch_starts
    MealBoundary.DINNER -> R.string.settings_dinner_starts
    MealBoundary.SNACK -> R.string.settings_late_snack_starts
}

private fun MealBoundary.valueIn(schedule: MealSchedule): Int = when (this) {
    MealBoundary.BREAKFAST -> schedule.breakfastStartMinutes
    MealBoundary.LUNCH -> schedule.lunchStartMinutes
    MealBoundary.DINNER -> schedule.dinnerStartMinutes
    MealBoundary.SNACK -> schedule.snackStartMinutes
}

private fun MealBoundary.allowedRange(schedule: MealSchedule): IntRange = when (this) {
    MealBoundary.BREAKFAST -> 0..(schedule.lunchStartMinutes - 15)
    MealBoundary.LUNCH -> (schedule.breakfastStartMinutes + 15)..(schedule.dinnerStartMinutes - 15)
    MealBoundary.DINNER -> (schedule.lunchStartMinutes + 15)..(schedule.snackStartMinutes - 15)
    MealBoundary.SNACK -> (schedule.dinnerStartMinutes + 15)..1439
}

private fun MealBoundary.updatedSchedule(schedule: MealSchedule, minutes: Int): MealSchedule = when (this) {
    MealBoundary.BREAKFAST -> schedule.copy(breakfastStartMinutes = minutes)
    MealBoundary.LUNCH -> schedule.copy(lunchStartMinutes = minutes)
    MealBoundary.DINNER -> schedule.copy(dinnerStartMinutes = minutes)
    MealBoundary.SNACK -> schedule.copy(snackStartMinutes = minutes)
}
