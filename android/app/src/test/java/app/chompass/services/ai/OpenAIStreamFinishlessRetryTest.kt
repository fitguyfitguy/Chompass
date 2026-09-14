package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.ServingUnitInferenceMode
import kotlinx.coroutines.runBlocking
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
 * #97 (L3a): local OpenAI-compatible servers can end the SSE stream without
 * a finish_reason chunk, mid-reply. The client flags the assembled text as
 * stream-incomplete instead of handing it to the food-JSON parser as
 * success, and the service retries the identical call once non-streaming
 * (compact-retry ladder applies) only when that text fails to parse.
 * Servers that omit finish_reason on success must not pay the extra request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OpenAIStreamFinishlessRetryTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    private val foodJson =
        """{"name":"Eggs","calories":232,"protein":19.0,"carbs":1.5,"fat":16.0,"serving_size_grams":150.0,"emoji":"🥚","unit_options":[]}"""

    /** SSE chunk carrying a content delta (and optionally a finish_reason). */
    private fun chunk(text: String?, finishReason: String? = null, jsonNullFinish: Boolean = false): String {
        val delta = JSONObject().apply { if (text != null) put("content", text) }
        val choice = JSONObject().put("delta", delta)
        when {
            finishReason != null -> choice.put("finish_reason", finishReason)
            jsonNullFinish -> choice.put("finish_reason", JSONObject.NULL)
        }
        return "data: " + JSONObject().put("choices", JSONArray().put(choice)) + "\n\n"
    }

    /** Non-streaming chat.completion body. */
    private fun completion(text: String, finishReason: String = "stop"): String {
        val choice = JSONObject()
            .put("message", JSONObject().put("content", text))
            .put("finish_reason", finishReason)
        return JSONObject().put("choices", JSONArray().put(choice)).toString()
    }

    private fun newService(): FoodAnalysisService =
        FoodAnalysisService(
            prefs = prefs,
            okHttp = OkHttpClient(),
            keyLookup = { "test-key" },
            inferenceModeForTest = ServingUnitInferenceMode.GRAMS_ONLY,
        )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking {
            // OPENROUTER keeps the OpenAI-compatible client path without the
            // user-CA trust wrap (CUSTOM_OPENAI would hit AndroidCAStore,
            // unavailable under Robolectric); the base-url pref is a plain
            // per-provider key, so the mock server slots right in.
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
    fun finishlessStream_parseFailure_retriesOnceNonStreaming() = runBlocking {
        // Leg 1: stream cuts after a partial JSON fragment — no finish chunk.
        server.enqueue(MockResponse().setBody(chunk("{\"name\":\"Eggs\",\"calories\":232,")))
        // Leg 2: the non-streaming retry returns the full JSON.
        server.enqueue(MockResponse().setBody(completion(foodJson)))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue("first leg must stream", JSONObject(first.body.readUtf8()).getBoolean("stream"))
        assertFalse("retry must be non-streaming", JSONObject(second.body.readUtf8()).has("stream"))
    }

    @Test
    fun finishlessStream_validJson_noRetry() = runBlocking {
        // A server that omits finish_reason but returned complete JSON must
        // not pay a double request.
        server.enqueue(MockResponse().setBody(chunk(foodJson)))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun completeStream_singleRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(chunk(foodJson) + chunk(null, finishReason = "stop")))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun explicitNullFinishReasonChunks_thenCut_flagsIncompleteAndRetries() = runBlocking {
        // Real OpenAI streams carry "finish_reason":null on every content
        // chunk; a cut then looks like no finish_reason ever arriving. The
        // JsonNull-safe extraction must not read those as the stream ending.
        server.enqueue(
            MockResponse().setBody(
                chunk("{\"name\":\"Eggs\",", jsonNullFinish = true) +
                    chunk("\"calories\":232,", jsonNullFinish = true)
            )
        )
        server.enqueue(MockResponse().setBody(completion(foodJson)))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(2, server.requestCount)
        server.takeRequest() // streamed first leg
        val second = server.takeRequest()
        assertFalse("retry must be non-streaming", JSONObject(second.body.readUtf8()).has("stream"))
    }

    @Test
    fun finishlessStream_allRecoveriesFail_surfaceInvalidResponseBounded() {
        // Finish-less one-shot retry fails to parse, then the MACROS
        // downshift also fails to parse: exactly one of each recovery,
        // then the error surfaces (no third attempt).
        server.enqueue(MockResponse().setBody(chunk("{\"name\":\"Eggs\"")))
        server.enqueue(MockResponse().setBody(completion("total garbage, no json object")))
        // The downshift leg streams again, so the third response is SSE.
        server.enqueue(MockResponse().setBody(chunk("still not json") + chunk(null, finishReason = "stop")))

        val ex = runCatching { runBlocking { newService().analyzeText("eggs") } }.exceptionOrNull()

        assertTrue("expected InvalidResponse, got $ex", ex is AiError.InvalidResponse)
        assertEquals(3, server.requestCount)
    }
}
