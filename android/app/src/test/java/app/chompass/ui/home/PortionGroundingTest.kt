package app.chompass.ui.home

import app.chompass.models.FoodSource
import app.chompass.models.PendingFoodAnalysisDraft
import app.chompass.models.PendingFoodInputDraft
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.ai.toMicronutrients
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

class PortionGroundingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun service() = FoodAnalysisService(
        callAiDelegate = { _, _, _ -> error("unused") },
    )

    @Test
    fun parsePositiveGrams_rejectsBlankNonPositiveAndParsesComma() {
        assertNull(parsePositiveGrams(""))
        assertNull(parsePositiveGrams("  "))
        assertNull(parsePositiveGrams("0"))
        assertNull(parsePositiveGrams("-5"))
        assertNull(parsePositiveGrams("abc"))
        assertEquals(280.0, parsePositiveGrams("280")!!, 0.0)
        assertEquals(280.5, parsePositiveGrams("280,5")!!, 0.0)
    }

    @Test
    fun shouldOfferPortionClarify_onlyForSnapFood() {
        assertTrue(shouldOfferPortionClarify(FoodSource.SNAP_FOOD))
        assertFalse(shouldOfferPortionClarify(FoodSource.SNAP_FOOD, portionPreConfirmed = true))
        assertFalse(shouldOfferPortionClarify(FoodSource.TEXT_INPUT))
        assertFalse(shouldOfferPortionClarify(FoodSource.BARCODE))
        assertFalse(shouldOfferPortionClarify(FoodSource.MANUAL))
        assertFalse(shouldOfferPortionClarify(FoodSource.NUTRITION_LABEL))
    }

    @Test
    fun appendUserMealContext_keepsNoteSeparateFromConfirmedGrams() {
        val base = "Analyze this food image."
        val withBoth = service().appendUserMealContext(base, "scrambled eggs, bacon", 280.0)
        assertTrue(withBoth.contains("Additional context from the user about this meal: scrambled eggs, bacon"))
        assertTrue(withBoth.contains("User-confirmed total edible portion: 280 g"))
        assertTrue(withBoth.contains("Treat this as ground truth"))

        val noteOnly = service().appendUserMealContext(base, "extra cheese", null)
        assertTrue(noteOnly.contains("extra cheese"))
        assertFalse(noteOnly.contains("User-confirmed"))

        val gramsOnly = service().appendUserMealContext(base, "  ", 150.0)
        assertFalse(gramsOnly.contains("Additional context"))
        assertTrue(gramsOnly.contains("User-confirmed total edible portion: 150 g"))

        val rejectsNonPositive = service().appendUserMealContext(base, null, 0.0)
        assertEquals(base, rejectsNonPositive)
        assertEquals(base, service().appendUserMealContext(base, null, -10.0))
    }

    @Test
    fun pendingFoodInputDraft_decodesLegacyJsonWithoutConfirmedGrams() {
        val legacy = """{"imageFilename":"img.jpg","note":"oats","source":"snapFood","createdAt":1720000000000}"""
        val draft = json.decodeFromString(PendingFoodInputDraft.serializer(), legacy)
        assertEquals("img.jpg", draft.imageFilename)
        assertEquals("oats", draft.note)
        assertNull(draft.confirmedPortionGrams)
        assertEquals(FoodSource.SNAP_FOOD, draft.source)
    }

    @Test
    fun pendingFoodInputDraft_decodesLegacyJsonWithoutTargetDate() {
        // Pre-fix drafts carry no targetDate; they must default to the
        // decode-day (matching the old behavior of logging to today).
        val before = LocalDate.now()
        val legacy = """{"imageFilename":"img.jpg","note":"oats","source":"snapFood","confirmedPortionGrams":150,"createdAt":1720000000000}"""
        val draft = json.decodeFromString(PendingFoodInputDraft.serializer(), legacy)
        assertTrue(
            "legacy draft should default to today, was ${draft.targetDate}",
            draft.targetDate == before || draft.targetDate == LocalDate.now(),
        )
    }

    @Test
    fun pendingFoodInputDraft_roundTripsTargetDate() {
        val target = LocalDate.of(2026, 8, 12)
        val draft = PendingFoodInputDraft(
            imageFilename = "meal.jpg",
            note = "chicken bowl",
            confirmedPortionGrams = 320.5,
            targetDate = target,
        )
        val encoded = json.encodeToString(PendingFoodInputDraft.serializer(), draft)
        val decoded = json.decodeFromString(PendingFoodInputDraft.serializer(), encoded)
        assertEquals(320.5, decoded.confirmedPortionGrams!!, 0.0)
        assertEquals("chicken bowl", decoded.note)
        assertEquals(target, decoded.targetDate)
    }

    @Test
    fun pendingFoodAnalysisDraft_roundTripsTargetDate() {
        val target = LocalDate.of(2026, 8, 11)
        val draft = PendingFoodAnalysisDraft(
            analysis = FoodAnalysis(
                name = "Greek yogurt with berries",
                calories = 295,
                protein = 23.2,
                carbs = 33.8,
                fat = 6.3,
                servingSizeGrams = 250.0,
            ),
            source = FoodSource.TEXT_INPUT,
            targetDate = target,
        )
        val encoded = json.encodeToString(PendingFoodAnalysisDraft.serializer(), draft)
        val decoded = json.decodeFromString(PendingFoodAnalysisDraft.serializer(), encoded)
        assertEquals("Greek yogurt with berries", decoded.analysis.name)
        assertEquals(target, decoded.targetDate)
    }

    @Test
    fun pendingFoodAnalysisDraft_decodesLegacyJsonWithoutTargetDate() {
        val before = LocalDate.now()
        val legacy = """{"analysis":{"name":"Oats","calories":200,"protein":7,"carbs":35,"fat":3,"servingSizeGrams":100},"imageFilename":null,"source":"textInput","createdAt":1720000000000}"""
        val draft = json.decodeFromString(PendingFoodAnalysisDraft.serializer(), legacy)
        assertTrue(
            "legacy draft should default to today, was ${draft.targetDate}",
            draft.targetDate == before || draft.targetDate == LocalDate.now(),
        )
        assertEquals("Oats", draft.analysis.name)
    }

    @Test
    fun exactGrams_scalesMacrosDeterministicallyLikeResultSheet() {
        val analysis = FoodAnalysis(
            name = "Plate",
            calories = 400,
            protein = 30.0,
            carbs = 40.0,
            fat = 10.0,
            servingSizeGrams = 200.0,
            sugar = 5.0,
            fiber = 8.0,
        )
        val confirmedGrams = 100.0
        val scale = confirmedGrams / analysis.servingSizeGrams
        assertEquals(0.5, scale, 0.0)
        assertEquals(200, (analysis.calories * scale).roundToInt())
        assertEquals(15.0, analysis.protein * scale, 0.001)
        assertEquals(20.0, analysis.carbs * scale, 0.001)
        assertEquals(5.0, analysis.fat * scale, 0.001)
        val micros = analysis.toMicronutrients().scaled(scale, round1 = true)
        assertEquals(2.5, micros.sugar!!, 0.001)
        assertEquals(4.0, micros.fiber!!, 0.001)
    }

    @Test
    fun analyzeFood_injectsConfirmedGramsIntoPrompt() = runBlocking {
        var capturedPrompt: String? = null
        val service = FoodAnalysisService(
            callAiDelegate = { prompt, _, op ->
                if (op == "analyzeFood" || op == "analyzeFoodMulti") capturedPrompt = prompt
                """
                {"name":"Eggs","calories":200,"protein":14,"carbs":2,"fat":14,"serving_size_grams":150,"unit_options":[]}
                """.trimIndent()
            },
            inferenceModeForTest = ServingUnitInferenceMode.GRAMS_ONLY,
        )
        service.analyzeFood(
            imageBytes = byteArrayOf(1, 2, 3),
            description = "breakfast scramble",
            confirmedPortionGrams = 280.0,
        )
        val prompt = capturedPrompt!!
        assertTrue(prompt.contains("breakfast scramble"))
        assertTrue(prompt.contains("User-confirmed total edible portion: 280 g"))
    }
}
