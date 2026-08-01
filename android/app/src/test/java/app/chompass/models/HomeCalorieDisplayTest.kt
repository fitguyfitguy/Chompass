package app.chompass.models

import app.chompass.services.health.ActivityDataSource
import app.chompass.services.health.HomeActivitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeCalorieDisplayTest {
    private val emptySnapshot = HomeActivitySnapshot(date = LocalDate.now())

    @Test
    fun addActive_increasesGoalAndRemaining() {
        val mode = HomeCalorieDisplayMode.ADD_ACTIVE
        assertEquals(2200, HomeCalorieDisplay.effectiveGoal(mode, 2000, 200))
        assertEquals(700, HomeCalorieDisplay.remaining(mode, eaten = 1500, baseGoal = 2000, activeCalories = 200))
        assertEquals(0.682f, HomeCalorieDisplay.progressRatio(mode, 1500, 2000, 200), 0.001f)
    }

    @Test
    fun addActive_estimated_decomposesWithoutDoubleCount() {
        val effectiveCalories = 2607
        val estimatedActive = 828
        val sedentary = effectiveCalories - estimatedActive
        val burn = ResolvedActiveBurn(estimatedActive, ActiveCalorieSource.ESTIMATED)
        val mode = HomeCalorieDisplay.effectiveMode(HomeCalorieDisplayMode.ADD_ACTIVE, burn)
        val base = HomeCalorieDisplay.gaugeBaseGoal(mode, effectiveCalories, sedentary)
        assertEquals(HomeCalorieDisplayMode.ADD_ACTIVE, mode)
        assertEquals(sedentary, base)
        assertEquals(effectiveCalories, HomeCalorieDisplay.effectiveGoal(mode, base, estimatedActive))
    }

    @Test
    fun addActive_measured_bonusAboveEstimate() {
        val effectiveCalories = 2607
        val estimatedActive = 828
        val sedentary = effectiveCalories - estimatedActive
        val measuredActive = 1000
        val mode = HomeCalorieDisplayMode.ADD_ACTIVE
        val base = HomeCalorieDisplay.gaugeBaseGoal(mode, effectiveCalories, sedentary)
        assertEquals(2779, HomeCalorieDisplay.effectiveGoal(mode, base, measuredActive))
    }

    @Test
    fun static_ignoresActive() {
        val mode = HomeCalorieDisplayMode.STATIC
        assertEquals(2000, HomeCalorieDisplay.effectiveGoal(mode, 2000, 400))
        assertEquals(500, HomeCalorieDisplay.remaining(mode, 1500, 2000, 400))
    }

    @Test
    fun effectiveMode_addActive_usesEstimatedWhenNoHc() {
        val estimated = ResolvedActiveBurn(400, ActiveCalorieSource.ESTIMATED)
        assertEquals(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            HomeCalorieDisplay.effectiveMode(HomeCalorieDisplayMode.ADD_ACTIVE, estimated)
        )
    }

    @Test
    fun resolveActiveBurn_prefersMeasuredOverEstimated() {
        val snapshot = HomeActivitySnapshot(
            date = LocalDate.now(),
            activeCalories = 500,
            source = ActivityDataSource.HEALTH_CONNECT,
        )
        val burn = HomeCalorieDisplay.resolveActiveBurn(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            snapshot,
            estimatedDailyActive = 400,
        )
        assertEquals(ActiveCalorieSource.MEASURED, burn?.source)
        assertEquals(500, burn?.calories)
    }

    @Test
    fun resolveActiveBurn_estimatedWhenHcUnavailable() {
        val burn = HomeCalorieDisplay.resolveActiveBurn(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            emptySnapshot,
            estimatedDailyActive = 400,
        )
        assertEquals(ActiveCalorieSource.ESTIMATED, burn?.source)
        assertEquals(400, burn?.calories)
    }

    @Test
    fun resolveActiveBurn_addsManualOnTopOfMeasured() {
        val snapshot = HomeActivitySnapshot(
            date = LocalDate.now(),
            activeCalories = 500,
            source = ActivityDataSource.HEALTH_CONNECT,
        )
        val burn = HomeCalorieDisplay.resolveActiveBurn(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            snapshot,
            estimatedDailyActive = 400,
            manualActiveCalories = 150,
        )
        assertEquals(ActiveCalorieSource.MEASURED, burn?.source)
        assertEquals(650, burn?.calories)
    }

    @Test
    fun resolveActiveBurn_manualOnlyWhenNoHcOrEstimate() {
        val burn = HomeCalorieDisplay.resolveActiveBurn(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            emptySnapshot,
            estimatedDailyActive = 0,
            manualActiveCalories = 220,
        )
        assertEquals(ActiveCalorieSource.MANUAL, burn?.source)
        assertEquals(220, burn?.calories)
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
