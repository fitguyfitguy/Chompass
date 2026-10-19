package app.chompass.ui.settings

import androidx.navigation.NavHostController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.HomeMacroCardLayout
import app.chompass.models.HomeFoodLogLayout
import app.chompass.models.HomeDisplayPreferences
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.home.FoodLogMacroChipPickerDialog
import app.chompass.ui.home.HomeLookPreview
import app.chompass.ui.home.HomeTopNutrientPickerDialog
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
@Composable
fun HomeDisplaySettingsScreen(
    container: AppContainer,
    nav: NavHostController? = null,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    val display = ui.homeDisplay
    var showNutrientPicker by remember { mutableStateOf(false) }
    var showChipPicker by remember { mutableStateOf(false) }
    var showCalorieLayoutPicker by remember { mutableStateOf(false) }
    var showNutrientCardLayoutPicker by remember { mutableStateOf(false) }
    var showFoodLogLayoutPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.home_display_title),
                onBack = onBack,
            )
        },
    ) { padding ->
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
                HomeLookPreview(
                    display = display,
                    modifier = Modifier.fillMaxWidth(),
                    sampleFoodCount = 2,
                )
            }

            item {
                ChompassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.home_display_nutrient_cards),
                            value = display.homeTopNutrients.joinToString(", ") {
                                container.appContext.getString(it.displayNameRes)
                            },
                            onClick = { showNutrientPicker = true },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        SettingRow(
                            label = stringResource(R.string.home_display_nutrient_card_layout),
                            value = nutrientCardLayoutLabel(display.macroCardLayout),
                            onClick = { showNutrientCardLayoutPicker = true },
                        )
                    }
                }
            }

            item {
                ChompassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
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
                    ChompassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
                        Column {
                            ToggleRow(
                                label = stringResource(R.string.home_display_show_active_calories),
                                checked = display.showActiveCalories,
                                onChange = vm::setHomeShowActiveCalories,
                            )
                            Text(
                                stringResource(R.string.home_display_show_active_calories_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // The gauge mode editor lives on Settings → Goals now; this row is
            // only a pointer so Customize Home never holds a second editor.
            item {
                ChompassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.home_display_calorie_mode),
                            value = stringResource(display.calorieDisplayMode.displayNameRes),
                            onClick = { nav?.navigate(ChompassRoutes.SETTINGS_GOALS) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        SettingRow(
                            label = stringResource(R.string.home_display_calorie_layout),
                            value = calorieHeroLayoutLabel(display.calorieHeroLayout),
                            onClick = { showCalorieLayoutPicker = true },
                        )
                    }
                }
            }

            item {
                ChompassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.home_display_food_log_chips),
                            value = display.foodLogMacroChips.map { stringResource(it.glyphRes()) }.joinToString(" "),
                            onClick = { showChipPicker = true },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        SettingRow(
                            label = stringResource(R.string.home_display_food_log_layout),
                            value = foodLogLayoutLabel(display.foodLogLayout),
                            onClick = { showFoodLogLayoutPicker = true },
                        )
                    }
                }
            }
        }
    }

    if (showNutrientPicker) {
        HomeTopNutrientPickerDialog(
            selected = display.homeTopNutrients,
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
    if (showFoodLogLayoutPicker) {
        FoodLogLayoutPickerDialog(
            selected = display.foodLogLayout,
            onSelect = {
                vm.setHomeFoodLogLayout(it)
                showFoodLogLayoutPicker = false
            },
            onDismiss = { showFoodLogLayoutPicker = false },
        )
    }
    if (showNutrientCardLayoutPicker) {
        NutrientCardLayoutPickerDialog(
            selected = display.macroCardLayout,
            onSelect = {
                vm.setHomeMacroCardLayout(it)
                showNutrientCardLayoutPicker = false
            },
            onDismiss = { showNutrientCardLayoutPicker = false },
        )
    }
    if (showCalorieLayoutPicker) {
        CalorieLayoutPickerDialog(
            selected = display.calorieHeroLayout,
            onSelect = {
                vm.setHomeCalorieHeroLayout(it)
                showCalorieLayoutPicker = false
            },
            onDismiss = { showCalorieLayoutPicker = false }
        )
    }
}

@Composable
private fun foodLogLayoutLabel(layout: HomeFoodLogLayout?): String = stringResource(
    when (layout) {
        null -> R.string.home_food_log_layout_default
        HomeFoodLogLayout.CLASSIC -> R.string.home_food_log_layout_classic
    }
)

@Composable
private fun FoodLogLayoutPickerDialog(
    selected: HomeFoodLogLayout?,
    onSelect: (HomeFoodLogLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    app.chompass.ui.components.ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_food_log_layout), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HomeFoodLogLayout?>(null) + HomeFoodLogLayout.entries).forEach { layout ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .clickable { onSelect(layout) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        foodLogLayoutLabel(layout),
                        fontWeight = if (layout == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (layout == selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        app.chompass.ui.components.ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun nutrientCardLayoutLabel(layout: HomeMacroCardLayout?): String = stringResource(
    when (layout) {
        null -> R.string.home_nutrient_card_layout_default
        HomeMacroCardLayout.SLIM_LINE -> R.string.home_nutrient_card_layout_slim_line
        HomeMacroCardLayout.CLASSIC -> R.string.home_nutrient_card_layout_classic
    }
)

@Composable
private fun NutrientCardLayoutPickerDialog(
    selected: HomeMacroCardLayout?,
    onSelect: (HomeMacroCardLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    app.chompass.ui.components.ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_nutrient_card_layout), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HomeMacroCardLayout?>(null) + HomeMacroCardLayout.entries).forEach { layout ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .clickable { onSelect(layout) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        nutrientCardLayoutLabel(layout),
                        fontWeight = if (layout == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (layout == selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        app.chompass.ui.components.ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun calorieHeroLayoutLabel(layout: HomeCalorieHeroLayout?): String = stringResource(
    when (layout) {
        null -> R.string.home_calorie_layout_default
        HomeCalorieHeroLayout.SIDE_GAUGE -> R.string.home_calorie_layout_gauge
        HomeCalorieHeroLayout.COMPACT_ARC -> R.string.home_calorie_layout_compact_arc
        HomeCalorieHeroLayout.COMPACT_HORIZONTAL -> R.string.home_calorie_layout_compact_horizontal
        HomeCalorieHeroLayout.HIDDEN -> R.string.home_calorie_layout_hidden
    }
)

@Composable
private fun CalorieLayoutPickerDialog(
    selected: HomeCalorieHeroLayout?,
    onSelect: (HomeCalorieHeroLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    app.chompass.ui.components.ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_calorie_layout), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HomeCalorieHeroLayout?>(null, HomeCalorieHeroLayout.SIDE_GAUGE, HomeCalorieHeroLayout.COMPACT_ARC, HomeCalorieHeroLayout.COMPACT_HORIZONTAL, HomeCalorieHeroLayout.HIDDEN)).forEach { layout ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .clickable { onSelect(layout) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        calorieHeroLayoutLabel(layout),
                        fontWeight = if (layout == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (layout == selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        app.chompass.ui.components.ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
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
        IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease)) }
        Text(goal.toString(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase)) }
    }
}
