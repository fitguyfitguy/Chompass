package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemini per-request config: thinking level is mapped per model family off
 * the closed AIProvider catalog (flash-lite minimal, 3.5-3.8 flash low, older
 * families untouched), the user response-length cap reaches Gemini, and JSON
 * mode is only requested when Google Search grounding is off. If the mapping
 * regresses to "always minimal" or drops the cap, the token-budget bug class
 * (#97 family) returns; if thinkingConfig leaks onto 2.5-family models, these
 * fail before a 400 ever reaches a user.
 */
class GeminiClientRequestConfigTest {
    @Test
    fun flashLite_getsThinkingLevelMinimal() {
        for (model in listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite")) {
            val config = GeminiClient.generationConfig(model, maxTokens = null, jsonResponse = false)!!

            assertTrue(model, config.has("thinkingConfig"))
            assertEquals(model, "minimal", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        }
    }

    @Test
    fun flash38_getsThinkingLevelLow() {
        for (model in listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash")) {
            val config = GeminiClient.generationConfig(model, maxTokens = null, jsonResponse = false)!!

            assertTrue(model, config.has("thinkingConfig"))
            assertEquals(model, "low", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        }
    }

    @Test
    fun proAnd25Family_getNoThinkingConfig() {
        for (model in listOf("gemini-3.1-pro-preview", "gemini-2.5-flash", "gemini-2.5-pro")) {
            val config = GeminiClient.generationConfig(model, maxTokens = 16384, jsonResponse = false)!!

            assertFalse(model, config.has("thinkingConfig"))
            assertEquals(model, 16384, config.getInt("maxOutputTokens"))
        }
    }

    @Test
    fun generationConfig_returnsNull_whenNothingApplies() {
        for (model in listOf("gemini-3.1-pro-preview", "gemini-2.5-flash", "gemini-2.5-pro")) {
            assertNull(model, GeminiClient.generationConfig(model, maxTokens = null, jsonResponse = false))
        }
    }

    @Test
    fun foodOps_getJsonMime_andMaxOutputTokens() {
        val config = GeminiClient.generationConfig("gemini-3.8-flash", maxTokens = 16384, jsonResponse = true)!!

        assertEquals("low", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertEquals(16384, config.getInt("maxOutputTokens"))
        assertEquals("application/json", config.getString("responseMimeType"))
    }

    @Test
    fun googleSearchOmitsJsonMime() {
        val config = GeminiClient.generationConfig("gemini-3.8-flash", maxTokens = 16384, jsonResponse = false)!!

        assertFalse(config.has("responseMimeType"))
    }

    @Test
    fun nonPositiveCap_isOmitted() {
        val config = GeminiClient.generationConfig("gemini-3.8-flash", maxTokens = 0, jsonResponse = false)!!

        assertFalse(config.has("maxOutputTokens"))
    }
}
