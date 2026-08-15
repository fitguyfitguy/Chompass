package app.chompass.services.ai

import app.chompass.data.OpenRouterReasoningEffort
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
}
