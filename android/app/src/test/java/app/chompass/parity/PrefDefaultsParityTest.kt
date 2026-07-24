package app.chompass.parity

import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.models.MealSchedule
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.AIProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dual-side lock for shared preference defaults vs `testdata/parity/pref-defaults.json`.
 * Asserts Kotlin default sources (not live DataStore).
 */
class PrefDefaultsParityTest {

    @Test
    fun sharedPrefDefaultsMatchParityFixture() {
        val f = ParityFixtures.readJson("pref-defaults.json")

        assertFalse(f.getBoolean("showWater"))
        assertEquals(2000, f.getInt("waterGoalMl"))
        assertTrue(f.getBoolean("aiFallbackEnabled"))
        assertEquals("gemini", f.getString("fallbackAiProvider"))
        assertEquals(
            AIProvider.GEMINI.defaultFallbackModel,
            f.getString("fallbackAiModel"),
        )

        assertEquals(
            f.getJSONArray("homeTopNutrients").toStringList(),
            HomeTopNutrient.DefaultSelection.map { it.storageKey },
        )
        assertEquals(
            f.getJSONArray("foodLogMacroChips").toStringList(),
            FoodLogMacroChip.DefaultSelection.map { it.storageKey },
        )
        assertEquals(f.getInt("homeNutrientCardCount"), HomeDisplayPreferences.DEFAULT_NUTRIENT_CARD_COUNT)
        assertEquals(f.getString("calorieGaugeMode"), HomeCalorieDisplayMode.Default.storageKey)
        assertTrue(f.getBoolean("weekStartsOnMonday"))
        assertEquals(f.getInt("mealBreakfastStart"), MealSchedule.DEFAULT_BREAKFAST_START)
        assertEquals(f.getInt("mealLunchStart"), MealSchedule.DEFAULT_LUNCH_START)
        assertEquals(f.getInt("mealDinnerStart"), MealSchedule.DEFAULT_DINNER_START)
        assertEquals(f.getInt("mealSnackStart"), MealSchedule.DEFAULT_SNACK_START)

        val goals = f.getJSONObject("optionalNutrientGoals")
        val d = OptionalNutrientGoals.Default
        assertEquals(goals.getInt("sugar"), d.sugar)
        assertEquals(goals.getInt("addedSugar"), d.addedSugar)
        assertEquals(goals.getInt("fiber"), d.fiber)
        assertEquals(goals.getInt("saturatedFat"), d.saturatedFat)
        assertEquals(goals.getInt("cholesterol"), d.cholesterol)
        assertEquals(goals.getInt("sodium"), d.sodium)
        assertEquals(goals.getInt("potassium"), d.potassium)
        assertEquals(goals.getInt("transFat"), d.transFat)
        assertEquals(goals.getInt("calcium"), d.calcium)
        assertEquals(goals.getInt("iron"), d.iron)
        assertEquals(goals.getInt("magnesium"), d.magnesium)
        assertEquals(goals.getInt("zinc"), d.zinc)
        assertEquals(goals.getInt("vitaminA"), d.vitaminA)
        assertEquals(goals.getInt("vitaminC"), d.vitaminC)
        assertEquals(goals.getInt("vitaminD"), d.vitaminD)
        assertEquals(goals.getInt("vitaminB12"), d.vitaminB12)
        assertEquals(goals.getInt("vitaminE"), d.vitaminE)
        assertEquals(goals.getInt("vitaminK"), d.vitaminK)
        assertEquals(goals.getInt("folate"), d.folate)
        assertEquals(goals.getInt("omega3"), d.omega3)
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
