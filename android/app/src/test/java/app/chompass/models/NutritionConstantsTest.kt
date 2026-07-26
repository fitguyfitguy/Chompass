package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionConstantsTest {
    @Test
  fun kcalPerKg_matchesDocumented7700() {
    assertEquals(7700.0, NutritionConstants.KCAL_PER_KG_BODY_MASS, 0.0)
  }

  @Test
  fun dailyAdjustment_halfKgPerWeek() {
    assertEquals(550, NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(0.5))
    assertEquals(-550, NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(-0.5))
  }
}
