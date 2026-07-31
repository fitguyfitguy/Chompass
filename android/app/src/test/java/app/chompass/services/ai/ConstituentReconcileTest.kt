package app.chompass.services.ai

import app.chompass.models.FoodConstituent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstituentReconcileTest {
    @Test
    fun reconcile_scalesWithinBound() {
        val analysis = FoodAnalysis(
            name = "Breakfast",
            calories = 400,
            protein = 20.0,
            carbs = 40.0,
            fat = 10.0,
            servingSizeGrams = 200.0,
            constituents = listOf(
                FoodConstituent("egg", 100, 8.0, 1.0, 5.0, 80.0),
                FoodConstituent("toast", 220, 8.0, 35.0, 4.0, 90.0),
            ),
        )
        val out = ConstituentReconcile.reconcile(analysis)
        assertEquals(2, out.constituents.size)
        assertEquals(200.0, out.constituents.sumOf { it.servingSizeGrams }, 0.15)
        assertEquals(400, out.constituents.sumOf { it.calories })
        assertEquals(20.0, out.constituents.sumOf { it.protein }, 0.15)
    }

    @Test
    fun reconcile_dropsWhenTooFar() {
        val analysis = FoodAnalysis(
            name = "Breakfast",
            calories = 400,
            protein = 20.0,
            carbs = 40.0,
            fat = 10.0,
            servingSizeGrams = 200.0,
            constituents = listOf(
                FoodConstituent("egg", 10, 1.0, 1.0, 1.0, 10.0),
                FoodConstituent("toast", 10, 1.0, 1.0, 1.0, 10.0),
            ),
        )
        val out = ConstituentReconcile.reconcile(analysis)
        assertTrue(out.constituents.isEmpty())
    }

    @Test
    fun scaleAll_scalesMacrosAndGrams() {
        val rows = listOf(FoodConstituent("egg", 100, 10.0, 1.0, 7.0, 50.0))
        val scaled = ConstituentReconcile.scaleAll(rows, 2.0)
        assertEquals(200, scaled[0].calories)
        assertEquals(100.0, scaled[0].servingSizeGrams, 0.001)
        assertEquals(20.0, scaled[0].protein, 0.001)
    }

    @Test
    fun parseFood_parsesAndReconcilesConstituents() {
        val food = FoodJsonParser.parseFood(
            """
            {
              "name":"Eggs and toast",
              "calories":300,
              "protein":20,
              "carbs":30,
              "fat":10,
              "serving_size_grams":200,
              "constituents":[
                {"name":"eggs","calories":180,"protein":14,"carbs":2,"fat":8,"serving_size_grams":100,
                 "unit_options":[{"unit":"egg","grams_per_unit":50,"quantity":2}]},
                {"name":"toast","calories":120,"protein":6,"carbs":28,"fat":2,"serving_size_grams":100}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, food.constituents.size)
        assertEquals(200.0, food.constituents.sumOf { it.servingSizeGrams }, 0.15)
        assertEquals(300, food.constituents.sumOf { it.calories })
        assertEquals("egg", food.constituents[0].selectedServingUnit)
    }

    @Test
    fun parseFood_acceptsIngredientAlias() {
        val food = FoodJsonParser.parseFood(
            """
            {
              "name":"Meal",
              "calories":200,
              "protein":10,
              "carbs":20,
              "fat":5,
              "serving_size_grams":100,
              "ingredients":[
                {"name":"a","calories":100,"protein":5,"carbs":10,"fat":2,"serving_size_grams":50},
                {"name":"b","calories":100,"protein":5,"carbs":10,"fat":3,"serving_size_grams":50}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, food.constituents.size)
    }
}
