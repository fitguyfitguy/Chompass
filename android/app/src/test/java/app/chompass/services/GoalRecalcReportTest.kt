package app.chompass.services

import app.chompass.models.UserProfile
import app.chompass.services.ai.GoalCalculationReport
import app.chompass.services.ai.ImpliedWithheldReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure empirical-gate semantics: the individual scale trend beats formula
 * disagreement, so only a below-floor implied maintenance or thin data
 * withhold the implied numbers ([GoalCalculationReport.impliedWithheld]);
 * `trendsDisagree` stays a display/diagnostic flag.
 */
class GoalRecalcReportTest {
    /** MSJ male 25y/178cm/76kg ≈ 1753 BMR → floor 1753. */
    private fun profile() = UserProfile(heightCm = 178.0, weightKg = 76.0)

    private fun forecast(
        weighIns: Int,
        spanDays: Int,
        foodDays: Int,
        loggedDayAvg: Int,
        observedWeekly: Double?,
        trendsDisagree: Boolean = false,
    ) = WeightForecast(
        avgDailyCalories = loggedDayAvg,
        tdee = 2568,
        dailyEnergyBalance = 0,
        predictedWeeklyChangeKg = 0.0,
        observedWeeklyChangeKg = observedWeekly,
        currentWeightKg = 76.0,
        predictedWeight30dKg = 76.0,
        predictedWeight60dKg = 76.0,
        predictedWeight90dKg = 76.0,
        daysToGoal = null,
        goalReachDate = null,
        hasEnoughData = weighIns >= 2 && foodDays >= 2,
        trendsDisagree = trendsDisagree,
        daysOfFoodData = foodDays,
        weightEntriesUsed = weighIns,
        weightSpanDays = spanDays,
        loggedDayAvgCalories = loggedDayAvg,
    )

    @Test
    fun disagreeingTrends_stillTrustTheIndividualTrend() {
        // 12/40/40 is usable; implied 3450 is above the floor; only the formula
        // disagrees — the individual trend wins.
        val f = forecast(12, 40, 40, loggedDayAvg = 2900, observedWeekly = -0.5, trendsDisagree = true)
        val signals = empiricalSignals(f, profile())
        assertTrue(signals.empiricalUsable)
        assertTrue(signals.trustEmpirical)
        assertFalse(signals.impliedBelowFloor)

        val report = buildGoalCalculationReport(profile(), f, null)
        assertTrue(report.trendsDisagree)
        assertNull("disagreement alone must not withhold", report.impliedWithheld)
        assertEquals(3450, report.impliedMaintenance)
    }

    @Test
    fun thinData_withholdsAsThin() {
        // Thin data, and the implied maintenance (2290 + 550) is above the floor,
        // so THIN is the only withholding reason.
        val f = forecast(2, 4, 2, 2290, -0.5)
        val signals = empiricalSignals(f, profile())
        assertFalse(signals.trustEmpirical)
        assertEquals(ImpliedWithheldReason.THIN, buildGoalCalculationReport(profile(), f, null).impliedWithheld)
    }

    @Test
    fun impliedBelowFloor_withholdsAsBelowFloor() {
        // 1200 kcal logged while gaining 1 kg/week → implied 1200 − 1100 = 100 < floor.
        val f = forecast(8, 30, 30, 1200, +1.0)
        val signals = empiricalSignals(f, profile())
        assertTrue(signals.impliedBelowFloor)
        assertFalse(signals.trustEmpirical)
        assertEquals(ImpliedWithheldReason.BELOW_FLOOR, buildGoalCalculationReport(profile(), f, null).impliedWithheld)
    }
}
