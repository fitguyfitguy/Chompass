package app.chompass.models

/**
 * Canonical formula text fragments for AI goal prompts — must stay aligned with deterministic code.
 * Unit-tested for parity with [ActivityLevel] and [NutritionConstants].
 */
object GoalFormulaReference {
    fun activityMultipliersLine(): String =
        ActivityLevel.entries.joinToString(", ") { level ->
            "${level.name.lowercase()} ${formatMultiplier(level.multiplier)}"
        }

    fun proteinPerKgLine(): String =
        ActivityLevel.entries.joinToString(", ") { level ->
            "${level.name.lowercase()} ${formatMultiplier(level.proteinPerKg)}"
        }

    fun calorieAdjustmentLine(): String {
        val kcalPerKg = NutritionConstants.KCAL_PER_KG_BODY_MASS.toInt()
        return "lose: -(weeklyChangeKg*$kcalPerKg/7); gain: +(weeklyChangeKg*$kcalPerKg/7)"
    }

    fun moderateActivityMultiplierRationale(): String =
        "Moderate uses ${formatMultiplier(ActivityLevel.MODERATE.multiplier)} (between FAO/WHO light 1.375 " +
            "and moderate 1.55) for desk-active users who exercise a few times per week."

    private fun formatMultiplier(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
