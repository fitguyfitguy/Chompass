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

    @Test
    fun scaled_scalesMicrosWithRoundingAndClamp() {
        val row = FoodConstituent(
            "egg", 100, 10.0, 1.0, 7.0, 50.0,
            sugar = 1.21,
            sodium = -3.0,
            potassium = 123.45,
        )
        val scaled = row.scaled(2.0)
        assertEquals(2.4, scaled.sugar!!, 0.001)
        // Negative input clamps to 0 (per-100g micros are never negative).
        assertEquals(0.0, scaled.sodium!!, 0.001)
        assertEquals(246.9, scaled.potassium!!, 0.001)
        assertEquals(null, scaled.caffeine)
        // Factor 1 is a no-op for micros too.
        assertEquals(row, row.microsScaled(1.0))
    }

    @Test
    fun reconcile_scalesConstituentMicrosByGramsFactor() {
        val analysis = FoodAnalysis(
            name = "Breakfast",
            calories = 400,
            protein = 20.0,
            carbs = 40.0,
            fat = 10.0,
            servingSizeGrams = 200.0,
            constituents = listOf(
                FoodConstituent("egg", 100, 8.0, 1.0, 5.0, 80.0, sugar = 4.0, iron = 2.0),
                FoodConstituent("toast", 220, 8.0, 35.0, 4.0, 90.0, sugar = 9.0, fiber = 3.0),
            ),
        )
        val out = ConstituentReconcile.reconcile(analysis)
        assertEquals(2, out.constituents.size)
        // Grams rebalance to the 200 g meal; micros ride each row's own grams
        // factor (per-100g semantics) — 94.1/80 and 105.9/90.
        assertEquals(94.1, out.constituents[0].servingSizeGrams, 0.001)
        assertEquals(4.7, out.constituents[0].sugar!!, 0.001)
        assertEquals(2.4, out.constituents[0].iron!!, 0.001)
        assertEquals(105.9, out.constituents[1].servingSizeGrams, 0.001)
        assertEquals(10.6, out.constituents[1].sugar!!, 0.001)
        assertEquals(3.5, out.constituents[1].fiber!!, 0.001)
    }

    @Test
    fun parseFood_mapsConstituentMicroKeys() {
        val food = FoodJsonParser.parseFood(
            """
            {
              "name":"Bowl",
              "calories":300,
              "protein":20,
              "carbs":30,
              "fat":10,
              "serving_size_grams":200,
              "constituents":[
                {"name":"eggs","calories":180,"protein":14,"carbs":2,"fat":8,"serving_size_grams":100,
                 "sugar":0.5,"added_sugar":0.2,"fiber":0.0,"saturated_fat":3.1,
                 "monounsaturated_fat":4.2,"polyunsaturated_fat":1.7,"cholesterol":370,
                 "sodium":-5,"potassium":132,"trans_fat":0.1,"calcium":50,"iron":1.2,
                 "magnesium":12,"zinc":1.3,"vitamin_a":90,"vitamin_c":0.4,"vitamin_d":2.0,
                 "vitamin_b12":0.5,"vitamin_e":1.0,"vitamin_k":0.3,"folate":47,
                 "omega_3":0.2,"caffeine":10}
              ]
            }
            """.trimIndent(),
        )
        val row = food.constituents.single()
        assertEquals(1.0, row.sugar!!, 0.001)
        assertEquals(0.4, row.addedSugar!!, 0.001)
        assertEquals(0.0, row.fiber!!, 0.001)
        assertEquals(6.2, row.saturatedFat!!, 0.001)
        assertEquals(8.4, row.monounsaturatedFat!!, 0.001)
        assertEquals(3.4, row.polyunsaturatedFat!!, 0.001)
        assertEquals(740.0, row.cholesterol!!, 0.001)
        // Negative input clamps to 0 via InputSanitizer.micro (then rides the
        // 2x grams factor of the 100 g row inside the 200 g meal).
        assertEquals(0.0, row.sodium!!, 0.001)
        assertEquals(264.0, row.potassium!!, 0.001)
        assertEquals(0.2, row.transFat!!, 0.001)
        assertEquals(100.0, row.calcium!!, 0.001)
        assertEquals(2.4, row.iron!!, 0.001)
        assertEquals(24.0, row.magnesium!!, 0.001)
        assertEquals(2.6, row.zinc!!, 0.001)
        assertEquals(180.0, row.vitaminA!!, 0.001)
        assertEquals(0.8, row.vitaminC!!, 0.001)
        assertEquals(4.0, row.vitaminD!!, 0.001)
        assertEquals(1.0, row.vitaminB12!!, 0.001)
        assertEquals(2.0, row.vitaminE!!, 0.001)
        assertEquals(0.6, row.vitaminK!!, 0.001)
        assertEquals(94.0, row.folate!!, 0.001)
        assertEquals(0.4, row.omega3!!, 0.001)
        // Constituents never carry caffeine.
        assertEquals(null, row.caffeine)
    }
}
