package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionalNutrientGoalsTest {
    @Test
    fun withValue_clampsToMaxCustomGoal() {
        val goals = OptionalNutrientGoals.Default
        // 10,000 IU vitamin D = 250 mcg stays reachable.
        assertEquals(250, goals.withValue(OptionalNutrient.VITAMIN_D, 250).vitaminD)
        // Absurd values clamp to the per-nutrient cap.
        assertEquals(500, goals.withValue(OptionalNutrient.VITAMIN_D, 999_999).vitaminD)
        assertEquals(10_000, goals.withValue(OptionalNutrient.SODIUM, 1_000_000).sodium)
    }

    @Test
    fun withValue_keepsNonNegative() {
        val goals = OptionalNutrientGoals.Default
        assertEquals(0, goals.withValue(OptionalNutrient.FIBER, -10).fiber)
    }

    @Test
    fun withValue_outsideWheelRange_butUnderCap_isStoredAsIs() {
        // The wheel caps at range.last (e.g. 100 mcg); custom values may exceed it.
        val goals = OptionalNutrientGoals.Default
        assertEquals(250, goals.withValue(OptionalNutrient.VITAMIN_D, 250).vitaminD)
        assertEquals(120, goals.withValue(OptionalNutrient.VITAMIN_K, 120).vitaminK)
    }
}
