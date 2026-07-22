package org.codeberg.fitguy.nofud.models

import org.junit.Assert.assertEquals
import org.junit.Test

class AIProviderFallbackTest {
    @Test
    fun geminiDefaultModelIsFlash36() {
        assertEquals("gemini-3.6-flash", AIProvider.GEMINI.defaultModel)
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
