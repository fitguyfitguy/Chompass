package app.chompass.models

import java.time.Instant
import kotlin.math.roundToInt

/**
 * Pure projection of the standing goal derivation — the checkable chain
 * BMR → TDEE × activity multiplier → ± pace → CAL-SAFE clamp — for
 * transparency surfaces that read the live profile (Home budget dialog's
 * formula-baseline block). The recalc sheet's
 * [app.chompass.services.ai.GoalCalculationReport] covers the same chain
 * post-recalculate; this mirrors it for the number the user sees every day.
 */
data class GoalDerivation(
    val bmrKcal: Int,
    val activityMultiplier: Double,
    val tdeeKcal: Int,
    /** Signed goal pace in kcal/day (0 for maintain). */
    val paceKcal: Int,
    /** Unclamped TDEE ± pace — what the formula wanted. */
    val rawTargetKcal: Int,
    /** The persisted auto target after the CAL-SAFE clamp. */
    val formulaTargetKcal: Int,
    /** True when the CAL-SAFE floor/ceiling moved the target. */
    val formulaClamped: Boolean,
    val floorKcal: Int,
    val ceilingKcal: Int,
    /** Age of the body-fat reading in days, or null when undated (legacy). */
    val bfAgeDays: Long?,
)

fun UserProfile.toGoalDerivation(at: Instant = Instant.now()): GoalDerivation = GoalDerivation(
    bmrKcal = bmr.roundToInt(),
    activityMultiplier = activityLevel.multiplier,
    tdeeKcal = tdee.roundToInt(),
    paceKcal = calorieAdjustment,
    rawTargetKcal = rawDailyCalories,
    formulaTargetKcal = dailyCalories,
    formulaClamped = rawDailyCalories != dailyCalories,
    floorKcal = CalorieSafety.floorKcal(bmr),
    ceilingKcal = CalorieSafety.ceilingKcal(tdee, CalorieSafety.floorKcal(bmr)),
    bfAgeDays = bodyFatAgeDays(at),
)
