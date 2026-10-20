package app.chompass.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal provenance + the automatic CAL-SAFE re-clamp: AUTO sources (FORMULA/AI/
 * ADAPTIVE) are pulled back into the envelope when weight/body-fat syncs move
 * BMR/TDEE; MANUAL/UNKNOWN goals and locked calories are never touched.
 */
class GoalSourceTest {

    private fun profile(source: GoalSource) = UserProfile.Default.copy(
        weightKg = 95.0,
        customCalories = 1900,
        goalSource = source,
    )

    @Test
    fun goalSource_defaultsToUnknownAndRoundTrips() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(UserProfile.serializer(), "{}")
        assertEquals(GoalSource.UNKNOWN, decoded.goalSource)
        val encoded = Json.encodeToString(UserProfile.serializer(), decoded.copy(goalSource = GoalSource.MANUAL))
        assertTrue(encoded.contains("\"goalSource\":\"manual\""))
    }

    @Test
    fun recalculatedFromFormulas_marksFormula() {
        assertEquals(GoalSource.FORMULA, UserProfile.Default.recalculatedFromFormulas().goalSource)
    }

    @Test
    fun applyingAiGoals_marksAi() {
        val next = profile(GoalSource.FORMULA).applyingAiGoals(2100, 140, 180, 70)
        assertEquals(GoalSource.AI, next.goalSource)
    }

    @Test
    fun reclampedAutoGoal_raisesAiGoalBelowNewBmr() {
        val p = profile(GoalSource.AI)
        // MSJ male 25y/175cm/95kg = 950 + 1093.75 − 125 − 161 + 166 = 1923.75
        // → floor 1924 lifts the stored 1900.
        assertEquals(1924, CalorieSafety.floorKcal(p.bmr))
        val next = p.reclampedAutoGoal()
        assertEquals(1924, next.effectiveCalories)
        val macroKcal = 4 * next.effectiveProtein + 4 * next.effectiveCarbs + 9 * next.effectiveFat
        assertTrue("macro kcal $macroKcal", macroKcal in 1921..1924)
    }

    @Test
    fun reclampedAutoGoal_preservesManualAndUnknown() {
        for (source in listOf(GoalSource.MANUAL, GoalSource.UNKNOWN)) {
            assertEquals(profile(source), profile(source).reclampedAutoGoal())
        }
    }

    @Test
    fun reclampedAutoGoal_leavesLockedCalories() {
        val p = profile(GoalSource.AI).copy(caloriesLocked = true)
        assertEquals(p, p.reclampedAutoGoal())
    }

    @Test
    fun reclampedAutoGoal_raisesDayTypeProfilesBelowFloor() {
        val p = UserProfile.Default.copy(
            weightKg = 95.0,
            goalSource = GoalSource.FORMULA,
            macroPlan = MacroPlan(
                enabled = true,
                profiles = listOf(
                    MacroDayProfile("t", "Training day", 1500, 150, 150, 60),
                    MacroDayProfile("r", "Rest day", 1300, 140, 120, 55),
                ),
                mode = MacroPlanMode.MANUAL,
                defaultProfileId = "r",
            ),
        )
        val floor = CalorieSafety.floorKcal(p.bmr)
        val next = p.reclampedAutoGoal()
        for (dayProfile in next.macroPlan!!.profiles) {
            assertTrue("${dayProfile.id} ${dayProfile.calories} < $floor", dayProfile.calories >= floor)
        }
        assertNotEquals(p, next)
    }

    @Test
    fun reclampedAutoGoal_doesNotPinFormulaProfiles() {
        // A formula profile without custom targets sits inside the envelope and its
        // macros already add up, so the re-clamp is a no-op (stays live-formula).
        val p = UserProfile.Default.copy(weightKg = 95.0, goalSource = GoalSource.FORMULA)
        assertEquals(p, p.reclampedAutoGoal())
    }
}
