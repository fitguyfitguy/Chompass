package app.chompass.services.ai

import app.chompass.models.CalorieSafety
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Day-types integration in AI Recalculate (#60 phase 4, docs/local/
 * MACRO_PROFILES_DESIGN.md §3): the goal prompt carries the plan block
 * (profiles, schedule, today, weekly average, `profiles[]` contract), the
 * parser reads the optional array, and `UserProfile.applyingAiGoalsToPlan`
 * applies explicit rows per id or falls back to the base-change kcal delta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class GoalPlanRecalcTest {
    private val zone = ZoneId.systemDefault()

    private fun profileWithPlan(): UserProfile {
        val birthday = LocalDate.now().minusYears(30).atStartOfDay(zone).toInstant()
        return UserProfile(
            gender = Gender.MALE,
            birthday = birthday,
            heightCm = 178.0,
            weightKg = 76.0,
            goal = WeightGoal.LOSE,
            weeklyChangeKg = 0.5,
            customCalories = 2500,
            customProtein = 150,
            customCarbs = 250,
            customFat = 70,
            macroPlan = MacroPlan(
                enabled = true,
                profiles = listOf(
                    MacroDayProfile("t", "Training day", 2800, 170, 350, 78),
                    MacroDayProfile("r", "Rest day", 2100, 150, 160, 78),
                ),
                mode = MacroPlanMode.CYCLE,
                defaultProfileId = "r",
                cyclePattern = listOf("t", "r"),
                cycleAnchorDay = LocalDate.now().toString(),
            ),
        )
    }

    private fun service(reply: String, onPrompt: (String) -> Unit = {}): FoodAnalysisService =
        FoodAnalysisService(
            callAiDelegate = { prompt, _, _ ->
                onPrompt(prompt)
                reply
            },
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

    /**
     * Dense flat history: 60 logged days at TDEE + flat weigh-ins, goal pace 0
     * (maintain). The empirical gates pass, trends agree, and the implied
     * maintenance sits above the floor, so the model's parsed answer survives
     * the deterministic enforcement and carries `profiles[]` through.
     */
    private fun trustedHistory(profile: UserProfile): Pair<List<WeightEntry>, List<FoodEntry>> {
        val tdee = profile.tdee.toInt()
        val foods = (1..60L).map { food(tdee, it) }
        val weights = (0..5L).map {
            WeightEntry(date = Instant.now().minusSeconds((it * 7) * 86_400L), weightKg = 76.0)
        }
        return weights to foods
    }

    @Test
    fun `prompt carries the day types block when the plan is enabled`() = runBlocking {
        var captured = ""
        val (weights, foods) = trustedHistory(profileWithPlan())
        service("""{"calories":2400,"protein":150,"carbs":240,"fat":67,"reason":"ok"}""") {
            captured = it
        }.calculateGoals(
            profileWithPlan(), null, true, true,
            weights = weights, foods = foods,
        )
        assertTrue(captured.contains("DAY TYPES"))
        assertTrue(captured.contains("Training day (id t): 2800 kcal"))
        assertTrue(captured.contains("Rest day (id r): 2100 kcal"))
        assertTrue(captured.contains("repeating cycle Training day → Rest day"))
        assertTrue(captured.contains("Today ("))
        assertTrue(captured.contains("Weekly average target: 2450 kcal/day"))
        assertTrue(captured.contains("\"profiles\""))
        assertTrue(captured.contains("day-type id"))
    }

    @Test
    fun `prompt without a plan keeps the pre-day-types shape`() = runBlocking {
        var captured = ""
        service("""{"calories":2400,"protein":150,"carbs":240,"fat":67,"reason":"ok"}""") {
            captured = it
        }.calculateGoals(profileWithPlan().copy(macroPlan = null), null, true, true)
        assertFalse(captured.contains("DAY TYPES"))
        assertFalse(captured.contains("profiles"))
        assertTrue(captured.contains("Output no keys other than calories, protein, carbs, fat, reason."))
    }

    @Test
    fun `trusted result keeps the parsed profiles array`() = runBlocking {
        val profile = profileWithPlan().copy(
            goal = WeightGoal.MAINTAIN,
            weeklyChangeKg = null,
            goalWeightKg = null,
        )
        val (weights, foods) = trustedHistory(profile)
        // Reply exactly at the implied maintenance (intake at TDEE, flat weight,
        // maintain pace): the trusted path keeps the model's answer and array.
        val reply = profile.tdee.toInt()
        val result = service(
            """{"calories":$reply,"protein":150,"carbs":240,"fat":67,"reason":"ok","profiles":[""" +
                """{"id":"t","calories":2700,"protein_g":175,"carbs_g":330,"fat_g":75},""" +
                """{"id":"r","calories":2000,"protein_g":145,"carbs_g":150,"fat_g":72}]}""",
        ).calculateGoals(profile, null, true, true, weights = weights, foods = foods)
        assertEquals(reply, result.calories)
        assertEquals(2, result.profiles.size)
        assertEquals("t", result.profiles[0].id)
        assertEquals(2700, result.profiles[0].calories)
        assertEquals(175, result.profiles[0].proteinG)
        assertEquals(145, result.profiles[1].proteinG)
    }

    @Test
    fun `deterministic snap clears the profiles array for the delta fallback`() = runBlocking {
        // No forecast data: trustEmpirical is false, so the answer snaps to the
        // formula anchor and the model's per-profile rows are dropped — the
        // apply path then shifts profiles by the implied base delta instead.
        val result = service(
            """{"calories":1800,"protein":150,"carbs":240,"fat":67,"reason":"ok","profiles":[""" +
                """{"id":"t","calories":9999,"protein_g":175,"carbs_g":330,"fat_g":75}]}""",
        ).calculateGoals(profileWithPlan(), null, true, true)
        assertEquals(profileWithPlan().dailyCalories, result.calories)
        assertTrue(result.profiles.isEmpty())
    }

    @Test
    fun `parser reads profiles with protein_g keys and clamps ranges`() {
        val calc = FoodJsonParser.parseGoalCalculation(
            """{"calories":2400,"protein":150,"carbs":240,"fat":67,"reason":"ok","profiles":[""" +
                """{"id":"t","calories":2900,"protein_g":175,"carbs_g":360,"fat_g":80},""" +
                """{"id":"r","calories":900,"protein_g":10,"carbs_g":9999,"fat_g":-5},""" +
                """{"calories":1234}]}""",
        )
        assertEquals(2, calc.profiles.size)
        assertEquals(2900, calc.profiles[0].calories)
        assertEquals(360, calc.profiles[0].carbsG)
        // Below the parser floor / above the macro caps / no id: clamped or dropped.
        assertEquals(CalorieSafety.ABSOLUTE_FLOOR_KCAL, calc.profiles[1].calories)
        assertEquals(1200, calc.profiles[1].carbsG)
        assertEquals(0, calc.profiles[1].fatG)
    }

    @Test
    fun `applyingAiGoalsToPlan applies explicit rows per id with per-profile clamps`() {
        val profile = profileWithPlan()
        val result = GoalCalculation(
            calories = 2400,
            protein = 150,
            carbs = 240,
            fat = 67,
            profiles = listOf(
                GoalCalculationProfile("t", 3000, 180, 370, 80),
                GoalCalculationProfile("r", 900, 145, 150, 72),
                GoalCalculationProfile("ghost", 5000, 1, 1, 1),
            ),
        )
        val next = profile.applyingAiGoalsToPlan(result)
        val plan = next.macroPlan!!
        assertEquals(2, plan.profiles.size)
        assertEquals(3000, plan.profiles.first { it.id == "t" }.calories)
        assertEquals(180, plan.profiles.first { it.id == "t" }.proteinG)
        // Rest day floored per profile (MACRO-CYCLE-C) via the base clamp.
        assertEquals(
            CalorieSafety.clampAuto(900, profile.bmr, profile.tdee),
            plan.profiles.first { it.id == "r" }.calories,
        )
        // Base anchor applied as usual.
        assertEquals(2400, next.effectiveCalories)
    }

    @Test
    fun `missing profiles array falls back to the base-change kcal delta`() {
        val profile = profileWithPlan()
        val result = GoalCalculation(calories = 2000, protein = 150, carbs = 180, fat = 56)
        val next = profile.applyingAiGoalsToPlan(result)
        val plan = next.macroPlan!!
        val training = plan.profiles.first { it.id == "t" }
        val rest = plan.profiles.first { it.id == "r" }
        // Base moved 2500 → 2000 (−500): every profile shifts by the same delta,
        // each clamped to its own floor.
        assertEquals(
            CalorieSafety.clampAuto(2800 - 500, profile.bmr, profile.tdee),
            training.calories,
        )
        assertEquals(
            CalorieSafety.clampAuto(2100 - 500, profile.bmr, profile.tdee),
            rest.calories,
        )
        // Spread preserved: training stays above rest unless the floor lifted rest.
        if (rest.calories == CalorieSafety.clampAuto(2100 - 500, profile.bmr, profile.tdee)) {
            assertTrue(training.calories - rest.calories >= 0)
        }
        // Re-balanced macros keep the calorie identity within fat-rounding slack.
        val kcalFromMacros = 4 * training.proteinG + 4 * training.carbsG + 9 * training.fatG
        assertTrue(kcalFromMacros in (training.calories - 9)..training.calories)
    }

    @Test
    fun `locked base calories skip plan application`() {
        val profile = profileWithPlan().copy(caloriesLocked = true)
        val result = GoalCalculation(
            calories = 2000,
            protein = 150,
            carbs = 180,
            fat = 56,
            profiles = listOf(GoalCalculationProfile("t", 3000, 180, 370, 80)),
        )
        val next = profile.applyingAiGoalsToPlan(result)
        assertEquals(2800, next.macroPlan!!.profiles.first { it.id == "t" }.calories)
        assertEquals(2500, next.effectiveCalories)
    }

    @Test
    fun `plan disabled or absent leaves applyingAiGoals behavior untouched`() {
        val disabled = profileWithPlan().copy(macroPlan = profileWithPlan().macroPlan?.copy(enabled = false))
        val result = GoalCalculation(calories = 2000, protein = 150, carbs = 180, fat = 56)
        assertEquals(disabled.applyingAiGoals(2000, 150, 180, 56), disabled.applyingAiGoalsToPlan(result))
        val noPlan = profileWithPlan().copy(macroPlan = null)
        assertEquals(noPlan.applyingAiGoals(2000, 150, 180, 56), noPlan.applyingAiGoalsToPlan(result))
    }
}
