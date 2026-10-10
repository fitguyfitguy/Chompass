package app.chompass.services.ai

import app.chompass.models.AIProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Response-token floor for cloud providers: reasoning models (OpenAI
 * max_completion_tokens, DeepSeek max_tokens, Gemini maxOutputTokens, GLM
 * reasoning_content) bill hidden thinking against the cap, so the 1024
 * default truncated replies before any content — calculateGoals came back
 * finish=length with empty content on deepseek-flash (2026-10-10). The floor
 * applies to every cloud call at both cap sites (analysis dispatch and
 * chat); Ollama keeps the user cap verbatim because local models are the
 * context-constrained ones.
 */
class ResponseTokenFloorTest {
    @Test
    fun cloudProvidersFloorAt4096() {
        assertEquals(4096, effectiveResponseTokens(AIProvider.GEMINI, 1024))
        assertEquals(4096, effectiveResponseTokens(AIProvider.ANTHROPIC, 1024))
        assertEquals(4096, effectiveResponseTokens(AIProvider.OPENAI, 1024))
        assertEquals(4096, effectiveResponseTokens(AIProvider.OPENROUTER, 1024))
        assertEquals(4096, effectiveResponseTokens(AIProvider.CUSTOM_OPENAI, 1024))
        assertEquals(4096, effectiveResponseTokens(AIProvider.DEEP_INFRA, 256))
    }

    @Test
    fun userCapAboveFloorWins() {
        assertEquals(8192, effectiveResponseTokens(AIProvider.GEMINI, 8192))
        assertEquals(4096, effectiveResponseTokens(AIProvider.ANTHROPIC, 4096))
        assertEquals(6000, effectiveResponseTokens(AIProvider.CUSTOM_OPENAI, 6000))
    }

    @Test
    fun ollamaKeepsUserCapVerbatim() {
        // Local models are the context-constrained ones: a forced 4096
        // num_predict can exceed a small local context window.
        assertEquals(1024, effectiveResponseTokens(AIProvider.OLLAMA, 1024))
        assertEquals(256, effectiveResponseTokens(AIProvider.OLLAMA, 256))
        assertEquals(8192, effectiveResponseTokens(AIProvider.OLLAMA, 8192))
    }
}
