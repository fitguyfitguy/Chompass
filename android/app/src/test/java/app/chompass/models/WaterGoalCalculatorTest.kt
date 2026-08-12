package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WATER-DYN-A/B/C from docs/WATER_DYNAMIC_GOAL_DESIGN.md. All assertions are
 * hand-computed from the registered formulas; change the formulas only
 * together with the docs and the calculation-change checklist.
 */
class WaterGoalCalculatorTest {
    // -- WATER-DYN-A: gross drink goal ------------------------------------

    @Test
    fun weightBaseIs35MlPerKgRoundedTo50() {
        assertEquals(2450, WaterGoalCalculator.weightBaseGoalMl(70.0))
        assertEquals(2550, WaterGoalCalculator.weightBaseGoalMl(73.0)) // 2555 → 2550
        assertEquals(1750, WaterGoalCalculator.weightBaseGoalMl(50.0))
    }

    @Test
    fun baseSourceResolvesWeightOrManual() {
        assertEquals(
            2450,
            WaterGoalCalculator.baseGoalMl(WaterGoalCalculator.BASE_SOURCE_WEIGHT, 70.0, 2000),
        )
        // Weight source without a profile weight falls back to the manual goal.
        assertEquals(
            2000,
            WaterGoalCalculator.baseGoalMl(WaterGoalCalculator.BASE_SOURCE_WEIGHT, null, 2000),
        )
        assertEquals(
            1800,
            WaterGoalCalculator.baseGoalMl(WaterGoalCalculator.BASE_SOURCE_MANUAL, 70.0, 1800),
        )
    }

    @Test
    fun tempFactorFollowsTableAndClamps() {
        assertEquals(1.0, WaterGoalCalculator.tempFactor(25), 1e-9)
        assertEquals(1.04, WaterGoalCalculator.tempFactor(26), 1e-9)
        assertEquals(1.2, WaterGoalCalculator.tempFactor(30), 1e-9)
        assertEquals(1.4, WaterGoalCalculator.tempFactor(35), 1e-9)
        assertEquals(1.6, WaterGoalCalculator.tempFactor(40), 1e-9)
        // Clamped: 45 °C would be 1.8.
        assertEquals(1.6, WaterGoalCalculator.tempFactor(45), 1e-9)
        // No reduction at or below 25 °C.
        assertEquals(1.0, WaterGoalCalculator.tempFactor(24), 1e-9)
        assertEquals(1.0, WaterGoalCalculator.tempFactor(-10), 1e-9)
    }

    @Test
    fun activityFactorFollowsLevelTable() {
        assertEquals(1.0, WaterGoalCalculator.activityFactor(ActivityLevel.SEDENTARY), 1e-9)
        assertEquals(1.1, WaterGoalCalculator.activityFactor(ActivityLevel.LIGHT), 1e-9)
        assertEquals(1.2, WaterGoalCalculator.activityFactor(ActivityLevel.MODERATE), 1e-9)
        assertEquals(1.3, WaterGoalCalculator.activityFactor(ActivityLevel.ACTIVE), 1e-9)
        assertEquals(1.4, WaterGoalCalculator.activityFactor(ActivityLevel.VERY_ACTIVE), 1e-9)
        assertEquals(1.5, WaterGoalCalculator.activityFactor(ActivityLevel.EXTRA_ACTIVE), 1e-9)
    }

    @Test
    fun grossGoalMultipliesAndRoundsTo50() {
        // 2000 × 1.2 (30 °C) × 1.2 (moderate) = 2880 → 2900.
        assertEquals(
            2900,
            WaterGoalCalculator.grossGoalMl(2000, 30, WaterGoalCalculator.activityFactor(ActivityLevel.MODERATE)),
        )
        assertEquals(2450, WaterGoalCalculator.grossGoalMl(2450, 25, 1.0))
        // Activity factor 1.0 = "use profile activity" off.
        assertEquals(2400, WaterGoalCalculator.grossGoalMl(2000, 30, 1.0))
    }

    // -- WATER-DYN-B: food-water subtraction ------------------------------

