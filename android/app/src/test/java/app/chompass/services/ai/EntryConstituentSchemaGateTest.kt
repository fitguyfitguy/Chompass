package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.ServingUnitInferenceMode
import kotlinx.coroutines.runBlocking
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
}
