package org.codeberg.fitguy.nofud.models

import org.codeberg.fitguy.nofud.services.KetoCarbRecommendationService
import org.codeberg.fitguy.nofud.services.WeightForecastMath
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Golden vectors for end-to-end deterministic goal math. Values are hand-computed from the
 * formulas documented in CALCULATION_METHODS.md — update both when formulas change.
 */
class CalculationGoldenScenariosTest {

  private fun atAge(years: Int) =
    LocalDate.now().minusYears(years.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()

  @Test
  fun golden_maleModerateMaintain_mifflinPath() {
    val p = UserProfile(
      gender = Gender.MALE,
      birthday = atAge(30),
      heightCm = 180.0,
      weightKg = 80.0,
      activityLevel = ActivityLevel.MODERATE,
      goal = WeightGoal.MAINTAIN,
    )
    assertEquals(1780.0, p.bmr, 0.5)
    assertEquals(2607.7, p.tdee, 0.5)
    assertEquals(0, p.calorieAdjustment)
    assertEquals(2607, p.dailyCalories)
    assertEquals(828, p.estimatedDailyActiveCalories) // round(2607.7 - 1780)
    assertEquals(1779, p.sedentaryCalorieBudget()) // 2607 - 828
    assertEquals(2607, p.sedentaryCalorieBudget() + p.estimatedDailyActiveCalories)
    assertEquals(128, p.proteinGoal) // 1.6 * 80
    assertEquals(48, p.fatGoal) // 0.6 * 80
  }

  @Test
  fun golden_femaleCut_katchPath() {
    val p = UserProfile(
      gender = Gender.FEMALE,
      birthday = atAge(35),
      heightCm = 165.0,
      weightKg = 70.0,
      bodyFatPercentage = 0.28,
      activityLevel = ActivityLevel.LIGHT,
      goal = WeightGoal.LOSE,
      weeklyChangeKg = 0.5,
    )
    // Katch: LBM=50.4, BMR=1458.64
    assertEquals(1458.64, p.bmr, 1.0)
    assertEquals(-550, p.calorieAdjustment)
    // Protein: (1.2+0.2)/0.72 g per kg BW × 50.4 kg LBM ≈ 98
    assertEquals(98, p.proteinGoal)
  }

  @Test
  fun golden_ketoLose_sedentaryCarbsClamped() {
    val p = UserProfile(
      birthday = atAge(40),
      weightKg = 95.0,
      activityLevel = ActivityLevel.SEDENTARY,
      goal = WeightGoal.LOSE,
      dietMode = DietMode.KETO,
      weeklyChangeKg = 0.8,
      bodyFatPercentage = 0.32,
    )
    assertEquals(20, KetoCarbRecommendationService.recommendNetCarbs(p))
    assertEquals(20, p.carbsGoal)
    assertEquals(true, p.fatGoal >= 45)
  }

  @Test
  fun golden_gainExtraActive_proteinAndCalories() {
    val p = UserProfile(
      gender = Gender.MALE,
      birthday = atAge(25),
      heightCm = 185.0,
      weightKg = 75.0,
      activityLevel = ActivityLevel.EXTRA_ACTIVE,
      goal = WeightGoal.GAIN,
      weeklyChangeKg = 0.25,
    )
    assertEquals(275, p.calorieAdjustment)
    assertEquals(165, p.proteinGoal) // 2.2 * 75
  }

  @Test
  fun golden_sparseLogging_calendarDayAverage() {
    val intake = WeightForecastMath.averageDailyIntake(
      totalCalories = 6000,
      loggedDays = 3,
      calendarDaysInWindow = 90,
    )
    assertEquals(66, intake.avgDailyCalories)
    assertEquals(true, intake.usesCalendarDayAverage)
  }

  @Test
  fun golden_denseLogging_loggedDayAverage() {
    val intake = WeightForecastMath.averageDailyIntake(
      totalCalories = 45_000,
      loggedDays = 45,
      calendarDaysInWindow = 90,
    )
    assertEquals(1000, intake.avgDailyCalories)
    assertEquals(false, intake.usesCalendarDayAverage)
  }
}
