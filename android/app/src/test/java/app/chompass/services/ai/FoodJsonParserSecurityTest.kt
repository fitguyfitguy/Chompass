package app.chompass.services.ai

import app.chompass.services.InputSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security regression (docs/SECURITY_HARDENING_PLAN.md P2-4): AI output is
 * untrusted (a hostile description / OFF product name / shared-meal note can
 * steer the model). Parsing must clamp absurd numbers, drop NaN/Infinity, and
 * scrub text before anything reaches the diary.
 */
class FoodJsonParserSecurityTest {
    @Test
    fun parseFood_clampsAbsurdNumbersAndNaN() {
        val food = FoodJsonParser.parseFood(
            """{"name":"X","calories":99999999,"protein":-3,"carbs":"1e300","fat":1.5,"serving_size_grams":-5}""",
        )
        assertEquals(InputSanitizer.MAX_CALORIES.toInt(), food.calories)
        assertEquals(0.0, food.protein, 0.0)
        assertEquals(InputSanitizer.MAX_MACRO_GRAMS, food.carbs, 0.0)
        assertEquals(100.0, food.servingSizeGrams!!, 0.0)
    }

    @Test
    fun parseFood_scrubsControlCharsAndCapsNameLength() {
        val longName = "n".repeat(300)
        val food = FoodJsonParser.parseFood(
            """{"name":"B\u0000ad\u0008$longName","calories":100,"protein":1,"carbs":1,"fat":1}""",
        )
        assertTrue(food.name.startsWith("Bad"))
        assertNull(food.name.firstOrNull { it.isISOControl() })
        assertTrue(food.name.length <= InputSanitizer.MAX_NAME_LENGTH)
    }

    @Test
    fun parseFood_negativeServingFallsBackToDefault () {
        val food = FoodJsonParser.parseFood(
            """{"name":"Y","calories":50,"protein":0.5,"carbs":1,"fat":0.1,"serving_size_grams":-20}""",
        )
        assertEquals(100.0, food.servingSizeGrams!!, 0.0)
    }

    @Test
    fun parseFood_microsAreBounded() {
        val food = FoodJsonParser.parseFood(
            """{"name":"Z","calories":100,"protein":1,"carbs":1,"fat":1,"sodium":999999999.0,"vitamin_b12":1e300}""",
        )
        assertEquals(InputSanitizer.MAX_MICRO_UNITS, food.sodium!!, 0.0)
        assertTrue(food.vitaminB12!! <= InputSanitizer.MAX_MICRO_UNITS)
    }

    @Test
    fun parseOptionalNutrientGoals_absurdValuesAreCapped() {
        val goals = FoodJsonParser.parseOptionalNutrientGoals(
            """{"sodium":999999999,"vitamin_a":1e300,"sugar":-50}""",
        )
        assertEquals(InputSanitizer.MAX_MICRO_UNITS.toInt(), goals.sodium)
        assertEquals(InputSanitizer.MAX_MICRO_UNITS.toInt(), goals.vitaminA)
        assertEquals(0, goals.sugar)
    }

    @Test
    fun parseRecognition_scrubsComponentNames() {
        val result = FoodJsonParser.parseRecognition(
            """{"meal_name":"\u0000Meal\u202E","components":[{"name":"E\u0000ggs","estimated_grams":1e300,"quantity":-2}]}""",
        )
        assertEquals("Meal", result.mealName)
        assertEquals("Eggs", result.components.single().name)
        // Clamp policy: huge-but-finite values are bounded, not dropped.
        assertEquals(InputSanitizer.MAX_SERVING_GRAMS, result.components.single().estimatedGrams!!, 0.0)
        assertEquals(0.0, result.components.single().quantity!!, 0.0)
    }
}
