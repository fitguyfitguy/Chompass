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
import app.chompass.R
import app.chompass.models.WaterGoalCalculator
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudGlassTextButton
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.WheelPicker
import app.chompass.ui.theme.AppColors
import app.chompass.ui.util.clockTimePattern
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class WaterReminderField {
    START, END, CUP
}

/**
 * Drinking window + cup size for the adaptive water reminder (issue #3).
 * The interval preview is the planning form of WATER-DYN-C and updates live
 * as the wheels change, so what the user sees equals what the alarm chain
 * computes at day start.
 */
@Composable
internal fun WaterReminderPlanSheet(
    currentStartMinutes: Int,
    currentEndMinutes: Int,
    currentCupMl: Int,
    goalMl: Int,
    onSave: (startMinutes: Int, endMinutes: Int, cupMl: Int) -> Unit,
) {
    var startMinutes by remember(currentStartMinutes) { mutableIntStateOf(currentStartMinutes) }
    var endMinutes by remember(currentEndMinutes) { mutableIntStateOf(currentEndMinutes) }
    var cupMl by remember(currentCupMl) { mutableIntStateOf(currentCupMl) }
    var editing by remember { mutableStateOf<WaterReminderField?>(null) }
    val context = LocalContext.current
    val formatter = remember(context) { DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault()) }
    fun formattedTime(minutes: Int): String =
        LocalTime.of(minutes / 60, minutes % 60).format(formatter)

    val field = editing
    if (field == null) {
        Text(
            stringResource(R.string.settings_water_reminder_plan),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_water_reminder_interval_help),
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
                SettingRow(
                    label = stringResource(R.string.settings_water_drinking_starts),
                    value = formattedTime(startMinutes),
                ) { editing = WaterReminderField.START }
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_water_drinking_ends),
                    value = formattedTime(endMinutes),
                ) { editing = WaterReminderField.END }
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_water_cup_size),
                    value = stringResource(R.string.settings_water_cup_size_summary, cupMl),
                ) { editing = WaterReminderField.CUP }
            }
        }
        Spacer(Modifier.height(10.dp))
        val interval = WaterGoalCalculator.planningIntervalMin(goalMl, cupMl, endMinutes - startMinutes)
        if (interval != null) {
            Text(
                stringResource(
                    R.string.settings_water_reminder_interval_preview,
                    interval,
                    WaterGoalCalculator.cupsFor(goalMl, cupMl),
                    cupMl,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie,
            )
        } else {
            Text(
                stringResource(R.string.settings_water_reminder_window_too_short),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_water_reminder_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(16.dp))
        GradientSaveButton { onSave(startMinutes, endMinutes, cupMl) }
        FudGlassTextButton(
            text = stringResource(R.string.settings_water_restore_default_plan),
            onClick = {
                startMinutes = 8 * 60
                endMinutes = 21 * 60
                cupMl = WaterGoalCalculator.DEFAULT_CUP_SIZE_ML
            },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Calorie,
        )
        Spacer(Modifier.height(8.dp))
    } else {
        when (field) {
            WaterReminderField.START -> TimeFieldEditor(
                title = stringResource(R.string.settings_water_drinking_starts),
                options = ((0..(endMinutes - 15) step 15).toList() + startMinutes)
                    .filter { it in 0..(endMinutes - 15) }
                    .distinct()
                    .sorted(),
                selected = startMinutes,
                formatter = formatter,
                onSave = { startMinutes = it; editing = null },
                onCancel = { editing = null },
            )
            WaterReminderField.END -> TimeFieldEditor(
                title = stringResource(R.string.settings_water_drinking_ends),
                options = (((startMinutes + 15)..1439 step 15).toList() + endMinutes)
                    .filter { it in (startMinutes + 15)..1439 }
                    .distinct()
                    .sorted(),
                selected = endMinutes,
                formatter = formatter,
                onSave = { endMinutes = it; editing = null },
                onCancel = { editing = null },
            )
            WaterReminderField.CUP -> {
                Text(
                    stringResource(R.string.settings_water_cup_size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                NumericWheelPicker(
                    value = cupMl,
                    onValueChange = { cupMl = it },
                    min = 50,
                    max = 1_000,
                    unit = stringResource(R.string.unit_ml),
                    step = 50,
                )
                Spacer(Modifier.height(16.dp))
                GradientSaveButton { editing = null }
                FudGlassTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { editing = null },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TimeFieldEditor(
    title: String,
    options: List<Int>,
    selected: Int,
    formatter: DateTimeFormatter,
    onSave: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedMinutes by remember(selected) { mutableIntStateOf(selected) }
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(16.dp))
    WheelPicker(
        items = options,
        selected = selectedMinutes,
        onSelect = { selectedMinutes = it },
        label = { LocalTime.of(it / 60, it % 60).format(formatter) },
    )
    Spacer(Modifier.height(16.dp))
    GradientSaveButton { onSave(selectedMinutes) }
    FudGlassTextButton(
        text = stringResource(R.string.action_cancel),
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun WaterManualTempSheet(current: Int, onSave: (Int) -> Unit) {
    var temp by remember(current) { mutableIntStateOf(current.coerceIn(-10, 45)) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_water_manual_temp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_water_manual_temp_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(16.dp))
        NumericWheelPicker(
            value = temp,
            onValueChange = { temp = it },
            min = -10,
            max = 45,
            unit = stringResource(R.string.unit_celsius),
        )
        Spacer(Modifier.height(16.dp))
        GradientSaveButton { onSave(temp) }
        Spacer(Modifier.height(8.dp))
    }
}
