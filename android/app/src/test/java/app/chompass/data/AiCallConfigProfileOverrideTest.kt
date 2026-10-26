package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.AIProvider
import app.chompass.models.AiProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Per-profile advanced overrides resolved by [PreferencesStore.aiCallConfig]:
 * a set profile field beats the global pref, a null field falls back to it,
 * and with both unset the factory defaults apply. Profile timeout values
 * clamp exactly like the global one. Max response length is not a setting:
 * the cap is the fixed 4096 default on every profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AiCallConfigProfileOverrideTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun resetStores() {
        // The fudai DataStore is a per-process singleton that survives across
        // test methods: clear profiles + every global the config reads.
        runBlocking {
            prefs().dataStore.edit {
                it.remove(Keys.AI_PROFILES_JSON)
                it.remove(Keys.ACTIVE_AI_PROFILE_ID)
                it.remove(Keys.FALLBACK_AI_PROFILE_ID)
                it.remove(Keys.AI_READ_TIMEOUT_SECONDS)
                it.remove(Keys.GEMINI_GOOGLE_SEARCH_ENABLED)
                it.remove(Keys.OPENROUTER_REASONING_EFFORT)
            }
        }
    }

    private fun prefs() = PreferencesStore(context)

    private fun profile(): AiProfile = AiProfile(
        id = UUID.randomUUID(),
        name = "Tuned",
        provider = AIProvider.CUSTOM_OPENAI,
        baseUrl = "http://localhost:1234/v1",
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
    )

    private suspend fun activate(p: AiProfile) {
        prefs().mutateProfiles { it += p }
        prefs().setActiveAiProfileId(p.id.toString())
    }

    @Test
    fun setProfileField_overridesGlobalPref() = runBlocking {
        val prefs = prefs()
        prefs.setAiReadTimeoutSeconds(60)
        prefs.setGeminiGoogleSearchEnabled(false)
        prefs.setOpenRouterReasoningEffort(OpenRouterReasoningEffort.LOW)
        activate(
            profile().copy(
                readTimeoutSeconds = 90,
                reasoningEffort = "high",
                geminiGoogleSearch = true,
            )
        )
        val cfg = prefs.aiCallConfig()
        assertEquals(90, cfg.aiReadTimeoutSeconds)
        assertEquals(OpenRouterReasoningEffort.HIGH, cfg.reasoningEffort)
        assertEquals(true, cfg.geminiGoogleSearch)
    }

    @Test
    fun nullProfileField_fallsBackToGlobalPref() = runBlocking {
        val prefs = prefs()
        prefs.setAiReadTimeoutSeconds(60)
        prefs.setGeminiGoogleSearchEnabled(true)
        prefs.setOpenRouterReasoningEffort(OpenRouterReasoningEffort.LOW)
        activate(profile())
        val cfg = prefs.aiCallConfig()
        assertEquals(60, cfg.aiReadTimeoutSeconds)
        assertEquals(OpenRouterReasoningEffort.LOW, cfg.reasoningEffort)
        assertEquals(true, cfg.geminiGoogleSearch)
    }

    @Test
    fun bothUnset_fallsToFactoryDefaults() = runBlocking {
        activate(profile())
        val cfg = prefs().aiCallConfig()
        // CUSTOM_OPENAI with the AUTO reasoning effort may think on the
        // provider side, so the cap is the reasoning headroom, not the flat one.
        assertEquals(MAX_REASONING_RESPONSE_TOKENS, cfg.maxResponseTokens)
        assertEquals(DEFAULT_AI_READ_TIMEOUT_SECONDS, cfg.aiReadTimeoutSeconds)
        assertEquals(OpenRouterReasoningEffort.AUTO, cfg.reasoningEffort)
        assertEquals(false, cfg.geminiGoogleSearch)
    }

    @Test
    fun reasoningOff_fallsToFlatCap() = runBlocking {
        activate(profile().copy(reasoningEffort = OpenRouterReasoningEffort.DISABLED.storageKey))
        val cfg = prefs().aiCallConfig()
        assertEquals(DEFAULT_MAX_RESPONSE_TOKENS, cfg.maxResponseTokens)
        assertEquals(OpenRouterReasoningEffort.DISABLED, cfg.reasoningEffort)
    }

    @Test
    fun profileTimeoutValue_clampsLikeTheGlobal() = runBlocking {
        activate(profile().copy(readTimeoutSeconds = 5))
        val cfg = prefs().aiCallConfig()
        assertEquals(MIN_AI_READ_TIMEOUT_SECONDS, cfg.aiReadTimeoutSeconds)
    }
}
