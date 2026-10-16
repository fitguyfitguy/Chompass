package app.chompass.models

import app.chompass.BuildConfig

import org.junit.Assert.assertEquals
import org.junit.Test

class AIProviderFallbackTest {
    @Test
    fun geminiDefaultModelMatchesVariant() {
        // Debug defaults to Flash-Lite (3.7 Flash 503s on the free tier during
        // device testing; 3.8's free tier unverified); release keeps the
        // parity-locked 3.8 default.
        val expected = if (BuildConfig.DEBUG) "gemini-3.5-flash-lite" else "gemini-3.8-flash"
        assertEquals(expected, AIProvider.GEMINI.defaultModel)
    }

    @Test
    fun geminiDefaultFallbackIsFlashLite() {
        assertEquals("gemini-3.5-flash-lite", AIProvider.GEMINI.defaultFallbackModel)
    }

    @Test
    fun geminiUnsetFallbackResolvesToFlashLite() {
        assertEquals(
            "gemini-3.5-flash-lite",
            AIProvider.GEMINI.supportedFallbackModelOrDefault(null),
        )
        assertEquals(
            "gemini-3.5-flash-lite",
            AIProvider.GEMINI.supportedFallbackModelOrDefault(""),
        )
    }

    @Test
    fun geminiRetired25FallbackFallsBackToDefault() {
        // gemini-2.5 left the catalog (Google 2.5-family shutdown, 2026-10);
        // stale selections resolve to the current default, not a catalog miss.
        assertEquals(
            AIProvider.GEMINI.defaultFallbackModel,
            AIProvider.GEMINI.supportedFallbackModelOrDefault("gemini-2.5-flash"),
        )
    }

    @Test
    fun geminiRetired25ModelFallsBackToDefault() {
        assertEquals(
            AIProvider.GEMINI.defaultModel,
            AIProvider.GEMINI.supportedModelOrDefault("gemini-2.5-pro"),
        )
    }

    @Test
    fun geminiUnknownFallbackFallsBackToFlashLite() {
        assertEquals(
            "gemini-3.5-flash-lite",
            AIProvider.GEMINI.supportedFallbackModelOrDefault("gemini-does-not-exist"),
        )
    }

    @Test
    fun ollamaCustomModelNameIsPreserved() {
        assertEquals(
            "llama3.2:latest",
            AIProvider.OLLAMA.supportedModelOrDefault("llama3.2:latest"),
        )
    }

    @Test
    fun openAiRuntimeLineupIdIsPreserved() {
        // #107: not curated, but the shape of the runtime lineup, so a picked
        // id survives select, restore, and request routing.
        assertEquals("gpt-6-astra", AIProvider.OPENAI.supportedModelOrDefault("gpt-6-astra"))
        assertEquals("gpt-5.4-mini", AIProvider.OPENAI.supportedModelOrDefault("gpt-3.5-turbo"))
    }

    @Test
    fun groqRuntimeLineupIdIsPreserved() {
        // #132: multi-vendor providers have no stable id shape; a runtime-picked
        // id survives select and request routing (and the fallback-profile gate).
        val id = "meta-llama/llama-4-scout-17b-16e-instruct"
        assertEquals(id, AIProvider.GROQ.supportedModelOrDefault(id))
        assertEquals(id, AIProvider.GROQ.supportedFallbackModelOrDefault(id))
    }

    @Test
    fun xaiRuntimeLineupIdIsPreserved() {
        assertEquals("grok-4.3-canary", AIProvider.XAI.supportedModelOrDefault("grok-4.3-canary"))
    }
}
