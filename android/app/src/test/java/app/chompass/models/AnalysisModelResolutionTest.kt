package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The meal-analysis model slot (#148): a pinned Food model wins for the
 * food-analysis text family, a missing or blank slot falls back to the
 * profile's primary model. Image routing stays in
 * [resolveModelForRequest] — the vision slot wins for photo requests even
 * when a Food model is pinned.
 */
class AnalysisModelResolutionTest {
    @Test
    fun analysisSlotOverridesPrimaryModel() {
        assertEquals(
            "gemini-3.5-flash-lite",
            analysisSelectedModel("gemini-3.5-flash-lite", "gemini-3.6-flash"),
        )
    }

    @Test
    fun unsetAnalysisSlotUsesPrimaryModel() {
        assertEquals(
            "gemini-3.6-flash",
            analysisSelectedModel(null, "gemini-3.6-flash"),
        )
    }

    @Test
    fun blankAnalysisSlotUsesPrimaryModel() {
        assertEquals(
            "gemini-3.6-flash",
            analysisSelectedModel("  ", "gemini-3.6-flash"),
        )
    }

    @Test
    fun textRequests_useAnalysisSlot_overPrimary() {
        val selected = analysisSelectedModel("gemini-3.5-flash-lite", "gemini-3.6-flash")
        assertEquals(
            "gemini-3.5-flash-lite",
            resolveModelForRequest(AIProvider.GEMINI, selected, visionModel = null, hasImages = false),
        )
    }

    @Test
    fun imageRequests_visionSlotBeatsAnalysisSlot() {
        val selected = analysisSelectedModel("gemini-3.5-flash-lite", "gemini-3.6-flash")
        assertEquals(
            "gemini-3.5-flash",
            resolveModelForRequest(AIProvider.GEMINI, selected, visionModel = "gemini-3.5-flash", hasImages = true),
        )
        // No vision slot: the pinned analysis model serves photos too.
        assertEquals(
            "gemini-3.5-flash-lite",
            resolveModelForRequest(AIProvider.GEMINI, selected, visionModel = null, hasImages = true),
        )
    }
}
