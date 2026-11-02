package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coach's model slot: a pinned Coach model wins for chat, a missing or blank
 * slot falls back to the profile's primary model. Image routing stays in
 * [app.chompass.models.resolveModelForRequest] and is unchanged by this slot.
 */
class CoachModelResolutionTest {
    @Test
    fun coachSlotOverridesPrimaryModel() {
        assertEquals(
            "claude-opus-5-5",
            coachSelectedModel("claude-opus-5-5", "claude-haiku-5-5"),
        )
    }

    @Test
    fun unsetCoachSlotUsesPrimaryModel() {
        assertEquals(
            "claude-haiku-5-5",
            coachSelectedModel(null, "claude-haiku-5-5"),
        )
    }

    @Test
    fun blankCoachSlotUsesPrimaryModel() {
        assertEquals(
            "claude-haiku-5-5",
            coachSelectedModel("  ", "claude-haiku-5-5"),
        )
    }
}
