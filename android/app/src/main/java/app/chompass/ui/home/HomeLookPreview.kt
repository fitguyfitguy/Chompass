package app.chompass.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.ActiveCalorieSource
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.HomeFoodLogLayout
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.models.MealType
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.components.MacroCard
import app.chompass.ui.components.StepsCard
import app.chompass.ui.theme.nutrientAccentColor
import java.time.Instant

/** Fixed sample-day numbers. Not the user's diary. */
internal object HomeLookSample {
    const val EATEN = 1680
    const val BASE_GOAL = 2000
    const val ACTIVE = 380
    const val STEPS = 6420L

    /** Logged amount and goal for one nutrient card. Not [HomeTopNutrient.goal]. */
    fun macro(nutrient: HomeTopNutrient): Pair<Double, Int> = when (nutrient) {
        HomeTopNutrient.PROTEIN -> 92.0 to 140
        HomeTopNutrient.CARBS -> 160.0 to 220
        HomeTopNutrient.FAT -> 48.0 to 65
        HomeTopNutrient.FIBER -> 18.0 to 30
        HomeTopNutrient.SUGAR -> 36.0 to 50
        HomeTopNutrient.SODIUM -> 1400.0 to 2300
        HomeTopNutrient.CAFFEINE -> 95.0 to 400
        else -> 60.0 to 100
    }
}

/**
 * Sample day of the real calorie hero, nutrient cards, and food-log rows.
 * Settings shows two foods; onboarding shows one so the first control stays on screen.
 */
@Composable
internal fun HomeLookPreview(
    display: HomeDisplayPreferences,
    modifier: Modifier = Modifier,
    sampleFoodCount: Int = 2,
) {
    val foodCount = sampleFoodCount.coerceIn(1, 2)
    val addActive = display.calorieDisplayMode == HomeCalorieDisplayMode.ADD_ACTIVE
    val foods = sampleFoods().take(foodCount)

    ChompassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        padding = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_look_sample_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            if (display.calorieHeroLayout != HomeCalorieHeroLayout.HIDDEN) {
                Text(
                    stringResource(R.string.home_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
                CalorieHero(
                    current = HomeLookSample.EATEN,
                    baseGoal = HomeLookSample.BASE_GOAL,
                    planned = 0,
                    displayMode = display.calorieDisplayMode,
                    activeCalories = if (addActive) HomeLookSample.ACTIVE else 0,
                    activeCalorieSource = if (addActive) ActiveCalorieSource.ESTIMATED else null,
                    showActiveCalories = display.showActiveCalories,
                    liveActiveBurn = HomeLookSample.ACTIVE,
                    burnShade = null,
                    restingBurn = null,
                    goalDerivation = null,
                    recalcDetailsAvailable = false,
                    onShowRecalcDetails = null,
                    dayTypeLabel = null,
                    onDayTypeClick = null,
                    onUntrackedClick = null,
                    freezeProgress = true,
                    layout = display.calorieHeroLayout,
                    preview = true,
                )
            }
            if (display.showSteps) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StepsCard(
                        steps = HomeLookSample.STEPS,
                        goal = display.stepGoal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(if (display.showSteps) 4.dp else 12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                display.homeTopNutrients.forEach { nutrient ->
                    val (current, goal) = HomeLookSample.macro(nutrient)
                    MacroCard(
                        label = stringResource(nutrient.displayNameRes),
                        shortLabel = stringResource(display.cardLabelRes(nutrient)),
                        current = current,
                        goal = goal,
                        unit = stringResource(nutrient.unitRes),
                        modifier = Modifier.weight(1f),
                        freezeProgress = true,
                        planned = 0.0,
                        fatFloor = nutrient == HomeTopNutrient.FAT,
                        accentColor = nutrientAccentColor(nutrient),
                        layout = display.macroCardLayout,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MealSectionHeader(
                meal = MealType.LUNCH.id,
                totalCalories = foods.sumOf { it.calories },
                totalProtein = foods.sumOf { it.protein },
                totalCarbs = foods.sumOf { it.carbs },
                totalFat = foods.sumOf { it.fat },
                totalFiber = foods.sumOf { it.fiber ?: 0.0 },
                totalSugar = foods.sumOf { it.sugar ?: 0.0 },
                macroChips = display.foodLogMacroChips,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                foods.forEach { entry ->
                    FoodRow(
                        entry = entry,
                        macroChips = display.foodLogMacroChips,
                        compact = display.foodLogLayout != HomeFoodLogLayout.CLASSIC,
                    )
                }
            }
        }
    }
}

@Composable
private fun sampleFoods(): List<FoodEntry> {
    val yogurt = stringResource(R.string.home_look_sample_food_yogurt)
    val bowl = stringResource(R.string.home_look_sample_food_bowl)
    return remember(yogurt, bowl) {
        listOf(
            FoodEntry(
                name = yogurt,
                calories = 280,
                protein = 22.0,
                carbs = 32.0,
                fat = 6.0,
                fiber = 2.0,
                sugar = 18.0,
                emoji = "🥣",
                source = FoodSource.MANUAL,
                imageFilename = null,
                timestamp = Instant.parse("2026-06-15T08:30:00Z"),
            ),
            FoodEntry(
                name = bowl,
                calories = 640,
                protein = 42.0,
                carbs = 70.0,
                fat = 18.0,
                fiber = 4.0,
                sugar = 3.0,
                emoji = "🍗",
                source = FoodSource.MANUAL,
                imageFilename = null,
                timestamp = Instant.parse("2026-06-15T12:40:00Z"),
                servingSizeGrams = 350.0,
            ),
        )
    }
}
