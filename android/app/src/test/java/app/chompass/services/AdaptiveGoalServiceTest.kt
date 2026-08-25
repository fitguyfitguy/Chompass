package app.chompass.services

import app.chompass.models.ActivityLevel
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.MacroPlanResolver
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adaptive Goals with day types (#60 phase 4): the weekly tweak runs against
 * the plan-aware baseline (journaled lookback, else forward average) and then
 * moves EVERY day-type profile by the same delta, each clamped to its own
 * floor. Locked base calories skip the whole pass.
 */
class AdaptiveGoalServiceTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun profileWithPlan(): UserProfile {
        val birthday = LocalDate.now().minusYears(30).atStartOfDay(zone).toInstant()
        return UserProfile(
            gender = Gender.MALE,
            birthday = birthday,
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = ActivityLevel.MODERATE,
            goal = WeightGoal.LOSE,
            weeklyChangeKg = 0.5,
            goalWeightKg = 75.0,
            customCalories = 2500,
            macroPlan = MacroPlan(
                enabled = true,
                profiles = listOf(
                    MacroDayProfile("t", "Training day", 2800, 170, 350, 78),
                    MacroDayProfile("r", "Rest day", 2200, 150, 180, 78),
                ),
                mode = MacroPlanMode.CYCLE,
                defaultProfileId = "r",
                cyclePattern = listOf("t", "r"),
                cycleAnchorDay = today.toString(),
            ),
        )
    }

    private fun journalEntry(daysAgo: Long, calories: Int, profileId: String? = null): GoalJournalEntry =
        GoalJournalEntry(
            date = today.minusDays(daysAgo).toString(),
            calories = calories,
            proteinG = 150,
            carbsG = 200,
            fatG = 70,
            profileId = profileId,
            updatedAtMillis = 1_000L + daysAgo,
            source = GoalJournalSource.PLAN,
        )

    private fun food(calories: Int, daysAgo: Long) = FoodEntry(
        name = "Meal",
        calories = calories,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        timestamp = Instant.now().minusSeconds(daysAgo * 86_400L),
        source = FoodSource.MANUAL,
    )

    /** Losing far too slowly (observed ~0.03 kg/wk vs target 0.5): adjustment −150. */
    private fun slowLossHistory() = listOf(
        WeightEntry(date = Instant.now().minusSeconds(35L * 86_400L), weightKg = 80.0),
        WeightEntry(date = Instant.now().minusSeconds(28L * 86_400L), weightKg = 79.95),
        WeightEntry(date = Instant.now().minusSeconds(21L * 86_400L), weightKg = 79.9),
        WeightEntry(date = Instant.now().minusSeconds(14L * 86_400L), weightKg = 79.85),
        WeightEntry(date = Instant.now().minusSeconds(7L * 86_400L), weightKg = 79.82),
        WeightEntry(date = Instant.now().minusSeconds(0L), weightKg = 79.8),
    ) to (1..8L).map { food(2400, it) }

    @Test
    fun `planCurrentCalories uses the journaled lookback with enough coverage`() {
        val profile = profileWithPlan()
        val journal = (0..9L).map { journalEntry(it, if (it % 2 == 0L) 2800 else 2200) }
        val average = AdaptiveGoalService.planCurrentCalories(profile, journal, today)
        assertNotNull(average)
        assertEquals(2500, average)
    }

    @Test
    fun `planCurrentCalories falls back to the forward average on thin coverage`() {
        val profile = profileWithPlan()
        // Only 3 journaled days (< PLAN_MIN_JOURNAL_DAYS): forward window over the
        // 2-day cycle anchored today resolves t then r → 2500.
        val journal = listOf(journalEntry(1, 9999), journalEntry(2, 1234), journalEntry(3, 4321))
        assertEquals(2500, AdaptiveGoalService.planCurrentCalories(profile, journal, today))
        // No journal at all: same forward average.
        assertEquals(2500, AdaptiveGoalService.planCurrentCalories(profile, emptyList(), today))
    }

    @Test
    fun `planCurrentCalories ignores out-of-window journal days`() {
        val profile = profileWithPlan()
        val journal = listOf(journalEntry(20, 4000), journalEntry(30, 3000))
        // Neither entry is inside the 14-day window and coverage is 0 → forward average.
        assertEquals(2500, AdaptiveGoalService.planCurrentCalories(profile, journal, today))
    }

    @Test
    fun `planCurrentCalories is null when the plan is off`() {
        assertNull(AdaptiveGoalService.planCurrentCalories(profileWithPlan().copy(macroPlan = null), emptyList(), today))
        val disabled = profileWithPlan().copy(macroPlan = profileWithPlan().macroPlan?.copy(enabled = false))
        assertNull(AdaptiveGoalService.planCurrentCalories(disabled, emptyList(), today))
    }

    @Test
    fun `weekly tweak spreads to every profile and reports the new average`() {
        val profile = profileWithPlan()
        val (weights, foods) = slowLossHistory()
        val planAverage = MacroPlanResolver.averageForward(
            profile.macroPlan, MacroPlanResolver.baseTargets(profile), today,
        ).calories
        assertEquals(2500, planAverage)
        val result = AdaptiveGoalService.apply(
            profile = profile,
            weights = weights,
            foods = foods,
            planAverageCalories = planAverage,
        )
        assertTrue(result.changed)
        val plan = result.profile.macroPlan!!
        val training = plan.profiles.first { it.id == "t" }
        val rest = plan.profiles.first { it.id == "r" }
        assertEquals(2800 - 150, training.calories)
        assertEquals(2200 - 150, rest.calories)
        // Spread preserved, macros re-balanced to the new totals.
        assertEquals(2800 - 2200, training.calories - rest.calories)
        val kcalFromMacros = 4 * training.proteinG + 4 * training.carbsG + 9 * training.fatG
        assertTrue(kcalFromMacros in (training.calories - 9)..training.calories)
        // Base target moves by the same delta.
        assertEquals(2500 - 150, result.profile.effectiveCalories)
        // Headline number is the new weekly average (2500 − 150).
        assertEquals(2350, result.updatedCalories)
        assertTrue(result.message.contains("weekly average"))
    }

    @Test
    fun `per-profile floor clamps the spread when a rest day hits it`() {
        // Average 2150 stays above the floor, so the weekly tweak fires; the rest
        // day (1900 − 150) lands below the floor and clamps while training keeps
        // the full delta — each profile is clamped to its own floor.
        val profile = profileWithPlan().let {
            it.copy(
                macroPlan = it.macroPlan!!.copy(
                    profiles = listOf(
                        MacroDayProfile("t", "Training day", 2400, 170, 220, 78),
                        MacroDayProfile("r", "Rest day", 1900, 150, 160, 78),
                    ),
                ),
            )
        }
        val (weights, foods) = slowLossHistory()
        val result = AdaptiveGoalService.apply(
            profile = profile,
            weights = weights,
            foods = foods,
            planAverageCalories = 2150,
        )
        assertTrue(result.changed)
        val plan = result.profile.macroPlan!!
        val floor = app.chompass.models.CalorieSafety.floorKcal(profile.bmr)
        assertEquals(floor, plan.profiles.first { it.id == "r" }.calories)
        assertEquals(
            app.chompass.models.CalorieSafety.clampAuto(2400 - 150, profile.bmr, profile.tdee),
            plan.profiles.first { it.id == "t" }.calories,
        )
    }

    @Test
    fun `locked base calories skip the pass even with a plan`() {
        val profile = profileWithPlan().copy(caloriesLocked = true)
        val (weights, foods) = slowLossHistory()
        val result = AdaptiveGoalService.apply(
            profile = profile,
            weights = weights,
            foods = foods,
            planAverageCalories = 2500,
        )
        assertFalse(result.changed)
        assertNull(result.updatedCalories)
        assertEquals(2800, result.profile.macroPlan!!.profiles.first { it.id == "t" }.calories)
    }

    @Test
    fun `without a plan the behavior is unchanged`() {
        val profile = profileWithPlan().copy(macroPlan = null)
        val (weights, foods) = slowLossHistory()
        val single = AdaptiveGoalService.apply(profile, weights, foods)
        assertTrue(single.changed)
        assertEquals(2350, single.updatedCalories)
        assertFalse(single.message.contains("weekly average"))
    }
}
