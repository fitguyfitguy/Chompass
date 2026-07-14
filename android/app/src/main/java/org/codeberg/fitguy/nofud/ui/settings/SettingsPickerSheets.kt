package org.codeberg.fitguy.nofud.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.OptionalNutrient
import org.codeberg.fitguy.nofud.models.OptionalNutrientGoals
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.codeberg.fitguy.nofud.ui.components.DateWheelPicker
import org.codeberg.fitguy.nofud.ui.components.DecimalWheelPicker
import org.codeberg.fitguy.nofud.ui.components.FeetInchesWheelPicker
import org.codeberg.fitguy.nofud.ui.components.FudGlassPrimaryButton
import org.codeberg.fitguy.nofud.ui.components.FudGlassTextField
import org.codeberg.fitguy.nofud.ui.components.FudIconBubble
import org.codeberg.fitguy.nofud.ui.components.NumericWheelPicker
import org.codeberg.fitguy.nofud.ui.components.SplitDecimalWheelPicker
import org.codeberg.fitguy.nofud.ui.components.UnitToggle
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.Icon

internal fun optionalNutrientSummary(goals: OptionalNutrientGoals): String =
    "Fiber ${goals.fiber}g, Sodium ${goals.sodium}mg"

internal fun OptionalNutrient.pickerRange(): IntRange = when (this) {
    OptionalNutrient.SUGAR -> 0..200
    OptionalNutrient.ADDED_SUGAR -> 0..100
    OptionalNutrient.FIBER -> 0..100
    OptionalNutrient.SATURATED_FAT -> 0..80
    OptionalNutrient.CHOLESTEROL -> 0..1000
    OptionalNutrient.SODIUM -> 0..5000
    OptionalNutrient.POTASSIUM -> 0..7000
    OptionalNutrient.TRANS_FAT -> 0..10
    OptionalNutrient.CALCIUM -> 300..2000
    OptionalNutrient.IRON -> 5..45
    OptionalNutrient.MAGNESIUM -> 100..800
    OptionalNutrient.ZINC -> 3..40
    OptionalNutrient.VITAMIN_A -> 300..3000
    OptionalNutrient.VITAMIN_C -> 20..500
    OptionalNutrient.VITAMIN_D -> 5..100
    OptionalNutrient.VITAMIN_B12 -> 1..20
    OptionalNutrient.VITAMIN_E -> 5..100
    OptionalNutrient.VITAMIN_K -> 30..300
    OptionalNutrient.FOLATE -> 100..1000
    OptionalNutrient.OMEGA3 -> 0..10
}

internal fun OptionalNutrient.pickerStep(): Int = when (this) {
    OptionalNutrient.FIBER,
    OptionalNutrient.SATURATED_FAT,
    OptionalNutrient.TRANS_FAT,
    OptionalNutrient.IRON,
    OptionalNutrient.ZINC,
    OptionalNutrient.VITAMIN_D,
    OptionalNutrient.VITAMIN_B12,
    OptionalNutrient.VITAMIN_E,
    OptionalNutrient.OMEGA3 -> 1
    OptionalNutrient.CHOLESTEROL -> 25
    OptionalNutrient.SODIUM,
    OptionalNutrient.POTASSIUM,
    OptionalNutrient.CALCIUM,
    OptionalNutrient.VITAMIN_A,
    OptionalNutrient.FOLATE -> 50
    OptionalNutrient.MAGNESIUM -> 25
    OptionalNutrient.VITAMIN_C,
    OptionalNutrient.VITAMIN_K -> 10
    OptionalNutrient.SUGAR,
    OptionalNutrient.ADDED_SUGAR -> 5
}

