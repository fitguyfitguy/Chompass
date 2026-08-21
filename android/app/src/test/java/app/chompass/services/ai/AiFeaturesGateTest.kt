package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #20 phase 2: the master AI-features switch (`aiFeaturesEnabled`).
 * With it off, no data may reach an LLM provider: `FoodAnalysisService.callAi`
 * throws [AiError.Disabled] before any prompt build / key read / network, the
 * coach throws before assembling the system prompt, goal calculation returns
 * the deterministic formula values, and the serving-unit AI call degrades to
 * heuristics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AiFeaturesGateTest {
    private lateinit var prefs: PreferencesStore

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking {
            prefs.setAiFeaturesEnabled(true)
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setFallbackEnabled(false)
        }
    }

    @After
    fun tearDown() {
        // The DataStore is a process-wide singleton: restore every pref this
        // class mutates so later suites (FoodAnalysisWatchdogTest & co.) see
        // pristine state — a leaked AI_CALL mode adds a spurious serving-unit
        // request to their MockWebServer expectations.
        runBlocking {
            prefs.setAiFeaturesEnabled(true)
            prefs.setFallbackEnabled(true)
            prefs.setServingUnitInferenceMode(ServingUnitInferenceMode.GRAMS_ONLY)
        }
    }

    private fun serviceWithKeys(): FoodAnalysisService = FoodAnalysisService(
        prefs = prefs,
        keyLookup = { "test-key" }, // any non-null key satisfies GEMINI's requiresApiKey check
    )

    private val foodJsonNoUnits = """
    {
      "name": "Pizza",
      "calories": 800,
      "protein": 30.0,
      "carbs": 90.0,
      "fat": 35.0,
      "serving_size_grams": 360.0,
      "unit_options": []
    }
  """.trimIndent()

    @Test
    fun aiFeaturesEnabled_defaultsTrue_andPersists() = runBlocking {
        assertTrue(prefs.aiFeaturesEnabled.first())

        prefs.setAiFeaturesEnabled(false)
        assertEquals(false, prefs.aiFeaturesEnabled.first())

        prefs.setAiFeaturesEnabled(true)
        assertEquals(true, prefs.aiFeaturesEnabled.first())
    }

    @Test
    fun callAi_throwsDisabled_whenMasterSwitchOff() = runBlocking {
        prefs.setAiFeaturesEnabled(false)
        val service = serviceWithKeys()

        try {
            service.analyzeText("pork mince with onions")
            fail("expected AiError.Disabled")
        } catch (e: AiError) {
            assertTrue("expected Disabled, got $e", e is AiError.Disabled)
        }
    }

    @Test
    fun callAi_doesNotThrowDisabled_whenMasterSwitchOn() = runBlocking {
        // AI on: the gate must not fire — the error comes from the network path
        // (no base URL configured → InvalidUrl), proving the gate is the only
        // thing that throws Disabled.
        val service = serviceWithKeys()
        try {
            service.analyzeText("pork mince with onions")
            fail("expected an AiError")
        } catch (e: AiError) {
            assertTrue("expected non-Disabled error, got $e", e !is AiError.Disabled)
        }
    }

    @Test
    fun calculateGoals_returnsFormulaValues_whenMasterSwitchOff() = runBlocking {
        prefs.setAiFeaturesEnabled(false)
        val profile = UserProfile(
            customCalories = 2100,
            customProtein = 150,
            customCarbs = 220,
            customFat = 70,
        )
        val result = serviceWithKeys().calculateGoals(
            profile = profile,
            forecast = null,
            heightMetric = true,
            weightMetric = true,
        )

        assertEquals(profile.dailyCalories, result.calories)
        assertEquals(profile.proteinGoal, result.protein)
        assertEquals(profile.carbsGoal, result.carbs)
        assertEquals(profile.fatGoal, result.fat)
        assertTrue(result.reason?.isNotBlank() == true)
    }

    @Test
    fun servingUnitInference_degradesToHeuristic_whenMasterSwitchOff() = runBlocking {
        // The analysis itself runs through the delegate (test seam), then
        // finalizeAnalysis asks for serving units: with AI off, AI_CALL mode
        // must degrade to HEURISTIC — no second AI request, units from the name.
        prefs.setAiFeaturesEnabled(false)
        prefs.setServingUnitInferenceMode(ServingUnitInferenceMode.AI_CALL)
        val ops = mutableListOf<String>()
        val service = FoodAnalysisService(
            prefs = prefs,
            callAiDelegate = { _, _, op ->
                ops += op
                when (op) {
                    "analyzeText" -> foodJsonNoUnits
                    else -> error("unexpected op: $op")
                }
            },
        )

        val result = service.analyzeText("pepperoni pizza")

        assertEquals(listOf("analyzeText"), ops)
        assertTrue("heuristic units expected", result.servingUnitOptions.isNotEmpty())
    }

    @Test
    fun estimateOptionalNutrientGoals_throwsDisabled_whenMasterSwitchOff() = runBlocking {
        prefs.setAiFeaturesEnabled(false)

        try {
            serviceWithKeys().estimateOptionalNutrientGoals(null)
            fail("expected AiError.Disabled")
        } catch (e: AiError) {
            assertTrue("expected Disabled, got $e", e is AiError.Disabled)
        }
    }

    @Test
    fun chatService_sendMessage_throwsDisabled_whenMasterSwitchOff() = runBlocking {
        prefs.setAiFeaturesEnabled(false)
        val chat = ChatService(
            prefs = prefs,
            foodAnalysisService = serviceWithKeys(),
            keyLookup = { "test-key" },
        )

        try {
            chat.sendMessage(
                history = emptyList(),
                newUserMessage = "hello",
                profile = UserProfile(),
                weights = emptyList(),
                bodyFats = emptyList(),
                foods = emptyList(),
                heightMetric = true,
                weightMetric = true,
            )
            fail("expected AiError.Disabled")
        } catch (e: AiError) {
            assertTrue("expected Disabled, got $e", e is AiError.Disabled)
        }
    }
}
