package app.chompass.services.ai

import android.app.Application
import app.chompass.data.GoalRecalcTierOverride
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.services.WeightAnalysisService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
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
 * Codeberg #123 Goal recalc prompt override: AUTO keeps the model-name rules
 * (an unclassified local/custom model runs the SMART prompt), SAFE forces the
 * compact prompt, SMART beats even a small-cloud-model name match.
 *
 * OPENROUTER + custom base URL keeps the OpenAI-compatible client path
 * without the user-CA trust wrap (CUSTOM_OPENAI/OLLAMA would hit
 * AndroidCAStore, unavailable under Robolectric — same workaround as
 * OpenAIStreamFinishlessRetryTest) and is one of the providers that keeps
 * arbitrary model names, which the unclassified-model case needs (OPENAI
 * would resolve them to its lineup default, gpt-5.4-mini, a small model).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GoalTierOverrideTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    private val goalJson = """{"calories":1980,"protein":136,"carbs":257,"fat":45,"reason":"test"}"""

    /** Weight series in the past so any test-JVM zone keeps the same local dates. */
    private val weights = listOf(
        WeightEntry(date = Instant.parse("2026-08-01T12:00:00Z"), weightKg = 76.4),
        WeightEntry(date = Instant.parse("2026-08-05T12:00:00Z"), weightKg = 76.2),
    )
    private val foods = listOf(
        FoodEntry(
            name = "test", calories = 2290, protein = 100.0, carbs = 200.0, fat = 50.0,
            source = FoodSource.MANUAL, timestamp = Instant.parse("2026-08-14T12:00:00Z"),
        ),
    )

    private fun profile() = UserProfile(heightCm = 178.0, weightKg = 76.0)

    /** Thin but present forecast: aggregates exist, but the empirical gate withholds. */
    private fun thinForecast(): app.chompass.services.WeightForecast {
        val zone = java.time.ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val entries = listOf(
            WeightEntry(date = now.minus(4, ChronoUnit.DAYS), weightKg = 76.0),
            WeightEntry(date = now, weightKg = 76.2),
        )
        val dayFoods = listOf(
            FoodEntry(
                name = "test", calories = 2290, protein = 100.0, carbs = 200.0, fat = 50.0,
                source = FoodSource.MANUAL, timestamp = today.minusDays(1).atStartOfDay(zone).toInstant(),
            ),
            FoodEntry(
                name = "test", calories = 2290, protein = 100.0, carbs = 200.0, fat = 50.0,
                source = FoodSource.MANUAL, timestamp = today.minusDays(2).atStartOfDay(zone).toInstant(),
            ),
        )
        return WeightAnalysisService.compute(entries, dayFoods, profile(), now = now, zone = zone)
    }

    private fun newService(): FoodAnalysisService =
        FoodAnalysisService(
            prefs = prefs,
            okHttp = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            keyLookup = { "test-key" },
        )

    private fun completion(text: String): String {
        val choice = JSONObject()
            .put("message", JSONObject().put("content", text))
            .put("finish_reason", "stop")
        return JSONObject().put("choices", JSONArray().put(choice)).toString()
    }

    private fun geminiText(text: String): String =
        JSONObject()
            .put(
                "candidates",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))),
                    ),
                ),
            )
            .toString()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking {
            prefs.setFallbackEnabled(false)
            prefs.setCustomBaseUrl(AIProvider.OPENROUTER, server.url("/").toString())
            prefs.setCustomBaseUrl(AIProvider.GEMINI, server.url("/").toString())
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        // The DataStore is a process-wide singleton: restore prefs this class
        // mutates so later tests/suites see pristine state.
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setSelectedAIModel(AIProvider.GEMINI.defaultModel)
            prefs.setFallbackEnabled(true)
            prefs.setGoalRecalcTier(GoalRecalcTierOverride.AUTO)
            prefs.setCustomBaseUrl(AIProvider.OPENROUTER, null)
            prefs.setCustomBaseUrl(AIProvider.GEMINI, null)
        }
    }

    @Test
    fun auto_keepsSmartForUnclassifiedLocalModel() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.OPENROUTER)
        prefs.setSelectedAIModel("my-local-model")
        val service = newService()
        server.enqueue(MockResponse().setResponseCode(200).setBody(completion(goalJson)))

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(
            "AUTO must keep the model-name rules: an unclassified local model runs the SMART prompt",
            body.contains("RAW INTAKE, last 14 days"),
        )
        assertEquals(GoalRecalcTier.SMART, result.tier)
    }

    @Test
    fun safeOverrideForcesCompactPrompt() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.OPENROUTER)
        prefs.setSelectedAIModel("my-local-model")
        prefs.setGoalRecalcTier(GoalRecalcTierOverride.SAFE)
        val service = newService()
        server.enqueue(MockResponse().setResponseCode(200).setBody(completion(goalJson)))

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        val body = server.takeRequest().body.readUtf8()
        assertFalse("forced SAFE must withhold the intake table", body.contains("RAW INTAKE"))
        assertTrue("forced SAFE must carry the thin-data gate", body.contains("too thin"))
        assertEquals(GoalRecalcTier.SAFE, result.tier)
    }

    @Test
    fun smartOverrideBeatsSmallModelName() = runBlocking {
        prefs.setSelectedAIProvider(AIProvider.GEMINI)
        prefs.setSelectedAIModel("gemini-3.5-flash-lite")
        prefs.setGoalRecalcTier(GoalRecalcTierOverride.SMART)
        val service = newService()
        server.enqueue(MockResponse().setResponseCode(200).setBody(geminiText(goalJson)))

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(
            "SMART override must beat the small-model name match",
            body.contains("RAW INTAKE, last 14 days"),
        )
        assertEquals(GoalRecalcTier.SMART, result.tier)
    }
}
