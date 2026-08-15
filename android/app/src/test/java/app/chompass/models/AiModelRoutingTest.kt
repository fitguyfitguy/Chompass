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
            "gemini-3.7-flash",
            resolveModelForRequest(AIProvider.GEMINI, "gemini-3.6-flash", "not-a-model", hasImages = true),
        )
    }
}
