package app.chompass.ui.home

import app.chompass.models.FoodSource
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.PartialFoodAnalysis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ready-gate rules for the progressive Log sheet (no Android instrumentation).
 * Mirrors [HomeUiState.analysisReadyForEdit] / [HomeUiState.showFoodResultSheet].
 */
class FoodResultReadyGateTest {
    @Test
    fun showSheet_whileBusyEvenWithoutAnalysis() {
        assertTrue(showFoodResultSheet(pending = null, busy = true))
        assertFalse(showFoodResultSheet(pending = null, busy = false))
        assertTrue(showFoodResultSheet(pending = sampleAnalysis(), busy = false))
    }

    @Test
    fun analysisReady_requiresPendingAndNotBusy() {
        assertFalse(analysisReadyForEdit(pending = null, busy = true))
        assertFalse(analysisReadyForEdit(pending = sampleAnalysis(), busy = true))
        assertFalse(analysisReadyForEdit(pending = null, busy = false))
        assertTrue(analysisReadyForEdit(pending = sampleAnalysis(), busy = false))
    }

    @Test
    fun portionClarify_stillSnapOnlyAfterReady() {
        assertTrue(shouldOfferPortionClarify(FoodSource.SNAP_FOOD))
        assertFalse(shouldOfferPortionClarify(FoodSource.SNAP_FOOD, portionPreConfirmed = true))
        assertFalse(shouldOfferPortionClarify(FoodSource.TEXT_INPUT))
    }

    @Test
    fun partialPreview_requiresName() {
        assertTrue(
            PartialFoodAnalysis(name = "Oatmeal", calories = 200).toPreviewAnalysis() != null,
        )
        assertTrue(
            PartialFoodAnalysis(calories = 200).toPreviewAnalysis() == null,
        )
    }

    private fun showFoodResultSheet(pending: FoodAnalysis?, busy: Boolean): Boolean =
        pending != null || busy

    private fun analysisReadyForEdit(pending: FoodAnalysis?, busy: Boolean): Boolean =
        pending != null && !busy

    private fun sampleAnalysis() = FoodAnalysis(
        name = "Test meal",
        calories = 400,
        protein = 20.0,
        carbs = 40.0,
        fat = 10.0,
        servingSizeGrams = 250.0,
    )
}
