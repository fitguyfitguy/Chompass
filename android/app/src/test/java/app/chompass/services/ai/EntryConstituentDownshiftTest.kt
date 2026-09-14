package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.ServingUnitInferenceMode
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

/**
 * #97 (L2): MICROS-constituents entry prompts ask for 22 micro fields per
 * row; small local models truncate or mangle the reply and the entry fails
 * with "Could not understand the AI response" / "truncated twice". A failed
 * MICROS attempt retries once at the macros-only schema with its 2048-token
 * floor; a successful first attempt must stay a single request with the
 * unchanged MICROS prompt. Bounded: one finish-less one-shot retry and one
 * downshift per analysis.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EntryConstituentDownshiftTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    private val foodJson =
        """{"name":"Eggs","calories":232,"protein":19.0,"carbs":1.5,"fat":16.0,"serving_size_grams":150.0,"emoji":"🥚","unit_options":[]}"""

    private val truncatedJson =
        """{"name":"Big breakfast","calories":900,"constituents":[{"name":"egg","calories":90,"prot"""

    private fun chunk(text: String?, finishReason: String? = null): String {
        val delta = JSONObject().apply { if (text != null) put("content", text) }
        val choice = JSONObject().put("delta", delta)
        if (finishReason != null) choice.put("finish_reason", finishReason)
        return "data: " + JSONObject().put("choices", JSONArray().put(choice)) + "\n\n"
    }

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

    /** Recorded bodies drain on first read — parse each request body exactly once. */
    private fun bodyOf(request: RecordedRequest): JSONObject = JSONObject(request.body.readUtf8())

    private fun promptOf(body: JSONObject): String {
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        return content.getJSONObject(content.length() - 1).getString("text")
    }

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
    fun microsTruncation_downshiftsToMacros_andSucceeds() = runBlocking {
        // Leg 1: streamed MICROS reply cut at the token limit.
        server.enqueue(MockResponse().setBody(chunk(truncatedJson, finishReason = "length")))
        // Legs 2-3: the client's non-streaming compact-retry ladder also truncates.
        server.enqueue(MockResponse().setBody(completion(truncatedJson, "length")))
        server.enqueue(MockResponse().setBody(completion(truncatedJson, "length")))
        // Leg 4: the MACROS downshift retry streams a complete macros-only reply.
        server.enqueue(MockResponse().setBody(chunk(foodJson, finishReason = "stop")))

        val result = newService().analyzeText("big breakfast")

        assertEquals("Eggs", result.name)
        assertEquals(4, server.requestCount)
        val requests = (0 until 4).map { server.takeRequest() }
        val firstBody = bodyOf(requests[0])
        assertEquals(4096, firstBody.getInt("max_tokens"))
        assertTrue(promptOf(firstBody).contains("Each constituent micronutrient MUST sum"))
        val downshiftBody = bodyOf(requests[3])
        assertEquals(2048, downshiftBody.getInt("max_tokens"))
        assertTrue("downshift keeps constituent rows", promptOf(downshiftBody).contains("\"constituents\""))
        assertFalse("downshift drops the per-row micros ask", promptOf(downshiftBody).contains("Each constituent micronutrient MUST sum"))
    }

    @Test
    fun microsGarbageCompleteStream_downshiftsToMacros() = runBlocking {
        // The stream finished cleanly but the text is not parseable JSON
        // (the observed gemma-4-26b failure shape).
        server.enqueue(MockResponse().setBody(chunk("sorry, I cannot answer that here") + chunk(null, finishReason = "stop")))
        server.enqueue(MockResponse().setBody(chunk(foodJson, finishReason = "stop")))

        val result = newService().analyzeText("big breakfast")

        assertEquals("Eggs", result.name)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertFalse(promptOf(bodyOf(server.takeRequest())).contains("Each constituent micronutrient MUST sum"))
    }

    @Test
    fun microsFirstAttemptSuccess_singleRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(chunk(foodJson, finishReason = "stop")))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(1, server.requestCount)
        assertTrue("happy path keeps the MICROS prompt", promptOf(bodyOf(server.takeRequest())).contains("Each constituent micronutrient MUST sum"))
    }

    @Test
    fun downshiftFailureSurfacesError_bounded() {
        server.enqueue(MockResponse().setBody(chunk("garbage reply, no object") + chunk(null, finishReason = "stop")))
        server.enqueue(MockResponse().setBody(chunk("still garbage") + chunk(null, finishReason = "stop")))

        val ex = runCatching { runBlocking { newService().analyzeText("eggs") } }.exceptionOrNull()

        assertTrue("expected InvalidResponse, got $ex", ex is AiError.InvalidResponse)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun finishlessMicros_retriesSameSchemaOneShot_beforeAnyDownshift() = runBlocking {
        // Finish-less stream + parse failure retries the IDENTICAL MICROS
        // call non-streaming — the schema is not downgraded while a
        // same-shape answer may still work.
        server.enqueue(MockResponse().setBody(chunk("{\"name\":\"Eggs\",\"calories\":232,")))
        server.enqueue(MockResponse().setBody(completion(foodJson)))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(2, server.requestCount)
        val firstBody = bodyOf(server.takeRequest())
        val retryBody = bodyOf(server.takeRequest())
        assertTrue(firstBody.getBoolean("stream"))
        assertFalse("one-shot retry is non-streaming", retryBody.has("stream"))
        assertTrue("one-shot retry keeps the MICROS schema", promptOf(retryBody).contains("Each constituent micronutrient MUST sum"))
        assertEquals(4096, retryBody.getInt("max_tokens"))
    }

    @Test
    fun finishlessRetryTruncates_thenDownshift_stillBounded() = runBlocking {
        // Finish-less stream → one-shot retry truncates twice → one MACROS
        // downshift. Exactly one of each recovery, then success.
        server.enqueue(MockResponse().setBody(chunk("{\"name\":\"Eggs\",")))
        server.enqueue(MockResponse().setBody(completion(truncatedJson, "length")))
        server.enqueue(MockResponse().setBody(completion(truncatedJson, "length")))
        server.enqueue(MockResponse().setBody(chunk(foodJson, finishReason = "stop")))

        val result = newService().analyzeText("eggs")

        assertEquals("Eggs", result.name)
        assertEquals(4, server.requestCount)
        val requests = (0 until 4).map { bodyOf(server.takeRequest()) }
        assertTrue(requests[0].getBoolean("stream"))
        assertFalse(requests[1].has("stream"))
        assertEquals(2048, requests[3].getInt("max_tokens"))
        assertFalse("final leg is the MACROS downshift", promptOf(requests[3]).contains("Each constituent micronutrient MUST sum"))
    }
}
