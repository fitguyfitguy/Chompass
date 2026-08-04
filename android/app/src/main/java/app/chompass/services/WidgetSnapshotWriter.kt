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
import app.chompass.models.UserProfile
import app.chompass.models.WidgetNutrient
import app.chompass.models.WidgetSnapshot
import app.chompass.services.health.HomeActivityReader
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.widgetAccentColors
import app.chompass.models.WaterEntry
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
) {
    fun observe() = combine(
        combine(
            foodRepository.entries,
            profileRepository.profile,
            prefs.homeDisplayPreferences,
            prefs.appThemeColor,
            prefs.optionalNutrientGoals,
        ) { entries, profile, _, _, _ ->
            entries to profile
        },
        combine(
            prefs.waterTrackingEnabled,
            prefs.waterDailyGoalMl,
            waterRepository.entries,
        ) { enabled, goalMl, waterEntries ->
            Triple(enabled, goalMl, waterEntries)
        },
    ) { core, water -> Triple(core.first, core.second, water) }
        .distinctUntilChanged()
        .onEach { (entries, profile, water) -> publish(entries, profile, water) }

    /**
     * Recompute and publish from current repos/prefs. Used when Theme Color is
     * System and the Material You primary changes without any pref/diary change
     * (wallpaper / palette refresh on resume).
     */
    suspend fun refresh() {
        val entries = foodRepository.entries.first()
        val profile = profileRepository.profile.first()
        val water = Triple(
            prefs.waterTrackingEnabled.first(),
            prefs.waterDailyGoalMl.first(),
            waterRepository.entries.first(),
        )
        publish(entries, profile, water)
    }

    private suspend fun publish(
        entries: List<FoodEntry>,
        profile: UserProfile?,
        water: Triple<Boolean, Int, List<WaterEntry>>,
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
            val (themeStart, themeEnd) = theme.widgetAccentColors(context)
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
            val waterTodayMl = water.third
                .filter { it.date.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
                .sumOf { it.milliliters }
            val weightUnit = prefs.weightUnit.first()
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
                stepsToday = activity.steps,
                stepGoal = display.stepGoal,
                waterTrackingEnabled = water.first,
                waterCurrentMl = waterTodayMl,
                waterGoalMl = water.second.coerceAtLeast(1),
                waterUseMetric = weightUnit == "kg",
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
