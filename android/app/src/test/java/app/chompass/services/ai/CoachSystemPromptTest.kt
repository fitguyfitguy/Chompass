package app.chompass.services.ai

import app.chompass.models.ActivityLevel
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CoachSystemPromptTest {
    private val zone = ZoneId.systemDefault()

    private fun profile(): UserProfile {
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
        )
    }

    private fun food(calories: Int, daysAgo: Long) = FoodEntry(
        name = "Meal",
        calories = calories,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        timestamp = Instant.now().minusSeconds(daysAgo * 86_400L),
        source = FoodSource.MANUAL,
    )

    @Test
    fun prompt_nineDayDiary_citesLoggedDayAverageNotCalendarDiluted() {
        val foods = (1..9).map { food(calories = 2100, daysAgo = it.toLong()) }
        val weights = listOf(
            WeightEntry(date = Instant.now().minusSeconds(7 * 86_400L), weightKg = 80.5),
            WeightEntry(date = Instant.now(), weightKg = 80.0),
        )
        val prompt = buildSystemPrompt(
            profile = profile(),
            weights = weights,
            bodyFats = emptyList(),
            foods = foods,
            heightMetric = true,
            weightMetric = true,
        )
        assertTrue(prompt.contains("2100 kcal"))
        assertFalse(prompt.contains("Avg daily intake: 207 kcal"))
        assertTrue(prompt.contains("across 9 logged days"))
        assertTrue(prompt.contains("get_calorie_totals"))
    }

    @Test
    fun intakeAverage_reports7and30DayWindowsOverLoggedDays() {
        fun foodWithMacros(kcal: Int, p: Double, c: Double, f: Double, daysAgo: Long) = food(kcal, daysAgo).copy(
            protein = p, carbs = c, fat = f,
        )
        // Days 1..3 (both windows): 2000/3000/4000 kcal.
        // Day 20 (30-day window only): 1000 kcal.
        // Today: excluded (incomplete day).
        val foods = listOf(
            foodWithMacros(2000, 150.0, 200.0, 60.0, daysAgo = 1),
            foodWithMacros(3000, 150.0, 300.0, 80.0, daysAgo = 2),
            foodWithMacros(4000, 150.0, 400.0, 100.0, daysAgo = 3),
            foodWithMacros(1000, 101.0, 100.0, 31.0, daysAgo = 20),
            foodWithMacros(9000, 900.0, 900.0, 900.0, daysAgo = 0),
        )
        val prompt = buildSystemPrompt(
            profile = profile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = foods,
            heightMetric = true,
            weightMetric = true,
        )
        // 7-day window: 3 logged days, mean 3000 kcal, 150P/300C/80F.
        assertTrue(prompt.contains("Average intake last 7 days (3 logged days): 3000 kcal, 150g protein, 300g carbs, 80g fat"))
        // 30-day window: 4 logged days, mean 2500 kcal, 138P/250C/68F.
        assertTrue(prompt.contains("Average intake last 30 days (4 logged days): 2500 kcal, 138g protein, 250g carbs, 68g fat"))
        assertTrue(prompt.contains("Judge intake questions against these"))
    }

    @Test
    fun intakeAverageLine_noLoggedDays_omitsTheLine() {
        val prompt = buildSystemPrompt(
            profile = profile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = listOf(food(1000, daysAgo = 0)), // today only
            heightMetric = true,
            weightMetric = true,
        )
        assertFalse(prompt.contains("Average intake"))
    }

    @Test
    fun intakeAverage_onlyOldLogs_omitsThe7DayLineButKeeps30Day() {
        val prompt = buildSystemPrompt(
            profile = profile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = listOf(food(1000, daysAgo = 20)),
            heightMetric = true,
            weightMetric = true,
        )
        assertFalse(prompt.contains("last 7 days"))
        assertTrue(prompt.contains("Average intake last 30 days (1 logged day): 1000 kcal"))
    }

    @Test
    fun fastingBlock_whenProvided_sitsInTheVolatileTail() {
        val fasting = "## Fasting (intermittent fasting tracker - user-optional, local-only)\n" +
            "- Active fast: started 2026-08-24T08:00:00, elapsed 14h 20m, goal 16h"
        val prompt = buildSystemPrompt(
            profile = profile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
            fastingContext = fasting,
        )
        // The block must sit below the cache marker (volatile tail), never in
        // the stable prefix Anthropic caches between turns.
        assertTrue(prompt.contains(fasting))
        val volatileStart = prompt.indexOf("## Current date")
        assertTrue(volatileStart > 0)
        assertTrue(prompt.indexOf(fasting) > volatileStart)
        assertTrue(prompt.indexOf(fasting) < prompt.indexOf("## Data available"))
    }

    @Test
    fun fastingBlock_null_omitsTheSection() {
        val prompt = buildSystemPrompt(
            profile = profile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
            fastingContext = null,
        )
        assertFalse(prompt.contains("## Fasting"))
        assertFalse(prompt.contains("intermittent fasting"))
    }

    // -- Day types (#60 phase 4) --------------------------------------------

    private fun planProfile(): UserProfile {
        val today = java.time.LocalDate.now(zone)
        return profile().copy(
            macroPlan = app.chompass.models.MacroPlan(
                enabled = true,
                profiles = listOf(
                    app.chompass.models.MacroDayProfile("t", "Training day", 2800, 170, 350, 78),
                    app.chompass.models.MacroDayProfile("r", "Rest day", 2100, 150, 160, 78),
                ),
                mode = app.chompass.models.MacroPlanMode.CYCLE,
                defaultProfileId = "r",
                cyclePattern = listOf("t", "r"),
                cycleAnchorDay = today.toString(),
            ),
        )
    }

    @Test
    fun dayTypes_summarySitsAboveCacheMarker_todayLineBelowIt() {
        val prompt = buildSystemPrompt(
            profile = planProfile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
        )
        val volatileStart = prompt.indexOf("\n## Current date")
        assertTrue(volatileStart > 0)
        val stable = prompt.substring(0, volatileStart)
        // Stable config: profile list + schedule + weekly average.
        assertTrue(stable.contains("Day types: Training day 2800 kcal (170P/350C/78F); Rest day 2100 kcal (150P/160C/78F)"))
        assertTrue(stable.contains("Day-type schedule: repeating cycle Training day → Rest day"))
        assertTrue(stable.contains("weekly average target 2450 kcal/day"))
        // The per-day line must never live in the cached prefix.
        assertFalse(stable.contains("Today is a"))
        // Volatile tail carries today's resolved day type (anchor = today → training).
        val tail = prompt.substring(volatileStart)
        assertTrue(tail.contains("Today is a Training day: 2800 kcal, 170P/350C/78F"))
        assertTrue(tail.contains("weekly average target"))
    }

    @Test
    fun dayTypes_off_omitsBothLines() {
        val prompt = buildSystemPrompt(
            profile = profile().copy(macroPlan = null),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
        )
        assertFalse(prompt.contains("Day types:"))
        assertFalse(prompt.contains("Today is a"))
        // A disabled plan (keto pause) also shows nothing.
        val paused = planProfile().copy(macroPlan = planProfile().macroPlan?.copy(enabled = false))
        val pausedPrompt = buildSystemPrompt(
            profile = paused,
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
        )
        assertFalse(pausedPrompt.contains("Day types:"))
        assertFalse(pausedPrompt.contains("Today is a"))
    }
}
