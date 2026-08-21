package app.chompass.models

import app.chompass.services.KetoCarbRecommendationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  @Test
  fun applyingAiGoals_keepsLockedCalories() {
    val p = profile().copy(
      customCalories = 1900,
      customProtein = 180,
      customCarbs = 150,
      customFat = 60,
      caloriesLocked = true,
    )
    val next = p.applyingAiGoals(calories = 1500, protein = 120, carbs = 100, fat = 40)
    assertEquals(1900, next.effectiveCalories)
    assertTrue(next.caloriesLocked)
  }

  @Test
  fun applyingAiGoals_keepsAiMacrosWhenTheyAlreadyFillLockedCalories() {
    val p = profile().copy(
      customCalories = 1900,
      customProtein = 180,
      customCarbs = 150,
      customFat = 60,
      caloriesLocked = true,
    )
    // 140*4 + 180*4 + 70*9 = 1910, within 30 kcal of 1900.
    val next = p.applyingAiGoals(calories = 1500, protein = 140, carbs = 180, fat = 70)
    assertEquals(1900, next.effectiveCalories)
    assertEquals(140, next.effectiveProtein)
    assertEquals(180, next.effectiveCarbs)
  }

  @Test
  fun goalLockPromptSection_namesLockedCalories() {
    val p = profile().copy(customCalories = 1900, caloriesLocked = true)
    val section = p.goalLockPromptSection()
    assertTrue(section.contains("Calories locked at 1900"))
    assertTrue(section.contains("Do not lower calories"))
  }

  @Test
  fun applyingAiGoals_writesUnlockedCalories() {
    val p = profile().copy(
      customCalories = 1900,
      customProtein = 180,
      customCarbs = 150,
      customFat = 60,
      caloriesLocked = false,
    )
    val next = p.applyingAiGoals(calories = 2100, protein = 140, carbs = 180, fat = 70)
    assertEquals(2100, next.effectiveCalories)
    assertEquals(140, next.effectiveProtein)
    assertFalse(next.caloriesLocked)
  }

  @Test
  fun applyingAiGoals_clampsUnlockedCaloriesToFloor() {
    val p = profile(
      gender = Gender.FEMALE,
      ageYears = 60,
      heightCm = 155.0,
      weightKg = 52.0,
      activityLevel = ActivityLevel.SEDENTARY,
      goal = WeightGoal.LOSE,
      weeklyChangeKg = 0.5,
    ).copy(customCalories = 1800)
    val next = p.applyingAiGoals(calories = 800, protein = 80, carbs = 80, fat = 40)
    assertEquals(CalorieSafety.floorKcal(p.bmr), next.effectiveCalories)
    assertTrue(next.effectiveCalories >= 1200)
  }

  @Test
  fun applyingAiGoals_keepsLockedProtein() {
    val p = profile().copy(
      customCalories = 2000,
      customProtein = 180,
      customCarbs = 150,
      customFat = 60,
      lockedMacros = setOf(AutoBalanceMacro.PROTEIN),
    )
    val next = p.applyingAiGoals(calories = 2100, protein = 100, carbs = 200, fat = 70)
    assertEquals(180, next.effectiveProtein)
    assertEquals(2100, next.effectiveCalories)
  }
}
