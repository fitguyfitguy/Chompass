package app.chompass.models

import app.chompass.services.health.ActivityDataSource
import app.chompass.services.health.HomeActivitySnapshot
import app.chompass.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

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
            energyLive = true,
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
            energyLive = true,
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
    fun resolveActiveBurn_measuredZero_morningKeepsAddActiveBudget() {
        // Live HC energy source, wearable has recorded nothing yet: measured 0
        // wins over the estimate, so the morning budget stays on the sedentary
        // base instead of substituting the whole estimated day (the old bug:
        // base + full estimate, collapsing at the first measurement).
        val snapshot = HomeActivitySnapshot(
            date = LocalDate.now(),
            activeCalories = 0,
            source = ActivityDataSource.UNAVAILABLE,
            energyLive = true,
        )
        val burn = HomeCalorieDisplay.resolveActiveBurn(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            snapshot,
            estimatedDailyActive = 800,
        )
        assertEquals(ActiveCalorieSource.MEASURED, burn?.source)
        assertEquals(0, burn?.calories)
        val mode = HomeCalorieDisplay.effectiveMode(HomeCalorieDisplayMode.ADD_ACTIVE, burn)
        assertEquals(HomeCalorieDisplayMode.ADD_ACTIVE, mode)
        // No budget jump: sedentary + 0 in the morning, then sedentary + first
        // measurement once activity lands.
        val sedentary = 1800
        assertEquals(sedentary, HomeCalorieDisplay.effectiveGoal(mode, sedentary, burn!!.calories))
        assertEquals(sedentary + 50, HomeCalorieDisplay.effectiveGoal(mode, sedentary, 50))
    }

    @Test
    fun resolveActiveBurn_measuredZero_addsManualOnTop() {
        val snapshot = HomeActivitySnapshot(
            date = LocalDate.now(),
            activeCalories = 0,
            source = ActivityDataSource.HEALTH_CONNECT,
            energyLive = true,
        )
        val burn = HomeCalorieDisplay.resolveActiveBurn(
            HomeCalorieDisplayMode.ADD_ACTIVE,
            snapshot,
            estimatedDailyActive = 400,
            manualActiveCalories = 150,
        )
        assertEquals(ActiveCalorieSource.MEASURED, burn?.source)
        assertEquals(150, burn?.calories)
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
        // Grow rule: once live burn exceeds the norm, the arc end becomes base + live.
        assertEquals(2054, HomeCalorieDisplay.burnShadeArcEnd(1494, 560, live = 380))
        assertEquals(2394, HomeCalorieDisplay.burnShadeArcEnd(1494, 560, live = 900))
    }

    @Test
    fun expectedTarget_growsOnlyPastTypical() {
        assertEquals(2054, HomeCalorieDisplay.expectedTarget(baseGoal = 1494, typical = 560, live = 0))
        assertEquals(2054, HomeCalorieDisplay.expectedTarget(1494, 560, live = 380))
        assertEquals(2394, HomeCalorieDisplay.expectedTarget(1494, 560, live = 900))
        assertEquals(1494, HomeCalorieDisplay.expectedTarget(1494, 0, 0))
        // The measured-0 morning still shows the projected day: base + typical.
        assertEquals(2600, HomeCalorieDisplay.expectedTarget(1800, 800, 0))
    }

    @Test
    fun burnShade_eatenFractionUsesArcEndScale() {
        // 1680 eaten on a 2054 kcal arc.
        assertEquals(0.818f, HomeCalorieDisplay.burnShadeEatenFraction(1680, 1494, 560), 0.001f)
        assertEquals(1f, HomeCalorieDisplay.burnShadeEatenFraction(9_999, 1494, 560), 0.001f)
        assertEquals(0f, HomeCalorieDisplay.burnShadeEatenFraction(0, 1494, 560), 0.001f)
        // Over-typical live burn grows the scale, so the same eaten amount reads lower.
        assertEquals(0.702f, HomeCalorieDisplay.burnShadeEatenFraction(1680, 1494, 560, live = 900), 0.001f)
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
        // Over typical: the arc end grows with live, so the live tip lands exactly
        // at the (grown) ring end while the typical zone shrinks relative to it.
        val over = HomeCalorieDisplay.burnShadeLiveFraction(base, live = 900, typical = typical)
        val overTypicalFrac = HomeCalorieDisplay.burnShadeTypicalFraction(base, typical, live = 900)
        assertTrue(over > overTypicalFrac)
        assertTrue(over < 1f)
        // Live equal to the old fixed arc end (base + typical) no longer fills the
        // ring: the end has grown past it to base + live.
        assertEquals(
            2054f / 3548f,
            HomeCalorieDisplay.burnShadeLiveFraction(base, live = base + typical, typical = typical),
            0.001f,
        )
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

    // --- #38: macro goals scale with the ring's projected day ---

    @Test
    fun homeTopNutrient_goal_scalesPcfOnly() {
        val profile = UserProfile(customCalories = 2607)
        val goals = OptionalNutrientGoals.Default
        val scaled = 1.2f
        assertEquals((profile.effectiveProtein * scaled).roundToInt(), HomeTopNutrient.PROTEIN.goal(profile, goals, scaled))
        assertEquals((profile.effectiveCarbs * scaled).roundToInt(), HomeTopNutrient.CARBS.goal(profile, goals, scaled))
        assertEquals((profile.effectiveFat * scaled).roundToInt(), HomeTopNutrient.FAT.goal(profile, goals, scaled))
        // Optional micronutrients are fixed daily targets — never scaled.
        assertEquals(goals.fiber, HomeTopNutrient.FIBER.goal(profile, goals, scaled))
        assertEquals(goals.sugar, HomeTopNutrient.SUGAR.goal(profile, goals, scaled))
        // Scale ≤ 1 (and the default) keep the stored goals untouched.
        assertEquals(profile.effectiveProtein, HomeTopNutrient.PROTEIN.goal(profile, goals))
        assertEquals(profile.effectiveProtein, HomeTopNutrient.PROTEIN.goal(profile, goals, 1f))
        assertEquals(profile.effectiveProtein, HomeTopNutrient.PROTEIN.goal(profile, goals, 0.9f))
    }

    @Test
    fun macroGoalScale_estimatePlusManual_tracksRingTarget() {
        // ADD_ACTIVE, PAL estimate only: the ring = base + estimate + manual,
        // i.e. the stored goal plus the manual kcal — the cards scale to match.
        val profile = UserProfile(customCalories = 2607)
        val state = HomeUiState(
            profile = profile,
            homeDisplay = HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE),
            manualActiveKcal = 300,
        )
        assertEquals(2607 + 300, state.heroCalorieGoal)
        assertEquals((2607 + 300).toFloat() / 2607f, state.macroGoalScale, 0.001f)
    }

    @Test
    fun macroGoalScale_typicalDay_isOne() {
        // Estimate-only day with no manual kcal: the ring converges to the
        // stored goal, so the cards never move.
        val profile = UserProfile(customCalories = 2607)
        val state = HomeUiState(
            profile = profile,
            homeDisplay = HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE),
        )
        assertEquals(2607, state.heroCalorieGoal)
        assertEquals(1f, state.macroGoalScale, 0.001f)
    }

    @Test
    fun macroGoalScale_static_isOne() {
        val profile = UserProfile(customCalories = 2607)
        val state = HomeUiState(
            profile = profile,
            homeDisplay = HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.STATIC),
            manualActiveKcal = 300,
        )
        assertEquals(2607, state.heroCalorieGoal)
        assertEquals(1f, state.macroGoalScale, 0.001f)
    }

    @Test
    fun macroGoalScale_overTypicalLiveBurn_scalesUp() {
        // Live measured burn above the day's norm grows the ring arc end; the
        // macro cards follow that projection.
        val profile = UserProfile(customCalories = 2607)
        val snapshot = HomeActivitySnapshot(
            date = LocalDate.now(),
            activeCalories = 1200,
            source = ActivityDataSource.HEALTH_CONNECT,
            energyLive = true,
        )
        val state = HomeUiState(
            profile = profile,
            homeDisplay = HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE),
            activitySnapshot = snapshot,
        )
        val typical = state.activeBurnTypical
        assertTrue("typical burn should be known", typical > 0)
        val expectedTarget = HomeCalorieDisplay.expectedTarget(state.gaugeBaseCalorieGoal, typical, 1200)
        assertEquals(expectedTarget, state.heroCalorieGoal)
        assertEquals(expectedTarget.toFloat() / 2607f, state.macroGoalScale, 0.001f)
        assertTrue(state.macroGoalScale > 1f)
    }

    @Test
    fun macroGoalScale_keto_staysOne() {
        // Keto macro targets are fixed by design (clamped net carbs, protein
        // floor, fat fills remaining) — never scaled with the ring.
        val profile = UserProfile(customCalories = 2607, dietMode = DietMode.KETO)
        val snapshot = HomeActivitySnapshot(
            date = LocalDate.now(),
            activeCalories = 1200,
            source = ActivityDataSource.HEALTH_CONNECT,
            energyLive = true,
        )
        val state = HomeUiState(
            profile = profile,
            homeDisplay = HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE),
            activitySnapshot = snapshot,
            manualActiveKcal = 300,
        )
        assertTrue(state.heroCalorieGoal > 2607)
        assertEquals(1f, state.macroGoalScale, 0.001f)
    }
}
