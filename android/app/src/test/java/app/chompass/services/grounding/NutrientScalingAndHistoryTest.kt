package app.chompass.services.grounding

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.NutrientSourceKind
import app.chompass.services.ai.FoodAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NutrientScalingAndHistoryTest {
    @Test
    fun cappedHistoryBoost_neverExceedsMax() {
        assertEquals(1.5, NutrientScaling.cappedHistoryBoost(9.0), 0.001)
        assertEquals(0.0, NutrientScaling.cappedHistoryBoost(-1.0), 0.001)
        assertEquals(0.8, NutrientScaling.cappedHistoryBoost(0.8), 0.001)
    }

    @Test
    fun scaleAnalysis_scalesMacrosFromPer100g() {
        val base = FoodAnalysis(
            name = "Banana",
            calories = 89,
            protein = 1.1,
            carbs = 22.8,
            fat = 0.3,
            servingSizeGrams = 100.0,
        )
        val scaled = NutrientScaling.scaleAnalysis(base, 200.0)
        assertEquals(178, scaled.calories)
        assertEquals(2.2, scaled.protein, 0.001)
        assertEquals(45.6, scaled.carbs, 0.001)
        assertEquals(200.0, scaled.servingSizeGrams!!, 0.001)
    }

    @Test
    fun sumAnalyses_addsComponents() {
        val a = FoodAnalysis("Egg", 70, 6.0, 0.5, 5.0, 50.0)
        val b = FoodAnalysis("Toast", 80, 3.0, 14.0, 1.0, 28.0)
        val sum = NutrientScaling.sumAnalyses("Breakfast", "🍳", listOf(a, b))
        assertEquals(150, sum.calories)
        assertEquals(9.0, sum.protein, 0.001)
        assertEquals(14.5, sum.carbs, 0.001)
        assertEquals(78.0, sum.servingSizeGrams!!, 0.001)
        assertEquals("🍳", sum.emoji)
    }

    @Test
    fun historySearch_ranksExactNameHighest() {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val entries = listOf(
            FoodEntry(
                name = "Banana",
                calories = 90,
                protein = 1.0,
                carbs = 23.0,
                fat = 0.3,
                timestamp = now.minusSeconds(86_400),
                source = FoodSource.MANUAL,
                servingSizeGrams = 118.0,
            ),
            FoodEntry(
                name = "Banana bread",
                calories = 200,
                protein = 3.0,
                carbs = 30.0,
                fat = 8.0,
                timestamp = now.minusSeconds(3_600),
                source = FoodSource.TEXT_INPUT,
                servingSizeGrams = 60.0,
            ),
        )
        val hits = ConfirmedHistorySearch.search(entries, "banana", now = now)
        assertTrue(hits.isNotEmpty())
        assertEquals("Banana", hits.first().entry.name)
        val candidate = ConfirmedHistorySearch.toCandidate(hits.first())
        assertEquals(NutrientSourceKind.HISTORY, candidate.sourceKind)
    }

    @Test
    fun historySearch_requiresLexicalOverlap() {
        val entries = listOf(
            FoodEntry(
                name = "Salmon fillet",
                calories = 200,
                protein = 22.0,
                carbs = 0.0,
                fat = 12.0,
                source = FoodSource.MANUAL,
            ),
        )
        assertTrue(ConfirmedHistorySearch.search(entries, "pizza").isEmpty())
    }

    @Test
    fun usdaTokenize_andScore_preferExact() {
        val tokens = UsdaFoodIndex.tokenize("Chicken breast raw")
        assertEquals(listOf("chicken", "breast", "raw"), tokens)
        val exact = UsdaFoodRecord(
            fdcId = 1,
            description = "chicken breast raw",
            dataType = "foundation_food",
            foodCategory = null,
            tokens = "chicken breast raw",
            servingUnit = null,
            servingGrams = null,
            calories = 120.0,
            protein = 22.0,
            carbs = 0.0,
            fat = 2.0,
            fiber = null,
            sugar = null,
            addedSugar = null,
            saturatedFat = null,
            monounsaturatedFat = null,
            polyunsaturatedFat = null,
            cholesterol = null,
            sodium = null,
            potassium = null,
            transFat = null,
            calcium = null,
            iron = null,
            magnesium = null,
            zinc = null,
            vitaminA = null,
            vitaminC = null,
            vitaminD = null,
            vitaminB12 = null,
            vitaminE = null,
            vitaminK = null,
            folate = null,
            omega3 = null,
        )
        val other = exact.copy(description = "chicken soup canned", tokens = "chicken soup canned", fdcId = 2)
        val q = UsdaFoodIndex.tokenize("chicken breast raw")
        assertTrue(UsdaFoodIndex.score(q, exact) > UsdaFoodIndex.score(q, other))
    }

    @Test
    fun usdaScore_prefersFnddsCookedOverFlour() {
        fun record(
            id: Long,
            desc: String,
            dataType: String,
            tokens: String,
        ) = UsdaFoodRecord(
            fdcId = id,
            description = desc,
            dataType = dataType,
            foodCategory = null,
            tokens = tokens,
            servingUnit = null,
            servingGrams = null,
            calories = 130.0,
            protein = 2.0,
            carbs = 28.0,
            fat = 0.3,
            fiber = null,
            sugar = null,
            addedSugar = null,
            saturatedFat = null,
            monounsaturatedFat = null,
            polyunsaturatedFat = null,
            cholesterol = null,
            sodium = null,
            potassium = null,
            transFat = null,
            calcium = null,
            iron = null,
            magnesium = null,
            zinc = null,
            vitaminA = null,
            vitaminC = null,
            vitaminD = null,
            vitaminB12 = null,
            vitaminE = null,
            vitaminK = null,
            folate = null,
            omega3 = null,
        )
        val cooked = record(
            1,
            "Rice, white, cooked",
            "survey_fndds_food",
            "rice white cooked",
        )
        val flour = record(
            2,
            "Rice flour, white",
            "foundation_food",
            "rice flour white",
        )
        val q = UsdaFoodIndex.tokenize("cooked white rice")
        assertTrue(UsdaFoodIndex.score(q, cooked) > UsdaFoodIndex.score(q, flour))
    }

    @Test
    fun usdaScore_penalizesBeverageMismatch() {
        fun record(id: Long, desc: String, tokens: String) = UsdaFoodRecord(
            fdcId = id,
            description = desc,
            dataType = "survey_fndds_food",
            foodCategory = null,
            tokens = tokens,
            servingUnit = null,
            servingGrams = null,
            calories = 40.0,
            protein = 1.0,
            carbs = 4.0,
            fat = 1.0,
            fiber = null,
            sugar = null,
            addedSugar = null,
            saturatedFat = null,
            monounsaturatedFat = null,
            polyunsaturatedFat = null,
            cholesterol = null,
            sodium = null,
            potassium = null,
            transFat = null,
            calcium = null,
            iron = null,
            magnesium = null,
            zinc = null,
            vitaminA = null,
            vitaminC = null,
            vitaminD = null,
            vitaminB12 = null,
            vitaminE = null,
            vitaminK = null,
            folate = null,
            omega3 = null,
        )
        val beer = record(1, "Beer", "beer")
        val dip = record(2, "Onion dip", "onion dip")
        val q = UsdaFoodIndex.tokenize("beer")
        assertTrue(UsdaFoodIndex.score(q, beer) > UsdaFoodIndex.score(q, dip))
    }
}
