package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picker metadata added for the grouped provider UI: every entry carries a
 * group + subtitle, exactly the 12 key-requiring cloud providers expose a
 * signup URL, and group ordering puts Gemini first and On-Device last.
 */
class AIProviderMetadataTest {
    private val keyRequiring = setOf(
        AIProvider.GEMINI, AIProvider.OPENAI, AIProvider.ANTHROPIC, AIProvider.XAI,
        AIProvider.OPENROUTER, AIProvider.TOGETHER_AI, AIProvider.GROQ,
        AIProvider.HUGGING_FACE, AIProvider.FIREWORKS, AIProvider.DEEP_INFRA,
        AIProvider.MISTRAL, AIProvider.OLLAMA_CLOUD,
    )

    @Test
    fun everyProviderHasGroupAndSubtitle() {
        for (p in AIProvider.entries) {
            assertNotNull("group missing for $p", p.group)
            assertTrue("subtitle missing for $p", p.subtitleRes != 0)
        }
    }

    @Test
    fun signupUrl_exactlyForTheTwelveCloudEntries() {
        assertEquals(12, keyRequiring.size)
        for (p in AIProvider.entries) {
            if (p in keyRequiring) {
                assertNotNull("signup URL missing for $p", p.signupUrl)
                assertTrue("signup URL not https for $p", p.signupUrl!!.startsWith("https://"))
            } else {
                assertNull("signup URL must be null for $p", p.signupUrl)
            }
        }
    }

    @Test
    fun groupOrder_geminiFirstOnDeviceLast() {
        val sorted = AIProvider.entries.sortedBy { it.group.ordinal }
        assertEquals(AIProvider.GEMINI, sorted.first())
        assertEquals(AIProvider.ON_DEVICE, sorted.last())
        // Cloud sits between Recommended and Local.
        assertTrue(
            sorted.indexOf(AIProvider.OPENAI) > sorted.indexOf(AIProvider.GEMINI) &&
                sorted.indexOf(AIProvider.OPENAI) < sorted.indexOf(AIProvider.OLLAMA),
        )
    }

    @Test
    fun runtimeLineup_exactlyForModelsServingProviders() {
        // #107/#132: providers with a usable GET /models on their built-in base
        // fetch a runtime lineup; GEMINI/ANTHROPIC keep curated lists, and the
        // local/device entries have no hosted /models. Ollama Cloud serves
        // ollama.com/v1/models in OpenAI shape.
        val expected = setOf(
            AIProvider.OPENAI, AIProvider.XAI, AIProvider.TOGETHER_AI, AIProvider.GROQ,
            AIProvider.HUGGING_FACE, AIProvider.FIREWORKS, AIProvider.DEEP_INFRA,
            AIProvider.MISTRAL, AIProvider.OLLAMA_CLOUD,
        )
        for (p in AIProvider.entries) {
            assertEquals("supportsRuntimeLineup wrong for $p", p in expected, p.supportsRuntimeLineup)
        }
    }
}
