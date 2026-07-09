package org.codeberg.fitguy.nofud.services

import org.codeberg.fitguy.nofud.models.ActivityLevel
import org.codeberg.fitguy.nofud.models.Gender
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class KetoCarbRecommendationServiceTest {

  private fun profile(
    goal: WeightGoal = WeightGoal.MAINTAIN,
    activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    weeklyChangeKg: Double? = null,
    bodyFatPercentage: Double? = null,
  ) = UserProfile(
    goal = goal,
    activityLevel = activityLevel,
    weeklyChangeKg = weeklyChangeKg,
    bodyFatPercentage = bodyFatPercentage,
    birthday = LocalDate.now().minusYears(30).atStartOfDay(ZoneId.systemDefault()).toInstant(),
  )

  @Test
  fun maintainModerate_isWithinRange() {
    assertEquals(32, KetoCarbRecommendationService.recommendNetCarbs(profile()))
  }

  @Test
  fun loseSedentary_clampsAtMinimum() {
    // 25 - 2 = 23, within 20..50
    assertEquals(23, KetoCarbRecommendationService.recommendNetCarbs(
      profile(goal = WeightGoal.LOSE, activityLevel = ActivityLevel.SEDENTARY)
    ))
  }

  @Test
  fun gainExtraActive_increasesCarbs() {
    assertEquals(48, KetoCarbRecommendationService.recommendNetCarbs(
      profile(goal = WeightGoal.GAIN, activityLevel = ActivityLevel.EXTRA_ACTIVE)
    ))
  }

  @Test
  fun aggressiveLossAndHighBodyFat_reducesFurther() {
    // 25 - 2 - 5 - 3 = 15 -> clamped to 20
    assertEquals(20, KetoCarbRecommendationService.recommendNetCarbs(
      profile(
        goal = WeightGoal.LOSE,
        activityLevel = ActivityLevel.SEDENTARY,
        weeklyChangeKg = 0.8,
        bodyFatPercentage = 0.35,
      )
    ))
  }

  @Test
  fun manualClamp_respectsBounds() {
    assertEquals(20, KetoCarbRecommendationService.clampManualNetCarbs(5))
    assertEquals(50, KetoCarbRecommendationService.clampManualNetCarbs(80))
    assertEquals(35, KetoCarbRecommendationService.clampManualNetCarbs(35))
  }
}
