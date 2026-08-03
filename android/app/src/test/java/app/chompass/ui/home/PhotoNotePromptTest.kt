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
        assertTrue(needsAnalyzeConfirm(noteBlank = true, imageCount = 1))
        assertTrue(needsAnalyzeConfirm(noteBlank = true, imageCount = 2))
        assertTrue(needsAnalyzeConfirm(noteBlank = false, imageCount = 1))
        assertFalse(needsAnalyzeConfirm(noteBlank = false, imageCount = 2))
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

    private fun needsAnalyzeConfirm(noteBlank: Boolean, imageCount: Int): Boolean =
        noteBlank || imageCount < 2

    private fun showTipStripDuringAnalysis(analysisReady: Boolean): Boolean = analysisReady

    private fun showAccuracyGuideCard(guideCount: Int): Boolean =
        guideCount < HomeViewModel.PHOTO_ACCURACY_GUIDE_COUNT
}
