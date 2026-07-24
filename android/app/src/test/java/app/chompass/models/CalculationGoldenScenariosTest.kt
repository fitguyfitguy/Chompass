package app.chompass.models

import app.chompass.parity.ParityFixtures
import app.chompass.services.KetoCarbRecommendationService
import app.chompass.services.WeightForecastMath
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate
import java.time.ZoneId

/**
 * Golden vectors from `testdata/parity/formulas-expected.json` — shared with the PWA
 * `nofud-core` formula tests. Update the JSON (and docs/CALCULATION_METHODS.md) when
 * formulas change; both runners assert against the same table.
 */
@RunWith(Parameterized::class)
class CalculationGoldenScenariosTest(
    private val scenarioId: String,
    private val scenario: JSONObject,
) {

    @Test
    fun matchesSharedParityFixture() {
        if (scenario.optString("kind") == "averageDailyIntake") {
            val input = scenario.getJSONObject("input")
            val expect = scenario.getJSONObject("expect")
            val intake = WeightForecastMath.averageDailyIntake(
                totalCalories = input.getInt("totalCalories"),
                loggedDays = input.getInt("loggedDays"),
                calendarDaysInWindow = input.getInt("calendarDaysInWindow"),
            )
            assertEquals(scenarioId, expect.getInt("avgDailyCalories"), intake.avgDailyCalories)
            assertEquals(scenarioId, expect.getBoolean("usesCalendarDayAverage"), intake.usesCalendarDayAverage)
            return
        }

        val profile = profileFrom(scenario.getJSONObject("profile"))
        val expect = scenario.getJSONObject("expect")

        if (expect.has("bmr")) {
            assertEquals(scenarioId, expect.getDouble("bmr"), profile.bmr, expect.optDouble("bmrTol", 0.5))
        }
        if (expect.has("tdee")) {
            assertEquals(scenarioId, expect.getDouble("tdee"), profile.tdee, expect.optDouble("tdeeTol", 0.5))
        }
        if (expect.has("calorieAdjustment")) {
            assertEquals(scenarioId, expect.getInt("calorieAdjustment"), profile.calorieAdjustment)
        }
        if (expect.has("dailyCalories")) {
            assertEquals(scenarioId, expect.getInt("dailyCalories"), profile.dailyCalories)
        }
        if (expect.has("estimatedDailyActiveCalories")) {
            assertEquals(scenarioId, expect.getInt("estimatedDailyActiveCalories"), profile.estimatedDailyActiveCalories)
        }
        if (expect.has("sedentaryCalorieBudget")) {
            assertEquals(scenarioId, expect.getInt("sedentaryCalorieBudget"), profile.sedentaryCalorieBudget())
        }
        if (expect.has("proteinGoal")) {
            assertEquals(scenarioId, expect.getInt("proteinGoal"), profile.proteinGoal)
        }
        if (expect.has("fatGoal")) {
            assertEquals(scenarioId, expect.getInt("fatGoal"), profile.fatGoal)
        }
        if (expect.has("carbsGoal")) {
            assertEquals(scenarioId, expect.getInt("carbsGoal"), KetoCarbRecommendationService.recommendNetCarbs(profile))
            assertEquals(scenarioId, expect.getInt("carbsGoal"), profile.carbsGoal)
        }
        if (expect.has("fatGoalMin")) {
            assertTrue("$scenarioId fatGoal>=${expect.getInt("fatGoalMin")}", profile.fatGoal >= expect.getInt("fatGoalMin"))
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> {
            val root = ParityFixtures.readJson("formulas-expected.json")
            val scenarios = root.getJSONArray("scenarios")
            return (0 until scenarios.length()).map { i ->
                val s = scenarios.getJSONObject(i)
                arrayOf(s.getString("id"), s)
            }
        }

        private fun atAge(years: Int) =
            LocalDate.now().minusYears(years.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()

        private fun activityLevel(raw: String): ActivityLevel = when (raw) {
            "sedentary" -> ActivityLevel.SEDENTARY
            "light" -> ActivityLevel.LIGHT
            "moderate" -> ActivityLevel.MODERATE
            "active" -> ActivityLevel.ACTIVE
            "very_active", "veryActive" -> ActivityLevel.VERY_ACTIVE
            "extra_active", "extraActive" -> ActivityLevel.EXTRA_ACTIVE
            else -> error("Unknown activityLevel: $raw")
        }

        private fun profileFrom(p: JSONObject): UserProfile {
            val sex = p.optString("sex", "male")
            val gender = when (sex) {
                "male" -> Gender.MALE
                "female" -> Gender.FEMALE
                else -> Gender.OTHER
            }
            val goal = when (p.getString("goal")) {
                "lose" -> WeightGoal.LOSE
                "gain" -> WeightGoal.GAIN
                else -> WeightGoal.MAINTAIN
            }
            val keto = p.optBoolean("ketoMode", false)
            return UserProfile(
                gender = gender,
                birthday = atAge(p.getInt("age")),
                heightCm = p.optDouble("heightCm", 170.0),
                weightKg = p.getDouble("weightKg"),
                bodyFatPercentage = if (p.isNull("bodyFatPercentage")) null else p.getDouble("bodyFatPercentage"),
                activityLevel = activityLevel(p.getString("activityLevel")),
                goal = goal,
                weeklyChangeKg = if (p.isNull("weeklyChangeKg")) null else p.getDouble("weeklyChangeKg"),
                dietMode = if (keto) DietMode.KETO else DietMode.STANDARD,
            )
        }
    }
}
