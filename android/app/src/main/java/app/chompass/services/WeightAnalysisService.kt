package app.chompass.services

import app.chompass.models.FoodEntry
import app.chompass.models.NutritionConstants
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure thermodynamic / statistical forecast of where the user's weight is heading based on
 * recent calorie intake, logged weight history, and their profile. No network, no LLM —
 * energy-balance math + linear regression.
 */
data class WeightForecast(
    val avgDailyCalories: Int,
    val tdee: Int,
    val dailyEnergyBalance: Int,
    val predictedWeeklyChangeKg: Double,
    val observedWeeklyChangeKg: Double?,
    val currentWeightKg: Double,
    val predictedWeight30dKg: Double,
    val predictedWeight60dKg: Double,
    val predictedWeight90dKg: Double,
    val daysToGoal: Int?,
    val goalReachDate: Instant?,
    val hasEnoughData: Boolean,
    val trendsDisagree: Boolean,
    val daysOfFoodData: Int,
    val weightEntriesUsed: Int,
    /** Calendar days in the 90-day lookback window (inclusive). */
    val calendarDaysInWindow: Int = WeightForecast.MAX_LOOKBACK_DAYS,
    /** True when sparse logging triggered calendar-day intake averaging. */
    val usesCalendarDayAverage: Boolean = false,
) {
    companion object {
        const val MAX_LOOKBACK_DAYS = 90
    }
}

data class AdaptiveGoalResult(
    val profile: UserProfile,
    val changed: Boolean,
    val updatedCalories: Int?,
    val message: String
)

object AdaptiveGoalService {
    private const val MINIMUM_FOOD_DAYS = 4
    private const val MINIMUM_WEIGHT_ENTRIES = 3
    private const val MINIMUM_DAILY_ADJUSTMENT = 25
    private const val MAXIMUM_DAILY_ADJUSTMENT = 150
    private val caloriesPerKg: Double get() = NutritionConstants.KCAL_PER_KG_BODY_MASS

    /**
     * [measuredTdee] (Health Connect active + basal energy, kcal/day) is preferred over the
     * formula TDEE when available: it sets the safety ceiling and, when there isn't yet enough
     * weight-trend data, drives a burn-based correction toward measured maintenance + the goal
     * pace. The weight-trend correction stays primary whenever enough logs/weigh-ins exist.
     */
    fun apply(
        profile: UserProfile,
        weights: List<WeightEntry>,
        foods: List<FoodEntry>,
        measuredTdee: Int? = null
    ): AdaptiveGoalResult {
        val forecast = WeightAnalysisService.compute(weights = weights, foods = foods, profile = profile)
        val observedWeeklyChangeKg = forecast.observedWeeklyChangeKg
        val targetWeeklyChangeKg = targetWeeklyChangeKg(profile)
        val currentCalories = profile.effectiveCalories
        val safetyFloor = max(profile.bmr.roundToInt(), 1_200)
        // Prefer Health Connect measured burn for the maintenance/ceiling basis when available.
        val maintenanceTdee = measuredTdee ?: profile.tdee.roundToInt()
        val safetyCeiling = max(safetyFloor, (maintenanceTdee * 1.25).roundToInt())

        val hasWeightTrend = forecast.daysOfFoodData >= MINIMUM_FOOD_DAYS &&
            forecast.weightEntriesUsed >= MINIMUM_WEIGHT_ENTRIES &&
            observedWeeklyChangeKg != null

        val limitedAdjustment: Int = if (hasWeightTrend && observedWeeklyChangeKg != null) {
            // Primary: correct from the real weight trend vs the target pace.
            val raw = (targetWeeklyChangeKg - observedWeeklyChangeKg) * caloriesPerKg / 7.0
            raw.roundToInt().coerceIn(-MAXIMUM_DAILY_ADJUSTMENT, MAXIMUM_DAILY_ADJUSTMENT)
        } else if (measuredTdee != null) {
            // Not enough weigh-ins/food yet, but Health Connect gives measured burn: steer the
            // target toward measured maintenance + the goal pace.
            val targetCalories = measuredTdee +
                NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(targetWeeklyChangeKg)
            (targetCalories - currentCalories).coerceIn(-MAXIMUM_DAILY_ADJUSTMENT, MAXIMUM_DAILY_ADJUSTMENT)
        } else {
            return AdaptiveGoalResult(
                profile = profile,
                changed = false,
                updatedCalories = null,
                message = "Adaptive Goals is on. It needs at least $MINIMUM_FOOD_DAYS logged food days and $MINIMUM_WEIGHT_ENTRIES recent weight entries — or Health Connect energy data — before making a correction."
            )
        }

        if (abs(limitedAdjustment) < MINIMUM_DAILY_ADJUSTMENT) {
            return AdaptiveGoalResult(
                profile = profile,
                changed = false,
                updatedCalories = null,
                message = "Your recent trend is close to your selected goal pace, so Adaptive Goals did not change calories this week."
            )
        }

        if (limitedAdjustment < 0 && currentCalories <= safetyFloor) {
            return AdaptiveGoalResult(
                profile = profile,
                changed = false,
                updatedCalories = null,
                message = "Adaptive Goals did not lower calories because your current target is already at the safety floor."
            )
        }
        if (limitedAdjustment > 0 && currentCalories >= safetyCeiling) {
            return AdaptiveGoalResult(
                profile = profile,
                changed = false,
                updatedCalories = null,
                message = "Adaptive Goals did not raise calories because your current target is already at the safety ceiling."
            )
        }

        val proposedCalories = currentCalories + limitedAdjustment
        val adjustedCalories = if (limitedAdjustment < 0) {
            max(proposedCalories, safetyFloor)
        } else {
            min(proposedCalories, safetyCeiling)
        }

        if (adjustedCalories == currentCalories) {
            return AdaptiveGoalResult(
                profile = profile,
                changed = false,
                updatedCalories = null,
                message = "Adaptive Goals checked your trend, but calorie guardrails kept this week's target unchanged."
            )
        }

        val nextProfile = profile.copy(customCalories = adjustedCalories)
        val signedAdjustment = adjustedCalories - currentCalories
        val sign = if (signedAdjustment > 0) "+" else ""
        val basis = if (hasWeightTrend) "your recent weight trend" else "your Health Connect energy burn"
        return AdaptiveGoalResult(
            profile = nextProfile,
            changed = true,
            updatedCalories = adjustedCalories,
            message = "Adaptive Goals adjusted calories by $sign$signedAdjustment kcal to $adjustedCalories kcal based on $basis. Pinned macros stay pinned; unlocked macros auto-balance."
        )
    }

