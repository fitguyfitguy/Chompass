package app.chompass.services.ai

import app.chompass.models.AIProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHttpTest {
    @Test
    fun userCaTrustAppliesToUserEnteredEndpointsOnly() {
        AIProvider.entries.forEach { provider ->
            assertEquals(
                "usesUserCaTrust($provider)",
                provider == AIProvider.CUSTOM_OPENAI || provider == AIProvider.OLLAMA,
                AiHttp.usesUserCaTrust(provider),
            )
        }
    }

    @Test
    fun clientForProviderWrapsOnlyCustomEndpointWithTrustBuilder() {
        val base = OkHttpClient()
        var trustCalls = 0
        val fake: (OkHttpClient) -> OkHttpClient = {
            trustCalls++
            base.newBuilder().build()
        }

        val custom = AiHttp.clientForProvider(base, AIProvider.CUSTOM_OPENAI, 60, fake)
        assertEquals(1, trustCalls)
        assertTrue(custom !== base)

        // Cloud provider: new client carrying the fixed cold-start read
        // timeout; trust builder untouched.
        val cloud = AiHttp.clientForProvider(base, AIProvider.GEMINI, 60, fake)
        assertTrue(cloud !== base)
        assertEquals(AiHttp.CLOUD_READ_TIMEOUT_SECONDS * 1000, cloud.readTimeoutMillis)
        assertEquals(1, trustCalls)
    }

    @Test
    fun effectiveReadTimeout_branchesOnConfigurableTimeout() {
        // User-configurable endpoints honor the settings seconds...
        for (provider in AIProvider.entries.filter { it.usesConfigurableRequestTimeout }) {
            assertEquals(
                "effectiveReadTimeoutSeconds($provider)",
                90,
                AiHttp.effectiveReadTimeoutSeconds(provider, settingsSeconds = 90),
            )
        }
        // ...builtin cloud endpoints use the fixed cold-start budget,
        // regardless of the settings value.
        for (provider in AIProvider.entries.filterNot { it.usesConfigurableRequestTimeout }) {
            assertEquals(
                "effectiveReadTimeoutSeconds($provider)",
                AiHttp.CLOUD_READ_TIMEOUT_SECONDS,
                AiHttp.effectiveReadTimeoutSeconds(provider, settingsSeconds = 90),
            )
        }
        assertEquals(
            AiHttp.effectiveReadTimeoutSeconds(AIProvider.GEMINI, 30),
            AiHttp.effectiveReadTimeoutSeconds(AIProvider.GEMINI, 300),
        )
    }

    @Test
    fun clientForProviderKeepsSettingsTimeoutForLocalProvider() {
        val base = OkHttpClient.Builder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
        // Trivial trust builder: the default one needs Android's CA store, absent on the JVM.
        assertEquals(90_000, AiHttp.clientForProvider(base, AIProvider.OLLAMA, 90) { it }.readTimeoutMillis)
        assertEquals(90_000, AiHttp.clientForProvider(base, AIProvider.CUSTOM_OPENAI, 90) { it }.readTimeoutMillis)
    }

    @Test
    fun clientForProviderKeepsReadTimeoutForLocalProviderAndAddsUserCaTrust() {
        val base = OkHttpClient()
        var trustCalls = 0
        val fake: (OkHttpClient) -> OkHttpClient = {
            trustCalls++
            base.newBuilder().build()
        }

        val ollama = AiHttp.clientForProvider(base, AIProvider.OLLAMA, 60, fake)
        assertTrue(ollama !== base) // read-timeout variant
        assertEquals(1, trustCalls) // Ollama is a user-entered endpoint → user-CA trust
    }

    // -- release cleartext gate (issue #8 follow-up) ---------------------

    @Test
    fun cleartextGateBlocksNonLoopbackHttpWhenDisabled() {
        assertThrows(AiError.InsecureHttpBlocked::class.java) {
            AiHttp.assertCleartextAllowed("http://192.168.1.10:8000/v1", allowInsecureHttp = false)
        }
    }

    @Test
    fun cleartextGateAllowsLoopbackHttpWhenDisabled() {
        // Default Ollama URL + emulator alias must keep working with the toggle off.
        AiHttp.assertCleartextAllowed("http://localhost:11434/v1", allowInsecureHttp = false)
        AiHttp.assertCleartextAllowed("http://127.0.0.1:11434/v1", allowInsecureHttp = false)
    }

    @Test
    fun cleartextGateAllowsHttpWhenEnabled() {
        AiHttp.assertCleartextAllowed("http://192.168.1.10:8000/v1", allowInsecureHttp = true)
    }

    @Test
    fun cleartextGateIgnoresHttpsAndBlank() {
        AiHttp.assertCleartextAllowed("https://192.168.1.10:8000/v1", allowInsecureHttp = false)
        AiHttp.assertCleartextAllowed("", allowInsecureHttp = false)
    }

    @Test
    fun normalizeDefaultsMissingSchemeToHttps() {
        assertEquals(
            "https://192.168.1.10:8000/v1",
            AiHttp.normalizeCustomBaseUrl("192.168.1.10:8000/v1"),
        )
    }

    @Test
    fun normalizePreservesExplicitHttps() {
        assertEquals(
            "https://your-endpoint.com/v1",
            AiHttp.normalizeCustomBaseUrl("https://your-endpoint.com/v1"),
        )
    }

    @Test
    fun normalizePreservesExplicitHttp() {
        assertEquals(
            "http://192.168.1.10:8000/v1",
            AiHttp.normalizeCustomBaseUrl("http://192.168.1.10:8000/v1"),
        )
    }

    @Test
    fun normalizeCollapsesStackedSchemes() {
        assertEquals(
            "https://your-endpoint.com/v1",
            AiHttp.normalizeCustomBaseUrl("https://https://your-endpoint.com/v1"),
        )
    }

    @Test
    fun normalizeCollapsesHttpThenHttpsToHttps() {
        assertEquals(
            "https://your-endpoint.com/v1",
            AiHttp.normalizeCustomBaseUrl("http://https://your-endpoint.com/v1"),
        )
    }

    @Test
    fun normalizeTrimsTrailingSlash() {
        assertEquals(
            "https://your-endpoint.com/v1",
            AiHttp.normalizeCustomBaseUrl("https://your-endpoint.com/v1/"),
        )
    }

    @Test
    fun normalizeTrimsWhitespace() {
        assertEquals(
            "https://your-endpoint.com/v1",
            AiHttp.normalizeCustomBaseUrl("  https://your-endpoint.com/v1  "),
        )
    }

    @Test
    fun normalizeLeavesBlankInputBlank() {
        assertEquals("", AiHttp.normalizeCustomBaseUrl("   "))
    }
}
