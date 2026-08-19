package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeUiStateEqualsTest {
    @Test
    fun imageByteIdentityDoesNotBreakEquality() {
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
            pendingInputImageBytes = byteArrayOf(9, 9, 9),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun waterChangeStillComparesUnequal() {
        val a = HomeUiState(waterTodayMl = 250)
        val b = a.copy(waterTodayMl = 500)
        assertNotEquals(a, b)
    }
}
