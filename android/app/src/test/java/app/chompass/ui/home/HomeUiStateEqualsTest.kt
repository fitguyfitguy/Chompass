package app.chompass.ui.home

import app.chompass.services.ai.FoodAnalysis
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [HomeUiState] dedupes through a `MutableStateFlow`, so equality is the
 * emission gate: two states that differ only in a field the equals ignored
 * used to be silently dropped. Photo bytes compare by content, not identity.
 */
class HomeUiStateEqualsTest {
    private fun analysis(name: String = "Oats") = FoodAnalysis(
        name = name,
        calories = 150,
        protein = 5.0,
        carbs = 27.0,
        fat = 3.0,
        servingSizeGrams = 40.0,
    )

    @Test
    fun samePixelContentIsEqualRegardlessOfIdentity() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val a = HomeUiState(
            pendingImageBytes = pixels,
            pendingAnalysisImages = listOf(pixels),
            pendingInputImageBytes = pixels,
            waterTodayMl = 250,
        )
        val b = a.copy(
            pendingImageBytes = byteArrayOf(1, 2, 3, 4),
            pendingAnalysisImages = listOf(byteArrayOf(1, 2, 3, 4)),
            pendingInputImageBytes = byteArrayOf(1, 2, 3, 4),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differingPendingImageBytesAreUnequal() {
        val a = HomeUiState(pendingImageBytes = byteArrayOf(1, 2, 3, 4))
        val b = a.copy(pendingImageBytes = byteArrayOf(9, 9, 9, 9))
        assertNotEquals(a, b)
    }

    @Test
    fun differingPendingAnalysisImagesAreUnequal() {
        val a = HomeUiState(pendingAnalysisImages = listOf(byteArrayOf(1), byteArrayOf(2)))
        val b = a.copy(pendingAnalysisImages = listOf(byteArrayOf(1), byteArrayOf(3)))
        assertNotEquals(a, b)
    }

    @Test
    fun differingPendingInputImageBytesAreUnequal() {
        val a = HomeUiState(pendingInputImageBytes = byteArrayOf(1))
        val b = a.copy(pendingInputImageBytes = null)
        assertNotEquals(a, b)
    }

    @Test
    fun differingPendingAnalysisIsUnequal() {
        val a = HomeUiState(pendingAnalysis = analysis())
        val b = a.copy(pendingAnalysis = analysis(name = "Toast"))
        assertNotEquals(a, b)
    }

    @Test
    fun differingLogTimeOverrideIsUnequal() {
        val a = HomeUiState(logTimeOverride = LocalTime.of(8, 30))
        val b = a.copy(logTimeOverride = LocalTime.of(9, 0))
        assertNotEquals(a, b)
    }

    @Test
    fun waterChangeStillComparesUnequal() {
        val a = HomeUiState(waterTodayMl = 250)
        val b = a.copy(waterTodayMl = 500)
        assertNotEquals(a, b)
    }
}
