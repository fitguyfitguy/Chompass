package app.chompass.services

import app.chompass.models.ActivityLevel
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeightAnalysisServiceTest {
    private val zone = ZoneId.systemDefault()

  private fun profile(tdeeBasisBmr: Double = 1780.0): UserProfile {
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

  private fun daysAgo(days: Long): Instant =
    Instant.now().minusSeconds(days * 86_400L)

  @Test
  fun forecast_predictedWeeklyChange_uses7700KcalPerKg() {
    val p = profile()
    val tdee = p.tdee.toInt()
    val foods = (1..50).map { day ->
      foodEntry(calories = tdee - 550, dayOffset = day.toLong())
    }
    val weights = listOf(
      WeightEntry(date = daysAgo(7), weightKg = 80.0),
      WeightEntry(date = daysAgo(0), weightKg = 79.8),
    )
    val forecast = WeightAnalysisService.compute(weights, foods, p)
    assertEquals(-0.5, forecast.predictedWeeklyChangeKg, 0.05)
    assertEquals(50, forecast.daysOfFoodData)
    assertFalse(forecast.usesCalendarDayAverage)
  }

  @Test
  fun forecast_observedTrend_fromLinearRegression() {
    val p = profile()
    val weights = listOf(
      WeightEntry(date = daysAgo(28), weightKg = 82.0),
      WeightEntry(date = daysAgo(21), weightKg = 81.5),
      WeightEntry(date = daysAgo(14), weightKg = 81.0),
      WeightEntry(date = daysAgo(7), weightKg = 80.5),
      WeightEntry(date = daysAgo(0), weightKg = 80.0),
    )
    val forecast = WeightAnalysisService.compute(weights, emptyList(), p)
    val observed = forecast.observedWeeklyChangeKg
    assertNotNull(observed)
    assertEquals(-0.5, observed!!, 0.15)
  }

  @Test
  fun forecast_flagsDisagreement_whenPredictedAndObservedDiverge() {
    val p = profile()
    val tdee = p.tdee.toInt()
    val foods = listOf(
      foodEntry(calories = tdee + 400, dayOffset = 1),
      foodEntry(calories = tdee + 400, dayOffset = 2),
    )
    val weights = listOf(
      WeightEntry(date = daysAgo(21), weightKg = 82.0),
      WeightEntry(date = daysAgo(14), weightKg = 81.0),
      WeightEntry(date = daysAgo(7), weightKg = 80.0),
      WeightEntry(date = daysAgo(0), weightKg = 79.0),
    )
    val forecast = WeightAnalysisService.compute(weights, foods, p)
    assertTrue(forecast.trendsDisagree)
  }

  @Test
  fun adaptiveGoal_lowersCaloriesWhenLosingTooSlowly() {
    val p = profile().copy(customCalories = 2200)
    val foods = (1..8).map { foodEntry(calories = 2200, dayOffset = it.toLong()) }
    val weights = listOf(
      WeightEntry(date = daysAgo(35), weightKg = 80.0),
      WeightEntry(date = daysAgo(28), weightKg = 79.95),
      WeightEntry(date = daysAgo(21), weightKg = 79.9),
      WeightEntry(date = daysAgo(14), weightKg = 79.85),
      WeightEntry(date = daysAgo(7), weightKg = 79.82),
      WeightEntry(date = daysAgo(0), weightKg = 79.8),
    )
    val result = AdaptiveGoalService.apply(p, weights, foods)
    assertTrue(result.changed)
    assertNotNull(result.updatedCalories)
    assertTrue(result.updatedCalories!! < 2200)
  }

  @Test
  fun adaptiveGoal_noChangeWhenInsufficientData() {
    val p = profile().copy(customCalories = 2000)
    val result = AdaptiveGoalService.apply(p, emptyList(), emptyList())
    assertFalse(result.changed)
    assertTrue(result.message.contains("four weeks"))
  }

  @Test
  fun adaptiveGoal_noChangeOnTwoNoisyWeeks() {
    val p = profile().copy(customCalories = 2000)
    val foods = (1..8).map { foodEntry(calories = 2000, dayOffset = it.toLong()) }
    val weights = listOf(
      WeightEntry(date = daysAgo(10), weightKg = 80.0),
      WeightEntry(date = daysAgo(5), weightKg = 80.4),
      WeightEntry(date = daysAgo(0), weightKg = 80.6),
    )
    val result = AdaptiveGoalService.apply(p, weights, foods)
    assertFalse(result.changed)
    assertTrue(result.message.contains("four weeks"))
  }

  @Test
  fun adaptiveGoal_usesMeasuredTdeeWhenTrendDataThin() {
    val p = profile().copy(customCalories = 1800, goal = WeightGoal.MAINTAIN)
    val measured = 1900
    val result = AdaptiveGoalService.apply(p, emptyList(), emptyList(), measuredTdee = measured)
    assertTrue(result.changed)
    assertEquals(1900, result.updatedCalories)
  }

  @Test
  fun forecast_sparseGapsAfterFirstLog_usesCalendarDayAverage() {
    val p = profile()
    // First log 80 days ago plus yesterday — gaps after logging started count as sparse.
    val foods = listOf(
      foodEntry(calories = 4500, dayOffset = 80),
      foodEntry(calories = 4500, dayOffset = 1),
    )
    val forecast = WeightAnalysisService.compute(emptyList(), foods, p)
    assertTrue(forecast.usesCalendarDayAverage)
    assertEquals(2, forecast.daysOfFoodData)
    assertEquals(4500, forecast.loggedDayAvgCalories)
    assertTrue(forecast.avgDailyCalories < 200)
    assertTrue(forecast.avgDailyCalories > 50)
  }

  @Test
  fun forecast_nineConsecutiveDays_usesLoggedDayAverage() {
    val p = profile()
    val foods = (1..9).map { foodEntry(calories = 2100, dayOffset = it.toLong()) }
    val forecast = WeightAnalysisService.compute(emptyList(), foods, p)
    assertFalse(forecast.usesCalendarDayAverage)
    assertEquals(9, forecast.daysOfFoodData)
    assertEquals(2100, forecast.avgDailyCalories)
    assertEquals(2100, forecast.loggedDayAvgCalories)
  }

  @Test
  fun forecast_excludesTodayFromIntake() {
    val p = profile()
    val foods = listOf(
      foodEntry(calories = 2000, dayOffset = 1),
      foodEntry(calories = 2000, dayOffset = 2),
      foodEntry(calories = 500, dayOffset = 0),
    )
    val forecast = WeightAnalysisService.compute(emptyList(), foods, p)
    assertEquals(2, forecast.daysOfFoodData)
    assertEquals(2000, forecast.loggedDayAvgCalories)
    assertEquals(2000, forecast.avgDailyCalories)
  }

  @Test
  fun seedYear_observedTrendIsSlowLoss_notGain() {
    val p = profile().copy(customCalories = 1900, weightKg = 73.5, goalWeightKg = 70.0)
    val weights = SampleDataGenerators.yearWeights()
    val foods = SampleDataGenerators.foodEntries()
    val forecast = WeightAnalysisService.compute(weights, foods, p)
    val observed = forecast.observedWeeklyChangeKg
    assertNotNull(observed)
    // 78 → 73.5 kg over a year ≈ −0.09 kg/week, not a gain.
    assertTrue("observed weekly kg=$observed", observed!! < 0.0)
    assertTrue("observed weekly kg=$observed should be slower than −0.5", observed > -0.3)
    val result = AdaptiveGoalService.apply(p, weights, foods)
    assertTrue("message=${result.message}", result.changed)
    assertNotNull(result.updatedCalories)
    assertEquals(150, 1900 - result.updatedCalories!!) // hits the −150 cap: slow loss vs −0.5 kg/week target
    assertTrue(result.message, result.message.contains("Observed"))
    assertTrue(result.message, result.message.contains("target"))
  }

  @Test
  fun adaptiveGoal_skipsWhenCaloriesLocked() {
    val p = profile().copy(customCalories = 1900, caloriesLocked = true)
    val foods = (1..5).map { foodEntry(calories = 2200, dayOffset = it.toLong()) }
    val weights = listOf(
      WeightEntry(date = daysAgo(21), weightKg = 80.0),
      WeightEntry(date = daysAgo(14), weightKg = 79.9),
      WeightEntry(date = daysAgo(7), weightKg = 79.85),
      WeightEntry(date = daysAgo(0), weightKg = 79.8),
    )
    val result = AdaptiveGoalService.apply(p, weights, foods)
    assertFalse(result.changed)
    assertEquals(1900, p.effectiveCalories)
  }

  private fun foodEntry(calories: Int, dayOffset: Long): FoodEntry =
    FoodEntry(
      name = "Test",
      calories = calories,
      protein = 0.0,
      carbs = 0.0,
      fat = 0.0,
      timestamp = daysAgo(dayOffset),
      source = FoodSource.MANUAL,
    )
}
