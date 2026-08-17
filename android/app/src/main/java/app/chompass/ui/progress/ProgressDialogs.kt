package app.chompass.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.DecimalWheelPicker
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassTextButton
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.SplitDecimalWheelPicker
import app.chompass.ui.components.UnitToggle
import app.chompass.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import app.chompass.models.UnitFormat

@Composable
internal fun AddWeightDialog(
    useMetric: Boolean,
    initialKg: Double,
    onUnitChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (Double, Instant) -> Unit
) {
    var pickerKg by remember { mutableStateOf(initialKg) }
    var metric by remember { mutableStateOf(useMetric) }
    var advanced by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf(LocalDate.now()) }
    var hourText by remember { mutableStateOf(LocalTime.now().hour.toString().padStart(2, '0')) }
    var minuteText by remember { mutableStateOf(LocalTime.now().minute.toString().padStart(2, '0')) }
    FudGlassDialog(onDismissRequest = onDismiss, scrollable = true) {
        Text(stringResource(R.string.progress_log_weight_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        UnitToggle(stringResource(R.string.unit_kg), stringResource(R.string.unit_lbs), metric, { metric = it; onUnitChange(it) }, Modifier.fillMaxWidth())
        if (metric) {
            SplitDecimalWheelPicker(
                value = pickerKg.coerceIn(30.0, 250.0),
                onValueChange = { pickerKg = it },
                min = 30,
                max = 250,
                unit = stringResource(R.string.unit_kg)
            )
        } else {
            val lbs = (UnitFormat.kgToLbs(pickerKg)).coerceIn(60.0, 500.0)
            SplitDecimalWheelPicker(
                value = lbs,
                onValueChange = { newLbs -> pickerKg = UnitFormat.lbsToKg(newLbs) },
                min = 60,
                max = 500,
                unit = stringResource(R.string.unit_lbs)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advanced = !advanced }
                .padding(top = 8.dp, bottom = if (advanced) 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.action_advanced),
                fontSize = 14.sp,
                color = AppColors.Calorie,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (advanced) stringResource(R.string.action_hide) else stringResource(R.string.action_show),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        if (advanced) {
            DateWheelPicker(
                selected = pickedDate,
                onSelect = { pickedDate = it },
                minYear = LocalDate.now().year - 10,
                maxYear = LocalDate.now().year,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FudGlassTextField(
                    value = hourText,
                    onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                    placeholder = stringResource(R.string.placeholder_hour),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FudGlassTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                    placeholder = stringResource(R.string.placeholder_minute),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            FudGlassTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Spacer(Modifier.width(8.dp))
            FudGlassPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: LocalTime.now().hour
                    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: LocalTime.now().minute
                    val loggedAt = if (advanced) {
                        pickedDate.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()
                    } else {
                        Instant.now()
                    }
                    onSubmit(pickerKg, loggedAt)
                },
                modifier = Modifier.width(132.dp)
            )
        }
    }
}

@Composable
internal fun AddBodyFatDialog(
    initialFraction: Double,
    onDismiss: () -> Unit,
    onSubmit: (Double, Instant) -> Unit
) {
    var pct by remember { mutableStateOf(initialFraction * 100) }
    var advanced by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf(LocalDate.now()) }
    var hourText by remember { mutableStateOf(LocalTime.now().hour.toString().padStart(2, '0')) }
    var minuteText by remember { mutableStateOf(LocalTime.now().minute.toString().padStart(2, '0')) }
    FudGlassDialog(onDismissRequest = onDismiss, scrollable = true) {
        Text(stringResource(R.string.progress_log_body_fat_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        DecimalWheelPicker(
            value = pct.coerceIn(3.0, 60.0),
            onValueChange = { pct = it },
            min = 3.0,
            max = 60.0,
            step = 0.5,
            unit = stringResource(R.string.unit_percent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advanced = !advanced }
                .padding(top = 8.dp, bottom = if (advanced) 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.action_advanced),
                fontSize = 14.sp,
                color = AppColors.Calorie,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (advanced) stringResource(R.string.action_hide) else stringResource(R.string.action_show),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        if (advanced) {
            DateWheelPicker(
                selected = pickedDate,
                onSelect = { pickedDate = it },
                minYear = LocalDate.now().year - 10,
                maxYear = LocalDate.now().year,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FudGlassTextField(
                    value = hourText,
                    onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                    placeholder = stringResource(R.string.placeholder_hour),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FudGlassTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                    placeholder = stringResource(R.string.placeholder_minute),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            FudGlassTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Spacer(Modifier.width(8.dp))
            FudGlassPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: LocalTime.now().hour
                    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: LocalTime.now().minute
                    val loggedAt = if (advanced) {
                        pickedDate.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()
                    } else {
                        Instant.now()
                    }
                    onSubmit(pct / 100.0, loggedAt)
                },
                modifier = Modifier.width(132.dp)
            )
        }
    }
}
