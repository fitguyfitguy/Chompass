package app.chompass.ui.settings

import androidx.navigation.NavHostController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.HomeMacroCardLayout
import app.chompass.models.HomeFoodLogLayout
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.MacroCard
import app.chompass.ui.home.FoodLogMacroChipPickerDialog
import app.chompass.ui.home.HomeLookPreview
import app.chompass.ui.home.HomeTopNutrientPickerDialog

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
        containerColor = Color.Transparent,
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

            // Group order mirrors the preview top-to-bottom: gauge, activity,
            // nutrient cards, food log.
            item {
                SectionCard(title = stringResource(R.string.home_display_group_calorie)) {
                    // The gauge mode editor lives on Settings → Goals; this row is
                    // only a pointer so Customize Home never holds a second editor.
                    SettingRow(
                        label = stringResource(R.string.home_display_calorie_mode_pointer),
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

            item {
                SectionCard(title = stringResource(R.string.home_display_group_activity)) {
                    ToggleRow(
                        label = stringResource(R.string.home_display_show_steps),
                        checked = display.showSteps,
                        onChange = vm::setHomeShowSteps,
                    )
                    SettingNote(stringResource(R.string.home_display_show_steps_desc))
                    if (display.showSteps) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        StepGoalRow(
                            goal = display.stepGoal,
                            onDecrement = { vm.setHomeStepGoal(display.stepGoal - 500) },
                            onIncrement = { vm.setHomeStepGoal(display.stepGoal + 500) }
                        )
                        SettingNote(stringResource(R.string.home_display_step_goal_hint))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    if (display.calorieDisplayMode == HomeCalorieDisplayMode.STATIC) {
                        ToggleRow(
                            label = stringResource(R.string.home_display_show_active_calories),
                            checked = display.showActiveCalories,
                            onChange = vm::setHomeShowActiveCalories,
                        )
                        SettingNote(stringResource(R.string.home_display_show_active_calories_desc))
                    } else {
                        // With Add Active the burn presentation is intrinsic to the
                        // gauge (expected-day arc, caption, budget sheet), so the
                        // STATIC-only toggle would read as broken here.
                        SettingNote(stringResource(R.string.home_display_active_calories_add_active_note))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.home_display_group_nutrients)) {
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ToggleRow(
                        label = stringResource(R.string.home_display_slim_full_names),
                        checked = display.fullNutrientNames,
                        onChange = vm::setHomeFullNutrientNames,
                    )
                    SettingNote(stringResource(R.string.home_display_slim_full_names_desc))
                }
            }

            item {
                SectionCard(title = stringResource(R.string.home_display_group_food_log)) {
                    SettingRow(
                        label = stringResource(R.string.home_display_food_log_chips),
                        value = display.foodLogMacroChips.joinToString(", ") {
                            container.appContext.getString(it.kindLabelRes())
                        },
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

/** Muted caption under a row, matching the active-calories desc pattern. */
@Composable
private fun SettingNote(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
internal fun foodLogLayoutLabel(layout: HomeFoodLogLayout?): String = stringResource(
    when (layout) {
        null -> R.string.home_food_log_layout_default
        HomeFoodLogLayout.CLASSIC -> R.string.home_food_log_layout_classic
    }
)

@Composable
internal fun FoodLogLayoutPickerDialog(
    selected: HomeFoodLogLayout?,
    onSelect: (HomeFoodLogLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_food_log_layout), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        // Two options split the full width side by side, so each preview
        // stretches across half the dialog instead of a narrow leading slot.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (listOf<HomeFoodLogLayout?>(null) + HomeFoodLogLayout.entries).forEach { layout ->
                FoodLogLayoutOption(
                    modifier = Modifier.weight(1f),
                    label = foodLogLayoutLabel(layout),
                    selected = layout == selected,
                    onClick = { onSelect(layout) },
                ) {
                    FoodLogLayoutWire(layout)
                }
            }
        }
    }
}

@Composable
private fun FoodLogLayoutOption(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Fixed slot: the two wires differ in height, so labels would
        // otherwise sit at different heights across the split.
        Box(
            Modifier.height(58.dp),
            contentAlignment = Alignment.Center,
        ) { preview() }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** Wireframe of one food-log row: bars stand in for name, kcal, and chips. */
@Composable
private fun FoodLogLayoutWire(layout: HomeFoodLogLayout?) {
    if (layout == HomeFoodLogLayout.CLASSIC) ClassicFoodRowWire() else CompactFoodRowWire()
}

/** Classic: big thumb, kcal + serving line, macro chips on their own line. */
@Composable
private fun ClassicFoodRowWire() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WireBar(Modifier.size(44.dp), corner = 8.dp)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                WireBar(Modifier.fillMaxWidth(0.75f).height(9.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WireBar(Modifier.width(30.dp).height(6.dp), color = WireAccent())
                    WireBar(Modifier.width(22.dp).height(5.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { WireBar(Modifier.width(20.dp).height(8.dp), corner = 4.dp, color = WireAccent(alpha = 0.30f)) }
        }
    }
}

/** Compact: smaller thumb, chips share the calorie line at the trailing edge. */
@Composable
private fun CompactFoodRowWire() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WireBar(Modifier.size(34.dp), corner = 8.dp)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            WireBar(Modifier.fillMaxWidth(0.7f).height(9.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WireBar(Modifier.width(30.dp).height(6.dp), color = WireAccent())
                Spacer(Modifier.weight(1f))
                WireBar(Modifier.width(16.dp).height(7.dp), corner = 4.dp, color = WireAccent(alpha = 0.30f))
                WireBar(Modifier.width(16.dp).height(7.dp), corner = 4.dp, color = WireAccent(alpha = 0.30f))
            }
        }
    }
}

@Composable
internal fun nutrientCardLayoutLabel(layout: HomeMacroCardLayout?): String = stringResource(
    when (layout) {
        null -> R.string.home_nutrient_card_layout_default
        HomeMacroCardLayout.SLIM_LINE -> R.string.home_nutrient_card_layout_slim_line
        HomeMacroCardLayout.CLASSIC -> R.string.home_nutrient_card_layout_classic
    }
)

@Composable
internal fun NutrientCardLayoutPickerDialog(
    selected: HomeMacroCardLayout?,
    onSelect: (HomeMacroCardLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_nutrient_card_layout), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HomeMacroCardLayout?>(null) + HomeMacroCardLayout.entries).forEach { layout ->
                SettingOptionRow(
                    label = nutrientCardLayoutLabel(layout),
                    selected = layout == selected,
                    thumbnail = {
                        Box(Modifier.width(108.dp)) {
                            MacroCard(
                                label = stringResource(R.string.macro_glyph_protein),
                                shortLabel = stringResource(R.string.macro_glyph_protein),
                                current = 92.0,
                                goal = 140,
                                unit = "g",
                                freezeProgress = true,
                                planned = 0.0,
                                layout = layout,
                            )
                        }
                    },
                    onClick = { onSelect(layout) },
                )
            }
        }
    }
}

@Composable
internal fun calorieHeroLayoutLabel(layout: HomeCalorieHeroLayout?): String = stringResource(
    when (layout) {
        null -> R.string.home_calorie_layout_default
        HomeCalorieHeroLayout.SIDE_GAUGE -> R.string.home_calorie_layout_gauge
        HomeCalorieHeroLayout.COMPACT_ARC -> R.string.home_calorie_layout_compact_arc
        HomeCalorieHeroLayout.COMPACT_HORIZONTAL -> R.string.home_calorie_layout_compact_horizontal
        HomeCalorieHeroLayout.HIDDEN -> R.string.home_calorie_layout_hidden
    }
)

@Composable
internal fun CalorieLayoutPickerDialog(
    selected: HomeCalorieHeroLayout?,
    onSelect: (HomeCalorieHeroLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChompassDialog(onDismissRequest = onDismiss, scrollable = true) {
        Text(stringResource(R.string.home_display_calorie_layout), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HomeCalorieHeroLayout?>(null, HomeCalorieHeroLayout.SIDE_GAUGE, HomeCalorieHeroLayout.COMPACT_ARC, HomeCalorieHeroLayout.COMPACT_HORIZONTAL, HomeCalorieHeroLayout.HIDDEN)).forEach { layout ->
                SettingOptionRow(
                    label = calorieHeroLayoutLabel(layout),
                    selected = layout == selected,
                    thumbnail = {
                        // Uniform 76 dp slot so every row has the same height.
                        Box(Modifier.width(132.dp).height(76.dp), contentAlignment = Alignment.Center) {
                            CalorieLayoutWire(layout)
                        }
                    },
                    onClick = { onSelect(layout) },
                )
            }
        }
    }
}

/**
 * One shared option-row shape for the layout pick: schematic thumbnail on the
 * left, label on the right; tapping applies and closes the dialog.
 */
@Composable
private fun SettingOptionRow(
    label: String,
    selected: Boolean,
    thumbnail: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp), contentAlignment = Alignment.Center) { thumbnail() }
        Text(
            label,
            modifier = Modifier.padding(start = 12.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Schematic calorie-hero previews: arcs and bars, no readable numbers. */
@Composable
private fun CalorieLayoutWire(layout: HomeCalorieHeroLayout?) {
    when (layout) {
        HomeCalorieHeroLayout.SIDE_GAUGE -> SideGaugeWire()
        HomeCalorieHeroLayout.COMPACT_ARC -> CompactArcWire()
        HomeCalorieHeroLayout.COMPACT_HORIZONTAL -> ProgressBarWire()
        HomeCalorieHeroLayout.HIDDEN -> HiddenWire()
        null -> DefaultHeroWire()
    }
}

/** Default: the 260 dp semicircle with the number and goal lines inside it. */
@Composable
private fun DefaultHeroWire() {
    Box(Modifier.width(108.dp).height(54.dp)) {
        BowlWire(Modifier.matchParentSize(), stroke = 6.dp)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            WireBar(Modifier.width(46.dp).height(9.dp), color = WireAccent(0.65f))
            WireBar(Modifier.width(30.dp).height(5.dp))
        }
    }
}

/** Arc gauge + numbers: the 120 dp bowl on the left, numbers beside it. */
@Composable
private fun SideGaugeWire() {
    Row(
        Modifier.width(132.dp).height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BowlWire(Modifier.width(56.dp).height(28.dp), stroke = 4.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WireBar(Modifier.width(44.dp).height(9.dp), color = WireAccent())
            WireBar(Modifier.width(30.dp).height(6.dp))
        }
    }
}

/** Compact arc: the 200 dp bowl with its number line inside it. */
@Composable
private fun CompactArcWire() {
    Box(Modifier.width(84.dp).height(42.dp)) {
        BowlWire(Modifier.matchParentSize(), stroke = 5.dp)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WireBar(Modifier.width(38.dp).height(8.dp), color = WireAccent(0.65f))
            WireBar(Modifier.width(24.dp).height(4.dp))
        }
    }
}

