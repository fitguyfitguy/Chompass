package org.codeberg.fitguy.nofud.services

import org.codeberg.fitguy.nofud.models.ActivityLevel
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.models.Gender
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WeightEntry
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    val foods = (1..5).map { foodEntry(calories = 2200, dayOffset = it.toLong()) }
    val weights = listOf(
      WeightEntry(date = daysAgo(21), weightKg = 80.0),
      WeightEntry(date = daysAgo(14), weightKg = 79.9),
      WeightEntry(date = daysAgo(7), weightKg = 79.85),
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
    assertTrue(result.message.contains("4"))
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
  fun forecast_sparseLogging_usesCalendarDayAverage() {
    val p = profile()
    val tdee = p.tdee.toInt()
    // Two high-calorie days in a 90-day window — logged-day avg would be ~3000.
    val foods = listOf(
      foodEntry(calories = 4500, dayOffset = 1),
      foodEntry(calories = 4500, dayOffset = 2),
    )
    val forecast = WeightAnalysisService.compute(emptyList(), foods, p)
    assertTrue(forecast.usesCalendarDayAverage)
    assertTrue(forecast.avgDailyCalories < 200)
    assertEquals(2, forecast.daysOfFoodData)
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
