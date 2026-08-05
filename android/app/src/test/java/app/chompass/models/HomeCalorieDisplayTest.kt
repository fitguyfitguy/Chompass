package app.chompass.models

import app.chompass.services.health.ActivityDataSource
import app.chompass.services.health.HomeActivitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun addActive_energyBurnMeasured_usesMeasuredBase() {
        val effectiveCalories = 2392
        val measuredActiveAverage = 489
        val sedentary = (effectiveCalories - measuredActiveAverage).coerceAtLeast(0)
        val mode = HomeCalorieDisplayMode.ADD_ACTIVE
        val base = HomeCalorieDisplay.gaugeBaseGoal(mode, effectiveCalories, sedentary)
        assertEquals(1903, base)
        // Mid-day live burn: budget starts at basal and grows toward the measured goal.
        assertEquals(2076, HomeCalorieDisplay.effectiveGoal(mode, base, 173))
        // A full average day (489 active) converges to the measured goal.
        assertEquals(effectiveCalories, HomeCalorieDisplay.effectiveGoal(mode, base, measuredActiveAverage))
    }

    @Test
    fun addActive_energyBurnMeasured_remainingUsesEffectiveGoal() {
        val mode = HomeCalorieDisplayMode.ADD_ACTIVE
        val base = 1903
        val active = 173
        assertEquals(1575, HomeCalorieDisplay.remaining(mode, eaten = 501, baseGoal = base, activeCalories = active))
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
    fun burnShade_arcEndIsBasePlusTypical() {
        assertEquals(2054, HomeCalorieDisplay.burnShadeArcEnd(baseGoal = 1494, typical = 560))
        assertEquals(1494, HomeCalorieDisplay.burnShadeArcEnd(1494, 0))
    }

    @Test
    fun burnShade_eatenFractionUsesArcEndScale() {
        // 1680 eaten on a 2054 kcal arc.
        assertEquals(0.818f, HomeCalorieDisplay.burnShadeEatenFraction(1680, 1494, 560), 0.001f)
        assertEquals(1f, HomeCalorieDisplay.burnShadeEatenFraction(9_999, 1494, 560), 0.001f)
        assertEquals(0f, HomeCalorieDisplay.burnShadeEatenFraction(0, 1494, 560), 0.001f)
    }

    @Test
    fun burnShade_liveFractionExtendsPastTypicalTowardFullRing() {
        val base = 1494
        val typical = 560
        // Under typical: live zone is shorter than the typical zone.
        val under = HomeCalorieDisplay.burnShadeLiveFraction(base, live = 380, typical = typical)
        val typicalFrac = HomeCalorieDisplay.burnShadeTypicalFraction(base, typical)
        assertTrue(under < typicalFrac)
        // Equal to typical: live reaches the typical zone end.
        assertEquals(
            typicalFrac,
            HomeCalorieDisplay.burnShadeLiveFraction(base, live = typical, typical = typical),
            0.001f,
        )
        // Over typical: live extends past the typical zone, still under the full ring.
        val over = HomeCalorieDisplay.burnShadeLiveFraction(base, live = 900, typical = typical)
        assertTrue(over > typicalFrac)
        assertTrue(over < 1f)
        // Live equal to the full arc end (base + typical) = the whole ring.
        assertEquals(1f, HomeCalorieDisplay.burnShadeLiveFraction(base, live = base + typical, typical = typical), 0.001f)
    }

    @Test
    fun burnShade_restingFractionGrowsFromLeft() {
        val base = 1494
        val typical = 560
        val rest = HomeCalorieDisplay.burnShadeRestingFraction(restingKcal = 870, baseGoal = base, typical = typical)
        assertEquals(0.424f, rest, 0.001f)
        assertEquals(0f, HomeCalorieDisplay.burnShadeRestingFraction(0, base, typical), 0.001f)
    }

    @Test
    fun burnShade_progressAndOverTypical() {
        assertEquals(0.679f, HomeCalorieDisplay.activeBurnShadeProgress(live = 380, typical = 560), 0.001f)
        assertEquals(0f, HomeCalorieDisplay.activeBurnShadeProgress(0, 560), 0.001f)
        assertEquals(1f, HomeCalorieDisplay.activeBurnShadeProgress(900, 560), 0.001f)
        assertEquals(0f, HomeCalorieDisplay.activeBurnShadeProgress(100, 0), 0.001f)
        assertTrue(HomeCalorieDisplay.isActiveBurnOverTypical(600, 560))
        assertFalse(HomeCalorieDisplay.isActiveBurnOverTypical(560, 560))
        assertFalse(HomeCalorieDisplay.isActiveBurnOverTypical(300, 560))
        assertFalse(HomeCalorieDisplay.isActiveBurnOverTypical(100, 0))
    }

    @Test
    fun foodLogMacroChips_defaultsToPcf() {
        assertEquals(FoodLogMacroChip.DefaultSelection, FoodLogMacroChip.fromStorage(null))
    }
}
