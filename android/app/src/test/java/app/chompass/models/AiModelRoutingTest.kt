package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** Capability-based model routing (upstream #195). */
class AiModelRoutingTest {
    @Test
    fun textOnlyRequests_usePrimaryModel() {
        assertEquals(
            "gemini-3.6-flash",
            resolveModelForRequest(AIProvider.GEMINI, "gemini-3.6-flash", "gemini-3.5-flash", hasImages = false),
        )
    }

    @Test
    fun imageRequests_withoutVisionSlot_usePrimaryModel() {
        assertEquals(
            "gemini-3.6-flash",
            resolveModelForRequest(AIProvider.GEMINI, "gemini-3.6-flash", null, hasImages = true),
        )
        assertEquals(
            "gemini-3.6-flash",
            resolveModelForRequest(AIProvider.GEMINI, "gemini-3.6-flash", "   ", hasImages = true),
        )
    }

    @Test
    fun imageRequests_withVisionSlot_useVisionModel() {
        assertEquals(
            "gemini-3.5-flash",
            resolveModelForRequest(AIProvider.GEMINI, "gemini-3.6-flash", "gemini-3.5-flash", hasImages = true),
        )
    }

    @Test
    fun customModelProvider_acceptsArbitraryVisionId() {
        assertEquals(
            "deepseek/deepseek-v4",
            resolveModelForRequest(AIProvider.OPENROUTER, "openrouter/free", "deepseek/deepseek-v4", hasImages = true),
        )
        // Blank primary falls back to the provider default; vision still wins for images.
        assertEquals(
            "deepseek/deepseek-v4",
            resolveModelForRequest(AIProvider.OPENROUTER, null, "deepseek/deepseek-v4", hasImages = true),
        )
    }

    @Test
    fun unknownVisionId_onCuratedProvider_fallsBackToDefault() {
        assertEquals(
            AIProvider.GEMINI.defaultModel,
            resolveModelForRequest(AIProvider.GEMINI, "gemini-3.6-flash", "not-a-model", hasImages = true),
        )
    }

    @Test
    fun runtimeOpenAiLineupId_survivesRouting() {
        // #107: a picked runtime lineup id (not curated) routes as the primary.
        assertEquals(
            "gpt-6-astra",
            resolveModelForRequest(AIProvider.OPENAI, "gpt-6-astra", null, hasImages = false),
        )
        // Off-lineup ids still snap to the OpenAI default.
        assertEquals(
            AIProvider.OPENAI.defaultModel,
            resolveModelForRequest(AIProvider.OPENAI, "gpt-3.5-turbo", null, hasImages = false),
        )
    }
}
