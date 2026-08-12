package app.chompass.models

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deterministic water-goal + reminder math for the dynamic water feature
 * (issue #3). Pure functions only; no prefs, no clock. Callers resolve the
 * inputs (base goal, expected high °C, profile activity, food grams, drunk
 * today, awake window) and pass them in, so the same numbers drive Settings
 * previews, the Home ring, the widget, and the reminder chain.
 *
 * Registered in docs/CALCULATION_METHODS.md as WATER-DYN-A/B/C; full design in
 * docs/WATER_DYNAMIC_GOAL_DESIGN.md.
 */
object WaterGoalCalculator {
    // -- WATER-DYN-A: gross drink goal ----------------------------------

    /** EFSA 2010 adult adequate intake ≈ 2.0–2.5 L/day → 35 ml/kg midpoint. */
    const val WEIGHT_BASE_ML_PER_KG = 35

    /** °C below which the temperature factor stays 1.0 (no reduction). */
    const val TEMP_FACTOR_BASE_C = 25

    /** +4 % per °C above [TEMP_FACTOR_BASE_C]. */
    const val TEMP_FACTOR_PER_DEGREE = 0.04

    /** Cap on the temperature factor (≈ 40 °C) so heatwaves never exceed +60 %. */
    const val MAX_TEMP_FACTOR = 1.6

    /** Base-goal sources; stored in `waterBaseSource`. */
    const val BASE_SOURCE_WEIGHT = "weight"
    const val BASE_SOURCE_MANUAL = "manual"

    /** 35 ml/kg body-weight base, rounded to the nearest 50 ml. */
    fun weightBaseGoalMl(weightKg: Double): Int = round50(weightKg * WEIGHT_BASE_ML_PER_KG)

    /** Resolves the base goal from the stored source; falls back to the manual
     *  goal when the weight source has no profile weight. */
    fun baseGoalMl(source: String, weightKg: Double?, manualGoalMl: Int): Int =
        if (source == BASE_SOURCE_WEIGHT) weightKg?.let { weightBaseGoalMl(it) } ?: manualGoalMl
        else manualGoalMl

    /**
     * 1 + 4 % per °C above 25 °C, clamped to [1.0, 1.6]. Temperatures at or
     * below 25 °C never reduce the goal (factor 1.0).
     */
    fun tempFactor(expectedHighC: Int): Double =
        (1 + TEMP_FACTOR_PER_DEGREE * max(0, expectedHighC - TEMP_FACTOR_BASE_C))
            .coerceIn(1.0, MAX_TEMP_FACTOR)

    /** Water multiplier per profile activity level (TDEE multipliers are
     *  BMR-relative and not reusable here). */
    fun activityFactor(level: ActivityLevel): Double = when (level) {
        ActivityLevel.SEDENTARY -> 1.0
        ActivityLevel.LIGHT -> 1.1
        ActivityLevel.MODERATE -> 1.2
        ActivityLevel.ACTIVE -> 1.3
        ActivityLevel.VERY_ACTIVE -> 1.4
        ActivityLevel.EXTRA_ACTIVE -> 1.5
    }

    /** Gross drink goal = base × temperature factor × activity factor, 50 ml steps. */
    fun grossGoalMl(baseGoalMl: Int, expectedHighC: Int, activityFactor: Double): Int =
        round50(baseGoalMl * tempFactor(expectedHighC) * activityFactor)

    // -- WATER-DYN-B: food-water subtraction (optional, coarse) ----------

    /** Coarse food water content: ~60 % of diary grams by mass. */
    const val FOOD_WATER_RATIO = 0.6

    /** Hard cap on the food-water credit so a huge diary never zeroes the goal. */
    const val MAX_FOOD_WATER_ML = 1_000

    /** Absolute floor for the net drink goal. */
    const val MIN_NET_GOAL_ML = 1_000

    /** Estimated water contributed by today's food, 50 ml steps, capped at 1 L. */
    fun foodWaterMl(foodGramsToday: Int): Int {
        if (foodGramsToday <= 0) return 0
        return minOf(round50(foodGramsToday * FOOD_WATER_RATIO), MAX_FOOD_WATER_ML)
    }

    /** Net drink goal after the food-water credit, never below 1 L. */
    fun netGoalMl(grossGoalMl: Int, foodWaterMl: Int): Int =
        max(grossGoalMl - foodWaterMl, MIN_NET_GOAL_ML)

    /** Today's net drink goal from the stored inputs. The single entry point
     *  Home / widget / Settings preview / reminder chain all share. */
    fun dailyNetGoalMl(
        baseSource: String,
        weightKg: Double?,
        manualBaseMl: Int,
        expectedHighC: Int,
        activityLevel: ActivityLevel,
        useProfileActivity: Boolean,
        foodGramsToday: Int,
        foodWaterEnabled: Boolean,
    ): Int = breakdown(
        baseSource, weightKg, manualBaseMl, expectedHighC,
        activityLevel, useProfileActivity, foodGramsToday, foodWaterEnabled,
    ).netGoalMl

    /** Full breakdown of today's goal. The Settings preview shows each input. */
    fun breakdown(
        baseSource: String,
        weightKg: Double?,
        manualBaseMl: Int,
        expectedHighC: Int,
        activityLevel: ActivityLevel,
        useProfileActivity: Boolean,
        foodGramsToday: Int,
        foodWaterEnabled: Boolean,
    ): WaterGoalBreakdown {
        val base = baseGoalMl(baseSource, weightKg, manualBaseMl)
        val temp = tempFactor(expectedHighC)
        val act = if (useProfileActivity) activityFactor(activityLevel) else 1.0
        val gross = round50(base * temp * act)
        val food = if (foodWaterEnabled) foodWaterMl(foodGramsToday) else 0
        return WaterGoalBreakdown(
            baseMl = base,
            grossMl = gross,
            foodWaterMl = food,
            netGoalMl = netGoalMl(gross, food),
            tempFactor = temp,
            activityFactor = act,
        )
    }

    /**
     * Coarse food-mass estimate for WATER-DYN-B: serving grams × quantity
     * summed across today's diary entries. Entries without a serving weight
     * contribute 0 (the estimate is intentionally rough).
     */
    fun estimateDiaryGrams(entries: List<FoodEntry>): Int =
        entries.sumOf { (it.servingSizeGrams ?: 0.0) * (it.selectedServingQuantity ?: 0.0) }
            .roundToInt()

    // -- WATER-DYN-C: adaptive reminder interval --------------------------

    const val MIN_INTERVAL_MIN = 30
    const val MAX_INTERVAL_MIN = 240

    /** Default standard cup used when the user has not picked one. */
    const val DEFAULT_CUP_SIZE_ML = 300

    /** Whole cups needed to cover [goalMl]; at least 1 when the goal is positive. */
    fun cupsFor(goalMl: Int, cupSizeMl: Int): Int =
        ceil(max(goalMl, 0) / cupSizeMl.coerceAtLeast(1).toDouble()).toInt()

    /**
     * Amount to drink at the next reminder: one cup, capped by the goal
     * remainder (0 once the goal is met — callers then re-arm for tomorrow).
     * Every reminder prompts one cup so the per-cup cadence and the quantity
     * agree; a tail smaller than a cup (e.g. 250 ml left with a 300 ml cup)
     * becomes the final reminder's amount.
     */
    fun nextDrinkMl(netGoalMl: Int, drunkTodayMl: Int, cupSizeMl: Int): Int =
        minOf(cupSizeMl, max(netGoalMl - drunkTodayMl, 0))

    /**
     * Full-day planning form of the reporter's formula:
     * `window ÷ cups(goal ÷ cup)`, rounded to 5 min and clamped like the live
     * form so the Settings preview matches what the alarm chain does at day
     * start. Null when the window is empty (end ≤ start).
     */
    fun planningIntervalMin(netGoalMl: Int, cupSizeMl: Int, awakeWindowMinutes: Int): Int? {
        if (awakeWindowMinutes <= 0) return null
        return clampInterval(round5(awakeWindowMinutes / cupsFor(netGoalMl, cupSizeMl).toDouble()))
    }

    /**
     * Live form, recomputed after every entry: remaining cups over the
     * remaining awake window. Ahead of plan (drank more) → longer interval;
     * behind → shorter, down to the [MIN_INTERVAL_MIN] floor. Null when the
     * goal is already met or the window has elapsed. The caller re-arms for
     * tomorrow instead.
     */
    fun liveIntervalMin(
        netGoalMl: Int,
        drunkTodayMl: Int,
        cupSizeMl: Int,
        nowMinutes: Int,
        awakeStartMinutes: Int,
        awakeEndMinutes: Int,
    ): Int? {
        val cupsRemaining = cupsFor(netGoalMl - drunkTodayMl, cupSizeMl)
        if (cupsRemaining == 0) return null // goal reached. No more today.
        val windowRemaining = awakeEndMinutes - max(nowMinutes, awakeStartMinutes)
        if (windowRemaining <= 0) return null // past awake end. Re-arm tomorrow.
        return clampInterval(round5(windowRemaining / cupsRemaining.toDouble()))
    }

    /**
     * Next-reminder decision for the adaptive chain, expressed as a minute
     * offset from [nowMinutes]:
     *  - before the window start → fire at the window start (offset to it),
     *    unless today's goal is already met (→ null = arm tomorrow);
     *  - inside the window → the live interval (may be null when goal met);
     *  - past the window end → null (arm tomorrow).
     * Null always means "the next fire belongs to tomorrow's window start".
     */
    fun nextFireOffsetMinutes(
        netGoalMl: Int,
        drunkTodayMl: Int,
        cupSizeMl: Int,
        nowMinutes: Int,
        awakeStartMinutes: Int,
        awakeEndMinutes: Int,
    ): Int? {
        if (nowMinutes < awakeStartMinutes) {
            return if (drunkTodayMl >= netGoalMl) null else awakeStartMinutes - nowMinutes
        }
        return liveIntervalMin(
            netGoalMl, drunkTodayMl, cupSizeMl,
            nowMinutes, awakeStartMinutes, awakeEndMinutes,
        )
    }

    // -- Rounding helpers -------------------------------------------------

    /** Nearest 50 ml (goal granularity everywhere). */
    fun round50(value: Double): Int = (value / 50).roundToInt() * 50

    /** Nearest 5 minutes (interval granularity everywhere). */
    fun round5(value: Double): Int = (value / 5).roundToInt() * 5

    private fun clampInterval(minutes: Int): Int = minutes.coerceIn(MIN_INTERVAL_MIN, MAX_INTERVAL_MIN)
}

/** Per-input breakdown of a dynamic water goal (Settings preview). */
data class WaterGoalBreakdown(
    val baseMl: Int,
    val grossMl: Int,
    val foodWaterMl: Int,
    val netGoalMl: Int,
    val tempFactor: Double,
    val activityFactor: Double,
)
