package app.chompass.services.ai

import android.app.Application
import android.util.Log
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * #97 (A5): a long multi-item text entry that exhausts the recovery ladder
 * (one-shot retry, macros downshift) is retried as sequential per-batch
 * analyses of its parsed items (4 per batch, ≤4 batches) and the results
 * merged — totals sum, constituents concatenate. Anything unsplittable, over
 * the batch cap, or failing mid-split propagates the original error, and a
 * single-shot success must stay exactly one request (no eager splitting).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EntryAutoSplitTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    /** Unparseable but complete streamed reply — burns one ladder leg. */
    private fun garbage(): String = chunk("not json at all", finishReason = "stop")

    private fun chunk(text: String?, finishReason: String? = null): String {
        val delta = JSONObject().apply { if (text != null) put("content", text) }
        val choice = JSONObject().put("delta", delta)
        if (finishReason != null) choice.put("finish_reason", finishReason)
        return "data: " + JSONObject().put("choices", JSONArray().put(choice)) + "\n\n"
    }

    private fun entryJson(
        name: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        servingGrams: Double,
        constituents: String,
    ): String = """
        {"name":"$name","calories":$calories,"protein":$protein,"carbs":$carbs,"fat":$fat,
        "serving_size_grams":$servingGrams,"emoji":"🍽️","constituents":[$constituents],"unit_options":[]}
    """.trimIndent().replace("\n", "")

    // Batch rows sum exactly to their batch totals (reconcile keeps them).
    private val batchOneJson = entryJson(
        "Eggs and oats", 300, 18.0, 40.0, 8.0, 250.0,
        """{"name":"Eggs","calories":140,"protein":10.0,"carbs":2.0,"fat":6.0,"serving_size_grams":100.0},""" +
            """{"name":"Oats","calories":160,"protein":8.0,"carbs":38.0,"fat":2.0,"serving_size_grams":150.0}""",
    )

    private val batchTwoJson = entryJson(
        "Rice and beans", 200, 14.0, 20.0, 6.0, 180.0,
        """{"name":"Rice","calories":120,"protein":8.0,"carbs":8.0,"fat":2.0,"serving_size_grams":100.0},""" +
            """{"name":"Beans","calories":80,"protein":6.0,"carbs":12.0,"fat":4.0,"serving_size_grams":80.0}""",
    )

    /** Both #97 repro formats: newline items, a dash list line, and a quoted JSON-ish ingredient line. */
    private val longDescription = listOf(
        "scrambled eggs",
        "oatmeal bowl",
        "greek yogurt",
        "banana",
        "coffee - toast - juice",
        "\"130 gram, chicken breast\"",
    ).joinToString("\n")

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
            // OPENROUTER: same OpenAI-compatible client path as a local custom
            // endpoint, without the user-CA trust wrap (AndroidCAStore is not
            // available under Robolectric). "gemma-4-26b-a4b" is a strong-class
            // model, so the entry legs run the MICROS constituents schema.
            prefs.setSelectedAIProvider(AIProvider.OPENROUTER)
            prefs.setSelectedAIModel("gemma-4-26b-a4b")
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
    fun ladderExhausted_splitsIntoBatches_andMerges() = runBlocking {
        // Legs 1-2: the full-list attempt fails the MICROS leg and its macros
        // downshift with unparseable replies — the ladder is exhausted.
        server.enqueue(MockResponse().setBody(garbage()))
        server.enqueue(MockResponse().setBody(garbage()))
        // Legs 3-4: each batch's fresh analysis succeeds.
        server.enqueue(MockResponse().setBody(chunk(batchOneJson, finishReason = "stop")))
        server.enqueue(MockResponse().setBody(chunk(batchTwoJson, finishReason = "stop")))

        val result = newService().analyzeText(longDescription)

        assertEquals("Eggs and Oats", result.name)
        assertEquals(500, result.calories)
        assertEquals(32.0, result.protein, 1e-9)
        assertEquals(60.0, result.carbs, 1e-9)
        assertEquals(14.0, result.fat, 1e-9)
        assertEquals(430.0, result.servingSizeGrams!!, 1e-9)
        assertEquals(listOf("Eggs", "Oats", "Rice", "Beans"), result.constituents.map { it.name })
        assertTrue(result.servingUnitOptions.isEmpty())
        assertEquals(4, server.requestCount)

        val requests = (0 until 4).map { server.takeRequest() }
        val firstBatchPrompt = promptOf(bodyOf(requests[2]))
        assertTrue(firstBatchPrompt.contains("scrambled eggs"))
        assertTrue(firstBatchPrompt.contains("banana"))
        assertFalse(firstBatchPrompt.contains("coffee"))
        val secondBatchPrompt = promptOf(bodyOf(requests[3]))
        assertTrue(secondBatchPrompt.contains("coffee"))
        assertTrue(secondBatchPrompt.contains("toast"))
        // The quoted JSON-ish ingredient line survives as one item, quotes stripped.
        assertTrue(secondBatchPrompt.contains("130 gram, chicken breast"))

        assertEquals(listOf("op=analyzeText split=batches=2 items=8"), splitLogLines())
    }

    @Test
    fun unsplittableDescription_noSecondRound() = runBlocking {
        server.enqueue(MockResponse().setBody(garbage()))
        server.enqueue(MockResponse().setBody(garbage()))

        val error = runCatching { newService().analyzeText("one big lasagna portion") }.exceptionOrNull()

        assertTrue("terminal error propagates", error is AiError.InvalidResponse)
        assertEquals(2, server.requestCount)
        assertTrue(splitLogLines().isEmpty())
    }

    @Test
    fun overBatchCap_originalErrorUnchanged() = runBlocking {
        server.enqueue(MockResponse().setBody(garbage()))
        server.enqueue(MockResponse().setBody(garbage()))
        val seventeenItems = (1..17).joinToString("\n") { "food item $it" }

        val error = runCatching { newService().analyzeText(seventeenItems) }.exceptionOrNull()

        assertTrue("terminal error propagates", error is AiError.InvalidResponse)
        assertEquals(2, server.requestCount)
        assertTrue(splitLogLines().isEmpty())
    }

    @Test
    fun singleShotSuccess_exactlyOneRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(chunk(batchOneJson, finishReason = "stop")))

        val result = newService().analyzeText("two eggs, oatmeal")

        assertEquals("Eggs and Oats", result.name)
        assertEquals(1, server.requestCount)
        assertTrue(splitLogLines().isEmpty())
    }
}
