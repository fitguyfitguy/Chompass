package app.chompass.data

import org.junit.Assert.assertEquals
import org.junit.Test
import app.chompass.services.ai.AiHttp

class AiPreferencesTest {
    @Test
    fun clampMaxResponseTokens_withinRange() {
        assertEquals(1024, clampMaxResponseTokens(1024))
    }

    @Test
    fun clampMaxResponseTokens_belowMinimum() {
        assertEquals(MIN_MAX_RESPONSE_TOKENS, clampMaxResponseTokens(1))
    }

    @Test
    fun clampMaxResponseTokens_aboveMaximum() {
        assertEquals(MAX_MAX_RESPONSE_TOKENS, clampMaxResponseTokens(999_999))
    }

    @Test
    fun clampAiReadTimeoutSeconds_withinRange() {
        assertEquals(120, clampAiReadTimeoutSeconds(120))
    }

    @Test
    fun clampAiReadTimeoutSeconds_belowMinimum() {
        assertEquals(MIN_AI_READ_TIMEOUT_SECONDS, clampAiReadTimeoutSeconds(5))
    }

    @Test
    fun clampAiReadTimeoutSeconds_aboveMaximum() {
        assertEquals(MAX_AI_READ_TIMEOUT_SECONDS, clampAiReadTimeoutSeconds(600))
    }

    @Test
    fun sanitizeApiKey_trimsWhitespace() {
        assertEquals("abc123", AiHttp.sanitizeApiKey(" abc123\n"))
    }

    @Test
    fun sanitizeApiKey_blankBecomesNull() {
        assertEquals(null, AiHttp.sanitizeApiKey("  \n"))
    }
}
