package org.codeberg.fitguy.nofud.services

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import org.codeberg.fitguy.nofud.data.FoodRepository
import org.codeberg.fitguy.nofud.data.PreferencesStore
import org.codeberg.fitguy.nofud.data.ProfileRepository
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplay
import org.codeberg.fitguy.nofud.models.HomeTopNutrient
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WidgetNutrient
import org.codeberg.fitguy.nofud.models.WidgetSnapshot
import org.codeberg.fitguy.nofud.services.health.HomeActivityReader
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import org.codeberg.fitguy.nofud.ui.theme.widgetAccentColors
import org.codeberg.fitguy.nofud.models.WaterEntry
import org.codeberg.fitguy.nofud.widget.AllMetricsAppWidget
import org.codeberg.fitguy.nofud.widget.CalorieAppWidget
import org.codeberg.fitguy.nofud.widget.ProteinAppWidget
import org.codeberg.fitguy.nofud.widget.WaterAppWidget
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
    private val waterRepository: org.codeberg.fitguy.nofud.data.WaterRepository,
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
            val burn = HomeCalorieDisplay.resolveActiveBurn(
                display.calorieDisplayMode,
                activity,
                profile.estimatedDailyActiveCalories,
            )
            val mode = HomeCalorieDisplay.effectiveMode(display.calorieDisplayMode, burn)
            val gaugeBase = HomeCalorieDisplay.gaugeBaseGoal(
                mode,
                effectiveCalories,
                profile.sedentaryCalorieBudget(effectiveCalories),
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
