package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Codeberg #25: a provider stream that trickles (chunks inside the per-read
 * timeout) or stalls must never leave the review sheet busy forever. The
 * wall-clock watchdog caps the whole attempt chain, recovers a parseable
 * partial, and skips the fallback once content already streamed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FoodAnalysisWatchdogTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore
    private var fallbackKey: String? = null

    private val foodJson = """
      {"name":"Eggs","calories":232,"protein":19.0,"carbs":1.5,"fat":16.0,"serving_size_grams":150.0,"emoji":"🥚","unit_options":[]}
    """.trimIndent()

    private fun sse(text: String): String {
        val chunk = JSONObject().put(
            "candidates", JSONArray().put(
                JSONObject().put(
                    "content", JSONObject().put(
                        "parts", JSONArray().put(JSONObject().put("text", text))
                    )
                )
            )
        )
        return "data: $chunk\n\n"
    }

    private fun errorJson(message: String): String =
        """{"error":{"message":"$message"}}"""

    private fun newService(watchdogSeconds: Int = 1): FoodAnalysisService =
        FoodAnalysisService(
            prefs = prefs,
            okHttp = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            watchdogSecondsOverride = watchdogSeconds,
            keyLookup = { provider ->
                // Any non-null key satisfies GEMINI's requiresApiKey check; the
                // fallback slot returns a key only when the test configured one.
                if (provider == AIProvider.GEMINI) fallbackKey ?: "test-key" else null
            },
        )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        fallbackKey = null
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setCustomBaseUrl(AIProvider.GEMINI, server.url("/").toString())
            prefs.setFallbackEnabled(false)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- pure cap ---

    @Test
    fun watchdogMillis_isAtLeastTwoMinutesAndScalesWithReadTimeout() {
        assertEquals(120_000L, FoodAnalysisService.analysisWatchdogMillis(30))
        assertEquals(180_000L, FoodAnalysisService.analysisWatchdogMillis(90))
        assertEquals(600_000L, FoodAnalysisService.analysisWatchdogMillis(300))
    }

    // --- recovery from a stalled stream (empty snapshot → friendly timeout) ---

    @Test
    fun stalledStream_throwsFriendlyTimeout() {
        // The server accepts the socket but stalls before writing any byte
        // (STALL_SOCKET_AT_START sleeps ~1s) — longer than the 300ms watchdog,
        // so the whole attempt chain times out with a friendly error.
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setSocketPolicy(SocketPolicy.STALL_SOCKET_AT_START)
                .setBody(sse(foodJson))
        )
        val ex = runCatching { runBlocking { newService(watchdogSeconds = 1).analyzeText("eggs") } }.exceptionOrNull()
        assertTrue("expected AiError.Timeout, got $ex", ex is AiError.Timeout)
    }

    @Test
    fun recoverFromStalledStream_returnsText_whenSnapshotParses() {
        val assembler = FoodPartialJsonAssembler()
        // The assembler buffer holds the raw model text (the SSE wrapper was
        // already stripped by the streaming client), so the snapshot is the
        // food JSON itself.
        assembler.push(foodJson)
        assertEquals(foodJson, FoodAnalysisService.recoverFromStalledStream(assembler))
    }

    @Test
    fun recoverFromStalledStream_throwsTimeout_whenSnapshotIsIncomplete() {
        val assembler = FoodPartialJsonAssembler()
        assembler.push("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"name\\\":\\\"Eggs\\\"")
        try {
            FoodAnalysisService.recoverFromStalledStream(assembler)
            fail("expected AiError.Timeout")
        } catch (e: AiError.Timeout) {
            // expected
        }
    }

    @Test
    fun recoverFromStalledStream_throwsTimeout_whenSnapshotIsBlank() {
        val assembler = FoodPartialJsonAssembler()
        try {
            FoodAnalysisService.recoverFromStalledStream(assembler)
            fail("expected AiError.Timeout")
        } catch (e: AiError.Timeout) {
            // expected
        }
    }

    // --- fallback behavior ---

    @Test
    fun midStreamFailure_doesNotFallBackToSecondProvider() {
        runBlocking {
            prefs.setFallbackEnabled(true)
            prefs.setFallbackCustomBaseUrl(AIProvider.GEMINI, server.url("/").toString())
        }
        fallbackKey = "fallback-key"
        // Response 1: SSE stream cuts off mid-body after the full JSON line.
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setBody(sse(foodJson) + "x".repeat(200_000))
        )
        // Response 2: the client's internal non-streaming retry also fails.
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorJson("boom")))

        val ex = runCatching { runBlocking { newService().analyzeText("eggs") } }.exceptionOrNull()
        assertTrue("expected an AiError, got $ex", ex is AiError)

        assertEquals(2, server.requestCount)
        val urls = (0 until server.requestCount).map { server.takeRequest().path!! }
        urls.forEach { assertTrue("no fallback model in $it", !it.contains("gemini-3.5-flash-lite")) }
    }

    @Test
    fun preContentFailure_stillFallsBackToSecondProvider() {
        runBlocking {
            prefs.setFallbackEnabled(true)
            prefs.setFallbackCustomBaseUrl(AIProvider.GEMINI, server.url("/").toString())
        }
        fallbackKey = "fallback-key"
        // Response 1: primary fails before any content (400, not retried).
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorJson("boom")))
        // Response 2: the fallback provider answers normally.
        server.enqueue(MockResponse().setResponseCode(200).setBody(sse(foodJson)))

        val analysis = runBlocking { newService().analyzeText("eggs") }

        assertEquals("Eggs", analysis.name)
        assertEquals(232, analysis.calories)
        assertEquals(2, server.requestCount)
        val primaryUrl = server.takeRequest().path!!
        val fallbackUrl = server.takeRequest().path!!
        assertTrue("expected primary model in $primaryUrl", primaryUrl.contains("gemini-3.7-flash"))
        assertTrue("expected fallback model in $fallbackUrl", fallbackUrl.contains("gemini-3.5-flash-lite"))
    }
}
