package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WidgetSnapshotTest {
    private fun snapshot(
        calories: Int = 1000,
        mode: HomeCalorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE,
        effectiveGoal: Int = 1800,
        active: Int = 0,
        base: Int = 1800,
        displayTarget: Int? = null,
    ) = WidgetSnapshot(
        date = Instant.now(),
        dayStart = Instant.now(),
        calories = calories,
        calorieGoal = 1800,
        protein = 0.0,
        proteinGoal = 100,
        carbs = 0.0,
        carbsGoal = 100,
        fat = 0.0,
        fatGoal = 100,
        calorieDisplayMode = mode.storageKey,
        effectiveCalorieGoal = effectiveGoal,
        activeCaloriesToday = active,
        gaugeBaseCalorieGoal = base,
        activeCalorieSource = ActiveCalorieSource.MEASURED.storageKey,
        displayGoalTarget = displayTarget,
    )

    @Test
    fun expectedTarget_drivesWidgetProgressAndRemaining() {
        // Measured-0 morning: the widget mirrors the hero's projected day
        // (base + typical) even though the budget is still the sedentary base.
        val morning = snapshot(displayTarget = 2600)
        assertEquals(2600, morning.resolvedDisplayGoalTarget)
        assertEquals(1600, morning.caloriesRemaining)
        assertEquals(1000.0 / 2600.0, morning.calorieProgress, 0.001)
    }

    @Test
    fun legacySnapshot_fallsBackToEffectiveGoal() {
        // Old snapshots without the target fields keep the budget-based readout.
        val legacy = snapshot(active = 320, effectiveGoal = 2120)
        assertEquals(2120, legacy.resolvedDisplayGoalTarget)
        assertEquals(1120, legacy.caloriesRemaining)
        assertEquals(1000.0 / 2120.0, legacy.calorieProgress, 0.001)
    }

    @Test
    fun staticMode_targetIsBaseGoal() {
        val static = snapshot(mode = HomeCalorieDisplayMode.STATIC, effectiveGoal = 1800)
        assertEquals(1800, static.resolvedDisplayGoalTarget)
        assertEquals(800, static.caloriesRemaining)
    }
}
