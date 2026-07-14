package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.codeberg.fitguy.nofud.models.MealSchedule

internal val PreferencesStore.mealScheduleImpl: Flow<MealSchedule> get() = dataStore.data.map { prefs ->
    MealSchedule(
        breakfastStartMinutes = prefs[Keys.MEAL_BREAKFAST_START] ?: MealSchedule.DEFAULT_BREAKFAST_START,
        lunchStartMinutes = prefs[Keys.MEAL_LUNCH_START] ?: MealSchedule.DEFAULT_LUNCH_START,
        dinnerStartMinutes = prefs[Keys.MEAL_DINNER_START] ?: MealSchedule.DEFAULT_DINNER_START,
        snackStartMinutes = prefs[Keys.MEAL_SNACK_START] ?: MealSchedule.DEFAULT_SNACK_START,
    ).validatedOrDefault()
}

internal suspend fun PreferencesStore.setMealScheduleImpl(schedule: MealSchedule) {
    val validated = schedule.validatedOrDefault()
    dataStore.edit {
        it[Keys.MEAL_BREAKFAST_START] = validated.breakfastStartMinutes
        it[Keys.MEAL_LUNCH_START] = validated.lunchStartMinutes
        it[Keys.MEAL_DINNER_START] = validated.dinnerStartMinutes
        it[Keys.MEAL_SNACK_START] = validated.snackStartMinutes
    }
}
