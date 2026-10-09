package app.chompass.services.ai

import android.app.Application
import android.util.Log
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.services.PerfLog
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * WP2 fill-missing: [FoodAnalysisService.fillEntry] is a metadata refresh,
 * not a re-read of the meal. It runs the entry prompt/schema text-only, and
 * deliberately skips the #97 A5 length-split retry ([mergeSplitAnalyses]
 * would fabricate summed duplicates on a fill) and [FoodAnalysisService]
 * finalize's serving-unit fallback (an extra AI call a fill never needs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FillEntryServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    /** Unparseable but complete streamed reply. */
    private fun garbage(): String = chunk("not json at all", finishReason = "stop")

    private fun chunk(text: String?, finishReason: String? = null): String {
        val delta = JSONObject().apply { if (text != null) put("content", text) }
        val choice = JSONObject().put("delta", delta)
        if (finishReason != null) choice.put("finish_reason", finishReason)
        return "data: " + JSONObject().put("choices", JSONArray().put(choice)) + "\n\n"
    }

    private fun fillJson(): String = """
        {"name":"Chicken bowl","calories":540,"protein":42.0,"carbs":60.0,"fat":0.0,
        "serving_size_grams":400.0,"emoji":"🍲","fiber":4.0,
        "constituents":[{"name":"chicken and rice","calories":540,"protein":42.0,"carbs":60.0,"fat":0.0,
        "serving_size_grams":400.0}],"unit_options":[]}
    """.trimIndent().replace("\n", "")

    /** Mealie-import shape: zero macros, all-zero constituent rows, null micros. */
    private fun mealieEntry(): FoodEntry = FoodEntry(
        name = "Chicken bowl",
        calories = 0,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        source = FoodSource.MANUAL,
        constituents = listOf(
            FoodConstituent("chicken and rice", 0, 0.0, 0.0, 0.0, servingSizeGrams = 400.0),
        ),
    )

    private fun newService(): FoodAnalysisService =
        FoodAnalysisService(
            prefs = prefs,
            okHttp = OkHttpClient(),
            keyLookup = { "test-key" },
            inferenceModeForTest = ServingUnitInferenceMode.GRAMS_ONLY,
        )

    /** Recorded bodies drain on first read — parse each request body exactly once. */
    private fun bodyOf(request: RecordedRequest): JSONObject = JSONObject(request.body.readUtf8())

    private fun promptOf(body: JSONObject): String {
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        return content.getJSONObject(content.length() - 1).getString("text")
    }

    private fun splitLogLines(): List<String> =
        ShadowLog.getLogs()
            .filter { it.type == Log.INFO && it.tag == PerfLog.TAG && it.msg.contains("split=") }
            .map { it.msg }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.OPENROUTER)
            // A "flash-lite" class model: entry legs run the MACROS constituents
            // schema, so the ladder has no micros downshift leg — a failing
            // response must propagate after exactly ONE request.
            prefs.setSelectedAIModel("gemini-2.5-flash-lite")
            prefs.setCustomBaseUrl(AIProvider.OPENROUTER, server.url("/v1").toString())
            prefs.setFallbackEnabled(false)
            prefs.setMealConstituentsEnabled(true)
            prefs.setMaxResponseTokens(1024)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lengthFailure_propagates_singleRequest_noSplit() = runBlocking {
        server.enqueue(MockResponse().setBody(garbage()))

        val error = runCatching { newService().fillEntry(mealieEntry()) }.exceptionOrNull()

        assertTrue("terminal error propagates", error is AiError.InvalidResponse)
        // No A5 split retry (which would fan out over batches), no downshift
        // leg, no serving-unit fallback — one request, error surfaces as-is.
        assertEquals(1, server.requestCount)
        assertTrue(splitLogLines().isEmpty())

        val prompt = promptOf(bodyOf(server.takeRequest()))
        assertTrue(prompt.contains("Missing fields to estimate"))
        assertTrue(prompt.contains("calories, protein, carbs, fat"))
        assertTrue(prompt.contains("fiber"))
        assertTrue(prompt.contains("Chicken bowl"))
        assertTrue(prompt.contains("chicken and rice"))
    }

    @Test
    fun success_parsesAsIs_exactlyOneRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(chunk(fillJson(), finishReason = "stop")))

        val result = newService().fillEntry(mealieEntry())

        assertEquals(540, result.calories)
        assertEquals(42.0, result.protein, 1e-9)
        assertEquals(4.0, result.fiber!!, 1e-9)
        assertEquals(listOf("Chicken and Rice"), result.constituents.map { it.name })
        // No finalize pass, no serving-unit inference call behind the parse.
        assertEquals(1, server.requestCount)
        assertTrue(splitLogLines().isEmpty())
    }
}
