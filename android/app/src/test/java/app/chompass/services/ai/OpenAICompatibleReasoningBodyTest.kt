package app.chompass.services.ai

import app.chompass.data.OpenRouterReasoningEffort
import app.chompass.models.AIProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** OpenRouter reasoning-effort request body (upstream #194). */
class OpenAICompatibleReasoningBodyTest {
    @Test
    fun nonOpenRouterCaller_returnsNull() {
        assertNull(OpenAICompatibleClient.reasoningBody(null, compactRetry = false))
        assertNull(OpenAICompatibleClient.reasoningBody(null, compactRetry = true))
    }

    @Test
    fun auto_regularCall_keepsHistoricalExcludeOnly() {
        val body = OpenAICompatibleClient.reasoningBody(OpenRouterReasoningEffort.AUTO, compactRetry = false)!!
        assertEquals("{\"exclude\":true}", body.toString())
    }

    @Test
    fun auto_compactRetry_keepsHistoricalLowEffort() {
        val body = OpenAICompatibleClient.reasoningBody(OpenRouterReasoningEffort.AUTO, compactRetry = true)!!
        assertEquals("low", body.getString("effort"))
        assertEquals(true, body.getBoolean("exclude"))
    }

    @Test
    fun explicitEfforts_sentOnEveryRequest() {
        for (effort in listOf(
            OpenRouterReasoningEffort.LOW,
            OpenRouterReasoningEffort.MEDIUM,
            OpenRouterReasoningEffort.HIGH,
        )) {
            for (compactRetry in listOf(false, true)) {
                val body = OpenAICompatibleClient.reasoningBody(effort, compactRetry)!!
                assertEquals(effort.requestValue, body.getString("effort"))
                assertEquals(true, body.getBoolean("exclude"))
            }
        }
    }

    @Test
    fun disabled_excludeOnly_neverBudgeted() {
        for (compactRetry in listOf(false, true)) {
            val body = OpenAICompatibleClient.reasoningBody(OpenRouterReasoningEffort.DISABLED, compactRetry)!!
            assertEquals("{\"exclude\":true}", body.toString())
        }
    }

    @Test
    fun flatEffort_nullForAutoNullEffortAndForeignProviders() {
        assertNull(OpenAICompatibleClient.flatReasoningEffort(AIProvider.CUSTOM_OPENAI, null))
        assertNull(OpenAICompatibleClient.flatReasoningEffort(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.AUTO))
        assertNull(OpenAICompatibleClient.flatReasoningEffort(AIProvider.OPENROUTER, OpenRouterReasoningEffort.LOW))
        assertNull(OpenAICompatibleClient.flatReasoningEffort(AIProvider.OPENAI, OpenRouterReasoningEffort.HIGH))
    }

    @Test
    fun flatEffort_explicitValuesForCustomAndOllama() {
        assertEquals("low", OpenAICompatibleClient.flatReasoningEffort(AIProvider.CUSTOM_OPENAI, OpenRouterReasoningEffort.LOW))
        assertEquals("high", OpenAICompatibleClient.flatReasoningEffort(AIProvider.OLLAMA, OpenRouterReasoningEffort.HIGH))
        assertEquals("none", OpenAICompatibleClient.flatReasoningEffort(AIProvider.OLLAMA, OpenRouterReasoningEffort.DISABLED))
    }
}
