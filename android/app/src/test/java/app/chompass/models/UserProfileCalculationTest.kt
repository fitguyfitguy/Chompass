package app.chompass.models

import app.chompass.services.KetoCarbRecommendationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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
  fun useBodyFatInBMR_falseFallsBackToMifflin() {
    val katch = profile(weightKg = 80.0, bodyFatPercentage = 0.50)
    val msj = katch.copy(useBodyFatInBMR = false)
    assertTrue(katch.usesBodyFatForBMR)
    assertFalse(msj.usesBodyFatForBMR)
    assertTrue(msj.bmr > katch.bmr)
  }

  @Test
  fun katchStale_fallsBackToMifflin() {
    val at = Instant.now()
    val dated = profile(weightKg = 80.0, bodyFatPercentage = 0.20)
      .copy(bodyFatUpdatedAt = at.minus(java.time.Duration.ofDays(120)))
    assertFalse(dated.usesBodyFatForBMR)
    // Mifflin-St Jeor: 10*80 + 6.25*180 - 5*30 - 161 + 166 = 1780
    assertEquals(1780.0, dated.bmr, 0.5)
    assertEquals(120L, dated.bodyFatAgeDays(at))
  }

  @Test
  fun katchFreshDated_usesKatch() {
    val at = Instant.now()
    val dated = profile(weightKg = 80.0, bodyFatPercentage = 0.20)
      .copy(bodyFatUpdatedAt = at.minus(java.time.Duration.ofDays(30)))
    assertTrue(dated.usesBodyFatForBMR)
    // Katch-McArdle: 370 + 21.6*64 = 1752.4
    assertEquals(1752.4, dated.bmr, 0.5)
    assertFalse(dated.isBodyFatStale(at))
  }

  @Test
  fun katchLegacyNullDate_usesKatch() {
    val legacy = profile(weightKg = 80.0, bodyFatPercentage = 0.20)
    assertTrue(legacy.bodyFatUpdatedAt == null)
    assertTrue(legacy.usesBodyFatForBMR)
    assertFalse(legacy.isBodyFatStale())
    assertEquals(null, legacy.bodyFatAgeDays())
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
  fun proteinGoal_withBodyFatCapsAtLeanMass() {
    val withBf = profile(weightKg = 80.0, bodyFatPercentage = 0.30, activityLevel = ActivityLevel.MODERATE)
    val noBf = profile(weightKg = 80.0, bodyFatPercentage = null, activityLevel = ActivityLevel.MODERATE)
    // Total-weight rate = 1.6*80 = 128; with a valid 30% reading the 2.2 g/kg
    // LBM cap wins: 2.2*56 = 123.2 → 123.
    assertEquals(123, withBf.proteinGoal)
    assertEquals(128, noBf.proteinGoal)
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
    // AI macros take the budget: P140/F70 kept, carbs absorb to 1900 exactly.
    val next = p.applyingAiGoals(calories = 1500, protein = 140, carbs = 180, fat = 70)
    assertEquals(1900, next.effectiveCalories)
    assertEquals(140, next.effectiveProtein)
    assertEquals(70, next.effectiveFat)
    assertEquals(177, next.effectiveCarbs)
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
    // Fat floor 48 kept the AI fat at 70; carbs absorb to 2100 (sum 2098).
    assertEquals(70, next.effectiveFat)
    assertEquals(227, next.effectiveCarbs)
    assertFalse(next.caloriesLocked)
  }

  @Test
  fun applyCaloriesEdit_standardCut_keepsProteinAndFat() {
    val p = profile().copy(
      customCalories = 2100,
      customProtein = 150,
      customCarbs = 215,
      customFat = 60,
    )
    val next = p.applyCaloriesEdit(1500)
    assertEquals(1500, next.effectiveCalories)
    assertEquals(150, next.effectiveProtein)
    assertEquals(60, next.effectiveFat)
    assertEquals(90, next.effectiveCarbs)
    assertEquals(1500, 4 * next.effectiveProtein + 4 * next.effectiveCarbs + 9 * next.effectiveFat)
  }

  @Test
  fun applyCaloriesEdit_raisesFatToFloor() {
    val p = profile().copy(
      customCalories = 2000,
      customProtein = 150,
      customCarbs = 215,
      customFat = 30,
    )
    val next = p.applyCaloriesEdit(2000)
    assertEquals(48, next.effectiveFat)
    assertEquals(242, next.effectiveCarbs)
    assertEquals(150, next.effectiveProtein)
  }

  @Test
  fun applyCaloriesEdit_ketoFatFills() {
    val p = profile(dietMode = DietMode.KETO).copy(
      customCalories = 1500,
      customProtein = 150,
      customCarbs = 30,
      customFat = 70,
    )
    val next = p.applyCaloriesEdit(1500)
    assertEquals(150, next.effectiveProtein)
    assertEquals(30, next.effectiveCarbs)
    assertEquals(86, next.effectiveFat)
  }

  @Test
  fun applyCaloriesEdit_lockedProteinStays() {
    val p = profile().copy(
      customCalories = 2000,
      customProtein = 180,
      customCarbs = 150,
      customFat = 60,
      lockedMacros = setOf(AutoBalanceMacro.PROTEIN),
    )
    val next = p.applyCaloriesEdit(1500)
    assertEquals(180, next.effectiveProtein)
    assertEquals(1500, next.effectiveCalories)
    assertEquals(1500, 4 * next.effectiveProtein + 4 * next.effectiveCarbs + 9 * next.effectiveFat)
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

  @Test
  fun highBodyFat_capsProteinAtLeanMass() {
    val p = profile(
      ageYears = 40,
      heightCm = 180.0,
      weightKg = 120.0,
      bodyFatPercentage = 0.40,
      goal = WeightGoal.LOSE,
      weeklyChangeKg = 0.5,
    )
    // Katch: 370 + 21.6*0.6*120 = 1925.2; TDEE ×1.465 = 2820.42; lose 0.5 → 2270.
    assertEquals(1925.2, p.bmr, 0.5)
    // Total-weight rate (1.6+0.2)*120 = 216 caps at 2.4 g/kg LBM = 172.8 → 172.
    assertEquals(172, p.proteinGoal)
    // Fat floor: max(0.6*120 = 72, 20% of 2270 ≈ 50) = 72.
    assertEquals(72, p.fatGoal)
  }

  @Test
  fun standardFatGoal_meets20PercentOfCalories() {
    val p = profile(
      weightKg = 60.0,
      heightCm = 170.0,
      activityLevel = ActivityLevel.SEDENTARY,
      goal = WeightGoal.MAINTAIN,
    )
    // 0.6 g/kg = 36 g; 20% of ~1821 kcal ≈ 40 g — the calorie leg wins.
    assertTrue(p.fatGoal > 36)
    assertEquals(maxOf(36, kotlin.math.round(0.2 * p.dailyCalories / 9.0).toInt()), p.fatGoal)
  }

  @Test
  fun invalidBodyFatFraction_fallsBackToMifflin() {
    val typo = profile(bodyFatPercentage = 28.0) // percent-vs-fraction typo
    assertFalse(typo.usesBodyFatForBMR)
    assertEquals(profile().bmr, typo.bmr, 1e-9)
    // No valid reading → no LBM cap on protein either.
    assertEquals(profile().proteinGoal, typo.proteinGoal)
    assertFalse(profile(bodyFatPercentage = 0.01).usesBodyFatForBMR)
    assertFalse(profile(bodyFatPercentage = 0.70).usesBodyFatForBMR)
  }

  @Test
  fun applyingAiGoals_keepsProteinRateMode() {
    val p = profile().copy(
      customCalories = 2500,
      proteinTargetMode = ProteinTargetMode.G_PER_KG_TOTAL,
      proteinGramsPerKg = 1.8,
    )
    val next = p.applyingAiGoals(calories = 2100, protein = 140, carbs = 180, fat = 70)
    assertEquals(ProteinTargetMode.G_PER_KG_TOTAL, next.proteinTargetMode)
    assertNull(next.customProtein)
    assertNotNull(next.proteinGramsPerKg)
    // The AI grams re-enter as the per-kg rate, so effective protein matches.
    assertEquals(140, next.effectiveProtein)
    assertEquals(1.75, next.proteinGramsPerKg!!, 1e-9)
  }

  @Test
  fun pinCalories_materializesEffectiveAndLocks() {
    val p = profile()
    val pinned = p.pinCalories()
    assertEquals(p.effectiveCalories, pinned.customCalories)
    assertEquals(p.effectiveCalories, pinned.effectiveCalories)
    assertTrue(pinned.caloriesLocked)
  }

  @Test
  fun unpinCalories_clearsLockKeepsNumber() {
    val p = profile().copy(customCalories = 1900, caloriesLocked = true)
    val next = p.unpinCalories()
    assertFalse(next.caloriesLocked)
    assertEquals(1900, next.effectiveCalories)
  }

  @Test
  fun pinMacro_pinsProteinGrams() {
    val p = profile()
    val pinned = p.pinMacro(AutoBalanceMacro.PROTEIN)
    assertEquals(p.effectiveProtein, pinned.customProtein)
    assertTrue(pinned.isMacroLocked(AutoBalanceMacro.PROTEIN))
    val kept = pinned.applyingAiGoals(calories = 2100, protein = 100, carbs = 200, fat = 70)
    assertEquals(p.effectiveProtein, kept.effectiveProtein)
  }

  @Test
  fun unpinMacro_clearsLockKeepsGrams() {
    val p = profile().copy(
      customProtein = 180,
      lockedMacros = setOf(AutoBalanceMacro.PROTEIN),
    )
    val next = p.unpinMacro(AutoBalanceMacro.PROTEIN)
    assertFalse(next.isMacroLocked(AutoBalanceMacro.PROTEIN))
    assertEquals(180, next.effectiveProtein)
  }

  @Test
  fun pinMacro_thirdLockIsNoOp() {
    val p = profile().copy(
      customProtein = 180,
      customCarbs = 200,
      lockedMacros = setOf(AutoBalanceMacro.PROTEIN, AutoBalanceMacro.CARBS),
    )
    val next = p.pinMacro(AutoBalanceMacro.FAT)
    assertEquals(p, next)
    assertFalse(next.isMacroLocked(AutoBalanceMacro.FAT))
  }
}
