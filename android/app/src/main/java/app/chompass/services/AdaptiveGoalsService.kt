package app.chompass.services

import app.chompass.data.BodyMeasurementRepository
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.data.WeightRepository
import app.chompass.models.UserProfile
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.health.HealthConnectManager
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Adaptive Goals + measured Energy Burn TDEE resolution. Extracted from
 * [app.chompass.AppContainer] so the DI root stays a wiring map, not a use-case bag.
 */
class AdaptiveGoalsService(
    private val prefs: PreferencesStore,
    private val health: HealthConnectManager,
    private val profileRepository: ProfileRepository,
    private val foodRepository: FoodRepository,
    private val weightRepository: WeightRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val foodAnalysis: FoodAnalysisService,
) {
    private var refreshInFlight = false

    /**
     * Energy Burn toggle resolved to a number: the user's measured maintenance from Health Connect
     * (14-day active + basal average), or null when Energy Burn is off, Health is unavailable, or
     * there isn't enough data. Single source consulted by both manual Recalculate and Adaptive.
     */
    suspend fun measuredEnergyTdeeIfEnabled(profile: UserProfile): Int? {
        if (!prefs.healthEnergyGoalsEnabled.first()) return null
        if (!prefs.healthConnectEnabled.first()) return null
        if (!health.isAvailable() || !health.hasEnergyRead()) return null
        val summary = runCatching { health.readRecentEnergySummary(days = 14) }.getOrNull() ?: return null
        return summary.totalAverageCalories ?: (profile.bmr.roundToInt() + summary.activeAverageCalories)
    }

    /**
     * Adaptive Goals: automatically re-runs the FULL AI goal calculation (the same one the
     * Recalculate button uses) about once a week, from the latest logged food + weight trend
     * (hit-and-trial) and — when Energy Burn is on — the measured Health maintenance anchor.
     * Silent and non-destructive on AI failure (keeps existing goals; marks checked so it does not
     * retry on every app open). Protein is pinned to the activity multiplier, like manual recalc.
     */
    suspend fun refreshIfNeeded(force: Boolean = false): AdaptiveGoalResult? {
        if (refreshInFlight) return null
        refreshInFlight = true
        try {
            if (!prefs.adaptiveGoalsEnabled.first()) return null

            val today = LocalDate.now()
            if (!force && !shouldCheckAdaptiveGoals(prefs.adaptiveGoalsLastCheckDay.first(), today)) {
                return null
            }

            val profile = profileRepository.current() ?: return null
            val heightMetric = prefs.heightUnit.first() == "cm"
            val weightMetric = prefs.weightUnit.first() == "kg"
            val measuredTdee = measuredEnergyTdeeIfEnabled(profile)
            val forecast = WeightAnalysisService.compute(
                weights = weightRepository.entries.first(),
                foods = foodRepository.entries.first(),
                profile = profile
            )
            val result = runCatching {
                foodAnalysis.calculateGoals(
                    profile,
                    forecast,
                    heightMetric,
                    weightMetric,
                    measuredTdee,
                    bodyMeasurementRepository.latestSnapshot(),
                )
            }.getOrNull()
            // Mark checked on success OR AI failure so a misconfigured provider isn't hit on every
            // foreground; the weekly cadence simply resumes next week.
            prefs.setAdaptiveGoalsLastCheckDay(today.toString())
            if (result == null) return null

            prefs.saveAdaptiveGoalPreviousTargetsIfNeeded(profile)
            val next = profile.recalculatedFromFormulas().copy(
                customCalories = result.calories,
                customProtein = result.protein,
                customCarbs = result.carbs,
                customFat = result.fat
            )
            profileRepository.save(next)
            return AdaptiveGoalResult(
                profile = next,
                changed = true,
                updatedCalories = result.calories,
                message = "Updated to ${result.calories} kcal from your latest data." +
                    (result.reason?.let { " $it" } ?: "")
            )
        } finally {
            refreshInFlight = false
        }
    }

    private fun shouldCheckAdaptiveGoals(lastCheckDay: String?, today: LocalDate): Boolean {
        val lastCheck = lastCheckDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return true
        return !lastCheck.plusDays(7).isAfter(today)
    }
}
