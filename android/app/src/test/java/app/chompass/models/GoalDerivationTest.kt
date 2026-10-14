package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure projection for the standing goal derivation (Home budget dialog's
 * formula-baseline block): pins the CAL-SAFE clamp direction (floor vs
 * ceiling) and the body-fat recency passthrough.
 */
class GoalDerivationTest {
    private fun birthday(years: Int): Instant =
        LocalDate.now().minusYears(years.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun profile(
        weightKg: Double = 80.0,
        activityLevel: ActivityLevel = ActivityLevel.MODERATE,
        goal: WeightGoal = WeightGoal.MAINTAIN,
        weeklyChangeKg: Double? = null,
        bodyFatPercentage: Double? = null,
        bodyFatUpdatedAt: Instant? = null,
    ) = UserProfile(
        gender = Gender.MALE,
        birthday = birthday(30),
        heightCm = 180.0,
        weightKg = weightKg,
        activityLevel = activityLevel,
        goal = goal,
        weeklyChangeKg = weeklyChangeKg,
        bodyFatPercentage = bodyFatPercentage,
        bodyFatUpdatedAt = bodyFatUpdatedAt,
    )

    @Test
    fun maintainProfile_unclamped_chainMatchesProfile() {
        val p = profile()
        val d = p.toGoalDerivation()
        // MSJ male 80kg/180cm/30y = 1780; TDEE = 1780 * 1.465 = 2607.7
        assertEquals(1780, d.bmrKcal)
        assertEquals(1.465, d.activityMultiplier, 1e-9)
        assertEquals(2608, d.tdeeKcal)
        assertEquals(0, d.paceKcal)
        assertEquals(p.rawDailyCalories, d.rawTargetKcal)
        assertEquals(p.dailyCalories, d.formulaTargetKcal)
        assertFalse(d.formulaClamped)
        assertEquals(1780, d.floorKcal)
        // Ceiling = max(floor, 6000, 1.5×TDEE) = max(1780, 6000, 3912) = 6000
        assertEquals(6000, d.ceilingKcal)
        assertEquals(null, d.bfAgeDays)
    }

    @Test
    fun aggressiveDeficit_clampsUpToFloor() {
        // 1780 BMR, 2607 TDEE, -1650 pace → raw 957 < floor 1780.
        val p = profile(goal = WeightGoal.LOSE, weeklyChangeKg = 1.5)
        val d = p.toGoalDerivation()
        assertEquals(-1650, d.paceKcal)
        assertTrue(d.formulaClamped)
        assertEquals(957, d.rawTargetKcal)
        assertEquals(d.floorKcal, d.formulaTargetKcal)
        assertTrue(d.rawTargetKcal < d.formulaTargetKcal)
    }

    @Test
    fun extremeGain_clampsDownToCeiling() {
        // very_active TDEE 3070, +3300 pace → raw 6370 > ceiling 6000.
        val p = profile(
            activityLevel = ActivityLevel.VERY_ACTIVE,
            goal = WeightGoal.GAIN,
            weeklyChangeKg = 3.0,
        )
        val d = p.toGoalDerivation()
        assertTrue(d.formulaClamped)
        assertEquals(6370, d.rawTargetKcal)
        assertEquals(d.ceilingKcal, d.formulaTargetKcal)
        assertTrue(d.rawTargetKcal > d.formulaTargetKcal)
    }

    @Test
    fun staleBodyFat_fallsBackToMifflinAndCarriesAge() {
        val at = Instant.now()
        val stale = profile(
            bodyFatPercentage = 0.50,
            bodyFatUpdatedAt = at.minus(Duration.ofDays(120)),
        )
        val d = stale.toGoalDerivation(at)
        // Katch would be 370 + 21.6*40 = 1234; stale → MSJ 1780.
        assertEquals(1780, d.bmrKcal)
        assertEquals(120L, d.bfAgeDays)
        assertEquals(1780, d.floorKcal)

        val fresh = profile(
            bodyFatPercentage = 0.50,
            bodyFatUpdatedAt = at.minus(Duration.ofDays(10)),
        ).toGoalDerivation(at)
        assertEquals(1234, fresh.bmrKcal)
        assertEquals(10L, fresh.bfAgeDays)
        assertEquals(1234, fresh.floorKcal)
    }
}
