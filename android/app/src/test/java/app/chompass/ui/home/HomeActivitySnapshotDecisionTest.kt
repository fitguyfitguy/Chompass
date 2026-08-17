package app.chompass.ui.home

import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decisions for the home activity-snapshot refresh path, extracted from
 * [HomeViewModel.refreshActivitySnapshot] (Codeberg #22 race family: stale
 * Health Connect reads overwriting a newer day's snapshot, and the
 * estimate-as-measured bug where the hero read a day estimate instead of a
 * live measured burn).
 */
class HomeActivitySnapshotDecisionTest {
    private val base = HomeDisplayPreferences()

    @Test
    fun needsActivitySnapshot_whenStepsOrActiveShown() {
        assertFalse(needsActivitySnapshotFor(base))
        assertTrue(needsActivitySnapshotFor(base.copy(showSteps = true)))
        assertTrue(needsActivitySnapshotFor(base.copy(showActiveCalories = true)))
        assertTrue(needsActivitySnapshotFor(base.copy(showSteps = true, showActiveCalories = true)))
    }

    @Test
    fun needsMeasuredEnergy_onlyInAddActiveMode() {
        // Default display mode is not ADD_ACTIVE: no measured read needed.
        assertFalse(
            needsMeasuredEnergyFor(base, healthConnectEnabled = true, hasDebugActivityDays = false)
        )
    }

    @Test
    fun needsMeasuredEnergy_addActiveWithHealthConnect() {
        val addActive = base.copy(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE)
        assertTrue(
            needsMeasuredEnergyFor(addActive, healthConnectEnabled = true, hasDebugActivityDays = false)
        )
    }

    @Test
    fun needsMeasuredEnergy_addActiveWithDebugDaysEvenWhenHcOff() {
        // The seeder turns HC off but seeds debug activity days; the hero must
        // still read them or the seeded burn never reaches the gauge.
        val addActive = base.copy(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE)
        assertTrue(
            needsMeasuredEnergyFor(addActive, healthConnectEnabled = false, hasDebugActivityDays = true)
        )
    }

    @Test
    fun needsMeasuredEnergy_addActiveWithNeitherSource() {
        val addActive = base.copy(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE)
        assertFalse(
            needsMeasuredEnergyFor(addActive, healthConnectEnabled = false, hasDebugActivityDays = false)
        )
    }

    @Test
    fun needsMeasuredEnergy_otherModesNeverReadMeasured() {
        for (mode in HomeCalorieDisplayMode.entries.filter { it != HomeCalorieDisplayMode.ADD_ACTIVE }) {
            val display = base.copy(calorieDisplayMode = mode)
            assertFalse(
                "mode $mode must not read measured energy",
                needsMeasuredEnergyFor(display, healthConnectEnabled = true, hasDebugActivityDays = true),
            )
        }
    }
}
