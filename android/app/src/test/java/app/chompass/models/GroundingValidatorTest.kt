package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingValidatorTest {
    @Test
    fun atwaterKcal_matchesMacroEnergy() {
        // 4*25 + 4*50 + 9*20 = 100 + 200 + 180 = 480
        assertEquals(480.0, GroundingValidator.atwaterKcal(25.0, 50.0, 20.0), 0.01)
    }

    @Test
    fun validateServing_flagsNegativeServing() {
        val result = GroundingValidator.validateServing(
            analysisName = "Test",
            calories = 100,
            protein = 10.0,
            carbs = 10.0,
            fat = 2.0,
            servingGrams = 0.0,
        )
        assertTrue(result.notes.any { it.contains("positive") })
    }

    @Test
    fun validateServing_detectsKjAsKcal() {
        // ~418 kJ reported as "calories" for a ~100 kcal Atwater meal.
        val result = GroundingValidator.validateServing(
            analysisName = "Toast",
            calories = 418,
            protein = 4.0,
            carbs = 15.0,
            fat = 2.0,
            servingGrams = 40.0,
        )
        assertNotNull(result.correctedCalories)
        assertTrue(result.notes.any { it.contains("kilojoules", ignoreCase = true) })
    }

    @Test
    fun validateServing_skipsKjWhenAMacroIsMissing() {
        // Bob's Red Mill oats shape: 180 kcal, P6, carbs omitted, F3 → Atwater 51, ratio 3.53.
        val result = GroundingValidator.validateServing(
            analysisName = "Rolled Oats",
            calories = 180,
            protein = 6.0,
            carbs = null,
            fat = 3.0,
            servingGrams = 45.0,
        )
        assertNull(result.correctedCalories)
    }

    @Test
    fun validateServing_kjStillFiresWhenCarbsAreZero() {
        val result = GroundingValidator.validateServing(
            analysisName = "Chicken",
            calories = 418,
            protein = 25.0,
            carbs = 0.0,
            fat = 0.0,
            servingGrams = 100.0,
        )
        assertNotNull(result.correctedCalories)
    }

    @Test
    fun validateServing_detectsSodiumInGrams() {
        val result = GroundingValidator.validateServing(
            analysisName = "Soup",
            calories = 80,
            protein = 3.0,
            carbs = 10.0,
            fat = 2.0,
            servingGrams = 250.0,
            sodiumMg = 0.4,
        )
        assertEquals(400.0, result.correctedSodiumMg!!, 0.01)
    }

    @Test
    fun validateServing_flagsImplausiblePer100g() {
        val result = GroundingValidator.validateServing(
            analysisName = "Oil?",
            calories = 900,
            protein = 0.0,
            carbs = 0.0,
            fat = 100.0,
            servingGrams = 100.0,
            caloriesPer100g = 1200.0,
        )
        assertTrue(result.notes.any { it.contains("Calories per 100") })
    }

    @Test
    fun duplicateComponentNames_findsCollisions() {
        assertEquals(
            listOf("egg"),
            GroundingValidator.duplicateComponentNames(listOf("Egg", "Toast", "egg")),
        )
        assertTrue(GroundingValidator.duplicateComponentNames(listOf("Egg", "Toast")).isEmpty())
    }

    @Test
    fun foodEntry_groundingDefaultsNullForLegacyDecode() {
        val json = """
            {"name":"Apple","calories":52,"protein":0.3,"carbs":14.0,"fat":0.2,"timestamp":0,"source":"manual"}
        """.trimIndent()
        val entry = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(FoodEntry.serializer(), json)
        assertNull(entry.grounding)
        assertEquals("Apple", entry.name)
    }
}
