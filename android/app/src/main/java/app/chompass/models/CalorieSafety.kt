package app.chompass.models

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CAL-SAFE: unsupervised calorie-target guardrail. Auto paths (formula, Recalculate,
 * Adaptive, onboarding) never persist below [floorKcal] or above [ceilingKcal].
 * Manual pins ([UserProfile.customCalories]) are not clamped here — see Settings confirm.
 *
 * Floor: max(BMR, [ABSOLUTE_FLOOR_KCAL]). Ceiling: min([PARSER_CEILING_KCAL], max(floor, 1.5×TDEE))
 * — the 1.5×TDEE proportional cap bounded by the 6000 parser ceiling, so a normal profile
 * caps at 1.5×TDEE and only a TDEE above 4000 kcal reaches 6000. The outer floor max keeps
 * coerceIn(floor, ceiling) valid for a corrupt profile whose BMR exceeds 6000.
 * See [docs/CALCULATION_METHODS.md].
 */
object CalorieSafety {
    const val ABSOLUTE_FLOOR_KCAL = 1_200
    const val PARSER_CEILING_KCAL = 6_000
    const val ADULT_MIN_AGE = 18
    private const val AUTO_TDEE_CEILING_MULT = 1.5

    fun floorKcal(bmr: Double): Int = max(bmr.roundToInt(), ABSOLUTE_FLOOR_KCAL)

    fun ceilingKcal(tdee: Double, floor: Int): Int {
        val proportional = maxOf(floor, (tdee * AUTO_TDEE_CEILING_MULT).roundToInt())
        return maxOf(floor, minOf(PARSER_CEILING_KCAL, proportional))
    }

    fun clampAuto(raw: Int, bmr: Double, tdee: Double): Int {
        val floor = floorKcal(bmr)
        return raw.coerceIn(floor, ceilingKcal(tdee, floor))
    }

    fun clampAuto(raw: Int, profile: UserProfile): Int =
        clampAuto(raw, profile.bmr, profile.tdee)
}
