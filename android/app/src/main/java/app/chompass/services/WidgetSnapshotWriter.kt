package app.chompass.services

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.models.FoodEntry
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.UserProfile
import app.chompass.models.WaterEntry
import app.chompass.models.WaterGoalCalculator
import app.chompass.models.WidgetNutrient
import app.chompass.models.WidgetSnapshot
import app.chompass.services.health.HomeActivityReader
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.appearanceIsDark
import app.chompass.ui.theme.widgetAccentColors
import app.chompass.widget.AllMetricsAppWidget
import app.chompass.widget.CalorieAppWidget
import app.chompass.widget.ProteinAppWidget
import app.chompass.widget.WaterAppWidget
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recomputes today's totals whenever food entries or the user profile change,
 * writes the [WidgetSnapshot] into DataStore, and asks Glance to redraw both
 * the Calorie and Protein app widgets. Mirrors the iOS WidgetSnapshotWriter
 * call sites (every FoodStore change + profile-change notification + scene
 * resume).
 */
class WidgetSnapshotWriter(
    private val context: Context,
    private val prefs: PreferencesStore,
    private val foodRepository: FoodRepository,
    private val profileRepository: ProfileRepository,
    private val homeActivityReader: HomeActivityReader,
    private val waterRepository: app.chompass.data.WaterRepository,
    private val weatherRepository: app.chompass.data.WeatherRepository,
) {
    /** Bundled water inputs flowing through [observe]/[publish]. */
    internal data class WaterInputs(
        val enabled: Boolean,
        val manualGoalMl: Int,
        val dynamic: WaterDynamicInputs,
        val plan: WaterPlanInputs,
        val entries: List<WaterEntry>,
    )

    /** Bundled inputs flowing through [observe]/[publish]. */
    internal data class SnapshotInputs(
        val entries: List<FoodEntry>,
        val profile: UserProfile?,
        val appearance: String,
        val water: WaterInputs,
    )

    internal data class WaterDynamicInputs(
        val dynamicEnabled: Boolean,
        val baseSource: String,
        val tempC: Int,
        val useProfileActivity: Boolean,
        val foodWaterEnabled: Boolean,
    )

    /** Adaptive reminder inputs for the next-drink plan in the snapshot. */
    internal data class WaterPlanInputs(
        val reminderEnabled: Boolean,
        val awakeStartMinutes: Int,
        val awakeEndMinutes: Int,
        val cupSizeMl: Int,
    )

    internal fun observe() = combine(
        combine(
            combine(
                foodRepository.entriesForDate(LocalDate.now()),
                profileRepository.profile,
                prefs.homeDisplayPreferences,
                prefs.appThemeColor,
                prefs.optionalNutrientGoals,
            ) { entries, profile, _, _, _ ->
                entries to profile
            },
            prefs.appearanceMode,
        ) { core, appearance ->
            Triple(core.first, core.second, appearance)
        },
        combine(
            combine(
                prefs.waterTrackingEnabled,
                prefs.waterDailyGoalMl,
                waterRepository.entries,
            ) { enabled, goalMl, waterEntries ->
                Triple(enabled, goalMl, waterEntries)
            },
            combine(
                prefs.waterDynamicEnabled,
                prefs.waterBaseSource,
                weatherRepository.state,
                prefs.waterUseProfileActivity,
                prefs.waterFoodWaterEnabled,
            ) { dyn, source, weather, useAct, foodWater ->
                WaterDynamicInputs(dyn, source, weather.effectiveHighC, useAct, foodWater)
            },
            combine(
                combine(
                    prefs.waterReminderEnabled,
                    prefs.waterCupSizeMl,
                ) { reminder, cup -> reminder to cup },
                combine(
                    prefs.waterAwakeStartHour,
                    prefs.waterAwakeStartMinute,
                    prefs.waterAwakeEndHour,
                    prefs.waterAwakeEndMinute,
                ) { sh, sm, eh, em -> sh * 60 + sm to eh * 60 + em },
            ) { reminderAndCup, window ->
                WaterPlanInputs(
                    reminderEnabled = reminderAndCup.first,
                    cupSizeMl = reminderAndCup.second,
                    awakeStartMinutes = window.first,
                    awakeEndMinutes = window.second,
                )
            },
        ) { water, dynamic, plan ->
            WaterInputs(
                enabled = water.first,
                manualGoalMl = water.second,
                dynamic = dynamic,
                plan = plan,
                entries = water.third,
            )
        },
    ) { core, water ->
        SnapshotInputs(core.first, core.second, core.third, water)
    }
        .distinctUntilChanged()
        .onEach { inputs -> publish(inputs.entries, inputs.profile, inputs.appearance, inputs.water) }

    /**
     * Recompute and publish from current repos/prefs. Used when Theme Color is
     * System and the Material You primary changes without any pref/diary change
     * (wallpaper / palette refresh on resume).
     */
    suspend fun refresh() {
        val entries = foodRepository.entriesForDate(LocalDate.now()).first()
        val profile = profileRepository.profile.first()
        val appearance = prefs.appearanceMode.first()
        val water = WaterInputs(
            enabled = prefs.waterTrackingEnabled.first(),
            manualGoalMl = prefs.waterDailyGoalMl.first(),
            dynamic = WaterDynamicInputs(
                dynamicEnabled = prefs.waterDynamicEnabled.first(),
                baseSource = prefs.waterBaseSource.first(),
                tempC = weatherRepository.state.first().effectiveHighC,
                useProfileActivity = prefs.waterUseProfileActivity.first(),
                foodWaterEnabled = prefs.waterFoodWaterEnabled.first(),
            ),
            plan = WaterPlanInputs(
                reminderEnabled = prefs.waterReminderEnabled.first(),
                awakeStartMinutes = prefs.waterAwakeStartHour.first() * 60 + prefs.waterAwakeStartMinute.first(),
                awakeEndMinutes = prefs.waterAwakeEndHour.first() * 60 + prefs.waterAwakeEndMinute.first(),
                cupSizeMl = prefs.waterCupSizeMl.first(),
            ),
            entries = waterRepository.entries.first(),
        )
        publish(entries, profile, appearance, water)
    }

    private suspend fun publish(
        entries: List<FoodEntry>,
        profile: UserProfile?,
        appearance: String,
        water: WaterInputs,
    ) {
        val todaysEntries = entries.filter {
            it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
        }
        if (profile == null) {
            prefs.clearWidgetSnapshot()
        } else {
            val display = prefs.homeDisplayPreferences.first()
            val selection = display.homeTopNutrients
            val optionalGoals = prefs.optionalNutrientGoals.first()
            val theme = AppThemeColor.fromKey(prefs.appThemeColor.first())
            val (themeStart, themeEnd) = theme.widgetAccentColors(context, appearanceIsDark(appearance, context))
            val activity = homeActivityReader.readForDate(LocalDate.now())
            val effectiveCalories = profile.effectiveCalories
            // Energy Burn measured active average overrides the PAL estimate for the ADD_ACTIVE
            // split, so the widget mirrors the home gauge (goal − measured active → base).
            val measuredActive = prefs.healthEnergyMeasuredActive.first()
            val estimatedActive = measuredActive.takeIf { it > 0 } ?: profile.estimatedDailyActiveCalories
            val burn = HomeCalorieDisplay.resolveActiveBurn(
                display.calorieDisplayMode,
                activity,
                estimatedActive,
                prefs.manualActiveEntries.first()
                    .filter { it.date == LocalDate.now().toString() }
                    .sumOf { it.calories },
            )
            val mode = HomeCalorieDisplay.effectiveMode(display.calorieDisplayMode, burn)
            val sedentary = measuredActive.takeIf { it > 0 }
                ?.let { (effectiveCalories - it).coerceAtLeast(0) }
                ?: profile.sedentaryCalorieBudget(effectiveCalories)
            val gaugeBase = HomeCalorieDisplay.gaugeBaseGoal(
                mode,
                effectiveCalories,
                sedentary,
            )
            val activeCalories = burn?.calories ?: 0
            val effectiveGoal = HomeCalorieDisplay.effectiveGoal(mode, gaugeBase, activeCalories)
            // Expected-day target mirroring the hero: base + the larger of the active
            // norm and live burn. Written only when a live measured source exists
            // (Health Connect energy or debug data, even at 0 in the morning);
            // estimate/manual-only days keep the effective goal, which equals the
            // target there, and no active-burn caption in the widget.
            val liveStory = activity.energyLive
            val displayGoalTarget = if (mode == HomeCalorieDisplayMode.ADD_ACTIVE && liveStory) {
                HomeCalorieDisplay.expectedTarget(gaugeBase, estimatedActive, activeCalories)
            } else {
                null
            }
            val waterTodayMl = water.entries
                .filter { it.date.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
                .sumOf { it.milliliters }
            val waterGoalMl = if (water.dynamic.dynamicEnabled) {
                WaterGoalCalculator.dailyNetGoalMl(
                    baseSource = water.dynamic.baseSource,
                    weightKg = profile.weightKg,
                    manualBaseMl = water.manualGoalMl,
                    expectedHighC = water.dynamic.tempC,
                    activityLevel = profile.activityLevel,
                    useProfileActivity = water.dynamic.useProfileActivity,
                    foodGramsToday = WaterGoalCalculator.estimateDiaryGrams(todaysEntries),
                    foodWaterEnabled = water.dynamic.foodWaterEnabled,
                )
            } else {
                water.manualGoalMl
            }
            val weightUnit = prefs.weightUnit.first()
            // Next planned drink for the widget's "Next 300 ml · 15:24" line —
            // same re-derivation the reminder chain and Home use.
            val waterPlan = WaterReminderPlanner.next(
                prefs = prefs,
                foodRepository = foodRepository,
                profileRepository = profileRepository,
                waterRepository = waterRepository,
                weatherRepository = weatherRepository,
            )
            val snapshot = WidgetSnapshot(
                date = Instant.now(),
                dayStart = WidgetSnapshot.todayStart(),
                calories = todaysEntries.sumOf { it.calories },
                calorieGoal = effectiveCalories,
                protein = todaysEntries.sumOf { it.protein },
                proteinGoal = profile.effectiveProtein,
                carbs = todaysEntries.sumOf { it.carbs },
                carbsGoal = profile.effectiveCarbs,
                fat = todaysEntries.sumOf { it.fat },
                fatGoal = profile.effectiveFat,
                homeNutrients = selection.map { nutrient ->
                    WidgetNutrient(
                        id = nutrient.storageKey,
                        label = context.getString(nutrient.displayNameRes),
                        unit = context.getString(nutrient.unitRes),
                        value = nutrient.current(todaysEntries),
                        goal = nutrient.goal(profile, optionalGoals).toDouble()
                    )
                },
                themeStartHex = themeStart.toArgb() and 0xFFFFFF,
                themeEndHex = themeEnd.toArgb() and 0xFFFFFF,
                proteinHex = theme.macroPalette.proteinArgb(),
                carbsHex = theme.macroPalette.carbsArgb(),
                fatHex = theme.macroPalette.fatArgb(),
                fiberHex = theme.macroPalette.fiberArgb(),
                nutrientCardCount = display.nutrientCardCount,
                calorieDisplayMode = display.calorieDisplayMode.storageKey,
                effectiveCalorieGoal = effectiveGoal,
                activeCaloriesToday = activeCalories,
                gaugeBaseCalorieGoal = gaugeBase,
                activeCalorieSource = burn?.source?.storageKey,
                displayGoalTarget = displayGoalTarget,
                activeBurnTypical = displayGoalTarget?.let { estimatedActive },
                stepsToday = activity.steps,
                stepGoal = display.stepGoal,
                waterTrackingEnabled = water.enabled,
                waterCurrentMl = waterTodayMl,
                waterGoalMl = waterGoalMl.coerceAtLeast(1),
                waterUseMetric = weightUnit == "kg",
                waterNextFireAtMillis = waterPlan?.nextFireMillis,
                waterNextDrinkMl = waterPlan?.drinkMl ?: 0,
                appearanceMode = appearance,
            )
            prefs.setWidgetSnapshot(snapshot)
        }
        runCatching { CalorieAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "CalorieAppWidget.updateAll failed", it) }
        runCatching { ProteinAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "ProteinAppWidget.updateAll failed", it) }
        runCatching { AllMetricsAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "AllMetricsAppWidget.updateAll failed", it) }
        runCatching { WaterAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "WaterAppWidget.updateAll failed", it) }
    }

    private companion object {
        const val TAG = "FudAIWidget"
    }
}
