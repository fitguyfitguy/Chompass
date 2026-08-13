package app.chompass.services

import app.chompass.AppContainer
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.data.WaterRepository
import app.chompass.data.WeatherRepository
import app.chompass.models.ActivityLevel
import app.chompass.models.WaterGoalCalculator
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Computes the next fire for the adaptive water reminder chain (issue #3,
 * WATER-DYN-C in docs/WATER_DYNAMIC_GOAL_DESIGN.md). Pure state → plan; every
 * caller (ChompassApp start, Settings re-sync, entry add/delete, reminder
 * fire, boot re-arm) re-derives the plan from live prefs + diary, so the
 * cadence always reflects the most recent entry (the reporter's ask).
 */
object WaterReminderPlanner {
    /** Next fire and the cadence to show in the notification. */
    data class Plan(
        val nextFireMillis: Long,
        /** Interval shown as "next in X min"; null → default text (day-start / next-day fire). */
        val intervalMinutes: Int?,
        /** Amount to drink at the next fire: one cup, capped by the goal remainder. */
        val drinkMl: Int,
    )

    /**
     * Next fire from current state; null when the water reminder is off or the
     * window is degenerate (end ≤ start). The window is derived from the
     * awake start/end prefs (minutes of day); the goal is the dynamic net goal
     * when enabled, else the stored manual goal. Container convenience
     * overload — the dependency form is used by callers without an
     * [AppContainer] (e.g. the widget snapshot writer).
     */
    suspend fun next(container: AppContainer, nowMillis: Long = System.currentTimeMillis()): Plan? =
        next(
            prefs = container.prefs,
            foodRepository = container.foodRepository,
            profileRepository = container.profileRepository,
            waterRepository = container.waterRepository,
            weatherRepository = container.weatherRepository,
            nowMillis = nowMillis,
        )

    /** Dependency form of [next]; same plan, no container needed. */
    suspend fun next(
        prefs: PreferencesStore,
        foodRepository: FoodRepository,
        profileRepository: ProfileRepository,
        waterRepository: WaterRepository,
        weatherRepository: WeatherRepository,
        nowMillis: Long = System.currentTimeMillis(),
    ): Plan? {
        if (!prefs.waterTrackingEnabled.first() || !prefs.waterReminderEnabled.first()) return null
        val startMin = prefs.waterAwakeStartHour.first() * 60 + prefs.waterAwakeStartMinute.first()
        val endMin = prefs.waterAwakeEndHour.first() * 60 + prefs.waterAwakeEndMinute.first()
        if (endMin - startMin <= 0) return null
        val cupMl = prefs.waterCupSizeMl.first()

        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val nowMin = now.hour * 60 + now.minute

        val goalMl = if (prefs.waterDynamicEnabled.first()) {
            val foodGrams = WaterGoalCalculator.estimateDiaryGrams(
                foodRepository.entriesForDate(now.toLocalDate()).first(),
            )
            val profile = profileRepository.current()
            WaterGoalCalculator.dailyNetGoalMl(
                baseSource = prefs.waterBaseSource.first(),
                weightKg = profile?.weightKg,
                manualBaseMl = prefs.waterDailyGoalMl.first(),
                expectedHighC = weatherRepository.state.first().effectiveHighC,
                activityLevel = profile?.activityLevel ?: ActivityLevel.SEDENTARY,
                useProfileActivity = prefs.waterUseProfileActivity.first(),
                foodGramsToday = foodGrams,
                foodWaterEnabled = prefs.waterFoodWaterEnabled.first(),
            )
        } else {
            prefs.waterDailyGoalMl.first()
        }

        val drunkToday = waterRepository.entries.first()
            .filter { it.date.atZone(zone).toLocalDate() == now.toLocalDate() }
            .sumOf { it.milliliters }

        val offset = WaterGoalCalculator.nextFireOffsetMinutes(
            netGoalMl = goalMl,
            drunkTodayMl = drunkToday,
            cupSizeMl = cupMl,
            nowMinutes = nowMin,
            awakeStartMinutes = startMin,
            awakeEndMinutes = endMin,
        )
        val nextFireMillis = if (offset != null) {
            nowMillis + offset * 60_000L
        } else {
            // Goal met or window elapsed → re-arm for tomorrow's window start.
            now.toLocalDate().plusDays(1)
                .atTime(LocalTime.of(startMin / 60, startMin % 60))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
        val intervalMinutes = when {
            offset == null -> null
            nowMin < startMin -> WaterGoalCalculator.planningIntervalMin(goalMl, cupMl, endMin - startMin)
            else -> offset
        }
        // One cup per reminder, capped by the remainder; a next-day fire starts
        // tomorrow fresh, so its amount is the first cup of the new day.
        val drinkMl = if (offset != null) {
            WaterGoalCalculator.nextDrinkMl(goalMl, drunkToday, cupMl)
        } else {
            WaterGoalCalculator.nextDrinkMl(goalMl, 0, cupMl)
        }
        return Plan(
            nextFireMillis = nextFireMillis,
            intervalMinutes = intervalMinutes,
            drinkMl = drinkMl,
        )
    }

    /** Arms the water reminder to match current state (cancels when off). */
    suspend fun rearm(container: AppContainer) {
        val plan = next(container)
        if (plan != null) {
            container.notifications.scheduleWaterReminderAt(plan.nextFireMillis)
        } else {
            container.notifications.cancelWaterReminder()
        }
    }
}
