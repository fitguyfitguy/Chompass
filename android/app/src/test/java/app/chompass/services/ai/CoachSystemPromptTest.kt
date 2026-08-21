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
}
