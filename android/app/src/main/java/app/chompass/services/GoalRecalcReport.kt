package app.chompass.services

import app.chompass.models.CalorieSafety
import app.chompass.models.NutritionConstants
import app.chompass.models.UserProfile
import app.chompass.services.ai.GoalCalculationReport
import app.chompass.services.ai.ImpliedWithheldReason

// Hit-and-trial empirical-maintenance confidence gates for goal recalculation
// (AI Recalculate SAFE tier + the Adaptive pass share them). Below these
// minimums the implied-maintenance numbers are withheld from the SAFE prompt
// (sparse weigh-ins anchored the model at ~BMR, e.g. 1742 kcal vs formula TDEE
// ~2680; CAL-SAFE cannot catch it because it is >= BMR). See
// docs/CALCULATION_METHODS.md § AI-RECALC.
const val EMPIRICAL_MIN_WEIGH_INS = 4
const val EMPIRICAL_MIN_SPAN_DAYS = 14
const val EMPIRICAL_MIN_FOOD_DAYS = 4
// Medium-confidence band: implied maintenance is shown, but a >15% deviation
// from the formula TDEE sends the model back to the formula.
const val EMPIRICAL_MEDIUM_MAX_WEIGH_INS = 6
const val EMPIRICAL_MEDIUM_MAX_SPAN_DAYS = 28
const val EMPIRICAL_MEDIUM_MAX_FOOD_DAYS = 10
// Deterministic enforcement: a trusted-empirical result that sits within this
// many kcal of the implied maintenance is treated as "goal pace not applied"
// and snapped to maintenance + pace (SAFE tier only).
const val EMPIRICAL_PACE_MISS_KCAL = 150

/**
 * App-side confidence gates for the SAFE tier's observed-data section and its
 * deterministic enforcement. See docs/CALCULATION_METHODS.md § AI-RECALC.
 */
data class EmpiricalSignals(
    val empiricalUsable: Boolean,
    val mediumConfidence: Boolean,
    val safetyFloor: Int,
    val loggedAvg: Int,
    val impliedMaintenance: Int?,
    val impliedBelowFloor: Boolean,
    val trustEmpirical: Boolean,
)

/**
 * App-side confidence gates for the SAFE tier's observed-data section and its
 * deterministic enforcement. With sparse weigh-ins the model anchored the target at
 * ~BMR (e.g. 1742 kcal vs formula TDEE ~2680) and CAL-SAFE passes it (it is >= BMR),
 * so the SAFE guard is prompt-side: withhold the implied numbers and the "prefer
 * empirical" instruction until these minimums are met. See docs/CALCULATION_METHODS.md.
 */
fun empiricalSignals(forecast: WeightForecast?, profile: UserProfile): EmpiricalSignals {
    val empiricalUsable = forecast != null &&
        forecast.weightEntriesUsed >= EMPIRICAL_MIN_WEIGH_INS &&
        forecast.weightSpanDays >= EMPIRICAL_MIN_SPAN_DAYS &&
        forecast.daysOfFoodData >= EMPIRICAL_MIN_FOOD_DAYS
    val mediumConfidence = empiricalUsable && (
        forecast!!.weightEntriesUsed < EMPIRICAL_MEDIUM_MAX_WEIGH_INS ||
            forecast.weightSpanDays < EMPIRICAL_MEDIUM_MAX_SPAN_DAYS ||
            forecast.daysOfFoodData < EMPIRICAL_MEDIUM_MAX_FOOD_DAYS
        )
    val safetyFloor = CalorieSafety.floorKcal(profile.bmr)
    val loggedAvg = (forecast?.loggedDayAvgCalories?.takeIf { it > 0 } ?: forecast?.avgDailyCalories) ?: 0
    val impliedMaintenance = forecast?.observedWeeklyChangeKg?.let { obs ->
        loggedAvg - NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(obs)
    }
    // True when the implied maintenance is below the BMR safety floor: the number is
    // withheld from SAFE (the model anchored on "below BMR" values before) and the
    // section falls back to the formula/measured anchor below.
    val impliedBelowFloor = impliedMaintenance != null && impliedMaintenance < safetyFloor
    // When false, the SAFE prompt forbids estimating maintenance from the observed data
    // and the deterministic enforcement snaps the model's calories back to the anchor.
    val trustEmpirical = empiricalUsable && !impliedBelowFloor &&
        !(forecast?.trendsDisagree ?: false)
    return EmpiricalSignals(
        empiricalUsable, mediumConfidence, safetyFloor, loggedAvg,
        impliedMaintenance, impliedBelowFloor, trustEmpirical,
    )
}

/**
 * Deterministic inputs behind a goal change (AI Recalculate or Adaptive), for the
 * result sheet's "formula baseline" + "data used" sections. Built from the same
 * signals as the prompts.
 */
fun buildGoalCalculationReport(
    profile: UserProfile,
    forecast: WeightForecast?,
    measuredTdee: Int?,
): GoalCalculationReport {
    val signals = empiricalSignals(forecast, profile)
    return GoalCalculationReport(
        bmr = profile.bmr.toInt(),
        tdee = profile.tdee.toInt(),
        activityMultiplier = profile.activityLevel.multiplier,
        calorieAdjustment = profile.calorieAdjustment,
        formulaCalories = profile.dailyCalories,
        formulaProtein = profile.proteinGoal,
        formulaCarbs = profile.carbsGoal,
        formulaFat = profile.fatGoal,
        measuredTdee = measuredTdee,
        weighIns = forecast?.weightEntriesUsed ?: 0,
        weightSpanDays = forecast?.weightSpanDays ?: 0,
        foodDays = forecast?.daysOfFoodData ?: 0,
        loggedDayAvgCalories = forecast?.loggedDayAvgCalories?.takeIf { it > 0 } ?: forecast?.avgDailyCalories,
        impliedMaintenance = signals.impliedMaintenance,
        impliedWithheld = when {
            signals.impliedBelowFloor -> ImpliedWithheldReason.BELOW_FLOOR
            forecast?.trendsDisagree == true -> ImpliedWithheldReason.DISAGREE
            !signals.empiricalUsable -> ImpliedWithheldReason.THIN
            else -> null
        },
        trendsDisagree = forecast?.trendsDisagree ?: false,
    )
}
