package org.codeberg.fitguy.nofud.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCalorieDisplayTest {

    @Test
    fun addActive_increasesGoalAndRemaining() {
        val mode = HomeCalorieDisplayMode.ADD_ACTIVE
        assertEquals(2200, HomeCalorieDisplay.effectiveGoal(mode, 2000, 200))
        assertEquals(700, HomeCalorieDisplay.remaining(mode, eaten = 1500, baseGoal = 2000, activeCalories = 200))
        assertEquals(0.682f, HomeCalorieDisplay.progressRatio(mode, 1500, 2000, 200), 0.001f)
    }

    @Test
    fun net_subtractsActiveFromEaten() {
        val mode = HomeCalorieDisplayMode.NET
        assertEquals(700, HomeCalorieDisplay.remaining(mode, eaten = 1800, baseGoal = 2000, activeCalories = 500))
        assertEquals(0.65f, HomeCalorieDisplay.progressRatio(mode, 1800, 2000, 500), 0.001f)
    }

    @Test
    fun static_ignoresActive() {
        val mode = HomeCalorieDisplayMode.STATIC
        assertEquals(2000, HomeCalorieDisplay.effectiveGoal(mode, 2000, 400))
        assertEquals(500, HomeCalorieDisplay.remaining(mode, 1500, 2000, 400))
    }

    @Test
    fun effectiveMode_fallsBackWithoutEnergy() {
        assertEquals(
            HomeCalorieDisplayMode.STATIC,
            HomeCalorieDisplay.effectiveMode(HomeCalorieDisplayMode.ADD_ACTIVE, energyAvailable = false)
        )
    }

    @Test
    fun homeTopNutrient_respectsCardCount() {
        val three = HomeTopNutrient.normalized(
            listOf(HomeTopNutrient.PROTEIN, HomeTopNutrient.CARBS, HomeTopNutrient.FAT, HomeTopNutrient.FIBER),
            cardCount = 3
        )
        assertEquals(3, three.size)
        assertTrue(three.contains(HomeTopNutrient.PROTEIN))
    }

    @Test
    fun foodLogMacroChips_defaultsToPcf() {
        assertEquals(FoodLogMacroChip.DefaultSelection, FoodLogMacroChip.fromStorage(null))
    }
}