    @Test
    fun foodWaterIs60PercentOfGrams() {
        assertEquals(900, WaterGoalCalculator.foodWaterMl(1500))
        assertEquals(600, WaterGoalCalculator.foodWaterMl(1000))
        assertEquals(0, WaterGoalCalculator.foodWaterMl(0))
        assertEquals(0, WaterGoalCalculator.foodWaterMl(-100))
    }

    @Test
    fun foodWaterCapsAtOneLiter() {
        // 2000 g × 0.6 = 1200 → capped.
        assertEquals(1000, WaterGoalCalculator.foodWaterMl(2000))
        assertEquals(1000, WaterGoalCalculator.foodWaterMl(2750))
    }

    @Test
    fun netGoalNeverDropsBelowOneLiter() {
        assertEquals(2000, WaterGoalCalculator.netGoalMl(2900, 900))
        assertEquals(2900, WaterGoalCalculator.netGoalMl(2900, 0))
        // 1500 − 1000 = 500 → floored.
        assertEquals(1000, WaterGoalCalculator.netGoalMl(1500, 1000))
    }

    @Test
    fun dailyNetGoalCombinesAllInputs() {
        // 70 kg → base 2450; 30 °C → 1.2; active → 1.3; food 1500 g → 900.
        // gross = 2450 × 1.2 × 1.3 = 3822 → 3800; net = 3800 − 900 = 2900.
        assertEquals(
            2900,
            WaterGoalCalculator.dailyNetGoalMl(
                baseSource = WaterGoalCalculator.BASE_SOURCE_WEIGHT,
                weightKg = 70.0,
                manualBaseMl = 2000,
                expectedHighC = 30,
                activityLevel = ActivityLevel.ACTIVE,
                useProfileActivity = true,
                foodGramsToday = 1500,
                foodWaterEnabled = true,
            ),
        )
        // Activity off → factor 1.0; food off → no credit: 2450 × 1.2 = 2940 → 2950.
        assertEquals(
            2950,
            WaterGoalCalculator.dailyNetGoalMl(
                baseSource = WaterGoalCalculator.BASE_SOURCE_WEIGHT,
                weightKg = 70.0,
                manualBaseMl = 2000,
                expectedHighC = 30,
                activityLevel = ActivityLevel.ACTIVE,
                useProfileActivity = false,
                foodGramsToday = 1500,
                foodWaterEnabled = false,
            ),
        )
        // No profile weight → manual base 2000, no temp/activity effect: 2000.
        assertEquals(
            2000,
            WaterGoalCalculator.dailyNetGoalMl(
                baseSource = WaterGoalCalculator.BASE_SOURCE_WEIGHT,
                weightKg = null,
                manualBaseMl = 2000,
                expectedHighC = 25,
                activityLevel = ActivityLevel.SEDENTARY,
                useProfileActivity = true,
                foodGramsToday = 0,
                foodWaterEnabled = false,
            ),
        )
    }

    // -- WATER-DYN-C: adaptive reminder interval --------------------------

    @Test
    fun cupsForCeilsGoalDividedByCup() {
        assertEquals(9, WaterGoalCalculator.cupsFor(2500, 300)) // 8.33 → 9
        assertEquals(1, WaterGoalCalculator.cupsFor(1000, 1000))
        assertEquals(0, WaterGoalCalculator.cupsFor(0, 300))
        assertEquals(1000, WaterGoalCalculator.cupsFor(1000, 0)) // cup coerced to ≥ 1
    }

