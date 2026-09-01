package app.chompass.data

import app.chompass.models.ActivityLevel
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.DietMode
import app.chompass.models.Gender
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.ProteinTargetMode
import app.chompass.models.UserProfile
import app.chompass.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Complete-onboarding merge invariants (Codeberg #60): re-running onboarding applies the
 * onboarding answers but must never wipe post-onboarding state — above all the macro
 * day-type plan, whose silent loss was the reported bug.
 */
class OnboardingProfileMergeTest {
    private val birthday = Instant.parse("1990-06-15T00:00:00Z")

    /** A profile that has lived past onboarding: day types, locks, protein mode, name. */
    private val stored = UserProfile(
        name = "Sam",
        gender = Gender.FEMALE,
        birthday = birthday,
        heightCm = 170.0,
        weightKg = 65.0,
        activityLevel = ActivityLevel.LIGHT,
        goal = WeightGoal.LOSE,
        dietMode = DietMode.KETO,
        bodyFatPercentage = 28.0,
        goalBodyFatPercentage = 24.0,
        useBodyFatInBMR = false,
        weeklyChangeKg = -0.5,
        goalWeightKg = 60.0,
        proteinGramsPerKg = 1.8,
        proteinTargetMode = ProteinTargetMode.G_PER_KG_TOTAL,
        autoBalanceMacro = AutoBalanceMacro.PROTEIN,
        caloriesLocked = true,
        lockedMacros = setOf(AutoBalanceMacro.FAT),
        macroPlan = MacroPlan(
            enabled = true,
            profiles = listOf(
                MacroDayProfile("t", "Training day", 2800, 170, 350, 78),
                MacroDayProfile("r", "Rest day", 2100, 150, 160, 78),
            ),
            mode = MacroPlanMode.CYCLE,
            defaultProfileId = "t",
            cyclePattern = listOf("t", "t", "r"),
            cycleAnchorDay = "2026-08-18",
        ),
    )

    /** What buildProfile() produces from fresh onboarding answers: onboarding-owned
     *  fields set, everything post-onboarding left at its default (macroPlan null). */
    private val onboarded = UserProfile(
        gender = Gender.MALE,
        birthday = birthday,
        heightCm = 182.0,
        weightKg = 92.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = WeightGoal.GAIN,
        dietMode = DietMode.STANDARD,
        bodyFatPercentage = 15.0,
        goalBodyFatPercentage = 12.0,
        weeklyChangeKg = 0.25,
        goalWeightKg = 95.0,
    )

    @Test
    fun `onboarding answers override stored body stats and goal`() {
        val merged = stored.withOnboardingInputs(onboarded)
        assertEquals(Gender.MALE, merged.gender)
        assertEquals(182.0, merged.heightCm, 1e-9)
        assertEquals(92.0, merged.weightKg, 1e-9)
        assertEquals(ActivityLevel.MODERATE, merged.activityLevel)
        assertEquals(WeightGoal.GAIN, merged.goal)
        assertEquals(DietMode.STANDARD, merged.dietMode)
        assertEquals(15.0, merged.bodyFatPercentage!!, 1e-9)
        assertEquals(12.0, merged.goalBodyFatPercentage!!, 1e-9)
        assertEquals(0.25, merged.weeklyChangeKg!!, 1e-9)
        assertEquals(95.0, merged.goalWeightKg!!, 1e-9)
    }

    @Test
    fun `macro day types and post-onboarding state survive re-running onboarding`() {
        val merged = stored.withOnboardingInputs(onboarded)
        assertEquals(stored.macroPlan, merged.macroPlan)
        assertEquals("Sam", merged.name)
        assertEquals(false, merged.useBodyFatInBMR)
        assertEquals(1.8, merged.proteinGramsPerKg!!, 1e-9)
        assertEquals(ProteinTargetMode.G_PER_KG_TOTAL, merged.proteinTargetMode)
        assertEquals(AutoBalanceMacro.PROTEIN, merged.autoBalanceMacro)
        assertEquals(true, merged.caloriesLocked)
        assertEquals(setOf(AutoBalanceMacro.FAT), merged.lockedMacros)
    }

    @Test
    fun `maintain-goal onboarding clears rate and goal weight but keeps day types`() {
        // buildProfile() nulls these when the user picks Maintain.
        val maintain = onboarded.copy(
            goal = WeightGoal.MAINTAIN, weeklyChangeKg = null, goalWeightKg = null,
        )
        val merged = stored.withOnboardingInputs(maintain)
        assertNull(merged.weeklyChangeKg)
        assertNull(merged.goalWeightKg)
        assertEquals(stored.macroPlan, merged.macroPlan)
    }
}
