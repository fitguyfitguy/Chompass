package app.chompass.services.ai

import app.chompass.models.AIProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A caller that aborts consumption mid-stream throws [CancellationException]
 * from [OpenAICompatibleClient.analyzeStreaming]'s onDelta (or the watchdog
 * fires TimeoutCancellationException, its subtype). That exception must
 * propagate: the non-streaming fallback would run a second full request
 * against a caller that already gave up — and, with a live scope, return a
 * bogus success from the aborted consumption. The server must see exactly
 * one request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OpenAIStreamCancelTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sseResponse(): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(
            """data: {"choices":[{"delta":{"content":"Hel"}}]}""" + "\n\n" +
                """data: {"choices":[{"delta":{"content":"lo"}}]}""" + "\n\n"
        )

    private fun completionBody(): String {
        val choice = org.json.JSONObject()
            .put("message", org.json.JSONObject().put("content", "fallback"))
            .put("finish_reason", "stop")
        return org.json.JSONObject().put("choices", org.json.JSONArray().put(choice)).toString()
    }

    @Test
    fun cancellationFromOnDelta_propagates_andSendsNoSecondRequest() = runBlocking {
        // Second response would serve the non-streaming fallback on a
        // regression; its arrival is itself the failure signal.
        server.enqueue(sseResponse())
        server.enqueue(MockResponse().setBody(completionBody()))
        var exitError: Throwable? = null
        var result: OpenAIStreamResult? = null
        try {
            result = OpenAICompatibleClient.analyzeStreaming(
                client = OkHttpClient(),
                baseUrl = server.url("/v1").toString(),
                model = "test-model",
                apiKey = "test-key",
                prompt = "analyze",
                imageBytesList = emptyList(),
                provider = AIProvider.OPENROUTER,
                maxTokens = 256,
                onDelta = { throw CancellationException("caller stopped consuming") },
            )
        } catch (e: Throwable) {
            exitError = e
        }
        assertEquals(null, result)
        assertTrue(
            "expected CancellationException to propagate, got $exitError",
            exitError is CancellationException,
        )
        assertEquals(1, server.requestCount)
    }
}
