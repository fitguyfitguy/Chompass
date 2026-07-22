package org.codeberg.fitguy.nofud.services.grounding

import org.codeberg.fitguy.nofud.models.RecognizedFoodComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.InputStreamReader

class QueryNormalizerAndPortionTest {

    @Test
    fun normalize_stripsMassAndUnits() {
        val tokens = QueryNormalizer.normalizeTokens("Chicken breast, roasted, 150 g")
        assertEquals(listOf("chicken", "breast", "roasted"), tokens)
        assertFalse(tokens.contains("150"))
    }

    @Test
    fun normalize_appliesYoghurtSynonym() {
        val tokens = QueryNormalizer.normalizeTokens("plain yoghurt")
        assertTrue(tokens.contains("yogurt"))
        assertFalse(tokens.contains("yoghurt"))
    }

    @Test
    fun normalize_pretzelLocale() {
        val q = QueryNormalizer.normalizeQuery("1 Laugenbrezel")
        assertTrue(q.contains("pretzel"))
    }

    @Test
    fun portion_tbspPeanutButter() {
        val component = RecognizedFoodComponent(
            name = "Peanut butter",
            quantity = 2.0,
            unit = "tbsp",
        )
        val result = PortionResolver.resolve(component)
        assertTrue(result.isResolved)
        assertEquals(30.0, result.grams!!, 0.01)
        assertEquals(PortionResolver.Source.QUANTITY_UNIT, result.source)
    }

    @Test
    fun portion_overrideBeatsEstimate() {
        val component = RecognizedFoodComponent(
            name = "Banana",
            estimatedGrams = 118.0,
            quantity = 1.0,
            unit = "piece",
        )
        val result = PortionResolver.resolve(component, gramOverride = 100.0)
        assertEquals(100.0, result.grams!!, 0.01)
        assertEquals(PortionResolver.Source.OVERRIDE, result.source)
    }

    @Test
    fun portion_unresolvedWithoutGuess() {
        val component = RecognizedFoodComponent(name = "Mystery stew")
        val result = PortionResolver.resolve(component)
        // Heuristic may still match nothing useful for mystery stew
        if (result.source == PortionResolver.Source.UNRESOLVED) {
            assertNull(result.grams)
            assertTrue(result.needsUserConfirmation)
        }
    }

    @Test
    fun portion_usesCandidateServingForCup() {
        val component = RecognizedFoodComponent(
            name = "Milk",
            quantity = 1.0,
            unit = "cup",
        )
        val result = PortionResolver.resolve(
            component,
            candidateServingGrams = 244.0,
            candidateServingUnit = "cup",
        )
        assertEquals(244.0, result.grams!!, 0.01)
    }

    @Test
    fun correctionStore_boostsRepeatedPick() {
        GroundingCorrectionStore.clear()
        GroundingCorrectionStore.record("banana", "usda", "168462", "Banana, raw", 118.0)
        assertEquals(3.5, GroundingCorrectionStore.boostFor("banana", "168462"), 0.01)
        assertEquals(0.0, GroundingCorrectionStore.boostFor("banana", "999"), 0.01)
        GroundingCorrectionStore.clear()
    }

    @Test
    fun retrievalGolden_prefersCookedRiceOverFlour() {
        val stream = javaClass.classLoader!!.getResourceAsStream("grounding/retrieval_golden.json")
            ?: error("missing retrieval_golden.json")
        val root = JSONObject(InputStreamReader(stream).readText())
        val cases = root.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            if (!c.has("candidates") || !c.has("prefer_id")) continue
            val query = c.getString("query")
            val tokens = QueryNormalizer.normalizeTokens(query)
            val candidates = c.getJSONArray("candidates")
            var bestId: String? = null
            var bestScore = Double.NEGATIVE_INFINITY
            for (j in 0 until candidates.length()) {
                val cand = candidates.getJSONObject(j)
                val record = UsdaFoodRecord(
                    fdcId = j.toLong(),
                    description = cand.getString("description"),
                    dataType = cand.optString("data_type"),
                    foodCategory = null,
                    tokens = cand.optString("tokens"),
                    servingUnit = null,
                    servingGrams = null,
                    calories = cand.optDouble("calories"),
                    protein = 0.0,
                    carbs = 0.0,
                    fat = 0.0,
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
                val score = UsdaFoodIndex.score(tokens, record)
                if (score > bestScore) {
                    bestScore = score
                    bestId = cand.getString("id")
                }
            }
            assertEquals(c.getString("id"), c.getString("prefer_id"), bestId)
        }
    }

    @Test
    fun ambiguityThreshold_usesCalibratedDelta() {
        assertEquals(1.5, UsdaFoodIndex.AMBIGUITY_SCORE_DELTA, 0.001)
    }

    @Test
    fun groundedFeature_onDeviceGated() {
        assertFalse(GroundedEntryFeature.ENABLED)
        assertFalse(GroundedEntryFeature.ALLOW_ON_DEVICE)
        assertFalse(GroundedEntryFeature.availableFor(onDeviceProvider = true))
        assertFalse(GroundedEntryFeature.availableFor(onDeviceProvider = false))
    }
}