/** Progress bar: numbers above a full-width track, caption below. */
@Composable
private fun ProgressBarWire() {
    Column(
        Modifier.width(132.dp).height(52.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WireBar(Modifier.width(40.dp).height(8.dp), color = WireAccent())
            WireBar(Modifier.width(24.dp).height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight()
                    .background(WireAccent())
            )
        }
        WireBar(Modifier.width(26.dp).height(5.dp))
    }
}

/** Hidden: a dashed empty slot. */
@Composable
private fun HiddenWire() {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Canvas(Modifier.width(132.dp).height(40.dp)) {
        drawRoundRect(
            color = line,
            style = Stroke(
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            ),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )
    }
}

/**
 * Semicircle bowl, matching CalorieArcCanvas: the arc ellipse is twice the
 * canvas height, so a canvas of w×w/2 yields a true circle's top half rather
 * than a squashed ellipse.
 */
@Composable
private fun BowlWire(modifier: Modifier, stroke: Dp) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fill = WireAccent()
    Canvas(modifier) {
        val s = stroke.toPx()
        val arcSize = Size(size.width - s, size.width - s)
        val topLeft = Offset(s / 2f, s / 2f)
        drawArc(track, 180f, 180f, false, topLeft = topLeft, size = arcSize, style = Stroke(s, cap = StrokeCap.Round))
        drawArc(fill, 180f, 180f * 0.62f, false, topLeft = topLeft, size = arcSize, style = Stroke(s, cap = StrokeCap.Round))
    }
}

@Composable
private fun WireAccent(alpha: Float = 0.55f): Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)

/** A bar standing in for a piece of text. */
@Composable
private fun WireBar(
    modifier: Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    corner: Dp = 2.dp,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(color)
    )
}

@Composable
private fun StepGoalRow(goal: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.home_display_step_goal), fontSize = 16.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease)) }
        Text(goal.toString(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase)) }
    }
}
