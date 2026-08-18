package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.HomeTopNutrient
import app.chompass.models.MacroValueFormatter
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.UserProfile
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import kotlin.math.roundToInt

/**
 * Verbatim port of struct NutritionDetailView in
 * ios/calorietracker/ContentView.swift (line ~720).
 *
 * Two sections:
 *   Macros: Calories / Protein / Carbs / Fat — each row shows icon +
 *     label + value + unit + '/ goal'.
 *   Detailed Nutrition: Sugar / Added Sugar / Fiber / Saturated Fat /
 *     Mono Unsat. Fat / Poly Unsat. Fat / Cholesterol / Sodium /
 *     Potassium — same icon+label+value+unit pattern, no goal column.
 *
 * Computes the per-day sum from the entries list passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDetailSheet(
    entries: List<FoodEntry>,
    profile: UserProfile?,
    homeTopNutrients: List<HomeTopNutrient>,
    optionalGoals: OptionalNutrientGoals,
    macroScale: Float = 1f,
    onHomeTopNutrientsChange: (List<HomeTopNutrient>) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var showHomeCardsPicker by remember { mutableStateOf(false) }
    val calories = entries.sumOf { it.calories }
    val protein = entries.sumOf { it.protein }
    val carbs = entries.sumOf { it.carbs }
    val fat = entries.sumOf { it.fat }
    val sugar = entries.sumOf { it.sugar ?: 0.0 }
    val addedSugar = entries.sumOf { it.addedSugar ?: 0.0 }
    val fiber = entries.sumOf { it.fiber ?: 0.0 }
    val satFat = entries.sumOf { it.saturatedFat ?: 0.0 }
    val monoFat = entries.sumOf { it.monounsaturatedFat ?: 0.0 }
    val polyFat = entries.sumOf { it.polyunsaturatedFat ?: 0.0 }
    val cholesterol = entries.sumOf { it.cholesterol ?: 0.0 }
    val sodium = entries.sumOf { it.sodium ?: 0.0 }
    val potassium = entries.sumOf { it.potassium ?: 0.0 }
    val transFat = entries.sumOf { it.transFat ?: 0.0 }
    val calcium = entries.sumOf { it.calcium ?: 0.0 }
    val iron = entries.sumOf { it.iron ?: 0.0 }
    val magnesium = entries.sumOf { it.magnesium ?: 0.0 }
    val zinc = entries.sumOf { it.zinc ?: 0.0 }
    val vitaminA = entries.sumOf { it.vitaminA ?: 0.0 }
    val vitaminC = entries.sumOf { it.vitaminC ?: 0.0 }
    val vitaminD = entries.sumOf { it.vitaminD ?: 0.0 }
    val vitaminB12 = entries.sumOf { it.vitaminB12 ?: 0.0 }
    val vitaminE = entries.sumOf { it.vitaminE ?: 0.0 }
    val vitaminK = entries.sumOf { it.vitaminK ?: 0.0 }
    val folate = entries.sumOf { it.folate ?: 0.0 }
    val omega3 = entries.sumOf { it.omega3 ?: 0.0 }
    val isDark = isDarkTheme()
    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow

    fun fmt(v: Double): String = if (v == 0.0) "—" else String.format("%.1f", v)

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
        containerColor = sheetSurface,
    ) {
        ChompassSheetLazyColumn(
            listState = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nutrition_details_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done), color = AppColors.Calorie) }
                }
            }

            item { NutritionSheetSectionHeader(stringResource(R.string.nutrition_section_home_cards)) }
            item {
                Card {
                    HomeCardsRow(
                        selected = homeTopNutrients,
                        onClick = { showHomeCardsPicker = true }
                    )
                }
            }

            item { NutritionSheetSectionHeader(stringResource(R.string.nutrition_section_macros)) }
            item {
                Card {
                    val calorieGoal = if (macroScale > 1f && profile != null) {
                        (profile.effectiveCalories * macroScale).roundToInt()
                    } else {
                        profile?.effectiveCalories ?: 2000
                    }
                    DetailRow(Icons.Filled.LocalFireDepartment, stringResource(R.string.nutrition_label_calories), "$calories", stringResource(R.string.unit_kcal), goal = "$calorieGoal", accentColor = AppColors.Calorie)
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_protein), MacroValueFormatter.string(protein), stringResource(R.string.unit_g), goal = "${HomeTopNutrient.PROTEIN.goal(profile, optionalGoals, macroScale)}", labelGlyph = "P", accentColor = AppColors.Protein)
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_carbs), MacroValueFormatter.string(carbs), stringResource(R.string.unit_g), goal = "${HomeTopNutrient.CARBS.goal(profile, optionalGoals, macroScale)}", labelGlyph = "C", accentColor = AppColors.Carbs)
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_fat), MacroValueFormatter.string(fat), stringResource(R.string.unit_g), goal = "${HomeTopNutrient.FAT.goal(profile, optionalGoals, macroScale)}", labelGlyph = "F", accentColor = AppColors.Fat)
                }
            }

            item { NutritionSheetSectionHeader(stringResource(R.string.nutrition_section_detailed)) }
            item {
                Card {
                    DetailRow(null, stringResource(R.string.nutrition_label_sugar), fmt(sugar), stringResource(R.string.unit_g), goal = "${optionalGoals.sugar}", labelGlyph = "S")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_added_sugar), fmt(addedSugar), stringResource(R.string.unit_g), goal = "${optionalGoals.addedSugar}", labelGlyph = "+")
                    Hairline()
                    DetailRow(Icons.Filled.Spa, stringResource(R.string.nutrition_label_fiber), fmt(fiber), stringResource(R.string.unit_g), goal = "${optionalGoals.fiber}", accentColor = AppColors.Fiber)
                    Hairline()
                    DetailRow(Icons.Filled.WaterDrop, stringResource(R.string.nutrition_label_saturated_fat), fmt(satFat), stringResource(R.string.unit_g), goal = "${optionalGoals.saturatedFat}")
                    Hairline()
                    DetailRow(Icons.Filled.WaterDrop, stringResource(R.string.nutrition_label_mono_fat), fmt(monoFat), stringResource(R.string.unit_g))
                    Hairline()
                    DetailRow(Icons.Filled.WaterDrop, stringResource(R.string.nutrition_label_poly_fat), fmt(polyFat), stringResource(R.string.unit_g))
                    Hairline()
                    DetailRow(Icons.Filled.Favorite, stringResource(R.string.nutrition_label_cholesterol), fmt(cholesterol), stringResource(R.string.unit_mg), goal = "${optionalGoals.cholesterol}")
                    Hairline()
                    DetailRow(Icons.Filled.Bolt, stringResource(R.string.nutrition_label_sodium), fmt(sodium), stringResource(R.string.unit_mg), goal = "${optionalGoals.sodium}")
                    Hairline()
                    DetailRow(Icons.Filled.Bolt, stringResource(R.string.nutrition_label_potassium), fmt(potassium), stringResource(R.string.unit_mg), goal = "${optionalGoals.potassium}")
                    Hairline()
                    DetailRow(Icons.Filled.WaterDrop, stringResource(R.string.nutrition_label_trans_fat), fmt(transFat), stringResource(R.string.unit_g), goal = "${optionalGoals.transFat}")
                    Hairline()
                    DetailRow(Icons.Filled.Bolt, stringResource(R.string.nutrition_label_calcium), fmt(calcium), stringResource(R.string.unit_mg), goal = "${optionalGoals.calcium}")
                    Hairline()
                    DetailRow(Icons.Filled.Bolt, stringResource(R.string.nutrition_label_iron), fmt(iron), stringResource(R.string.unit_mg), goal = "${optionalGoals.iron}")
                    Hairline()
                    DetailRow(Icons.Filled.Bolt, stringResource(R.string.nutrition_label_magnesium), fmt(magnesium), stringResource(R.string.unit_mg), goal = "${optionalGoals.magnesium}")
                    Hairline()
                    DetailRow(Icons.Filled.Bolt, stringResource(R.string.nutrition_label_zinc), fmt(zinc), stringResource(R.string.unit_mg), goal = "${optionalGoals.zinc}")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_vitamin_a), fmt(vitaminA), stringResource(R.string.unit_mcg), goal = "${optionalGoals.vitaminA}", labelGlyph = "A")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_vitamin_c), fmt(vitaminC), stringResource(R.string.unit_mg), goal = "${optionalGoals.vitaminC}", labelGlyph = "C")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_vitamin_d), fmt(vitaminD), stringResource(R.string.unit_mcg), goal = "${optionalGoals.vitaminD}", labelGlyph = "D")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_vitamin_b12), fmt(vitaminB12), stringResource(R.string.unit_mcg), goal = "${optionalGoals.vitaminB12}", labelGlyph = "B")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_vitamin_e), fmt(vitaminE), stringResource(R.string.unit_mg), goal = "${optionalGoals.vitaminE}", labelGlyph = "E")
                    Hairline()
                    DetailRow(null, stringResource(R.string.nutrition_label_vitamin_k), fmt(vitaminK), stringResource(R.string.unit_mcg), goal = "${optionalGoals.vitaminK}", labelGlyph = "K")
                    Hairline()
                    DetailRow(Icons.Filled.Spa, stringResource(R.string.nutrition_label_folate), fmt(folate), stringResource(R.string.unit_mcg), goal = "${optionalGoals.folate}")
                    Hairline()
                    DetailRow(Icons.Filled.WaterDrop, stringResource(R.string.nutrition_label_omega3), fmt(omega3), stringResource(R.string.unit_g), goal = "${optionalGoals.omega3}")
                }
            }
        }
    }

    if (showHomeCardsPicker) {
        HomeTopNutrientPickerDialog(
            selected = homeTopNutrients,
            cardCount = homeTopNutrients.size.coerceIn(1, 4),
            onSave = onHomeTopNutrientsChange,
            onDismiss = { showHomeCardsPicker = false }
        )
    }
}

// HomeTopNutrientPickerDialog moved to HomeNutrientPicker.kt

@Composable
private fun Card(content: @Composable () -> Unit) {
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        padding = 0.dp
    ) {
        Column { content() }
    }
}

@Composable
private fun NutritionSheetSectionHeader(title: String) {
    Text(
        title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        letterSpacing = 0.sp,
        modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun HomeCardsRow(
    selected: List<HomeTopNutrient>,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Spa, null, tint = AppColors.Calorie, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_nutrient_cards), fontSize = 17.sp)
            Text(
                selected.map { stringResource(it.displayNameRes) }.joinToString(", "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Row layout: icon (24dp pink, optional) + label (17sp) + value (17sp pink semibold)
 * + unit (13sp secondary) + optional '/ goal' (12sp tertiary).
 *
 * iOS uses LinearGradient on the SF Symbol; Compose uses a flat tint
 * since Material icons aren't text-paintable.
 */
@Composable
private fun DetailRow(
    icon: ImageVector?,
    label: String,
    value: String,
    unit: String,
    goal: String? = null,
    labelGlyph: String? = null,
    accentColor: Color = AppColors.Calorie,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        } else if (labelGlyph != null) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(labelGlyph, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
            Text(unit, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        goal?.let {
            Text(
                "/ $it",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .padding(start = 14.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}
