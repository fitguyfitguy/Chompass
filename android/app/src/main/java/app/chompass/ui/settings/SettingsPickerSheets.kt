package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.LocaleFormat
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WaterAmountFormat
import app.chompass.models.WeightGoal
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.DecimalWheelPicker
import app.chompass.ui.components.FeetInchesWheelPicker
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.SplitDecimalWheelPicker
import app.chompass.ui.components.UnitToggle
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.Icon
import app.chompass.models.UnitFormat

internal fun optionalNutrientSummary(goals: OptionalNutrientGoals): String =
    "Fiber ${goals.fiber}g, Sodium ${goals.sodium}mg"

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
    val isDark = isDarkTheme()
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
        SplitDecimalWheelPicker(UnitFormat.kgToLbs(kg), { lbs -> kg = UnitFormat.lbsToKg(lbs) }, 66, 551, stringResource(R.string.unit_lbs))
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
internal fun WaterGoalSheet(current: Int, onSave: (Int) -> Unit) {
    val initialGoal = (((current.coerceIn(50, 10_000) + 25) / 50) * 50).coerceIn(50, 10_000)
    var goal by remember(current) { mutableIntStateOf(initialGoal) }
    Text(stringResource(R.string.settings_water_goal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    NumericWheelPicker(
        value = goal,
        onValueChange = { goal = it },
        min = 50,
        max = 10_000,
        unit = stringResource(R.string.unit_ml),
        step = 50,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.settings_water_goal_wheel_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(16.dp))
    GradientSaveButton { onSave(goal) }
    Spacer(Modifier.height(8.dp))
}

internal fun formatWaterQuickPresetsSummary(presetsMl: List<Int>, useMetric: Boolean): String =
    presetsMl.joinToString(" · ") { ml ->
        if (useMetric) {
            "${ml} ml"
        } else {
            "${WaterAmountFormat.flOzFromMl(ml)} fl oz"
        }
    }

@Composable
internal fun WaterQuickPresetsSheet(
    current: List<Int>,
    useMetric: Boolean,
    onSave: (List<Int>) -> Unit,
) {
    var presets by remember(current) { mutableStateOf(current.sorted()) }

    fun updatePreset(index: Int, ml: Int) {
        presets = presets.toMutableList().apply {
            set(index, ml.coerceIn(WaterQuickPresets.MIN_ML, WaterQuickPresets.MAX_ML))
        }.sorted()
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_water_quick_presets),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_water_quick_presets_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        // Up to 5 wheels (~220dp each) exceed the sheet on small screens and at
        // large font scale, which used to push Save off-screen. Wheels scroll
        // (weight = remaining height after header + pinned footer are measured,
        // fill=false so the sheet stays compact when only 2-3 presets fit); the
        // add/remove row and Save stay pinned below and always reachable.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            presets.forEachIndexed { index, ml ->
                Text(
                    stringResource(R.string.settings_water_preset_label, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                if (useMetric) {
                    val snapped = (((ml.coerceIn(WaterQuickPresets.MIN_ML, WaterQuickPresets.MAX_ML) + 25) / 50) * 50)
                        .coerceIn(WaterQuickPresets.MIN_ML, WaterQuickPresets.MAX_ML)
                    var value by remember(index, ml) { mutableIntStateOf(snapped) }
                    NumericWheelPicker(
                        value = value,
                        onValueChange = {
                            value = it
                            updatePreset(index, it)
                        },
                        min = WaterQuickPresets.MIN_ML,
                        max = WaterQuickPresets.MAX_ML,
                        unit = stringResource(R.string.unit_ml),
                        step = 50,
                    )
                } else {
                    val flOz = WaterAmountFormat.flOzFromMl(ml).coerceIn(1, 68)
                    var value by remember(index, ml) { mutableIntStateOf(flOz) }
                    NumericWheelPicker(
                        value = value,
                        onValueChange = {
                            value = it
                            updatePreset(index, WaterAmountFormat.mlFromFlOz(it))
                        },
                        min = 1,
                        max = 68,
                        unit = stringResource(R.string.unit_fl_oz),
                        step = 1,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (presets.size < WaterQuickPresets.MAX_COUNT) {
                TextButton(
                    onClick = {
                        val next = (presets.lastOrNull() ?: 250) + 250
                        presets = (presets + next.coerceIn(WaterQuickPresets.MIN_ML, WaterQuickPresets.MAX_ML))
                            .sorted()
                            .distinct()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_water_add_preset))
                }
            }
            if (presets.size > WaterQuickPresets.MIN_COUNT) {
                TextButton(
                    onClick = { presets = presets.dropLast(1) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_water_remove_preset))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GradientSaveButton {
            onSave(WaterQuickPresets(presets).validatedOrDefault().amountsMl)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun GoalSpeedSheet(current: Double, goal: WeightGoal, useMetric: Boolean, onSave: (Double) -> Unit) {
    val isDark = isDarkTheme()
    Text(stringResource(R.string.sheet_weekly_change), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    val wUnit = if (useMetric) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lbs)
    val paceRes = if (goal == WeightGoal.LOSE) R.string.settings_pace_loss_format else R.string.settings_pace_gain_format
    val options = listOf(
        Triple(0.25, stringResource(R.string.onboarding_pace_slow), 0.25),
        Triple(0.5, stringResource(R.string.onboarding_pace_recommended), 0.5),
        Triple(1.0, stringResource(R.string.onboarding_pace_fast), 1.0),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((kg, title, displayKg) in options) {
            val displayAmount = if (useMetric) {
                LocaleFormat.decimal(displayKg, 1)
            } else {
                LocaleFormat.decimal(UnitFormat.kgToLbs(displayKg), 1)
            }
            val subtitle = stringResource(paceRes, "$displayAmount $wUnit")
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
