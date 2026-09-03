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
    fun geminiStoredFallbackIsPreserved() {
        assertEquals(
            "gemini-2.5-flash",
            AIProvider.GEMINI.supportedFallbackModelOrDefault("gemini-2.5-flash"),
        )
    }

    @Test
    fun geminiUnknownFallbackFallsBackToFlashLite() {
        assertEquals(
            "gemini-3.5-flash-lite",
            AIProvider.GEMINI.supportedFallbackModelOrDefault("gemini-does-not-exist"),
        )
    }
}
