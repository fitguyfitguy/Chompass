package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.home.FoodLogMacroChipPickerDialog
import app.chompass.ui.home.HomeTopNutrientPickerDialog
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors

@Composable
fun HomeDisplaySettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val display = ui.homeDisplay
    var showNutrientPicker by remember { mutableStateOf(false) }
    var showChipPicker by remember { mutableStateOf(false) }
    var showCalorieModePicker by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 14.dp,
                bottom = BottomNavScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = AppColors.Calorie,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.nav_settings),
                            color = AppColors.Calorie,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.home_display_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp, allowBlur = false) {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.home_display_nutrient_cards),
                            value = display.homeTopNutrients.joinToString(", ") {
                                container.appContext.getString(it.displayNameRes)
                            },
                            onClick = { showNutrientPicker = true },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        CardCountRow(
                            count = display.nutrientCardCount,
                            onDecrement = {
                                vm.setHomeNutrientCardCount(display.nutrientCardCount - 1)
                            },
                            onIncrement = {
                                vm.setHomeNutrientCardCount(display.nutrientCardCount + 1)
                            }
                        )
                    }
                }
            }

            item {
                FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp, allowBlur = false) {
                    Column {
                        ToggleRow(
                            label = stringResource(R.string.home_display_show_steps),
                            checked = display.showSteps,
                            onChange = vm::setHomeShowSteps,
                        )
                        if (display.showSteps) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            StepGoalRow(
                                goal = display.stepGoal,
                                onDecrement = { vm.setHomeStepGoal(display.stepGoal - 500) },
                                onIncrement = { vm.setHomeStepGoal(display.stepGoal + 500) }
                            )
                        }
                    }
                }
            }

            // With Add Active the burn presentation is intrinsic to the gauge
            // (expected-day arc, caption, budget sheet), so this toggle only
            // controls the STATIC "N active" caption.
            if (display.calorieDisplayMode == HomeCalorieDisplayMode.STATIC) {
                item {
                    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp, allowBlur = false) {
                        Column {
                            ToggleRow(
                                label = stringResource(R.string.home_display_show_active_calories),
                                checked = display.showActiveCalories,
                                onChange = vm::setHomeShowActiveCalories,
                            )
                            Text(
                                stringResource(R.string.home_display_show_active_calories_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            item {
                FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp, allowBlur = false) {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.home_display_calorie_mode),
                            value = stringResource(display.calorieDisplayMode.displayNameRes),
                            onClick = { showCalorieModePicker = true },
                        )
                        Text(
                            stringResource(
                                when (display.calorieDisplayMode) {
                                    HomeCalorieDisplayMode.STATIC -> R.string.home_calorie_mode_static_desc
                                    HomeCalorieDisplayMode.ADD_ACTIVE -> R.string.home_calorie_mode_add_active_desc
                                }
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        if (display.calorieDisplayMode == HomeCalorieDisplayMode.ADD_ACTIVE) {
                            Text(
                                stringResource(R.string.home_calorie_mode_add_active_hc_hint),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                    }
                }
            }

            item {
                FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp, allowBlur = false) {
                    SettingRow(
                        label = stringResource(R.string.home_display_food_log_chips),
                        value = display.foodLogMacroChips.joinToString(" ") { it.glyph },
                        onClick = { showChipPicker = true },
                    )
                }
            }
        }
    }

    if (showNutrientPicker) {
        HomeTopNutrientPickerDialog(
            selected = display.homeTopNutrients,
            cardCount = display.nutrientCardCount,
            onSave = vm::setHomeTopNutrients,
            onDismiss = { showNutrientPicker = false }
        )
    }
    if (showChipPicker) {
        FoodLogMacroChipPickerDialog(
            selected = display.foodLogMacroChips,
            onSave = vm::setFoodLogMacroChips,
            onDismiss = { showChipPicker = false }
        )
    }
    if (showCalorieModePicker) {
        CalorieModePickerDialog(
            selected = display.calorieDisplayMode,
            onSelect = {
                vm.setHomeCalorieDisplayMode(it)
                showCalorieModePicker = false
            },
            onDismiss = { showCalorieModePicker = false }
        )
    }
}

@Composable
private fun CardCountRow(count: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.home_display_card_count), fontSize = 17.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, enabled = count > HomeDisplayPreferences.MIN_NUTRIENT_CARD_COUNT) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(count.toString(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        IconButton(onClick = onIncrement, enabled = count < HomeDisplayPreferences.MAX_NUTRIENT_CARD_COUNT) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun StepGoalRow(goal: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.home_display_step_goal), fontSize = 17.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = null) }
        Text(goal.toString(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = null) }
    }
}

@Composable
private fun CalorieModePickerDialog(
    selected: HomeCalorieDisplayMode,
    onSelect: (HomeCalorieDisplayMode) -> Unit,
    onDismiss: () -> Unit,
) {
    app.chompass.ui.components.FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_calorie_mode), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeCalorieDisplayMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(mode.displayNameRes),
                        fontWeight = if (mode == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (mode == selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        app.chompass.ui.components.FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}
