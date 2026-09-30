package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.UserProfile
import kotlinx.coroutines.runBlocking
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
 * Codeberg pull #121: the Coach tool loop must send the flat
 * `reasoning_effort: "none"` to the official OpenAI endpoint for the curated
 * GPT-5.4+/GPT-6 ids (Chat Completions takes `function` tools there only with
 * reasoning off), and must not send it to the GPT-4.x ids, to runtime-only
 * lineup ids, or to a self-hosted OpenAI-compatible host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OpenAiCoachReasoningEffortTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        server = MockWebServer()
        server.start()
        runBlocking {
            prefs.setAiFeaturesEnabled(true)
            prefs.setFallbackEnabled(false)
            prefs.setSelectedAIProvider(AIProvider.OPENAI)
            prefs.setSelectedAIModel("gpt-6-luna")
            prefs.setCustomBaseUrl(AIProvider.OPENAI, server.url("/").toString())
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        // PreferencesStore wraps a process-wide DataStore singleton: restore
        // every pref this class mutates so later suites see pristine state.
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setSelectedAIModel(AIProvider.GEMINI.defaultModel)
            prefs.setFallbackEnabled(true)
            prefs.setCustomBaseUrl(AIProvider.OPENAI, null)
            prefs.setCustomBaseUrl(AIProvider.OPENROUTER, null)
            prefs.setCustomBaseUrl(AIProvider.CUSTOM_OPENAI, null)
        }
    }

    private fun completion(text: String = "ok"): String {
        val choice = JSONObject()
            .put("message", JSONObject().put("content", text))
            .put("finish_reason", "stop")
        return JSONObject().put("choices", JSONArray().put(choice)).toString()
    }

    /** Runs one Coach turn against [server] and returns the request body it sent. */
    private fun coachBody(provider: AIProvider, model: String): JSONObject = runBlocking {
        prefs.setSelectedAIProvider(provider)
        prefs.setSelectedAIModel(model)
        if (provider != AIProvider.OPENAI) {
            prefs.setCustomBaseUrl(provider, server.url("/").toString())
        }
        server.enqueue(MockResponse().setBody(completion()))
        ChatService(
            prefs = prefs,
            foodAnalysisService = FoodAnalysisService(prefs = prefs, keyLookup = { "test-key" }),
            keyLookup = { "test-key" },
        ).sendMessage(
            history = emptyList(),
            newUserMessage = "how many calories today?",
            profile = UserProfile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
        )
        JSONObject(server.takeRequest().body.readUtf8())
    }

    @Test
    fun curatedGpt6Luna_sendsFlatNone() {
        val body = coachBody(AIProvider.OPENAI, "gpt-6-luna")
        assertEquals("none", body.getString("reasoning_effort"))
        assertTrue(body.has("tools"))
        assertTrue(body.has("max_completion_tokens"))
        assertFalse(body.has("reasoning"))
    }

    @Test
    fun curatedGpt56Sol_sendsFlatNone() {
        assertEquals("none", coachBody(AIProvider.OPENAI, "gpt-5.6-sol").getString("reasoning_effort"))
    }

    @Test
    fun gpt4ToolModel_omitsTheField() {
        assertFalse(coachBody(AIProvider.OPENAI, "gpt-4o-mini").has("reasoning_effort"))
    }

    @Test
    fun runtimeLineupId_omitsTheField() {
        assertFalse(coachBody(AIProvider.OPENAI, "gpt-6-astra").has("reasoning_effort"))
    }

    @Test
    fun otherCompatibleProviderWithSameModel_keepsItsOwnReasoningShape() {
        // CUSTOM_OPENAI cannot run under Robolectric (AndroidCAStore, see
        // OpenAIStreamFinishlessRetryTest); OPENROUTER is the same
        // OpenAI-compatible tool loop without the user-CA trust wrap, and keeps
        // a custom model id. The flat `reasoning_effort` must stay OpenAI-only.
        val body = coachBody(AIProvider.OPENROUTER, "gpt-6-luna")
        assertFalse(body.has("reasoning_effort"))
        assertTrue(body.has("reasoning"))
    }
}
