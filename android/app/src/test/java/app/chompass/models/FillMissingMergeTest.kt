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
    fun constituentRows_containmentFallback_mealieStyleNames() {
        val entry = mealieEntry(
            constituents = listOf(
                FoodConstituent("200 g flour", 0, 0.0, 0.0, 0.0, servingSizeGrams = 200.0),
                FoodConstituent("2 eggs", 0, 0.0, 0.0, 0.0, servingSizeGrams = 100.0),
            ),
        )
        val merged = entry.fillMissingFrom(
            analysis(
                constituents = listOf(
                    FoodConstituent("flour", 720, 26.0, 152.0, 2.0, servingSizeGrams = 200.0),
                    FoodConstituent("eggs", 140, 12.0, 1.0, 10.0, servingSizeGrams = 100.0),
                ),
            ),
        )

        // Exact pass finds nothing (stored names carry grams prefixes);
        // containment pairs "200 g flour" ↔ "flour" and "2 eggs" ↔ "eggs".
        assertEquals(720, merged.constituents[0].calories)
        assertEquals(26.0, merged.constituents[0].protein, 1e-9)
        assertEquals(152.0, merged.constituents[0].carbs, 1e-9)
        assertEquals(140, merged.constituents[1].calories)
        assertEquals(12.0, merged.constituents[1].protein, 1e-9)
    }

    @Test
    fun constituentRows_containment_noWordBoundary_noFill() {
        val entry = mealieEntry(constituents = listOf(FoodConstituent("Rice", 0, 0.0, 0.0, 0.0, servingSizeGrams = 150.0)))
        val analysis = analysis(constituents = listOf(FoodConstituent("Price", 200, 5.0, 40.0, 1.0, servingSizeGrams = 150.0)))

        // "rice" sits inside "price" but not at a word boundary — no fill.
        assertSame(entry, entry.fillMissingFrom(analysis))
    }

    @Test
    fun constituentRows_containment_shortNameGuard() {
        val entry = mealieEntry(constituents = listOf(FoodConstituent("Rye bread", 0, 0.0, 0.0, 0.0, servingSizeGrams = 60.0)))
        val analysis = analysis(constituents = listOf(FoodConstituent("Ry", 250, 8.0, 48.0, 2.0, servingSizeGrams = 60.0)))

        // Shorter side under 3 characters: boundary hit, still no fill.
        assertSame(entry, entry.fillMissingFrom(analysis))
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

    @Test
    fun cascadeCopy_keepsOwnValuesAndServing_fillsOnlyMissing() {
        // Cascade shape: a favorite template's fill analysis applied to a
        // same-name today copy that carries its own serving and some filled
        // values. Stored values stay; only zero/null fields take the analysis,
        // scaled to the copy's grams (micros at 1 decimal).
        val copy = mealieEntry(servingGrams = 300.0).copy(
            calories = 500,
            protein = 30.0,
            fat = 0.0, // stays a gap
        )
        val merged = copy.fillMissingFrom(
            analysis(calories = 640, protein = 42.0, carbs = 58.0, fat = 24.0, servingGrams = 420.0, fiber = 9.0),
        )

        assertEquals(500, merged.calories)
        assertEquals(30.0, merged.protein, 1e-9)
        assertEquals(2.0, merged.sugar!!, 1e-9) // own value kept
        fun scale(v: Double) = kotlin.math.round(v * 300.0 / 420.0 * 10) / 10.0
        assertEquals(scale(58.0), merged.carbs, 1e-9)
        assertEquals(scale(24.0), merged.fat, 1e-9)
        assertEquals(scale(9.0), merged.fiber!!, 1e-9)
        assertEquals(300.0, merged.servingSizeGrams!!, 1e-9)
    }
}
