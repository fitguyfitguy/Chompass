package app.chompass.services

import app.chompass.R
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.data.WeightRepository
import app.chompass.models.UserProfile
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
    private val strings: (Int, Array<out Any>) -> String,
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
        // Persist the measured active average so the home gauge can split the measured goal
        // into a sedentary base (goal − measured active) instead of a PAL estimate.
        prefs.setHealthEnergyMeasuredActive(summary.activeAverageCalories)
        return summary.totalAverageCalories ?: (profile.bmr.roundToInt() + summary.activeAverageCalories)
    }

    /**
     * Adaptive Goals: once a week, apply the deterministic ±150 kcal tweak
     * ([AdaptiveGoalService.apply]) from the logged weight trend (or measured Health Connect
     * TDEE). Locked calories are left untouched. Recalculate stays the AI path.
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
            val measuredTdee = measuredEnergyTdeeIfEnabled(profile)
            val result = AdaptiveGoalService.apply(
                profile = profile,
                weights = weightRepository.entries.first(),
                foods = foodRepository.entries.first(),
                measuredTdee = measuredTdee,
            )
            prefs.setAdaptiveGoalsLastCheckDay(today.toString())
            if (!result.changed || result.updatedCalories == null) return result

            prefs.saveAdaptiveGoalPreviousTargetsIfNeeded(profile)
            profileRepository.save(result.profile)
            return result.copy(
                message = strings(R.string.vm_adaptive_updated, arrayOf(result.updatedCalories)) +
                    " ${result.message}"
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
