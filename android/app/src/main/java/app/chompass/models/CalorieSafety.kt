package app.chompass.models

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CAL-SAFE: unsupervised calorie-target guardrail. Auto paths (formula, Recalculate,
 * Adaptive, onboarding) never persist below [floorKcal] or above [ceilingKcal].
 * Manual pins ([UserProfile.customCalories]) are not clamped here — see Settings confirm.
 *
 * Floor: max(BMR, [ABSOLUTE_FLOOR_KCAL]). Ceiling: max(floor, 6000, 1.5×TDEE).
 * See [docs/CALCULATION_METHODS.md].
 */
object CalorieSafety {
    const val ABSOLUTE_FLOOR_KCAL = 1_200
    const val PARSER_CEILING_KCAL = 6_000
    const val ADULT_MIN_AGE = 18
    private const val AUTO_TDEE_CEILING_MULT = 1.5

    fun floorKcal(bmr: Double): Int = max(bmr.roundToInt(), ABSOLUTE_FLOOR_KCAL)

    fun ceilingKcal(tdee: Double, floor: Int): Int =
        maxOf(floor, PARSER_CEILING_KCAL, (tdee * AUTO_TDEE_CEILING_MULT).roundToInt())

    fun clampAuto(raw: Int, bmr: Double, tdee: Double): Int {
        val floor = floorKcal(bmr)
        return raw.coerceIn(floor, ceilingKcal(tdee, floor))
    }

    fun clampAuto(raw: Int, profile: UserProfile): Int =
        clampAuto(raw, profile.bmr, profile.tdee)
}
