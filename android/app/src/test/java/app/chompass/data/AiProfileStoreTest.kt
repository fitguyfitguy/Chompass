package app.chompass.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.chompass.R
import app.chompass.models.AiProfile
import app.chompass.models.AIProvider
import app.chompass.models.SpeechProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * AI profile storage + one-shot legacy single-slot migration (named AI
 * profiles): round-trip, active-profile fallback-to-first, malformed JSON
 * leniency, the 3-profile legacy case (selected + custom-endpoint + fallback),
 * idempotency, legacy key/DataStore sweep, and the fresh-install default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AiProfileStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun resetStores() {
        // The fudai DataStore is a per-process singleton that survives across
        // test methods: clear every profile + legacy slot so tests stay
        // order-independent.
        runBlocking {
            prefs().dataStore.edit {
                it.remove(Keys.AI_PROFILES_JSON)
                it.remove(Keys.ACTIVE_AI_PROFILE_ID)
                it.remove(Keys.FALLBACK_AI_PROFILE_ID)
                it.remove(Keys.SELECTED_AI_PROVIDER)
                it.remove(Keys.SELECTED_AI_MODEL)
                it.remove(Keys.FALLBACK_PROVIDER)
                it.remove(Keys.FALLBACK_MODEL)
                for (p in AIProvider.values()) {
                    it.remove(Keys.visionModel(p))
                    it.remove(Keys.customBaseUrl(p))
                    it.remove(Keys.fallbackCustomBaseUrl(p))
                }
            }
        }
        // KeyStore hands its SharedPreferences in for tests; start each test
        // from a clean keychain so legacy-entry assertions are exact.
        keychain().edit().clear().commit()
    }

    private fun keychain() = context.getSharedPreferences("keychain-test", Context.MODE_PRIVATE)

    private fun keyStore() = KeyStore(context, keychain())

    private fun prefs() = PreferencesStore(context)

    private fun profile(
        name: String,
        provider: AIProvider = AIProvider.GEMINI,
        model: String? = null,
    ) = AiProfile(
        id = UUID.randomUUID(),
        name = name,
        provider = provider,
        model = model,
        // Milli precision: InstantSerializer persists epochMilli only.
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
    )

    @Test
    fun profiles_roundTripThroughDataStore() = runBlocking {
        val prefs = prefs()
        val a = profile("Gemini", model = "gemini-3.8-flash")
        val b = profile("LM Studio", provider = AIProvider.CUSTOM_OPENAI, model = "qwen2.5-7b")
        prefs.mutateProfiles {
            it += a
            it += b
        }
        prefs.setActiveAiProfileId(b.id.toString())
        assertEquals(listOf(a, b), prefs.aiProfiles.first())
        assertEquals(b.id.toString(), prefs.activeAiProfileId.first())
        assertEquals(b, prefs.activeAiProfile.first())
    }

    @Test
    fun activeAiProfile_fallsBackToFirstListEntry() = runBlocking {
        val prefs = prefs()
        val a = profile("First")
        val b = profile("Second")
        prefs.mutateProfiles {
            it += a
            it += b
        }
        // No ACTIVE_AI_PROFILE_ID written: resolution must still land on a profile.
        assertNull(prefs.activeAiProfileId.first())
        assertEquals(a, prefs.activeAiProfile.first())
    }

    @Test
    fun malformedProfilesJson_degradesToEmptyList() = runBlocking {
        val prefs = prefs()
        prefs.dataStore.edit { it[Keys.AI_PROFILES_JSON] = "{not json" }
        assertTrue(prefs.aiProfiles.first().isEmpty())
        assertNull(prefs.activeAiProfile.first())
        // And the store recovers: a mutate rewrites valid JSON over the blob.
        val a = profile("Recovered")
        prefs.mutateProfiles { it += a }
        assertEquals(listOf(a), prefs.aiProfiles.first())
    }

    @Test
    fun migration_selectedPlusCustomEndpointPlusFallback_makesThreeProfiles() = runBlocking {
        val prefs = prefs()
        val ks = keyStore()
        prefs.dataStore.edit {
            it[Keys.SELECTED_AI_PROVIDER] = "GEMINI"
            it[Keys.SELECTED_AI_MODEL] = "gemini-3.8-flash"
            it[Keys.customBaseUrl(AIProvider.CUSTOM_OPENAI)] = "http://192.168.1.10:1234/v1"
            it[Keys.FALLBACK_PROVIDER] = "OPENAI"
            it[Keys.FALLBACK_MODEL] = "gpt-4o-mini"
        }
        ks.setApiKey(AIProvider.CUSTOM_OPENAI, "ck-custom")
        ks.setFallbackApiKey(AIProvider.OPENAI, "sk-fallback")

        prefs.ensureAiProfilesMigrated(ks, context)

        val profiles = prefs.aiProfiles.first()
        assertEquals(3, profiles.size)
        val gemini = profiles.single { it.provider == AIProvider.GEMINI }
        val custom = profiles.single { it.provider == AIProvider.CUSTOM_OPENAI }
        val fallback = profiles.single { it.id.toString() == prefs.fallbackAiProfileId.first() }

        assertEquals("gemini-3.8-flash", gemini.model)
        assertEquals(context.getString(AIProvider.GEMINI.displayNameRes), gemini.name)
        assertEquals(gemini.id.toString(), prefs.activeAiProfileId.first())

        assertEquals("http://192.168.1.10:1234/v1", custom.baseUrl)
        assertEquals("ck-custom", ks.profileApiKey(custom.id))

        assertEquals(AIProvider.OPENAI, fallback.provider)
        assertEquals("gpt-4o-mini", fallback.model)
        assertEquals("sk-fallback", ks.profileApiKey(fallback.id))
        // Fallback provider was NOT also migrated as a primary profile.
        assertEquals(1, profiles.count { it.provider == AIProvider.OPENAI })
        assertEquals(context.getString(AIProvider.OPENAI.displayNameRes) + " fallback", fallback.name)

        // Legacy slots are gone (DataStore + keychain).
        assertNull(prefs.dataStore.data.first()[Keys.SELECTED_AI_PROVIDER])
        assertNull(prefs.dataStore.data.first()[Keys.SELECTED_AI_MODEL])
        assertNull(prefs.dataStore.data.first()[Keys.FALLBACK_PROVIDER])
        assertNull(prefs.dataStore.data.first()[Keys.FALLBACK_MODEL])
        assertNull(prefs.dataStore.data.first()[Keys.customBaseUrl(AIProvider.CUSTOM_OPENAI)])
        assertNull(ks.apiKey(AIProvider.CUSTOM_OPENAI))
        assertNull(ks.fallbackApiKey(AIProvider.OPENAI))

        // Second run is a no-op (same ids, same count).
        prefs.ensureAiProfilesMigrated(ks, context)
        assertEquals(profiles, prefs.aiProfiles.first())
    }

    @Test
    fun migration_freshInstall_createsSingleDefaultGeminiProfile() = runBlocking {
        val prefs = prefs()
        prefs.ensureAiProfilesMigrated(keyStore(), context)
        val profiles = prefs.aiProfiles.first()
        assertEquals(1, profiles.size)
        val def = profiles.single()
        assertEquals(AIProvider.GEMINI, def.provider)
        assertEquals(context.getString(R.string.ai_provider_gemini), def.name)
        assertEquals(def, prefs.activeAiProfile.first())
        assertNull(prefs.fallbackAiProfileId.first())
    }

    @Test
    fun migration_keylessLegacyProviderStillMigratesWhenSelected() = runBlocking {
        val prefs = prefs()
        prefs.dataStore.edit { it[Keys.SELECTED_AI_PROVIDER] = "OLLAMA" }
        prefs.ensureAiProfilesMigrated(keyStore(), context)
        val profiles = prefs.aiProfiles.first()
        assertEquals(1, profiles.size)
        assertEquals(AIProvider.OLLAMA, profiles.single().provider)
    }

    @Test
    fun clearLegacyAiKeys_sweepsOnlyLegacyAiEntries() {
        val ks = keyStore()
        ks.setApiKey(AIProvider.GEMINI, "legacy-primary")
        ks.setFallbackApiKey(AIProvider.OPENAI, "legacy-fallback")
        val profileId = UUID.randomUUID()
        ks.setProfileApiKey(profileId, "profile-key")
        ks.setSpeechApiKey(SpeechProvider.OPENAI, "speech")
        ks.setWebDavPassword("webdav")

        ks.clearLegacyAiKeys()

        assertNull(ks.apiKey(AIProvider.GEMINI))
        assertNull(ks.fallbackApiKey(AIProvider.OPENAI))
        assertEquals("profile-key", ks.profileApiKey(profileId))
        assertEquals("speech", ks.speechApiKey(SpeechProvider.OPENAI))
        assertEquals("webdav", ks.webDavPassword())
    }

    @Test
    fun setProfileApiKey_blankDeletesEntry() = runBlocking {
        val ks = keyStore()
        val id = UUID.randomUUID()
        ks.setProfileApiKey(id, "secret")
        assertEquals("secret", ks.profileApiKey(id))
        ks.setProfileApiKey(id, "  ")
        assertNull(ks.profileApiKey(id))
    }
}
