package app.chompass.services.grounding

import app.chompass.models.FoodSource
import app.chompass.models.NutrientSourceKind
import app.chompass.export.DiaryExporter
import app.chompass.services.OpenFoodFactsService
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class SwissFoodIndexAndSearchTest {
    // --- SwissFoodRecord.toFoodAnalysis -------------------------------------

    @Test
    fun toFoodAnalysis_scalesPer100gToGrams_andTagsSwissProvenance() {
        val record = SwissFoodRecord(
            id = 42,
            lang = "en",
            name = "Emmentaler cheese, at least 45% fidm",
            calories = 408.0,
            protein = 27.0,
            carbs = 0.0,
            fat = 32.0,
            fiber = 0.0,
            sugar = 0.0,
            saturatedFat = 18.0,
            monounsaturatedFat = null,
            polyunsaturatedFat = null,
            cholesterol = 84.0,
            omega3 = null,
            sodium = 560.0,
            potassium = null,
            calcium = null,
            iron = null,
            magnesium = null,
            zinc = null,
            vitaminA = null,
            vitaminC = null,
            vitaminD = 0.5,
            vitaminB12 = 1.67,
            vitaminE = null,
            vitaminK = null,
            folate = null,
        )
        val analysis = record.toFoodAnalysis(grams = 60.0, datasetVersion = "swiss-sfdc-2026-08")

        assertEquals("Emmentaler cheese, at least 45% fidm", analysis.name)
        assertEquals((408.0 * 0.6).roundToInt(), analysis.calories)
        assertEquals(27.0 * 0.6, analysis.protein, 0.001)
        assertEquals(32.0 * 0.6, analysis.fat, 0.001)
        assertEquals(60.0, analysis.servingSizeGrams, 0.001)
        assertEquals(84.0 * 0.6, analysis.cholesterol!!, 0.001)
        assertEquals(0.5 * 0.6, analysis.vitaminD!!, 0.001)
        // Micros round to one decimal on scaling.
        assertEquals(1.0, analysis.vitaminB12!!, 0.001)

        val grounding = analysis.grounding!!
        assertEquals(NutrientSourceKind.SWISS, grounding.sourceKind)
        assertEquals("42", grounding.sourceId)
        assertEquals("swiss-sfdc-2026-08", grounding.datasetVersion)
        assertEquals(app.chompass.models.NutrientBasis.PER_100G, grounding.nutrientBasis)
    }

    // --- SwissFoodIndex.score -------------------------------------------------

    @Test
    fun score_prefersDeviceLanguageRows() {
        val de = SwissFoodRecord(
            id = 1, lang = "de", name = "Halbentrahmte Milch 1.5% Fett, UHT",
            calories = 46.0, protein = null, carbs = null, fat = null,
        )
        val en = SwissFoodRecord(
            id = 2, lang = "en", name = "Milk, half fat",
            calories = 46.0, protein = null, carbs = null, fat = null,
        )
        val tokens = SwissFoodIndex.tokenize("milch")
        val deScore = SwissFoodIndex.score(tokens, de, "de")
        val enScore = SwissFoodIndex.score(tokens, en, "de")
        assertTrue("device-language row should rank first", deScore > enScore)
        assertTrue(deScore > 0.0)

        // English fallback locale prefers the English row for "milk".
        val milkTokens = SwissFoodIndex.tokenize("milk")
        assertTrue(
            SwissFoodIndex.score(milkTokens, en, "en") > SwissFoodIndex.score(milkTokens, de, "en"),
        )
    }

    @Test
    fun score_exactNameOutranksPartialOverlap() {
        val exact = SwissFoodRecord(
            id = 1, lang = "en", name = "Oat drink",
            calories = 40.0, protein = null, carbs = null, fat = null,
        )
        val partial = SwissFoodRecord(
            id = 2, lang = "en", name = "Oat drink, with added calcium",
            calories = 40.0, protein = null, carbs = null, fat = null,
        )
        val tokens = SwissFoodIndex.tokenize("oat drink")
        assertTrue(
            SwissFoodIndex.score(tokens, exact, "en") > SwissFoodIndex.score(tokens, partial, "en"),
        )
    }

    // --- DatabaseSearchResult mapping -----------------------------------------

    @Test
    fun fromOff_mapsSearchHitToServingScaledResult() {
        val hit = OpenFoodFactsService.SearchHit(
            barcode = "7613034623230",
            name = "Milk, skimmed",
            brand = "Example Dairy",
            caloriesPer100g = 33.0,
            proteinPer100g = 3.4,
            carbsPer100g = 4.8,
            fatPer100g = 0.1,
            servingGrams = 200.0,
            incompleteEnergy = false,
            score = 5.0,
        )
        val result = DatabaseSearchResult.fromOff(hit)
        assertEquals(NutrientSourceKind.OPEN_FOOD_FACTS, result.sourceKind)
        assertEquals("7613034623230", result.sourceId)
        assertEquals("Example Dairy Milk, skimmed", result.name)
        assertEquals(200.0, result.servingGrams!!, 0.001)
        assertEquals(66.0, result.caloriesPerServing!!, 0.001)
        assertEquals(6.8, result.proteinPerServing!!, 0.001)
        assertEquals(66, result.displayCalories)
    }

    @Test
    fun fromSwiss_keepsLanguageTagAndScore() {
        val record = SwissFoodRecord(
            id = 7, lang = "de", name = "Buttermilch",
            calories = 38.0, protein = 3.4, carbs = 4.6, fat = 0.4,
        )
        val result = DatabaseSearchResult.fromSwiss(record, matchScore = 12.0)
        assertEquals(NutrientSourceKind.SWISS, result.sourceKind)
        assertEquals("7", result.sourceId)
        assertEquals("de", result.lang)
        assertEquals(100.0, result.servingGrams!!, 0.001)
        assertFalse(result.incompleteEnergy)
        assertEquals(12.0, result.matchScore, 0.001)
    }

    // --- cross-source ranking -------------------------------------------------

    @Test
    fun mergedRanking_exactSwissBeatsFuzzyOff() {
        // Exact Swiss "milk" row vs a weak OFF "milk chocolate" brand hit: after
        // normalization the exact offline match must outrank the fuzzy branded one.
        val swissRaw = 15.0
        val offRaw = 3.0
        assertTrue(
            FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.SWISS, swissRaw) >
                FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.OPEN_FOOD_FACTS, offRaw),
        )
        // ...and USDA exact also outranks the same fuzzy OFF hit.
        assertTrue(
            FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.USDA, 12.0) >
                FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.OPEN_FOOD_FACTS, offRaw),
        )
    }

    @Test
    fun mergedRanking_preservesWithinSourceOrdering() {
        val exact = FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.USDA, 13.0)
        val partial = FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.USDA, 6.0)
        assertTrue(exact > partial)
        assertEquals(1.0, FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.SWISS, 25.0), 0.001)
        assertEquals(0.0, FoodDatabaseSearch.normalizedMatchScore(NutrientSourceKind.USDA, 0.0), 0.001)
    }

    @Test
    fun fromUsda_carriesDatasetVersion() {
        val candidate = app.chompass.models.GroundingCandidate(
            sourceKind = NutrientSourceKind.USDA,
            sourceId = "167765",
            displayName = "Egg, whole, raw, fresh",
            score = 6.0,
            caloriesPer100g = 143.0,
            proteinPer100g = 12.6,
            carbsPer100g = 0.7,
            fatPer100g = 9.5,
            servingSizeGrams = 50.0,
            datasetVersion = "fdc-fixture",
        )
        val result = DatabaseSearchResult.fromUsda(candidate)
        assertEquals(72, result.displayCalories)
        assertEquals("fdc-fixture", result.datasetVersion)
    }

    // --- wire format round-trips ----------------------------------------------

    @Test
    fun swissSourceKind_serializesToSnakeCaseWireValue() {
        assertEquals(
            "\"swiss\"",
            Json.encodeToString(NutrientSourceKind.serializer(), NutrientSourceKind.SWISS),
        )
        assertEquals(
            NutrientSourceKind.SWISS,
            Json.decodeFromString(NutrientSourceKind.serializer(), "\"swiss\""),
        )
    }

    @Test
    fun searchFoodSource_exportsAsSearch() {
        assertEquals("search", DiaryExporter.sourceLabel(FoodSource.SEARCH))
    }

    @Test
    fun swissRecord_hasNoServingUnits() {
        // The Swiss bundle ships per-100g rows without household servings; the
        // result sheet lets the user type grams instead.
        val record = SwissFoodRecord(
            id = 1, lang = "en", name = "Almond",
            calories = 624.0, protein = 25.6, carbs = 7.8, fat = 52.1,
        )
        val analysis = record.toFoodAnalysis(100.0, "v")
        assertTrue(analysis.servingUnitOptions.isEmpty())
        assertNull(analysis.selectedServingUnit)
        assertNull(analysis.selectedServingQuantity)
    }
}
