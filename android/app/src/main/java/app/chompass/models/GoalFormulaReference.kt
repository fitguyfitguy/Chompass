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

    fun calorieSafetyLine(): String =
        "Never set calories below this user's BMR or ${CalorieSafety.ABSOLUTE_FLOOR_KCAL}. " +
            "If the weekly pace would break that floor, shrink the deficit instead of lowering the floor. " +
            "800 kcal is a medically supervised VLCD, not an app target."

    /** When a valid body-fat reading exists, protein is capped at lean mass (UserProfile). */
    fun proteinLbmCapLine(): String =
        "cap protein at 2.2 g/kg lean mass (2.4 g/kg while losing)"

    /** Standard fat floor: 0.6 g/kg bodyweight, at least 20% of calories (UserProfile). */
    fun fatTargetLine(): String =
        "at least max(0.6 g/kg bodyweight, 20% of calories)"

    /**
     * "1.55" / "1.375" / "1" — the canonical multiplier formatting (no
     * trailing zeros). Shared with UI surfaces that mirror the prompt's
     * numbers (RecalcResultSheet), so display and prompt never diverge.
     */
    internal fun formatMultiplier(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