    private fun targetWeeklyChangeKg(profile: UserProfile): Double = when (profile.goal) {
        WeightGoal.LOSE -> -(profile.weeklyChangeKg ?: 0.5)
        WeightGoal.MAINTAIN -> 0.0
        WeightGoal.GAIN -> profile.weeklyChangeKg ?: 0.5
    }
}

object WeightAnalysisService {
    fun compute(
        weights: List<WeightEntry>,
        foods: List<FoodEntry>,
        profile: UserProfile
    ): WeightForecast {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val cutoff = now.minusSeconds(WeightForecast.MAX_LOOKBACK_DAYS * 86_400L)

        val recentFoods = foods.filter { it.timestamp in cutoff..now }
        val daysLogged = recentFoods.map { it.timestamp.atZone(zone).toLocalDate() }.toSet().size
        val totalRecentCal = recentFoods.sumOf { it.calories }
        val calendarDays = WeightForecastMath.calendarDaysInclusive(cutoff, now, zone)
        val intake = WeightForecastMath.averageDailyIntake(totalRecentCal, daysLogged, calendarDays)
        val avgDailyCal = intake.avgDailyCalories

        val tdee = profile.tdee.toInt()
        val balance = avgDailyCal - tdee
        // 7,700 kcal ≈ 1 kg body fat (ISSN standard for deficit/surplus math).
        val predictedWeeklyKg = balance.toDouble() * 7.0 / NutritionConstants.KCAL_PER_KG_BODY_MASS

        val sortedWeights = weights.sortedByDescending { it.date }
        val currentWeight = sortedWeights.firstOrNull()?.weightKg ?: profile.weightKg

        val regressionWindow = sortedWeights.filter { it.date >= cutoff }
        val observedWeeklyKg = WeightForecastMath.theilSenSlopePerDay(regressionWindow, zone)?.let { it * 7.0 }

        val pred30 = currentWeight + predictedWeeklyKg * 30.0 / 7.0
        val pred60 = currentWeight + predictedWeeklyKg * 60.0 / 7.0
        val pred90 = currentWeight + predictedWeeklyKg * 90.0 / 7.0

        var daysToGoal: Int? = null
        var goalReachDate: Instant? = null
        val goalKg = profile.goalWeightKg
        if (goalKg != null && predictedWeeklyKg != 0.0 && profile.goal != WeightGoal.MAINTAIN) {
            val kgRemaining = goalKg - currentWeight
            val movingCorrectWay =
                (profile.goal == WeightGoal.LOSE && predictedWeeklyKg < 0 && kgRemaining < 0) ||
                        (profile.goal == WeightGoal.GAIN && predictedWeeklyKg > 0 && kgRemaining > 0)
            if (movingCorrectWay) {
                val daysPerKg = 7.0 / abs(predictedWeeklyKg)
                val days = (abs(kgRemaining) * daysPerKg).roundToInt()
                daysToGoal = days
                goalReachDate = now.plusSeconds(days * 86_400L)
            }
        }

        val hasEnoughData = daysLogged >= 2 && weights.size >= 2

        val trendsDisagree = WeightForecastMath.trendsDisagree(
            predictedWeeklyKg,
            observedWeeklyKg,
            hasEnoughData,
        )

        return WeightForecast(
            avgDailyCalories = avgDailyCal,
            tdee = tdee,
            dailyEnergyBalance = balance,
            predictedWeeklyChangeKg = predictedWeeklyKg,
            observedWeeklyChangeKg = observedWeeklyKg,
            currentWeightKg = currentWeight,
            predictedWeight30dKg = pred30,
            predictedWeight60dKg = pred60,
            predictedWeight90dKg = pred90,
            daysToGoal = daysToGoal,
            goalReachDate = goalReachDate,
            hasEnoughData = hasEnoughData,
            trendsDisagree = trendsDisagree,
            daysOfFoodData = daysLogged,
            weightEntriesUsed = regressionWindow.size,
            calendarDaysInWindow = intake.calendarDaysInWindow,
            usesCalendarDayAverage = intake.usesCalendarDayAverage,
        )
    }
}
