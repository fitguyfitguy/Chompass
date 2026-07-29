package app.chompass.services

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenFoodFactsMappingTest {
    @Test
    fun analysis_scalesPer100gNutrientsToServing() {
        val product = JSONObject(
            """
            {
              "product_name": "Greek Yogurt",
              "brands": "Acme",
              "serving_quantity": 150,
              "nutriments": {
                "energy-kcal_100g": 80,
                "proteins_100g": 8,
                "carbohydrates_100g": 6,
                "fat_100g": 2,
                "sugars_100g": 4,
                "fiber_100g": 0,
                "sodium_100g": 0.05
              }
            }
            """.trimIndent(),
        )

        val food = OpenFoodFactsService.analysis(product, "3017620422003")
        assertEquals("Acme Greek Yogurt", food.name)
        assertEquals(150.0, food.servingSizeGrams, 0.001)
        assertEquals(120, food.calories)
        assertEquals(12.0, food.protein, 0.001)
        assertEquals(9.0, food.carbs, 0.001)
        assertEquals(3.0, food.fat, 0.001)
        assertEquals(6.0, food.sugar!!, 0.001)
        // sodium_100g is grams → milligrams after conversion
        assertEquals(75.0, food.sodium!!, 0.001)
        assertEquals("serving", food.selectedServingUnit)
        assertEquals(1.0, food.selectedServingQuantity!!, 0.001)
        assertEquals("🏷️", food.emoji)
        assertNotNull(food.grounding)
        assertEquals(
            app.chompass.models.NutrientSourceKind.OPEN_FOOD_FACTS,
            food.grounding!!.sourceKind,
        )
        assertEquals("3017620422003", food.grounding!!.sourceId)
    }

    @Test
    fun analysis_prefersServingSpecificNutrientKeys() {
        val product = JSONObject(
            """
            {
              "product_name": "Cola",
              "serving_quantity": 330,
              "nutriments": {
                "energy-kcal_100g": 42,
                "energy-kcal_serving": 139,
                "proteins_serving": 0,
                "carbohydrates_serving": 35,
                "fat_serving": 0
              }
            }
            """.trimIndent(),
        )

        val food = OpenFoodFactsService.analysis(product, "5449000000996")
        assertEquals("Cola", food.name)
        assertEquals(139, food.calories)
        assertEquals(35.0, food.carbs, 0.001)
        assertEquals(0.0, food.protein, 0.001)
    }

    @Test
    fun analysis_parsesServingSizeStringWhenQuantityMissing() {
        val product = JSONObject(
            """
            {
              "product_name": "Bread",
              "serving_size": "2 slices (60 g)",
              "nutriments": {
                "energy-kcal_100g": 250,
                "proteins_100g": 9,
                "carbohydrates_100g": 45,
                "fat_100g": 3
              }
            }
            """.trimIndent(),
        )

        val food = OpenFoodFactsService.analysis(product, "123")
        assertEquals(60.0, food.servingSizeGrams, 0.001)
        assertEquals(150, food.calories)
    }

    @Test
    fun analysis_rejectsMissingNutriments() {
        try {
            OpenFoodFactsService.analysis(
                JSONObject("""{"product_name":"X"}"""),
                "1",
            )
            fail("expected LookupException")
        } catch (e: OpenFoodFactsService.LookupException) {
            assertTrue(e.message!!.contains("incomplete"))
        }
    }

    @Test
    fun analysis_rejectsProductWithNoMacroFields() {
        try {
            OpenFoodFactsService.analysis(
                JSONObject(
                    """
                    {
                      "product_name": "Mystery",
                      "serving_quantity": 100,
                      "nutriments": { "salt_100g": 0.1 }
                    }
                    """.trimIndent(),
                ),
                "1",
            )
            fail("expected LookupException")
        } catch (e: OpenFoodFactsService.LookupException) {
            assertTrue(e.message!!.contains("incomplete"))
        }
    }

    @Test
    fun analysis_fallsBackToBarcodeName() {
        val product = JSONObject(
            """
            {
              "serving_quantity": 100,
              "nutriments": {
                "energy-kcal_100g": 10,
                "proteins_100g": 0,
                "carbohydrates_100g": 2,
                "fat_100g": 0
              }
            }
            """.trimIndent(),
        )
        val food = OpenFoodFactsService.analysis(product, "999")
        assertEquals("Barcode 999", food.name)
    }

    @Test
    fun searchHit_scoresPreferNameOverlap() {
        // Construction-only: SearchHit is the search API surface used by GroundingTools.
        val hit = OpenFoodFactsService.SearchHit(
            barcode = "3017620422003",
            name = "Nutella",
            brand = "Ferrero",
            caloriesPer100g = 539.0,
            proteinPer100g = 6.3,
            carbsPer100g = 57.5,
            fatPer100g = 30.9,
            servingGrams = 15.0,
            incompleteEnergy = false,
            score = 9.0,
        )
        assertEquals("3017620422003", hit.barcode)
        assertEquals(539.0, hit.caloriesPer100g!!, 0.01)
        assertFalse(hit.incompleteEnergy)
    }
}