    @Test
    fun nextDrinkIsOneCupCappedByRemainingGoal() {
        // Full cup available → the cup.
        assertEquals(300, WaterGoalCalculator.nextDrinkMl(2500, 1200, 300))
        // Exactly one cup left.
        assertEquals(300, WaterGoalCalculator.nextDrinkMl(2000, 1700, 300))
        // Tail smaller than a cup → the remainder (final reminder of the day).
        assertEquals(250, WaterGoalCalculator.nextDrinkMl(2000, 1750, 300))
        // Goal already met (or over) → nothing left to drink.
        assertEquals(0, WaterGoalCalculator.nextDrinkMl(2000, 2000, 300))
        assertEquals(0, WaterGoalCalculator.nextDrinkMl(2000, 2500, 300))
        // Goal smaller than the cup → the whole goal (first cup of the day).
        assertEquals(200, WaterGoalCalculator.nextDrinkMl(200, 0, 300))
        // Bigger cup than goal remainder still caps at the remainder.
        assertEquals(150, WaterGoalCalculator.nextDrinkMl(1500, 1350, 1000))
    }

    @Test
    fun planningIntervalMatchesReportersWorkedExample() {
        // Reporter: 2500 ml ÷ 300 ml cup over 13 h → ~93 min. With whole cups
        // (9) the interval is 780 ÷ 9 ≈ 87 → 85 min.
        assertEquals(85, WaterGoalCalculator.planningIntervalMin(2500, 300, 13 * 60))
        // 2000 ml ÷ 250 ml = 8 cups over 13 h → 97.5 → 100.
        assertEquals(100, WaterGoalCalculator.planningIntervalMin(2000, 250, 13 * 60))
    }

    @Test
    fun planningIntervalClampsAndRejectsEmptyWindow() {
        // 1 cup over 13 h → 780 → clamped to 240.
        assertEquals(240, WaterGoalCalculator.planningIntervalMin(1000, 1000, 13 * 60))
        // 34 cups over 13 h → 22.9 → clamped to 30.
        assertEquals(30, WaterGoalCalculator.planningIntervalMin(10000, 300, 13 * 60))
        assertNull(WaterGoalCalculator.planningIntervalMin(2000, 300, 0))
        assertNull(WaterGoalCalculator.planningIntervalMin(2000, 300, -60))
    }

