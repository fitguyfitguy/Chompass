package app.chompass.models

/**
 * Shared nutrition and energy-balance constants used across goal setting, forecasting,
 * and adaptive adjustments. Single source of truth — see [docs/CALCULATION_METHODS.md].
 */
object NutritionConstants {
    /**
     * Approximate energy content of 1 kg body-mass change (kcal).
     * Aligned with ISSN guidance and Hall et al. (2011); ~3,500 kcal per lb (Wishnofsky 1958).
     * Used for goal pace, weight forecasts, and adaptive calorie corrections.
     */
    const val KCAL_PER_KG_BODY_MASS = 7_700.0

    /**
     * Body-fat readings older than this many days no longer feed Katch-McArdle; BMR falls
     * back to Mifflin-St Jeor. Undated (legacy) readings count as fresh.
     */
    const val BODY_FAT_RECENCY_DAYS = 90L

    /** Valid body-fat fraction range. Outside it, readings are ignored for BMR/macros
     *  (a percent-vs-fraction typo like 28.0 must not halve someone's BMR); the
     *  repository clamps stored entries into this window instead. */
    const val BODY_FAT_MIN_FRACTION = 0.03
    const val BODY_FAT_MAX_FRACTION = 0.65

    /**
     * Measured lean (fat-free) mass readings older than this many days no longer feed
     * Katch-McArdle; BMR falls back to the body-fat path, then Mifflin-St Jeor.
     * Undated readings count as fresh (same convention as body fat).
     */
    const val LEAN_MASS_RECENCY_DAYS = 90L

    /** Plausible lean-mass window in kg. A reading outside it is treated as absent —
     *  a typo (e.g. 6.1 for 61) must not rewrite someone's BMR. */
    const val LEAN_MASS_MIN_KG = 20.0
    const val LEAN_MASS_MAX_KG = 250.0

    /** Daily calorie adjustment (signed) for a weekly body-mass change rate in kg/week. */
    fun dailyCalorieAdjustmentForWeeklyRateKg(weeklyChangeKg: Double): Int =
        (weeklyChangeKg * KCAL_PER_KG_BODY_MASS / 7.0).toInt()
}
