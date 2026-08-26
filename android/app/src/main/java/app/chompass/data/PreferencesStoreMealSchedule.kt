package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.MealCatalog
import app.chompass.models.MealSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

internal fun PreferencesStore.parseMealCatalog(
    raw: String?,
    breakfast: Int,
    lunch: Int,
    dinner: Int,
    snack: Int,
): MealCatalog {
    if (!raw.isNullOrBlank()) {
        val parsed = runCatching { json.decodeFromString<MealCatalog>(raw) }.getOrNull()
        if (parsed != null) return parsed.validatedOrDefault()
    }
    return MealCatalog.fromLegacySchedule(
        MealSchedule(
            breakfastStartMinutes = breakfast,
            lunchStartMinutes = lunch,
            dinnerStartMinutes = dinner,
            snackStartMinutes = snack,
        ),
    ).validatedOrDefault()
}

internal val PreferencesStore.mealCatalogImpl: Flow<MealCatalog> get() = dataStore.data.map { prefs ->
    parseMealCatalog(
        raw = prefs[Keys.MEAL_CATALOG],
        breakfast = prefs[Keys.MEAL_BREAKFAST_START] ?: MealSchedule.DEFAULT_BREAKFAST_START,
        lunch = prefs[Keys.MEAL_LUNCH_START] ?: MealSchedule.DEFAULT_LUNCH_START,
        dinner = prefs[Keys.MEAL_DINNER_START] ?: MealSchedule.DEFAULT_DINNER_START,
        snack = prefs[Keys.MEAL_SNACK_START] ?: MealSchedule.DEFAULT_SNACK_START,
    )
}

internal val PreferencesStore.mealScheduleImpl: Flow<MealSchedule> get() =
    mealCatalogImpl.map { it.toLegacySchedule() }

internal suspend fun PreferencesStore.setMealCatalogImpl(catalog: MealCatalog) {
    val validated = catalog.validatedOrDefault()
    val legacy = validated.toLegacySchedule()
    dataStore.edit {
        it[Keys.MEAL_CATALOG] = json.encodeToString(validated)
        it[Keys.MEAL_BREAKFAST_START] = legacy.breakfastStartMinutes
        it[Keys.MEAL_LUNCH_START] = legacy.lunchStartMinutes
        it[Keys.MEAL_DINNER_START] = legacy.dinnerStartMinutes
        it[Keys.MEAL_SNACK_START] = legacy.snackStartMinutes
    }
}

internal suspend fun PreferencesStore.setMealScheduleImpl(schedule: MealSchedule) {
    setMealCatalogImpl(MealCatalog.fromLegacySchedule(schedule))
}
