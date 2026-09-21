package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.DailyNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.YearMonth
import java.util.UUID

/** Home note-card visibility (optional daily notes; default off). */
internal val PreferencesStore.dailyNotesEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.DAILY_NOTES_ENABLED, false)
internal suspend fun PreferencesStore.setDailyNotesEnabledImpl(v: Boolean) =
    setBoolPref(Keys.DAILY_NOTES_ENABLED, v)

/** Suggest meals by clock time; default on. */
internal val PreferencesStore.mealTimesEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.MEAL_TIMES_ENABLED, true)
internal suspend fun PreferencesStore.setMealTimesEnabledImpl(v: Boolean) =
    setBoolPref(Keys.MEAL_TIMES_ENABLED, v)

/** Optional meal planning mode; default off. */
internal val PreferencesStore.mealPlanningEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.MEAL_PLANNING_ENABLED, false)
internal suspend fun PreferencesStore.setMealPlanningEnabledImpl(v: Boolean) =
    setBoolPref(Keys.MEAL_PLANNING_ENABLED, v)

internal val PreferencesStore.noteEntriesImpl: Flow<List<DailyNote>>
    get() = flow {
        emitAll(noteBucketStore.allFlow())
    }

/** Full-dataset replace for imports/merges (mirrors [PreferencesStore.setWaterEntriesImpl]). */
internal suspend fun PreferencesStore.setNoteEntriesImpl(entries: List<DailyNote>) {
    noteBucketStore.replaceAll(entries.groupBy { YearMonth.from(it.date) })
}

/**
 * Month-scoped daily-note write — one record per day, so a note touches
 * exactly one ~KB bucket file instead of re-encoding anything global. Same
 * upsert/removal-by-id semantics as the water/food bucket helpers; the stable
 * per-day id ([DailyNote.idFor]) makes upsert-by-id == upsert-by-date.
 */
internal suspend fun PreferencesStore.applyNoteBucketChangesImpl(
    upsertsByMonth: Map<YearMonth, List<DailyNote>> = emptyMap(),
    removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
) {
    if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
    noteBucketStore.applyChanges(upsertsByMonth, removalIdsByMonth)
}

internal val PreferencesStore.untrackedDaysImpl: Flow<Set<String>>
    get() = dataStore.data.map { it[Keys.UNTRACKED_DAYS] ?: emptySet() }

internal suspend fun PreferencesStore.setUntrackedDaysImpl(dates: Set<String>) {
    dataStore.edit { prefs ->
        if (dates.isEmpty()) prefs.remove(Keys.UNTRACKED_DAYS)
        else prefs[Keys.UNTRACKED_DAYS] = dates
    }
}

internal val PreferencesStore.untrackedKcalByDayImpl: Flow<Map<String, Int>>
    get() = dataStore.data.map { prefs ->
        prefs[Keys.UNTRACKED_KCAL_BY_DAY]?.let { raw ->
            runCatching {
                json.decodeFromString(
                    MapSerializer(String.serializer(), Int.serializer()),
                    raw,
                )
            }.getOrNull()
        } ?: emptyMap()
    }

internal suspend fun PreferencesStore.setUntrackedKcalByDayImpl(map: Map<String, Int>) {
    dataStore.edit { prefs ->
        if (map.isEmpty()) {
            prefs.remove(Keys.UNTRACKED_KCAL_BY_DAY)
        } else {
            prefs[Keys.UNTRACKED_KCAL_BY_DAY] = json.encodeToString(
                MapSerializer(String.serializer(), Int.serializer()),
                map,
            )
        }
    }
}
