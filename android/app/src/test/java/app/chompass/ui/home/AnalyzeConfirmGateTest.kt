package app.chompass.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6 (UX friction audit): the sparse “Analyze now?” staging confirm stays only
 * for the first noteless single-photo analyze. Once the user confirms it, the
 * acknowledgment is persisted and later sparse analyzes run straight through.
 * Legacy gate rules (no acknowledgment) are covered by [PhotoNotePromptTest].
 */
class AnalyzeConfirmGateTest {
    @Test
    fun sparseCase_ackNotSet_dialogShows() {
        assertTrue(
            needsAnalyzeConfirm(
                requireNote = true,
                noteBlank = true,
                imageCount = 1,
                analyzeConfirmAcknowledged = false,
            )
        )
    }

    @Test
    fun sparseCase_ackSet_analyzeRunsDirectly() {
        assertFalse(
            needsAnalyzeConfirm(
                requireNote = true,
                noteBlank = true,
                imageCount = 1,
                analyzeConfirmAcknowledged = true,
            )
        )
    }

    @Test
    fun blankNoteMultiPhoto_ackSet_stillConfirms() {
        assertTrue(
            needsAnalyzeConfirm(
                requireNote = true,
                noteBlank = true,
                imageCount = 2,
                analyzeConfirmAcknowledged = true,
            )
        )
    }

    @Test
    fun notedSinglePhoto_ackSet_stillConfirms() {
        assertTrue(
            needsAnalyzeConfirm(
                requireNote = true,
                noteBlank = false,
                imageCount = 1,
                analyzeConfirmAcknowledged = true,
            )
        )
    }

    @Test
    fun photoNotePromptOff_neverConfirms_regardlessOfAck() {
        assertFalse(
            needsAnalyzeConfirm(
                requireNote = false,
                noteBlank = true,
                imageCount = 1,
                analyzeConfirmAcknowledged = true,
            )
        )
    }
}
