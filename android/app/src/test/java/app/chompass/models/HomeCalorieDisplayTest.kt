package app.chompass.models

import app.chompass.services.health.ActivityDataSource
import app.chompass.services.health.HomeActivitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun activeBurnArc_prefersMeasuredAverageReference() {
        val arc = HomeCalorieDisplay.activeBurnArc(
            liveHcActive = 320,
            manualActiveCalories = 0,
            healthConnectAverage = 540,
            estimatedDailyActive = 480,
        )
        assertEquals(320, arc?.live)
        assertEquals(540, arc?.typical)
        assertEquals(ActiveCalorieSource.MEASURED, arc?.typicalSource)
    }

    @Test
    fun activeBurnArc_fallsBackToEstimateReference() {
        val arc = HomeCalorieDisplay.activeBurnArc(
            liveHcActive = 320,
            manualActiveCalories = 0,
            healthConnectAverage = 0,
            estimatedDailyActive = 480,
        )
        assertEquals(320, arc?.live)
        assertEquals(480, arc?.typical)
        assertEquals(ActiveCalorieSource.ESTIMATED, arc?.typicalSource)
    }

    @Test
    fun activeBurnArc_liveIncludesManual() {
        val arc = HomeCalorieDisplay.activeBurnArc(
            liveHcActive = 100,
            manualActiveCalories = 60,
            healthConnectAverage = 200,
            estimatedDailyActive = 0,
        )
        assertEquals(160, arc?.live)
        assertEquals(200, arc?.typical)
    }

    @Test
    fun activeBurnArc_nullWhenNoLiveBurn() {
        assertNull(
            HomeCalorieDisplay.activeBurnArc(
                liveHcActive = 0,
                manualActiveCalories = 0,
                healthConnectAverage = 540,
                estimatedDailyActive = 480,
            )
        )
    }

    @Test
    fun activeBurnArc_manualOnlyTypicalUnavailable() {
        val arc = HomeCalorieDisplay.activeBurnArc(
            liveHcActive = 0,
            manualActiveCalories = 100,
            healthConnectAverage = 0,
            estimatedDailyActive = 0,
        )
        assertEquals(100, arc?.live)
        assertEquals(0, arc?.typical)
        assertEquals(ActiveCalorieSource.MANUAL, arc?.typicalSource)
    }

    @Test
    fun activeBurnRampProgress_clampsToUnitInterval() {
        assertEquals(0f, HomeCalorieDisplay.activeBurnRampProgress(live = 0, typical = 540), 0.0001f)
        assertEquals(0.5f, HomeCalorieDisplay.activeBurnRampProgress(live = 270, typical = 540), 0.0001f)
        assertEquals(1f, HomeCalorieDisplay.activeBurnRampProgress(live = 540, typical = 540), 0.0001f)
        assertEquals(1f, HomeCalorieDisplay.activeBurnRampProgress(live = 800, typical = 540), 0.0001f)
    }

    @Test
    fun activeBurnRampProgress_noReferenceIsFull() {
        assertEquals(1f, HomeCalorieDisplay.activeBurnRampProgress(live = 100, typical = 0), 0.0001f)
        assertEquals(1f, HomeCalorieDisplay.activeBurnRampProgress(live = 100, typical = -5), 0.0001f)
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
