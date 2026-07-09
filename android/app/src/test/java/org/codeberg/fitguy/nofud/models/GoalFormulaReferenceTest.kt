package org.codeberg.fitguy.nofud.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalFormulaReferenceTest {

  @Test
  fun activityMultipliersLine_matchesActivityLevelEnum() {
    val line = GoalFormulaReference.activityMultipliersLine()
    ActivityLevel.entries.forEach { level ->
      assertTrue(
        "missing ${level.name.lowercase()} ${level.multiplier}",
        line.contains("${level.name.lowercase()} ${format(level.multiplier)}"),
      )
    }
  }

  @Test
  fun proteinPerKgLine_matchesActivityLevelEnum() {
    val line = GoalFormulaReference.proteinPerKgLine()
    ActivityLevel.entries.forEach { level ->
      assertTrue(
        "missing protein ${level.name.lowercase()}",
        line.contains("${level.name.lowercase()} ${format(level.proteinPerKg)}"),
      )
    }
  }

  @Test
  fun calorieAdjustmentLine_usesNutritionConstants() {
    val kcal = NutritionConstants.KCAL_PER_KG_BODY_MASS.toInt()
    assertEquals(
      "lose: -(weeklyChangeKg*$kcal/7); gain: +(weeklyChangeKg*$kcal/7)",
      GoalFormulaReference.calorieAdjustmentLine(),
    )
  }

  @Test
  fun moderateRationale_documents1465Choice() {
    val rationale = GoalFormulaReference.moderateActivityMultiplierRationale()
    assertTrue(rationale.contains("1.465"))
    assertTrue(rationale.contains("1.375"))
    assertTrue(rationale.contains("1.55"))
    assertEquals(1.465, ActivityLevel.MODERATE.multiplier, 0.0)
  }

  private fun format(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
