package app.chompass.data

import org.junit.Assert.assertEquals
import org.junit.Test
import app.chompass.models.AIProvider
import app.chompass.services.ai.AiHttp

class AiPreferencesTest {
    @Test
    fun responseCapFor_reasoningAware() {
        // Reasoning-capable surfaces get headroom (thinking burns the cap);
        // Anthropic and on-device never receive reasoning fields here.
        assertEquals(
            MAX_REASONING_RESPONSE_TOKENS,
            responseCapFor(AIProvider.OPENROUTER, OpenRouterReasoningEffort.AUTO),
        )
        assertEquals(
            MAX_REASONING_RESPONSE_TOKENS,
            responseCapFor(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.LOW),
        )
        assertEquals(
            MAX_REASONING_RESPONSE_TOKENS,
            responseCapFor(AIProvider.GEMINI, OpenRouterReasoningEffort.AUTO),
        )
        assertEquals(
            DEFAULT_MAX_RESPONSE_TOKENS,
            responseCapFor(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.DISABLED),
        )
        assertEquals(
            DEFAULT_MAX_RESPONSE_TOKENS,
            responseCapFor(AIProvider.ANTHROPIC, OpenRouterReasoningEffort.AUTO),
        )
        assertEquals(
            DEFAULT_MAX_RESPONSE_TOKENS,
            responseCapFor(AIProvider.ON_DEVICE, OpenRouterReasoningEffort.AUTO),
        )
    }

    @Test
    fun clampAiReadTimeoutSeconds_capsAt900() {
        assertEquals(900, clampAiReadTimeoutSeconds(1000))
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
        assertEquals(MAX_AI_READ_TIMEOUT_SECONDS, clampAiReadTimeoutSeconds(1000))
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
