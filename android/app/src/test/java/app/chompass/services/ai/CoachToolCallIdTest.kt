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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #123: local OpenAI-compatible servers (LM Studio, llama.cpp
 * server) can return tool calls with an empty `id`. The tool loop used to
 * skip such results, leaving the echoed assistant `tool_calls` unanswered so
 * the next round's prompt was rejected and the conversation stayed broken.
 * The loop must synthesize a stable id, patch it into the assistant message
 * it already appended, and answer with a matching `tool_call_id`.
 *
 * OPENAI + custom base URL keeps the shared `runOpenAIToolLoop` path without
 * the user-CA trust wrap (CUSTOM_OPENAI would hit AndroidCAStore, unavailable
 * under Robolectric — same workaround as OpenAIStreamFinishlessRetryTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CoachToolCallIdTest {
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
            prefs.setSelectedAIModel("my-local-model")
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
            prefs.setCustomBaseUrl(AIProvider.CUSTOM_OPENAI, null)
        }
    }

    @Test
    fun emptyToolCallId_getsSynthesizedAndAnswered() = runBlocking {
        val toolCall = JSONObject()
            .put("id", "")
            .put("type", "function")
            .put(
                "function",
                JSONObject().put("name", "get_data_summary").put("arguments", "{}"),
            )
        val toolCallChoice = JSONObject()
            .put(
                "message",
                JSONObject()
                    .put("role", "assistant")
                    .put("content", JSONObject.NULL)
                    .put("tool_calls", JSONArray().put(toolCall)),
            )
            .put("finish_reason", "tool_calls")
        server.enqueue(MockResponse().setBody(JSONObject().put("choices", JSONArray().put(toolCallChoice)).toString()))
        server.enqueue(MockResponse().setBody(completion("done")))

        val reply = ChatService(
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

        assertEquals("done", reply.reply)

        // The second request must carry the assistant tool_call with a
        // non-empty id plus a tool result paired to that exact id.
        server.takeRequest() // round 1: the initial user message
        val second = JSONObject(server.takeRequest().body.readUtf8())
        val messages = second.getJSONArray("messages")
        var toolId: String? = null
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val calls = m.optJSONArray("tool_calls") ?: continue
            for (j in 0 until calls.length()) {
                val id = calls.optJSONObject(j)?.optString("id")
                if (!id.isNullOrEmpty()) toolId = id
            }
        }
        assertNotNull("assistant tool_call must carry a synthesized id", toolId)
        val answered = (0 until messages.length()).any {
            val m = messages.optJSONObject(it) ?: return@any false
            m.optString("role") == "tool" && m.optString("tool_call_id") == toolId
        }
        assertTrue("tool result must be paired with the synthesized id", answered)
    }

    private fun completion(text: String): String {
        val choice = JSONObject()
            .put("message", JSONObject().put("content", text))
            .put("finish_reason", "stop")
        return JSONObject().put("choices", JSONArray().put(choice)).toString()
    }
}
