package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FoodJsonParserTest {
    @Test
    fun extractJson_stripsMarkdownFence() {
        val raw = """
            Sure! Here you go:
            ```json
            {"name":"Apple","calories":95,"protein":0.5,"carbs":25,"fat":0.3}
            ```
        """.trimIndent()

        val json = FoodJsonParser.extractJson(raw)
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertEquals("Apple", FoodJsonParser.parseFood(raw).name)
    }

    @Test
    fun extractJson_handlesNestedBracesInsideStrings() {
        val raw = """{"name":"Soup {homemade}","calories":120,"protein":4,"carbs":18,"fat":3}"""
        val food = FoodJsonParser.parseFood("prefix $raw trailing prose")
        assertEquals("Soup {Homemade}", food.name)
        assertEquals(120, food.calories)
    }

    @Test
    fun parseFood_readsMacrosAndDefaultsMissingServingGrams() {
        val food = FoodJsonParser.parseFood(
            """{"name":"Oats","calories":150,"protein":5,"carbs":27,"fat":3,"emoji":"🥣"}""",
        )
        assertEquals("Oats", food.name)
        assertEquals(150, food.calories)
        assertEquals(5.0, food.protein, 0.001)
        assertEquals(27.0, food.carbs, 0.001)
        assertEquals(3.0, food.fat, 0.001)
        assertEquals(100.0, food.servingSizeGrams!!, 0.001)
        assertEquals("🥣", food.emoji)
    }

    @Test
    fun parseFood_readsUnitOptionsAndOptionalNutrients() {
        val food = FoodJsonParser.parseFood(
            """
            {
              "name":"Pizza slice",
              "calories":280,
              "protein":12,
              "carbs":32,
              "fat":11,
              "serving_size_grams":120,
              "fiber":2.5,
              "sodium":480,
              "unit_options":[{"unit":"slice","grams_per_unit":120,"quantity":1}]
            }
            """.trimIndent(),
        )
        assertEquals(120.0, food.servingSizeGrams!!, 0.001)
        assertEquals(2.5, food.fiber!!, 0.001)
        assertEquals(480.0, food.sodium!!, 0.001)
        assertEquals(1, food.servingUnitOptions.size)
        assertEquals("slice", food.selectedServingUnit)
        assertEquals(1.0, food.selectedServingQuantity!!, 0.001)
    }

    @Test
    fun parseFood_rejectsMissingName() {
        try {
            FoodJsonParser.parseFood("""{"calories":100,"protein":1,"carbs":1,"fat":1}""")
            fail("expected InvalidResponse")
        } catch (_: AiError.InvalidResponse) {
            // expected
        }
    }

    @Test
    fun parseFood_rejectsNonJson() {
        try {
            FoodJsonParser.parseFood("no json here")
            fail("expected InvalidResponse")
        } catch (_: AiError.InvalidResponse) {
            // expected
        }
    }

    @Test
    fun parseLabel_scalesToServing() {
        val label = FoodJsonParser.parseLabel(
            """
            {
              "name":"Yogurt",
              "calories_per_100g":80,
              "protein_per_100g":4,
              "carbs_per_100g":8,
              "fat_per_100g":2,
              "serving_size_grams":150
            }
            """.trimIndent(),
        )
        val scaled = label.scaled(150.0)
        assertEquals("Yogurt", scaled.name)
        assertEquals(120, scaled.calories)
        assertEquals(6.0, scaled.protein, 0.001)
        assertEquals(150.0, scaled.servingSizeGrams!!, 0.001)
    }

    @Test
    fun parseLabel_requiresPer100gMacros() {
        try {
            FoodJsonParser.parseLabel("""{"name":"X","calories_per_100g":10}""")
            fail("expected InvalidResponse")
        } catch (_: AiError.InvalidResponse) {
            // expected
        }
    }

    @Test
    fun parseGoalCalculation_clampsRanges() {
        val goals = FoodJsonParser.parseGoalCalculation(
            """{"calories":99999,"protein":-5,"carbs":50,"fat":40,"reason":"test"}""",
        )
        assertEquals(6000, goals.calories)
        assertEquals(0, goals.protein)
        assertEquals(50, goals.carbs)
        assertEquals(40, goals.fat)
        assertEquals("test", goals.reason)
    }

    @Test
    fun parseHealthEnergyGoalSuggestion_readsCalories() {
        val suggestion = FoodJsonParser.parseHealthEnergyGoalSuggestion(
            """{"calories":2200,"reason":"active day"}""",
        )
        assertEquals(2200, suggestion.calories)
        assertEquals("active day", suggestion.reason)
        assertNull(
            FoodJsonParser.parseHealthEnergyGoalSuggestion("""{"calories":"1800"}""").reason,
        )
    }
}
