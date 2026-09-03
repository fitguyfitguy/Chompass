package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionGoalPercentTest {
    @Test
    fun `positive goal returns rounded percent`() {
        assertEquals(32, nutritionGoalPercent(640.0, 2000.0))
        assertEquals(100, nutritionGoalPercent(150.0, 150.0))
        assertEquals(0, nutritionGoalPercent(0.0, 2000.0))
        assertEquals(150, nutritionGoalPercent(300.0, 200.0))
    }

    @Test
    fun `missing or non-positive goal is null`() {
        assertNull(nutritionGoalPercent(640.0, 0.0))
        assertNull(nutritionGoalPercent(640.0, -10.0))
        assertNull(nutritionGoalPercent(640.0, Double.NaN))
        assertNull(nutritionGoalPercent(Double.POSITIVE_INFINITY, 2000.0))
    }
}
