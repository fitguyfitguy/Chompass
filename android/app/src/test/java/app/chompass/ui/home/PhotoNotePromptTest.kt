package app.chompass.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pref / threshold rules for required photo notes before Analyze. */
class PhotoNotePromptTest {

    @Test
    fun requireNote_whenNotSkipped() {
        assertTrue(requirePhotoNote(skipPrompt = false))
        assertFalse(requirePhotoNote(skipPrompt = true))
    }

    @Test
    fun offerDontAskAgain_afterThresholdSkips() {
        val t = HomeViewModel.PHOTO_NOTE_SKIP_OFFER_THRESHOLD
        assertFalse(showDontAskAgain(skipPrompt = false, skipCount = t - 1))
        assertTrue(showDontAskAgain(skipPrompt = false, skipCount = t))
        assertTrue(showDontAskAgain(skipPrompt = false, skipCount = t + 2))
        assertFalse(showDontAskAgain(skipPrompt = true, skipCount = t + 5))
    }

    @Test
    fun analyzeEnabled_requiresNoteWhenPromptOn() {
        assertFalse(primaryAnalyzeEnabled(requireNote = true, note = ""))
        assertFalse(primaryAnalyzeEnabled(requireNote = true, note = "   "))
        assertTrue(primaryAnalyzeEnabled(requireNote = true, note = "2 eggs"))
        assertTrue(primaryAnalyzeEnabled(requireNote = false, note = ""))
    }

    private fun requirePhotoNote(skipPrompt: Boolean): Boolean = !skipPrompt

    private fun showDontAskAgain(skipPrompt: Boolean, skipCount: Int): Boolean =
        !skipPrompt && skipCount >= HomeViewModel.PHOTO_NOTE_SKIP_OFFER_THRESHOLD

    private fun primaryAnalyzeEnabled(requireNote: Boolean, note: String): Boolean =
        !requireNote || note.isNotBlank()
}