@Composable
internal fun <T> ListSheet(
    title: String,
    items: List<T>,
    label: @Composable (T) -> String,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    icon: ((T) -> ImageVector?)? = null,
    subtitle: (@Composable (T) -> String?)? = null,
    footer: String? = null,
    customField: ((String) -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            val isSel = selected(item)
            val rowIcon = icon?.invoke(item)
            val sub = subtitle?.invoke(item)
            val shape = MaterialTheme.shapes.medium
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (rowIcon != null) {
                    FudIconBubble(rowIcon, size = 22.dp, iconSize = 14.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        label(item),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (!sub.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (isSel) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.sheet_selected_a11y),
                        tint = AppColors.Calorie,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    if (customField != null) {
        footer?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        var custom by remember { mutableStateOf("") }
        Spacer(Modifier.height(8.dp))
        FudGlassTextField(
            value = custom,
            onValueChange = { custom = it },
            placeholder = stringResource(R.string.sheet_any_model_id),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        FudGlassPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = { if (custom.isNotBlank()) customField(custom.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ApiKeySheet(title: String, placeholder: String, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    FudGlassTextField(
        value = value,
        onValueChange = { value = it },
        placeholder = placeholder,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    FudGlassPrimaryButton(
        text = stringResource(R.string.action_save),
        onClick = { onSave(value) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { onSave("") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_clear_key)) }
}

@Composable
internal fun TextFieldSheet(
    title: String,
    initial: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    FudGlassTextField(
        value = value,
        onValueChange = { value = it },
        placeholder = placeholder,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    FudGlassPrimaryButton(
        text = stringResource(R.string.action_save),
        onClick = { onSave(value.trim()) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun HeightSheet(current: Int, useMetric: Boolean, onUnitChange: (Boolean) -> Unit, onSave: (Int) -> Unit) {
    var cm by remember(current) { mutableStateOf(current) }
    var metric by remember { mutableStateOf(useMetric) }
    Text(stringResource(R.string.sheet_height), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    UnitToggle(stringResource(R.string.unit_cm), stringResource(R.string.unit_ft_in), metric, { metric = it; onUnitChange(it) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    if (metric) NumericWheelPicker(cm, { cm = it }, 100, 250, stringResource(R.string.unit_cm))
    else FeetInchesWheelPicker(cm, { cm = it })
    Spacer(Modifier.height(16.dp))
    GradientSaveButton { onSave(cm) }
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun WeightSheet(titleText: String, current: Double, useMetric: Boolean, onUnitChange: (Boolean) -> Unit, onSave: (Double) -> Unit) {
    var kg by remember(current) { mutableStateOf(current) }
    var metric by remember { mutableStateOf(useMetric) }
    Text(titleText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    UnitToggle(stringResource(R.string.unit_kg), stringResource(R.string.unit_lbs), metric, { metric = it; onUnitChange(it) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    if (metric) {
        SplitDecimalWheelPicker(kg, { kg = it }, 30, 250, stringResource(R.string.unit_kg))
    } else {
        SplitDecimalWheelPicker(kg * 2.20462, { lbs -> kg = lbs / 2.20462 }, 66, 551, stringResource(R.string.unit_lbs))
    }
    Spacer(Modifier.height(16.dp))
    GradientSaveButton { onSave(kg) }
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun BodyFatSheet(current: Double?, onSave: (Double?) -> Unit) {
    var pct by remember(current) { mutableStateOf((current ?: 0.20) * 100) }
    Text(stringResource(R.string.sheet_body_fat_percent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    DecimalWheelPicker(pct, { pct = it }, 5.0, 60.0, 0.5, stringResource(R.string.unit_percent))
    Spacer(Modifier.height(12.dp))
    GradientSaveButton { onSave(pct / 100.0) }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { onSave(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_clear)) }
    Spacer(Modifier.height(8.dp))
}

/** Same wheel UX as BodyFatSheet, but framed as a goal — separate, optional,
 *  display-only. Seeds from the existing goal, falling back to the user's
 *  current body fat % so the wheel lands somewhere sensible on first open. */
@Composable
internal fun GoalBodyFatSheet(currentGoal: Double?, currentBodyFat: Double?, onSave: (Double?) -> Unit) {
    val seed = currentGoal ?: currentBodyFat ?: 0.15
    var pct by remember(currentGoal) { mutableStateOf(seed * 100) }
    Text(stringResource(R.string.sheet_goal_body_fat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (currentBodyFat != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sheet_goal_body_fat_currently, (currentBodyFat * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
    Spacer(Modifier.height(12.dp))
    DecimalWheelPicker(pct, { pct = it }, 3.0, 60.0, 0.5, stringResource(R.string.unit_percent))
    Spacer(Modifier.height(12.dp))
    GradientSaveButton { onSave(pct / 100.0) }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { onSave(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_remove_goal)) }
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun GoalSpeedSheet(current: Double, goal: WeightGoal, useMetric: Boolean, onSave: (Double) -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Text(stringResource(R.string.sheet_weekly_change), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    val wUnit = if (useMetric) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lbs)
    val paceRes = if (goal == WeightGoal.LOSE) R.string.settings_pace_loss_format else R.string.settings_pace_gain_format
    val options = listOf(
        Triple(0.25, stringResource(R.string.onboarding_pace_slow), stringResource(paceRes, "0.25 $wUnit")),
        Triple(0.5, stringResource(R.string.onboarding_pace_recommended), stringResource(paceRes, "0.5 $wUnit")),
        Triple(1.0, stringResource(R.string.onboarding_pace_fast), stringResource(paceRes, "1.0 $wUnit"))
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((kg, title, subtitle) in options) {
            val isSel = kotlin.math.abs(kg - current) < 0.01
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSel) AppColors.Calorie.copy(alpha = 0.13f)
                        else if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
                    )
                    .clickable { onSave(kg) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (isSel) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = AppColors.Calorie,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdaySheet(current: Instant, onSave: (Instant) -> Unit) {
    // Material3 DatePicker stores selection as UTC-midnight millis. We store
    // birthdays as a local-zone Instant. Round-trip both sides through the
    // user's local date to avoid an off-by-one when the user is east of UTC.
    val localDate = current.atZone(ZoneId.systemDefault()).toLocalDate()
    var pickedDate by remember(current) { mutableStateOf(localDate) }
    Text(stringResource(R.string.sheet_birthday), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    DateWheelPicker(
        selected = pickedDate,
        onSelect = { pickedDate = it },
        maxYear = LocalDate.now().year,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    GradientSaveButton {
        val newInstant = pickedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        onSave(newInstant)
    }
    Spacer(Modifier.height(8.dp))
}
