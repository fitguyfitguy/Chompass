package app.chompass.services.ai

import android.app.Application
import androidx.datastore.preferences.core.edit
import app.chompass.data.PreferencesStore
import app.chompass.models.AiProfile
import app.chompass.models.AIProvider
import app.chompass.models.ServingUnitInferenceMode
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.util.UUID

/**
 * One-off profile override ("Run with…" on a queue entry) + profile-keyed
 * fallback resolution: the override must dispatch the PROFILE's provider,
 * model, base URL, and per-profile API key — while the globally active
 * profile stays untouched — and the fallback must resolve through the
 * fallback slot profile (skipped entirely when it IS the active profile).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AiProfileOverrideTest {
    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer
    private lateinit var prefs: PreferencesStore

    private lateinit var profileA: AiProfile
    private lateinit var profileB: AiProfile

    private fun chunk(text: String, finishReason: String): String {
        val delta = JSONObject().put("content", text)
        val choice = JSONObject().put("delta", delta).put("finish_reason", finishReason)
        return "data: " + JSONObject().put("choices", JSONArray().put(choice)) + "\n\n"
    }

    private fun mealJson(): String = """
        {"name":"Chicken bowl","calories":540,"protein":42.0,"carbs":60.0,"fat":0.0,
        "serving_size_grams":400.0,"emoji":"🍲","fiber":4.0,
        "constituents":[{"name":"chicken and rice","calories":540,"protein":42.0,"carbs":60.0,"fat":0.0,
        "serving_size_grams":400.0}],"unit_options":[]}
    """.trimIndent().replace("\n", "")

    private fun bodyOf(request: RecordedRequest): JSONObject = JSONObject(request.body.readUtf8())

    private fun newService(): FoodAnalysisService = FoodAnalysisService(
        prefs = prefs,
        okHttp = OkHttpClient(),
        keyLookup = { profile -> if (profile.id == profileB.id) "key-b" else "key-a" },
        inferenceModeForTest = ServingUnitInferenceMode.GRAMS_ONLY,
    )

    @Before
    fun setUp() {
        serverA = MockWebServer()
        serverB = MockWebServer()
        serverA.start()
        serverB.start()
        val context = RuntimeEnvironment.getApplication()
        prefs = PreferencesStore(context)
        runBlocking {
            prefs.dataStore.edit {
                it.remove(app.chompass.data.Keys.AI_PROFILES_JSON)
                it.remove(app.chompass.data.Keys.ACTIVE_AI_PROFILE_ID)
                it.remove(app.chompass.data.Keys.FALLBACK_AI_PROFILE_ID)
            }
            profileA = AiProfile(
                id = UUID.randomUUID(),
                name = "Primary A",
                provider = AIProvider.OPENROUTER,
                model = "model-a",
                baseUrl = serverA.url("/v1").toString(),
                createdAt = Instant.now(),
            )
            profileB = AiProfile(
                id = UUID.randomUUID(),
                name = "Override B",
                provider = AIProvider.OPENROUTER,
                model = "model-b",
                baseUrl = serverB.url("/v1").toString(),
                createdAt = Instant.now(),
            )
            prefs.mutateProfiles {
                it += profileA
                it += profileB
            }
            prefs.setActiveAiProfileId(profileA.id.toString())
            prefs.setFallbackAiProfileId(null)
            prefs.setFallbackEnabled(false)
            prefs.setMealConstituentsEnabled(true)
            prefs.setMaxResponseTokens(1024)
        }
    }

    @After
    fun tearDown() {
        serverA.shutdown()
        serverB.shutdown()
    }

    @Test
    fun override_dispatchesProfileModelUrlAndKey() = runBlocking {
        serverB.enqueue(MockResponse().setBody(chunk(mealJson(), "stop")))

        val result = newService().analyzeFood(
            byteArrayOf(1, 2, 3, 4),
            profileOverride = profileB,
        ) { }

        assertEquals(540, result.calories)
        // The override profile's endpoint served the call; the active profile's did not.
        assertEquals(1, serverB.requestCount)
        assertEquals(0, serverA.requestCount)
        val request = serverB.takeRequest()
        assertEquals("Bearer key-b", request.getHeader("Authorization"))
        assertEquals("model-b", bodyOf(request).getString("model"))
        // One-off override: the globally active profile is untouched.
        assertEquals(profileA.id, prefs.activeAiProfile.first()?.id)
    }

    @Test
    fun fallback_skippedWhenFallbackSlotIsTheActiveProfile() = runBlocking {
        prefs.setFallbackEnabled(true)
        prefs.setFallbackAiProfileId(profileA.id.toString())
        serverA.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"boom"}}"""))

        val error = runCatching { newService().analyzeFood(byteArrayOf(1, 2, 3, 4)) { } }.exceptionOrNull()

        assertTrue("error propagates without a fallback retry", error is AiError.Api)
        assertEquals(1, serverA.requestCount)
        assertEquals(0, serverB.requestCount)
    }

    @Test
    fun fallback_usesFallbackProfileUrlAndKey() = runBlocking {
        prefs.setFallbackEnabled(true)
        prefs.setFallbackAiProfileId(profileB.id.toString())
        serverA.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"boom"}}"""))
        serverB.enqueue(MockResponse().setBody(chunk(mealJson(), "stop")))

        val result = newService().analyzeFood(byteArrayOf(1, 2, 3, 4)) { }

        assertEquals(540, result.calories)
        assertEquals(1, serverA.requestCount)
        assertEquals(1, serverB.requestCount)
        val request = serverB.takeRequest()
        assertEquals("Bearer key-b", request.getHeader("Authorization"))
        assertEquals("model-b", bodyOf(request).getString("model"))
    }
}