    @Test
    fun liveIntervalStartsAtPlanningValue() {
        // Day start: drunk 0 at 08:00, window 08:00–21:00 → same as planning.
        assertEquals(
            WaterGoalCalculator.planningIntervalMin(2500, 300, 13 * 60),
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 0, cupSizeMl = 300,
                nowMinutes = 8 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun liveIntervalLengthensWhenAheadOfPlan() {
        // 2500 goal, 1500 drunk → 1000 left = 4 cups at 14:00 (420 min left) → 105.
        assertEquals(
            105,
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 1500, cupSizeMl = 300,
                nowMinutes = 14 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun liveIntervalShortensWhenBehind() {
        // Nothing drunk at 20:00 → 9 cups over 60 min → 6.7 → clamped to 30.
        assertEquals(
            30,
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 0, cupSizeMl = 300,
                nowMinutes = 20 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun liveIntervalIsNullWhenGoalMetOrWindowElapsed() {
        // Goal reached → no more reminders today.
        assertNull(
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 2500, cupSizeMl = 300,
                nowMinutes = 14 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
        // Over the goal (drunk 3000) → remaining clamps to 0.
        assertNull(
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 3000, cupSizeMl = 300,
                nowMinutes = 14 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
        // Past awake end (22:00) → caller re-arms for tomorrow instead.
        assertNull(
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 0, cupSizeMl = 300,
                nowMinutes = 22 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun liveIntervalUsesFullWindowBeforeAwakeStart() {
        // 07:00 (before the 08:00 start) → full 13 h window → same as day start.
        assertEquals(
            85,
            WaterGoalCalculator.liveIntervalMin(
                netGoalMl = 2500, drunkTodayMl = 0, cupSizeMl = 300,
                nowMinutes = 7 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    // -- Rounding -----------------------------------------------------------

    @Test
    fun roundingHelpersSnapTo50MlAnd5Minutes() {
        assertEquals(50, WaterGoalCalculator.round50(25.0)) // 0.5 → ties away from zero
        assertEquals(2900, WaterGoalCalculator.round50(2880.0))
        assertEquals(5, WaterGoalCalculator.round5(2.5))
        assertEquals(85, WaterGoalCalculator.round5(86.67))
    }

    // -- Breakdown + diary grams (Settings preview / widget inputs) --------

    @Test
    fun breakdownExposesEachInputAndMatchesDailyNetGoal() {
        val b = WaterGoalCalculator.breakdown(
            baseSource = WaterGoalCalculator.BASE_SOURCE_WEIGHT,
            weightKg = 70.0,
            manualBaseMl = 2000,
            expectedHighC = 30,
            activityLevel = ActivityLevel.ACTIVE,
            useProfileActivity = true,
            foodGramsToday = 1500,
            foodWaterEnabled = true,
        )
        assertEquals(2450, b.baseMl)
        assertEquals(1.2, b.tempFactor, 1e-9)
        assertEquals(1.3, b.activityFactor, 1e-9)
        assertEquals(3800, b.grossMl) // 2450 × 1.2 × 1.3 = 3822 → 3800
        assertEquals(900, b.foodWaterMl)
        assertEquals(2900, b.netGoalMl)
        assertEquals(
            b.netGoalMl,
            WaterGoalCalculator.dailyNetGoalMl(
                baseSource = WaterGoalCalculator.BASE_SOURCE_WEIGHT,
                weightKg = 70.0,
                manualBaseMl = 2000,
                expectedHighC = 30,
                activityLevel = ActivityLevel.ACTIVE,
                useProfileActivity = true,
                foodGramsToday = 1500,
                foodWaterEnabled = true,
            ),
        )
    }

    @Test
    fun estimateDiaryGramsSumsServingGramsTimesQuantity() {
        fun entry(grams: Double?, qty: Double?) = FoodEntry(
            name = "x",
            calories = 1,
            protein = 0.0,
            carbs = 0.0,
            fat = 0.0,
            source = FoodSource.MANUAL,
            servingSizeGrams = grams,
            selectedServingQuantity = qty,
        )
        val entries = listOf(
            entry(100.0, 2.0),   // 200 g
            entry(250.0, 1.0),   // 250 g
            entry(null, 1.0),    // no weight → 0
            entry(100.0, null),  // no quantity → 0
        )
        assertEquals(450, WaterGoalCalculator.estimateDiaryGrams(entries))
        assertEquals(0, WaterGoalCalculator.estimateDiaryGrams(emptyList()))
    }

    // -- WATER-DYN-C: next-fire decision for the alarm chain ----------------

    @Test
    fun nextFireIsWindowStartWhenBeforeIt() {
        // 07:00, window 08:00–21:00 → fires at 08:00 (60 min away).
        assertEquals(
            60,
            WaterGoalCalculator.nextFireOffsetMinutes(
                netGoalMl = 2500, drunkTodayMl = 0, cupSizeMl = 300,
                nowMinutes = 7 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun nextFireSkipsToTomorrowWhenGoalMetBeforeWindow() {
        assertNull(
            WaterGoalCalculator.nextFireOffsetMinutes(
                netGoalMl = 2500, drunkTodayMl = 3000, cupSizeMl = 300,
                nowMinutes = 7 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun nextFireUsesLiveIntervalInsideWindow() {
        // 14:00, 1000 ml left (4 cups), 420 min left → 105.
        assertEquals(
            105,
            WaterGoalCalculator.nextFireOffsetMinutes(
                netGoalMl = 2500, drunkTodayMl = 1500, cupSizeMl = 300,
                nowMinutes = 14 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }

    @Test
    fun nextFireIsTomorrowWhenGoalMetOrWindowElapsed() {
        // Goal met at 14:00.
        assertNull(
            WaterGoalCalculator.nextFireOffsetMinutes(
                netGoalMl = 2500, drunkTodayMl = 2500, cupSizeMl = 300,
                nowMinutes = 14 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
        // 22:00, past the 21:00 end.
        assertNull(
            WaterGoalCalculator.nextFireOffsetMinutes(
                netGoalMl = 2500, drunkTodayMl = 0, cupSizeMl = 300,
                nowMinutes = 22 * 60, awakeStartMinutes = 8 * 60, awakeEndMinutes = 21 * 60,
            ),
        )
    }
}
