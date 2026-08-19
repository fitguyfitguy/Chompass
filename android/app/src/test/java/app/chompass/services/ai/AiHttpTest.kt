package app.chompass.services.ai

import app.chompass.models.AIProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

        // Cloud provider: same instance back, trust builder untouched.
        val cloud = AiHttp.clientForProvider(base, AIProvider.GEMINI, 60, fake)
        assertSame(base, cloud)
        assertEquals(1, trustCalls)
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
