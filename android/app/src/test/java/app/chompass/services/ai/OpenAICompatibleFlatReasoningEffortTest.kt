package app.chompass.services.ai

import app.chompass.data.OpenRouterReasoningEffort
import app.chompass.models.AIProvider
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

/**
 * #97: the reasoning-effort lever also drives Custom OpenAI-compatible and
 * Ollama hosts, as a flat `reasoning_effort` field. AUTO (and providers
 * without the lever) omit the field, so existing requests stay
 * byte-identical. A server that 400s over the unknown field gets one retry
 * without it.
 */
class OpenAICompatibleFlatReasoningEffortTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun completion(text: String = "ok"): String {
        val choice = JSONObject()
            .put("message", JSONObject().put("content", text))
            .put("finish_reason", "stop")
        return JSONObject().put("choices", JSONArray().put(choice)).toString()
    }

    private fun rejected400(message: String): MockResponse =
        MockResponse().setResponseCode(400).setBody(JSONObject().put("error", JSONObject().put("message", message)).toString())

    private fun analyze(
        provider: AIProvider,
        effort: OpenRouterReasoningEffort = OpenRouterReasoningEffort.AUTO,
    ): String = runBlocking {
        OpenAICompatibleClient.analyze(
            client,
            server.url("/").toString(),
            "test-model",
            null,
            "prompt",
            emptyList(),
            provider,
            512,
            effort,
        )
    }

    private fun requestBody(index: Int = 0): JSONObject =
        JSONObject(server.takeRequest().body.readUtf8())

    @Test
    fun customOpenAI_explicitEffort_sendsFlatField() {
        server.enqueue(MockResponse().setBody(completion()))
        assertEquals("ok", analyze(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.LOW))
        val body = requestBody()
        assertEquals("low", body.getString("reasoning_effort"))
        assertFalse(body.has("reasoning"))
    }

    @Test
    fun customOpenAI_auto_omitsFieldEntirely() {
        server.enqueue(MockResponse().setBody(completion()))
        analyze(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.AUTO)
        val body = requestBody()
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("reasoning"))
    }

    @Test
    fun customOpenAI_disabled_sendsNone() {
        server.enqueue(MockResponse().setBody(completion()))
        analyze(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.DISABLED)
        assertEquals("none", requestBody().getString("reasoning_effort"))
    }

    @Test
    fun ollama_explicitEffort_sendsFlatField() {
        server.enqueue(MockResponse().setBody(completion()))
        analyze(AIProvider.OLLAMA, OpenRouterReasoningEffort.HIGH)
        assertEquals("high", requestBody().getString("reasoning_effort"))
    }

    @Test
    fun openRouter_explicitEffort_keepsReasoningObject() {
        server.enqueue(MockResponse().setBody(completion()))
        analyze(AIProvider.OPENROUTER, OpenRouterReasoningEffort.LOW)
        val body = requestBody()
        assertFalse(body.has("reasoning_effort"))
        val reasoning = body.getJSONObject("reasoning")
        assertEquals(true, reasoning.getBoolean("exclude"))
        assertEquals("low", reasoning.getString("effort"))
    }

    @Test
    fun reasoningRejected400_retriesOnceWithoutTheField() {
        server.enqueue(rejected400("Unknown parameter: reasoning_effort"))
        server.enqueue(MockResponse().setBody(completion()))
        assertEquals("ok", analyze(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.MEDIUM))
        assertEquals(2, server.requestCount)
        val first = requestBody()
        val second = requestBody()
        assertEquals("medium", first.getString("reasoning_effort"))
        assertFalse(second.has("reasoning_effort"))
    }

    @Test
    fun other400_surfacesWithoutRetry() {
        server.enqueue(rejected400("Invalid model"))
        val error = runCatching { analyze(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.MEDIUM) }.exceptionOrNull()
        assertTrue(error is AiError.Api)
        assertEquals(400, (error as AiError.Api).httpStatus)
        assertEquals(1, server.requestCount)
    }
}
