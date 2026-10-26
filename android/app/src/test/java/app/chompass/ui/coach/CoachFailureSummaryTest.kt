package app.chompass.ui.coach

import app.chompass.services.ai.AiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CoachFailureSummaryTest {
    @Test
    fun namesTheCauseChainWithoutMessages() {
        val error = AiError.Network(IOException("POST https://private.example/v1 key=sk-secret: Bearer token"))

        val summary = coachFailureSummary(error)

        assertTrue(summary, summary.startsWith("${AiError.Network::class.java.name} <- java.io.IOException at "))
        assertFalse(summary, "private.example" in summary)
        assertFalse(summary, "sk-secret" in summary)
        assertFalse(summary, "Bearer" in summary)
    }

    @Test
    fun messagelessFailuresStillNameTheType() {
        // NetworkOnMainThreadException has no message, which is why the UI fell back to "Chat failed".
        val summary = coachFailureSummary(IllegalStateException())

        assertTrue(summary, summary.startsWith("java.lang.IllegalStateException at "))
    }
}
