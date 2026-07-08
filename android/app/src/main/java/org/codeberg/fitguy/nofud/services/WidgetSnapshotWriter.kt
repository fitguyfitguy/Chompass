package org.codeberg.fitguy.nofud.services

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import org.codeberg.fitguy.nofud.data.FoodRepository
import org.codeberg.fitguy.nofud.data.PreferencesStore
import org.codeberg.fitguy.nofud.data.ProfileRepository
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.HomeTopNutrient
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WidgetNutrient
import org.codeberg.fitguy.nofud.models.WidgetSnapshot
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import org.codeberg.fitguy.nofud.widget.AllMetricsAppWidget
import org.codeberg.fitguy.nofud.widget.CalorieAppWidget
import org.codeberg.fitguy.nofud.widget.ProteinAppWidget
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
    private val profileRepository: ProfileRepository
) {
    fun observe() = combine(
        foodRepository.entries,
        profileRepository.profile,
        prefs.homeTopNutrients,
        prefs.appThemeColor,
        prefs.optionalNutrientGoals
    ) { entries, profile, _, _, _ ->
        // Selection / theme / goals are re-read inside publish; they're combined
        // here only so their changes re-trigger a snapshot write.
        entries to profile
    }
        .distinctUntilChanged()
        .onEach { (entries, profile) -> publish(entries, profile) }

    suspend fun publish(entries: List<FoodEntry>, profile: UserProfile?) {
        val todaysEntries = entries.filter {
            it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
        }
        if (profile == null) {
            prefs.clearWidgetSnapshot()
        } else {
            val selection = HomeTopNutrient.fromStorage(prefs.homeTopNutrients.first())
            val optionalGoals = prefs.optionalNutrientGoals.first()
            val theme = AppThemeColor.fromKey(prefs.appThemeColor.first())
            val snapshot = WidgetSnapshot(
                date = Instant.now(),
                dayStart = WidgetSnapshot.todayStart(),
                calories = todaysEntries.sumOf { it.calories },
                calorieGoal = profile.effectiveCalories,
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
                themeStartHex = theme.start.toArgb() and 0xFFFFFF,
                themeEndHex = theme.end.toArgb() and 0xFFFFFF,
                proteinHex = theme.macroPalette.proteinArgb(),
                carbsHex = theme.macroPalette.carbsArgb(),
                fatHex = theme.macroPalette.fatArgb(),
                fiberHex = theme.macroPalette.fiberArgb(),
            )
            prefs.setWidgetSnapshot(snapshot)
        }
        runCatching { CalorieAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "CalorieAppWidget.updateAll failed", it) }
        runCatching { ProteinAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "ProteinAppWidget.updateAll failed", it) }
        runCatching { AllMetricsAppWidget().updateAll(context) }
            .onFailure { Log.e(TAG, "AllMetricsAppWidget.updateAll failed", it) }
    }

    private companion object {
        const val TAG = "FudAIWidget"
    }
}
