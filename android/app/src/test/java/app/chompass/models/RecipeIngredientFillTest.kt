package app.chompass.models

import app.chompass.services.ai.FoodAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recipe-builder fill-missing round trip (WP2 batch 3): [RecipeIngredient.toFillCandidate]
 * exposes the base (1x) values as a FoodEntry the gap scan and the fill prompt can
 * read, and [RecipeIngredient.filledFrom] maps a fill-merged candidate back without
 * touching identity (id, quantityScale) or known values.
 */
class RecipeIngredientFillTest {

    private fun gappedIngredient() = RecipeIngredient(
        name = "Cooked potatoes",
        baseCalories = 0,
        baseProtein = 0.0,
        baseCarbs = 0.0,
        baseFat = 0.0,
        quantityScale = 1.5,
        baseFiber = null,
        baseSodium = 320.0,
    )

    private fun analysis(
        calories: Int = 0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        fiber: Double? = null,
    ) = FoodAnalysis(
        name = "Cooked potatoes",
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        servingSizeGrams = null,
        fiber = fiber,
    )

    @Test
    fun candidateExposesGaps_atBaseValues() {
        val candidate = gappedIngredient().toFillCandidate()
        val gaps = candidate.gapReport()
        assertTrue(gaps.hasMacroGaps)
        assertTrue(candidate.fiber == null)
        // 1x values, never scaled by quantityScale.
        assertEquals(320.0, candidate.sodium!!, 1e-9)
        // No serving info: the fill merge must run at factor 1.0.
        assertNull(candidate.servingSizeGrams)
    }

    @Test
    fun filledGap_writesOnlyMissing_backAtBase() {
        val ingredient = gappedIngredient()
        val candidate = ingredient.toFillCandidate()
        val merged = candidate.fillMissingFrom(
            analysis(calories = 260, protein = 5.0, carbs = 58.0, fat = 0.1, fiber = 4.2),
        )
        val filled = ingredient.filledFrom(merged)
        assertEquals(260, filled.baseCalories)
        assertEquals(5.0, filled.baseProtein, 1e-9)
        assertEquals(58.0, filled.baseCarbs, 1e-9)
        assertEquals(0.1, filled.baseFat, 1e-9)
        assertEquals(4.2, filled.baseFiber!!, 1e-9)
        // Scaled view follows quantityScale: totals reflect the 1.5x the user set.
        assertEquals(390, filled.calories)
        // Known values and identity pass through untouched.
        assertEquals(320.0, filled.baseSodium!!, 1e-9)
        assertEquals("Cooked potatoes", filled.name)
        assertEquals(1.5, filled.quantityScale, 1e-9)
    }

    @Test
    fun noFillChange_returnsSameInstance_ingredientUntouched() {
        val ingredient = gappedIngredient()
        val candidate = ingredient.toFillCandidate()
        // Analysis has nothing the entry lacks → fill returns `this`…
        assertSame(candidate, candidate.fillMissingFrom(analysis()))
        // …and mapping an unchanged candidate back is a no-op.
        assertEquals(ingredient, ingredient.filledFrom(candidate))
    }

    @Test
    fun knownValuesSurvive_anOverreachingAnalysis() {
        val ingredient = gappedIngredient().copy(baseCalories = 210)
        val candidate = ingredient.toFillCandidate()
        val merged = candidate.fillMissingFrom(analysis(calories = 999, protein = 5.0))
        val filled = ingredient.filledFrom(merged)
        // Only-missing rule: the stored (non-zero) base calorie value wins.
        assertEquals(210, filled.baseCalories)
        assertEquals(5.0, filled.baseProtein, 1e-9)
    }

    @Test
    fun nulledMicros_stayNulled_whenAnalysisOmitsThem() {
        val ingredient = gappedIngredient().copy(baseVitaminC = 12.0)
        val candidate = ingredient.toFillCandidate()
        val merged = candidate.fillMissingFrom(analysis(calories = 100))
        val filled = ingredient.filledFrom(merged)
        assertEquals(12.0, filled.baseVitaminC!!, 1e-9)
        assertNull(filled.baseIron)
        assertNull(filled.baseCaffeine)
    }
}
