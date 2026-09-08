package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.ServingUnitInferenceMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Item 5: per-row micros only for strong-class models. Weak class still gets
 * the macros-only breakdown; the user toggle remains the master opt-out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EntryConstituentSchemaGateTest {
    private lateinit var prefs: PreferencesStore

    private val foodJson = """
        {"name":"Meal","calories":200,"protein":10.0,"carbs":20.0,"fat":8.0,"serving_size_grams":150.0,"unit_options":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun strongModel_requestsConstituentMicros() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.GEMINI)
        prefs.setSelectedAIModel("gemini-3.8-flash")
        prefs.setMealConstituentsEnabled(true)
        val prompt = captureAnalyzePrompt()
        assertTrue(prompt.contains("\"constituents\""))
        assertTrue(prompt.contains("added_sugar"))
        assertTrue(prompt.contains("Each constituent micronutrient MUST sum"))
    }

    @Test
    fun liteModel_requestsMacrosOnlyBreakdown() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.GEMINI)
        prefs.setSelectedAIModel("gemini-3.5-flash-lite")
        prefs.setMealConstituentsEnabled(true)
        val prompt = captureAnalyzePrompt()
        assertTrue(prompt.contains("\"constituents\""))
        assertFalse(
            "macros-only row must not list per-ingredient micros",
            prompt.contains("\"constituents\":[{\"name\":\"...\",\"calories\":0,\"protein\":0.0,\"carbs\":0.0,\"fat\":0.0,\"serving_size_grams\":0.0,\"emoji\":\"...\",\"sugar\""),
        )
        assertFalse(prompt.contains("Each constituent micronutrient MUST sum"))
        assertTrue(prompt.contains("with its own macros"))
    }

    @Test
    fun toggleOff_omitsConstituentsSchema() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.GEMINI)
        prefs.setSelectedAIModel("gemini-3.8-flash")
        prefs.setMealConstituentsEnabled(false)
        val prompt = captureAnalyzePrompt()
        assertFalse(prompt.contains("\"constituents\""))
        assertFalse(prompt.contains("Each constituent micronutrient MUST sum"))
    }

    @Test
    fun onDevice_omitsConstituentsSchema() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.ON_DEVICE)
        prefs.setSelectedAIModel(AIProvider.ON_DEVICE.defaultModel)
        prefs.setMealConstituentsEnabled(true)
        val prompt = captureAnalyzePrompt()
        assertFalse(prompt.contains("\"constituents\""))
        assertFalse(prompt.contains("Each constituent micronutrient MUST sum"))
        // #68: lean entry schema — no micro fields, no mg/mcg units sentence.
        assertFalse(prompt.contains("\"sodium\""))
        assertFalse(prompt.contains("mg for cholesterol"))
        assertTrue(prompt.contains("\"unit_options\""))
    }

    @Test
    fun photoWithLiteVisionOverride_requestsMacrosOnly() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.GEMINI)
        prefs.setSelectedAIModel("gemini-3.8-flash")
        prefs.setVisionModel(AIProvider.GEMINI, "gemini-3.5-flash-lite")
        prefs.setMealConstituentsEnabled(true)
        val prompt = captureAnalyzeFoodPrompt()
        assertTrue(prompt.contains("\"constituents\""))
        assertFalse(prompt.contains("Each constituent micronutrient MUST sum"))
        assertFalse(
            prompt.contains("\"constituents\":[{\"name\":\"...\",\"calories\":0,\"protein\":0.0,\"carbs\":0.0,\"fat\":0.0,\"serving_size_grams\":0.0,\"emoji\":\"...\",\"sugar\""),
        )
    }

    @Test
    fun photoWithoutVisionOverride_keepsSelectedModelMicros() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.GEMINI)
        prefs.setSelectedAIModel("gemini-3.8-flash")
        prefs.setVisionModel(AIProvider.GEMINI, null)
        prefs.setMealConstituentsEnabled(true)
        val prompt = captureAnalyzeFoodPrompt()
        assertTrue(prompt.contains("Each constituent micronutrient MUST sum"))
    }

    @Test
    fun promptKind_followsDispatchModelAndProvider() {
        assertEquals(
            EntryConstituentPromptKind.MICROS,
            entryConstituentPromptKind(true, AIProvider.GEMINI, "gemini-3.8-flash"),
        )
        assertEquals(
            EntryConstituentPromptKind.MACROS,
            entryConstituentPromptKind(true, AIProvider.GEMINI, "gemini-3.5-flash-lite"),
        )
        assertEquals(
            EntryConstituentPromptKind.LEAN,
            entryConstituentPromptKind(true, AIProvider.ON_DEVICE, AIProvider.ON_DEVICE.defaultModel),
        )
        // On-device is lean regardless of the constituents toggle (#68).
        assertEquals(
            EntryConstituentPromptKind.LEAN,
            entryConstituentPromptKind(false, AIProvider.ON_DEVICE, AIProvider.ON_DEVICE.defaultModel),
        )
        assertEquals(
            EntryConstituentPromptKind.NONE,
            entryConstituentPromptKind(false, AIProvider.GEMINI, "gemini-3.8-flash"),
        )
        val primary = entryJsonSchemaFor(EntryConstituentPromptKind.MICROS)
        val liteFallback = entryJsonSchemaFor(EntryConstituentPromptKind.MACROS)
        val cloudToggleOff = entryJsonSchemaFor(EntryConstituentPromptKind.NONE)
        val lean = entryJsonSchemaFor(EntryConstituentPromptKind.LEAN)
        assertTrue(primary.contains("Each constituent micronutrient MUST sum") || primary.contains("added_sugar"))
        assertTrue(entryConstituentsRuleFor(EntryConstituentPromptKind.MICROS).contains("Each constituent micronutrient MUST sum"))
        assertFalse(entryConstituentsRuleFor(EntryConstituentPromptKind.MACROS).contains("Each constituent micronutrient MUST sum"))
        assertEquals("", entryConstituentsRuleFor(EntryConstituentPromptKind.LEAN))
        assertFalse(
            liteFallback.contains("\"constituents\":[{\"name\":\"...\",\"calories\":0,\"protein\":0.0,\"carbs\":0.0,\"fat\":0.0,\"serving_size_grams\":0.0,\"emoji\":\"...\",\"sugar\""),
        )
        // Cloud toggle-off keeps the full-micro meal schema.
        assertTrue(cloudToggleOff.contains("\"sodium\""))
        assertFalse(cloudToggleOff.contains("\"constituents\""))
        // Lean: core fields only, no micros, no constituents (#68).
        assertTrue(lean.contains("\"calories\""))
        assertTrue(lean.contains("\"unit_options\""))
        assertFalse(lean.contains("\"sodium\""))
        assertFalse(lean.contains("\"vitamin_a\""))
        assertFalse(lean.contains("\"constituents\""))
        assertEquals(
            CONSTITUENT_MIN_RESPONSE_TOKENS,
            floorResponseTokensForOp("analyzeText", 1024, true),
        )
        assertEquals(1024, floorResponseTokensForOp("analyzeText", 1024, false))
    }

    private suspend fun captureAnalyzePrompt(): String {
        var captured: String? = null
        val service = FoodAnalysisService(
            prefs = prefs,
            callAiDelegate = { prompt, _, _ ->
                captured = prompt
                foodJson
            },
            inferenceModeForTest = ServingUnitInferenceMode.GRAMS_ONLY,
        )
        service.analyzeText("eggs and toast")
        return captured!!
    }

    private suspend fun captureAnalyzeFoodPrompt(): String {
        var captured: String? = null
        val service = FoodAnalysisService(
            prefs = prefs,
            callAiDelegate = { prompt, _, _ ->
                captured = prompt
                foodJson
            },
            inferenceModeForTest = ServingUnitInferenceMode.GRAMS_ONLY,
        )
        service.analyzeFood(byteArrayOf(1, 2, 3, 4))
        return captured!!
    }
}
