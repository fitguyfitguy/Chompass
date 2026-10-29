package app.chompass.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.HomeFoodLogLayout
import app.chompass.models.HomeMacroCardLayout
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.home.FoodLogMacroChipPickerDialog
import app.chompass.ui.home.HomeLookPreview
import app.chompass.ui.home.HomeTopNutrientPickerDialog
import app.chompass.ui.settings.CalorieLayoutPickerDialog
import app.chompass.ui.settings.FoodLogLayoutPickerDialog
import app.chompass.ui.settings.NutrientCardLayoutPickerDialog
import app.chompass.ui.settings.calorieHeroLayoutLabel
import app.chompass.ui.settings.foodLogLayoutLabel
import app.chompass.ui.settings.nutrientCardLayoutLabel

@Composable
internal fun HomeLookStep(
    display: HomeDisplayPreferences,
    onCalorieMode: (HomeCalorieDisplayMode) -> Unit,
    onShowActiveCalories: (Boolean) -> Unit,
    onShowSteps: (Boolean) -> Unit,
    onStepGoal: (Int) -> Unit,
    onTopNutrients: (List<HomeTopNutrient>) -> Unit,
    onFoodLogChips: (List<FoodLogMacroChip>) -> Unit,
    onCalorieLayout: (HomeCalorieHeroLayout?) -> Unit,
    onNutrientCardLayout: (HomeMacroCardLayout?) -> Unit,
    onFoodLogLayout: (HomeFoodLogLayout?) -> Unit,
) {
    var showNutrientPicker by remember { mutableStateOf(false) }
    var showChipPicker by remember { mutableStateOf(false) }
    var showCalorieLayoutPicker by remember { mutableStateOf(false) }
    var showNutrientCardLayoutPicker by remember { mutableStateOf(false) }
    var showFoodLogLayoutPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val nutrientValue = display.homeTopNutrients.joinToString(", ") {
        context.getString(it.displayNameRes)
    }
    val chipValue = display.foodLogMacroChips.joinToString(", ") {
        context.getString(it.kindLabelRes())
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepHeader(
            stringResource(R.string.onboarding_home_look_title),
            stringResource(R.string.onboarding_home_look_subtitle),
        )
        HomeLookPreview(display = display, sampleFoodCount = 1)
        HomeCalorieDisplayMode.entries.forEach { mode ->
            SelectionCard(
                icon = Icons.Outlined.LocalFireDepartment,
                title = stringResource(mode.displayNameRes),
                subtitle = stringResource(
                    when (mode) {
                        HomeCalorieDisplayMode.STATIC -> R.string.home_calorie_mode_static_desc
                        HomeCalorieDisplayMode.ADD_ACTIVE -> R.string.home_calorie_mode_add_active_desc
                    }
                ),
                selected = display.calorieDisplayMode == mode,
                onClick = { onCalorieMode(mode) },
            )
        }
        // Layout rows: the step's subtitle promises the full look is set up
        // here, so the three layout pickers must not be Settings-only.
        LayoutPickerCard(
            label = stringResource(R.string.home_display_calorie_layout),
            value = calorieHeroLayoutLabel(display.calorieHeroLayout),
            onClick = { showCalorieLayoutPicker = true },
        )
        LayoutPickerCard(
            label = stringResource(R.string.home_display_nutrient_card_layout),
            value = nutrientCardLayoutLabel(display.macroCardLayout),
            onClick = { showNutrientCardLayoutPicker = true },
        )
        LayoutPickerCard(
            label = stringResource(R.string.home_display_food_log_layout),
            value = foodLogLayoutLabel(display.foodLogLayout),
            onClick = { showFoodLogLayoutPicker = true },
        )
        if (display.calorieDisplayMode == HomeCalorieDisplayMode.STATIC) {
            ToggleCard(
                label = stringResource(R.string.home_display_show_active_calories),
                subtitle = stringResource(R.string.home_display_show_active_calories_desc),
                enabled = display.showActiveCalories,
                onToggle = onShowActiveCalories,
            )
        }
        ToggleCard(
            label = stringResource(R.string.home_display_show_steps),
            subtitle = stringResource(R.string.home_display_show_steps_desc),
            enabled = display.showSteps,
            onToggle = onShowSteps,
        )
        if (display.showSteps) {
            CountRow(
                label = stringResource(R.string.home_display_step_goal),
                value = display.stepGoal.toString(),
                onDecrement = { onStepGoal(display.stepGoal - 500) },
                onIncrement = { onStepGoal(display.stepGoal + 500) },
            )
        }
        ChompassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showNutrientPicker = true },
            cornerRadius = 20.dp,
            padding = 16.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_display_nutrient_cards),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    nutrientValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        ChompassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showChipPicker = true },
            cornerRadius = 20.dp,
            padding = 16.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_display_food_log_chips),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    chipValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showNutrientPicker) {
        HomeTopNutrientPickerDialog(
            selected = display.homeTopNutrients,
            onSave = onTopNutrients,
            onDismiss = { showNutrientPicker = false },
        )
    }
    if (showChipPicker) {
        FoodLogMacroChipPickerDialog(
            selected = display.foodLogMacroChips,
            onSave = onFoodLogChips,
            onDismiss = { showChipPicker = false },
        )
    }
    if (showCalorieLayoutPicker) {
        CalorieLayoutPickerDialog(
            selected = display.calorieHeroLayout,
            onSelect = {
                onCalorieLayout(it)
                showCalorieLayoutPicker = false
            },
            onDismiss = { showCalorieLayoutPicker = false },
        )
    }
    if (showNutrientCardLayoutPicker) {
        NutrientCardLayoutPickerDialog(
            selected = display.macroCardLayout,
            onSelect = {
                onNutrientCardLayout(it)
                showNutrientCardLayoutPicker = false
            },
            onDismiss = { showNutrientCardLayoutPicker = false },
        )
    }
    if (showFoodLogLayoutPicker) {
        FoodLogLayoutPickerDialog(
            selected = display.foodLogLayout,
            onSelect = {
                onFoodLogLayout(it)
                showFoodLogLayoutPicker = false
            },
            onDismiss = { showFoodLogLayoutPicker = false },
        )
    }
}

@Composable
private fun CountRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, enabled = decrementEnabled) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease))
        }
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        IconButton(onClick = onIncrement, enabled = incrementEnabled) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase))
        }
    }
}

@Composable
private fun LayoutPickerCard(label: String, value: String, onClick: () -> Unit) {
    ChompassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        padding = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
