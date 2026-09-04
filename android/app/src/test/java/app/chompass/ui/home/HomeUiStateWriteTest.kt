package app.chompass.ui.home

import android.app.Application
import app.chompass.models.PendingFoodAnalysisDraft
import app.chompass.models.UserProfile
import app.chompass.services.ai.GoalCalculation
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.RecalcSheetData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the hero ⓘ → recalc-details state writes. HomeUiState has a
 * hand-written equals that deliberately ignores photo-ByteArray fields; any new
 * state field (e.g. lastRecalcSheet/recalcSheet) must be added there too, or
 * StateFlow's equality check silently drops writes that differ only in that field
 * (observed 2026-08-22: the persisted goal-change sheet never reached the hero).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeUiStateWriteTest {
    private val sheet = RecalcSheetData(
        result = GoalCalculation(calories = 1917, protein = 132, carbs = 248, fat = 44),
        before = UserProfile(heightCm = 178.0, weightKg = 76.0),
        after = UserProfile(heightCm = 178.0, weightKg = 76.0),
    )

    @Test
    fun copyAndWrite_preservesSheetField() {
        val flow = MutableStateFlow(HomeUiState())
        val next = flow.value.copy(lastRecalcSheet = sheet)
        assertTrue("copy must carry the sheet", next.lastRecalcSheet != null)
        flow.value = next
        assertTrue("read-back must carry the sheet", flow.value.lastRecalcSheet != null)
    }

    @Test
    fun updateExtension_preservesSheetField() {
        val flow = MutableStateFlow(HomeUiState())
        flow.update { it.copy(lastRecalcSheet = sheet) }
        assertTrue(flow.value.lastRecalcSheet != null)
    }

    @Test
    fun combineStyleMerge_preservesSheetField() {
        val flow = MutableStateFlow(HomeUiState().copy(lastRecalcSheet = sheet))
        flow.update { cur ->
            cur.copy(
                profile = UserProfile(heightCm = 180.0, weightKg = 80.0),
                date = java.time.LocalDate.now(),
                todayEntries = emptyList(),
                foodLogSortOrder = FoodLogSortOrder.STANDARD,
                favoriteKeys = emptySet(),
            )
        }
        assertEquals(1917, flow.value.lastRecalcSheet?.result?.calories)
    }

    @Test
    fun equals_distinguishesSheetChanges() {
        val withSheet = HomeUiState().copy(lastRecalcSheet = sheet)
        assertTrue("custom equals must not treat sheet-only changes as equal", withSheet != HomeUiState())
    }

    @Test
    fun copyAndWrite_preservesRecoveredReview() {
        val draft = PendingFoodAnalysisDraft(
            analysis = FoodAnalysis(
                name = "Chicken rice",
                calories = 520,
                protein = 38.0,
                carbs = 55.0,
                fat = 12.0,
                servingSizeGrams = null,
            ),
            awaitingReview = true,
        )
        val flow = MutableStateFlow(HomeUiState())
        flow.update { it.copy(recoveredReview = draft) }
        assertTrue("update must carry the recovered review", flow.value.recoveredReview != null)
        assertTrue(
            "custom equals must not treat recoveredReview-only changes as equal",
            flow.value != HomeUiState(),
        )
    }
}
