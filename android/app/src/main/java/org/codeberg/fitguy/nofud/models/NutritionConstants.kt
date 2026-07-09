package org.codeberg.fitguy.nofud.models

/**
 * Shared nutrition and energy-balance constants used across goal setting, forecasting,
 * and adaptive adjustments. Single source of truth — see [CALCULATION_METHODS.md] at repo root.
 */
object NutritionConstants {
    /**
     * Approximate energy content of 1 kg body-mass change (kcal).
     * Aligned with ISSN guidance and Hall et al. (2011); ~3,500 kcal per lb (Wishnofsky 1958).
     * Used for goal pace, weight forecasts, and adaptive calorie corrections.
     */
    const val KCAL_PER_KG_BODY_MASS = 7_700.0

    /** Daily calorie adjustment (signed) for a weekly body-mass change rate in kg/week. */
    fun dailyCalorieAdjustmentForWeeklyRateKg(weeklyChangeKg: Double): Int =
        (weeklyChangeKg * KCAL_PER_KG_BODY_MASS / 7.0).toInt()
}
