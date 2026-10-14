package app.chompass.models

import app.chompass.services.ai.GoalCalculation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Edit-before-apply Recalculate (confirm sheet): the preview the sheet shows is
 * exactly what Apply persists — [UserProfile.applyingAiGoalsToPlan] of the
 * (possibly edited) result. `editPendingRecalc` recomputes that expression on
 * the edited result; `applyPendingRecalc` writes the preview. These tests pin
 * the shared expression: edited calories land, locked fields survive, and
 * day-type rows follow the implied kcal delta when the model sends no explicit
 * rows (the fallback both paths share).
 */
class RecalcConfirmPlanTest {
    private fun birthday(): Instant =
        LocalDate.now().minusYears(30).atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun plan(): MacroPlan = MacroPlan(
        enabled = true,
        profiles = listOf(
            MacroDayProfile(id = "training", name = "Training", calories = 2600, proteinG = 160, carbsG = 260, fatG = 70),
            MacroDayProfile(id = "rest", name = "Rest", calories = 2100, proteinG = 140, carbsG = 180, fatG = 60),
        ),
    )

    private fun profile(macroPlan: MacroPlan?, caloriesLocked: Boolean = false): UserProfile =
        UserProfile(
            gender = Gender.MALE,
            birthday = birthday(),
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = ActivityLevel.MODERATE,
            goal = WeightGoal.MAINTAIN,
            macroPlan = macroPlan,
            caloriesLocked = caloriesLocked,
        )

    @Test
    fun editedPreview_isWhatApplyPersists_unlocked() {
        val before = profile(plan())
        val result = GoalCalculation(calories = 2400, protein = 150, carbs = 200, fat = 60)
        // editPendingRecalc(CALORIES, 2100) → result.copy(calories = 2100)
        val editedResult = result.copy(calories = 2100)
        val preview = before.applyingAiGoalsToPlan(editedResult)
        assertEquals(2100, preview.effectiveCalories)
        // Day types shift by the implied kcal delta (vs the before profile's
        // effective goal), each within CAL-SAFE bounds — the shared fallback.
        val delta = preview.effectiveCalories - before.effectiveCalories
        assertEquals(
            CalorieSafety.clampAuto(2600 + delta, before.bmr, before.tdee),
            preview.macroPlan?.profileById("training")?.calories,
        )
        assertEquals(
            CalorieSafety.clampAuto(2100 + delta, before.bmr, before.tdee),
            preview.macroPlan?.profileById("rest")?.calories,
        )
        // The unedited apply path writes the model's number unchanged.
        assertEquals(2400, before.applyingAiGoalsToPlan(result).effectiveCalories)
    }

    @Test
    fun lockedCalories_surviveEditPreview() {
        val before = profile(plan(), caloriesLocked = true)
        val result = GoalCalculation(calories = 999, protein = 150, carbs = 200, fat = 60)
        val preview = before.applyingAiGoalsToPlan(result)
        // Locked base calories keep their stored value; the model number is ignored.
        assertEquals(before.effectiveCalories, preview.effectiveCalories)
        assertTrue(preview.macroPlan?.profileById("training")?.calories != null)
        // Day types are base-scoped: with locked base calories the plan is untouched.
        assertEquals(
            before.macroPlan?.profileById("training")?.calories,
            preview.macroPlan?.profileById("training")?.calories,
        )
    }
}
