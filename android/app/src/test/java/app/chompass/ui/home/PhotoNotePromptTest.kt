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
    fun analyzeConfirm_whenSparseInput() {
        assertTrue(needsAnalyzeConfirm(requireNote = true, noteBlank = true, imageCount = 1))
        assertTrue(needsAnalyzeConfirm(requireNote = true, noteBlank = true, imageCount = 2))
        assertTrue(needsAnalyzeConfirm(requireNote = true, noteBlank = false, imageCount = 1))
        assertFalse(needsAnalyzeConfirm(requireNote = true, noteBlank = false, imageCount = 2))
    }

    @Test
    fun analyzeConfirm_skippedWhenPhotoNotePromptOff() {
        assertFalse(needsAnalyzeConfirm(requireNote = false, noteBlank = true, imageCount = 1))
        assertFalse(needsAnalyzeConfirm(requireNote = false, noteBlank = true, imageCount = 2))
        assertFalse(needsAnalyzeConfirm(requireNote = false, noteBlank = false, imageCount = 1))
        assertFalse(needsAnalyzeConfirm(requireNote = false, noteBlank = false, imageCount = 2))
    }

    @Test
    fun tipStrip_onlyAfterAnalysisReady() {
        assertFalse(showTipStripDuringAnalysis(analysisReady = false))
        assertTrue(showTipStripDuringAnalysis(analysisReady = true))
    }

    @Test
    fun accuracyGuideCard_forFirstThreePhotoAnalyzes() {
        val t = HomeViewModel.PHOTO_ACCURACY_GUIDE_COUNT
        assertTrue(showAccuracyGuideCard(guideCount = 0))
        assertTrue(showAccuracyGuideCard(guideCount = t - 1))
        assertFalse(showAccuracyGuideCard(guideCount = t))
        assertFalse(showAccuracyGuideCard(guideCount = t + 5))
    }

    private fun requirePhotoNote(skipPrompt: Boolean): Boolean = !skipPrompt

    private fun showDontAskAgain(skipPrompt: Boolean, skipCount: Int): Boolean =
        !skipPrompt && skipCount >= HomeViewModel.PHOTO_NOTE_SKIP_OFFER_THRESHOLD

    private fun showTipStripDuringAnalysis(analysisReady: Boolean): Boolean = analysisReady

    private fun showAccuracyGuideCard(guideCount: Int): Boolean =
        guideCount < HomeViewModel.PHOTO_ACCURACY_GUIDE_COUNT
}
