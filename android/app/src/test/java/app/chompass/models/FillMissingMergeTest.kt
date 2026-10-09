package app.chompass.models

import app.chompass.services.ai.FoodAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only-missing merge semantics for [fillMissingFrom]: zero macros and null
 * micros are written from the analysis, never the other way around; grams
 * differences scale every written value; constituent rows fill by normalized
 * name match and unmatched analysis rows are dropped.
 */
class FillMissingMergeTest {

    private fun mealieEntry(
        servingGrams: Double? = null,
        fiber: Double? = null,
        constituents: List<FoodConstituent> = emptyList(),
        signature: String? = null,
    ) = FoodEntry(
        name = "Chicken bowl",
        calories = 0,
        protein = 0.0,
        carbs = 0.0,
        fat = 12.5,
        sugar = 2.0,
        fiber = fiber,
        servingSizeGrams = servingGrams,
        source = FoodSource.MANUAL,
        constituents = constituents,
        microsCompositionSignature = signature,
    )

    private fun analysis(
        calories: Int = 0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        servingGrams: Double? = null,
        fiber: Double? = null,
        sugar: Double? = null,
        constituents: List<FoodConstituent> = emptyList(),
    ) = FoodAnalysis(
        name = "Chicken bowl",
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        servingSizeGrams = servingGrams,
        fiber = fiber,
        sugar = sugar,
        constituents = constituents,
    )

    @Test
    fun fillsZeroMacros_nullMicros_keepsStoredValues() {
        val merged = mealieEntry().fillMissingFrom(
            analysis(calories = 540, protein = 42.0, carbs = 60.0, fat = 30.0, fiber = 4.0, sugar = 9.0),
        )

        assertEquals(540, merged.calories)
        assertEquals(42.0, merged.protein, 1e-9)
        assertEquals(60.0, merged.carbs, 1e-9)
        // Stored non-zero fat stays; analysis fat (30) must NOT overwrite.
        assertEquals(12.5, merged.fat, 1e-9)
        assertEquals(4.0, merged.fiber!!, 1e-9)
        // Stored non-null sugar stays; analysis sugar (9) must NOT overwrite.
        assertEquals(2.0, merged.sugar!!, 1e-9)
    }

    @Test
    fun analysisNullField_keepsStoredNonNull() {
        val merged = mealieEntry(fiber = 6.0).fillMissingFrom(analysis(calories = 100, protein = 5.0, carbs = 10.0))

        assertEquals(6.0, merged.fiber!!, 1e-9)
    }

    @Test
    fun zeroAnalysisValue_doesNotFill() {
        val merged = mealieEntry().fillMissingFrom(analysis())

        assertEquals(0, merged.calories)
        assertEquals(0.0, merged.protein, 1e-9)
        assertNull(merged.fiber)
    }

    @Test
    fun gramsFactor_scalesWrittenValues_1dpMicros() {
        // Entry is a 300 g portion; the analysis describes a 100 g serving.
        val merged = mealieEntry(servingGrams = 300.0).fillMissingFrom(
            analysis(calories = 180, protein = 12.34, carbs = 20.0, fat = 5.0, servingGrams = 100.0, fiber = 1.23),
        )

        assertEquals(540, merged.calories)
        assertEquals(37.0, merged.protein, 1e-9) // 12.34 * 3 → 37.0 (1 dp)
        assertEquals(60.0, merged.carbs, 1e-9)
        assertEquals(12.5, merged.fat, 1e-9) // stored value untouched, never scaled
        assertEquals(3.7, merged.fiber!!, 1e-9)
    }

    @Test
    fun sameGrams_noScaling() {
        val merged = mealieEntry(servingGrams = 200.0).fillMissingFrom(
            analysis(calories = 200, protein = 10.0, carbs = 20.0, fat = 6.0, servingGrams = 200.0),
        )

        assertEquals(200, merged.calories)
        assertEquals(10.0, merged.protein, 1e-9)
    }

    @Test
    fun constituentRows_fillByName_unmatchedDropped() {
        val entry = mealieEntry(
            constituents = listOf(
                FoodConstituent("Rice", 0, 0.0, 0.0, 0.0, servingSizeGrams = 150.0),
                FoodConstituent("Chicken", 260, 32.0, 0.0, 6.0, servingSizeGrams = 150.0),
            ),
            signature = "stale-signature",
        )
        val merged = entry.fillMissingFrom(
            analysis(
                calories = 420,
                protein = 32.0,
                carbs = 45.0,
                fat = 6.0,
                constituents = listOf(
                    FoodConstituent("rice", 130, 2.7, 28.0, 0.3, servingSizeGrams = 100.0),
                    FoodConstituent("broccoli", 50, 4.0, 10.0, 0.0, servingSizeGrams = 100.0),
                ),
            ),
        )

        // "Rice" ↔ "rice" match (trim + lowercase, favoriteKey convention),
        // all-zero row filled; Chicken (partial zeros) untouched.
        assertEquals(130, merged.constituents[0].calories)
        assertEquals(2.7, merged.constituents[0].protein, 1e-9)
        assertEquals(28.0, merged.constituents[0].carbs, 1e-9)
        assertEquals(0.3, merged.constituents[0].fat, 1e-9)
        assertEquals(260, merged.constituents[1].calories)
        // Broccoli has no stored name match → dropped, never appended.
        assertEquals(2, merged.constituents.size)
        // Signature re-stamped from the merged mix.
        assertEquals(microsCompositionSignature(merged.constituents), merged.microsCompositionSignature)
        assertNotEquals("stale-signature", merged.microsCompositionSignature)
    }

    @Test
    fun nothingFillable_returnsSameInstance() {
        val entry = mealieEntry(
            fiber = 4.0,
            constituents = listOf(FoodConstituent("Chicken", 260, 32.0, 0.0, 6.0, servingSizeGrams = 150.0)),
        )

        assertSame(entry, entry.fillMissingFrom(analysis(calories = 0, protein = 0.0, carbs = 0.0, fat = 0.0)))
    }

    @Test
    fun mealieShapeEndToEnd_fillResponse_landsInEmptyFieldsOnly() {
        // The plan's acceptance shape: Mealie entry (calories 0, constituents
        // all-zero, fiber null) + fill response {540 kcal, 42 P, fiber 4}.
        val entry = mealieEntry(
            fiber = null,
            constituents = listOf(FoodConstituent("chicken and rice", 0, 0.0, 0.0, 0.0, servingSizeGrams = 400.0)),
        )
        val merged = entry.fillMissingFrom(
            analysis(
                calories = 540,
                protein = 42.0,
                carbs = 55.0,
                fat = 0.0,
                fiber = 4.0,
                constituents = listOf(
                    FoodConstituent("chicken and rice", 540, 42.0, 55.0, 0.0, servingSizeGrams = 400.0),
                ),
            ),
        )

        assertEquals(540, merged.calories)
        assertEquals(42.0, merged.protein, 1e-9)
        assertEquals(55.0, merged.carbs, 1e-9)
        assertEquals(4.0, merged.fiber!!, 1e-9)
        assertEquals(12.5, merged.fat, 1e-9)
        assertEquals(540, merged.constituents.single().calories)
        assertTrue(merged.gapReport().missingMacros.isEmpty())
    }
}
