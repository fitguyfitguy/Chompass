package app.chompass.models

import app.chompass.services.KetoCarbRecommendationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UserProfileCalculationTest {
    private fun profile(
    gender: Gender = Gender.MALE,
    ageYears: Int = 30,
    heightCm: Double = 180.0,
    weightKg: Double = 80.0,
    activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    goal: WeightGoal = WeightGoal.MAINTAIN,
    dietMode: DietMode = DietMode.STANDARD,
    bodyFatPercentage: Double? = null,
    weeklyChangeKg: Double? = null,
  ): UserProfile {
    val birthday = LocalDate.now().minusYears(ageYears.toLong())
      .atStartOfDay(ZoneId.systemDefault())
      .toInstant()
    return UserProfile(
      gender = gender,
      birthday = birthday,
      heightCm = heightCm,
      weightKg = weightKg,
      activityLevel = activityLevel,
      goal = goal,
      dietMode = dietMode,
      bodyFatPercentage = bodyFatPercentage,
      weeklyChangeKg = weeklyChangeKg,
    )
  }

  @Test
  fun mifflinStJeor_maleBmr() {
    val p = profile(gender = Gender.MALE, ageYears = 30, heightCm = 180.0, weightKg = 80.0)
    // 10*80 + 6.25*180 - 5*30 + 5 = 1780
    assertEquals(1780.0, p.bmr, 0.5)
  }

  @Test
  fun mifflinStJeor_femaleBmr() {
    val p = profile(gender = Gender.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 65.0)
    // 10*65 + 6.25*165 - 5*30 - 161 = 1370.25
    assertEquals(1370.25, p.bmr, 0.5)
  }

  @Test
  fun katchMcArdle_usesLeanMass() {
    val p = profile(weightKg = 80.0, bodyFatPercentage = 0.20)
    // LBM = 64; BMR = 370 + 21.6*64 = 1752.4
    assertEquals(1752.4, p.bmr, 0.5)
    assertEquals(true, p.usesBodyFatForBMR)
  }

  @Test
  fun tdee_appliesActivityMultiplier() {
    val p = profile(activityLevel = ActivityLevel.MODERATE)
    assertEquals(1780.0 * 1.465, p.tdee, 0.5)
  }

  @Test
  fun calorieAdjustment_loseUses7700KcalPerKg() {
    val p = profile(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5)
    // 0.5 * 7700 / 7 = 550
    assertEquals(-550, p.calorieAdjustment)
    assertEquals(p.tdee.toInt() - 550, p.dailyCalories)
  }

  @Test
  fun calorieAdjustment_gainUses7700KcalPerKg() {
    val p = profile(goal = WeightGoal.GAIN, weeklyChangeKg = 0.25)
    assertEquals(275, p.calorieAdjustment)
  }

  @Test
  fun proteinGoal_includesCuttingBoost() {
    val maintain = profile(goal = WeightGoal.MAINTAIN, activityLevel = ActivityLevel.MODERATE)
    val lose = profile(goal = WeightGoal.LOSE, activityLevel = ActivityLevel.MODERATE)
    assertEquals(true, lose.proteinGoal > maintain.proteinGoal)
  }

  @Test
  fun standardCarbs_fillRemainingCalories() {
    val p = profile(goal = WeightGoal.MAINTAIN, activityLevel = ActivityLevel.SEDENTARY)
    val macroKcal = p.proteinGoal * 4 + p.carbsGoal * 4 + p.fatGoal * 9
    assertTrue(
      "macro kcal ${macroKcal} should be near daily ${p.dailyCalories}",
      kotlin.math.abs(macroKcal - p.dailyCalories) <= 12
    )
  }

  @Test
  fun ketoMode_clampsCarbsAndPreservesFatFloor() {
    val p = profile(
      goal = WeightGoal.LOSE,
      dietMode = DietMode.KETO,
      activityLevel = ActivityLevel.SEDENTARY,
      weeklyChangeKg = 0.5,
    )
    assertTrue(p.carbsGoal >= KetoCarbRecommendationService.MIN_NET_CARBS_G)
    assertTrue(p.carbsGoal <= KetoCarbRecommendationService.MAX_NET_CARBS_G)
    assertTrue(p.fatGoal >= 45)
    assertTrue(p.proteinGoal >= 60)
  }

  @Test
  fun withGoal_maintainDropsDeficitAndClearsPace() {
    val lose = profile(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5).copy(
      customCalories = 1800,
      goalWeightKg = 75.0,
    )
    val maintain = lose.withGoal(WeightGoal.MAINTAIN)
    assertEquals(WeightGoal.MAINTAIN, maintain.goal)
    assertEquals(null, maintain.weeklyChangeKg)
    assertEquals(null, maintain.goalWeightKg)
    assertEquals(maintain.tdee.toInt(), maintain.effectiveCalories)
    assertEquals(0, maintain.calorieAdjustment)
  }

  @Test
  fun withGoal_loseSeedsPaceAndAppliesDeficit() {
    val maintain = profile(goal = WeightGoal.MAINTAIN, weeklyChangeKg = null)
    val lose = maintain.withGoal(WeightGoal.LOSE)
    assertEquals(WeightGoal.LOSE, lose.goal)
    assertEquals(0.5, lose.weeklyChangeKg)
    assertEquals(lose.tdee.toInt() - 550, lose.effectiveCalories)
  }

  @Test
  fun withGoal_clearsMismatchedTargetWeight() {
    val lose = profile(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5).copy(goalWeightKg = 90.0)
    val stillLose = lose.withGoal(WeightGoal.LOSE)
    assertEquals(null, stillLose.goalWeightKg)
    val gain = profile(goal = WeightGoal.MAINTAIN).copy(goalWeightKg = 70.0)
    assertEquals(null, gain.withGoal(WeightGoal.GAIN).goalWeightKg)
  }
}
