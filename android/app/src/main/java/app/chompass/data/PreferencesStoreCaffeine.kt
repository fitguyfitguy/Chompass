package app.chompass.data

import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.models.OptionalNutrient
import app.chompass.models.OptionalNutrientGoals
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Optional caffeine tracker (device-pass revision of the caffeine plan); default off. */
internal val PreferencesStore.caffeineTrackingEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.CAFFEINE_TRACKING_ENABLED, false)
internal suspend fun PreferencesStore.setCaffeineTrackingEnabledImpl(v: Boolean) =
    setBoolPref(Keys.CAFFEINE_TRACKING_ENABLED, v)

/**
 * WS5 one-time migration: the legacy tracker daily-limit pref
 * (caffeineDailyLimitMg) was an alias of optionalNutrientGoals.caffeine, and
 * the two could disagree. A customized legacy value wins once over the
 * still-default goal (400); then the legacy key is dropped and never written
 * again — the Goals & Nutrition caffeine goal is the single daily-max knob.
 * The read-side merge lives in SettingsPrefsHydration.toSettingsHydration.
 */
internal suspend fun PreferencesStore.migrateCaffeineDailyLimitIfNeededImpl() {
    dataStore.edit { prefs ->
        val legacy = prefs[Keys.CAFFEINE_DAILY_LIMIT_MG] ?: return@edit
        prefs.remove(Keys.CAFFEINE_DAILY_LIMIT_MG)
        if (legacy == OptionalNutrient.CAFFEINE.defaultGoal) return@edit
        val goals = prefs[Keys.OPTIONAL_NUTRIENT_GOALS]?.let {
            runCatching { json.decodeFromString(OptionalNutrientGoals.serializer(), it) }.getOrNull()
        } ?: OptionalNutrientGoals.Default
        if (goals.caffeine != OptionalNutrient.CAFFEINE.defaultGoal) return@edit
        prefs[Keys.OPTIONAL_NUTRIENT_GOALS] =
            json.encodeToString(OptionalNutrientGoals.serializer(), goals.copy(caffeine = legacy))
    }
}

/** Quick-log chips on the Add Food hub (mirrors nicotine quick kinds). */
internal val PreferencesStore.caffeineQuickKindsImpl: Flow<List<CaffeineKind>>
    get() = stringPref(Keys.CAFFEINE_QUICK_KINDS)
        .map { CaffeineKind.quickKindsFromStorage(it) }
internal suspend fun PreferencesStore.setCaffeineQuickKindsImpl(kinds: List<CaffeineKind>) =
    setStringPref(Keys.CAFFEINE_QUICK_KINDS, CaffeineKind.quickKindsToStorage(kinds))

internal val PreferencesStore.caffeineEntriesImpl: Flow<List<CaffeineEntry>>
    get() = caffeineBucketStore.allFlow()

internal suspend fun PreferencesStore.setCaffeineEntriesImpl(entries: List<CaffeineEntry>) {
    caffeineBucketStore.replaceAll(
        entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
    )
}

/** Month-scoped caffeine write — a log touches exactly one bucket file. */
internal suspend fun PreferencesStore.applyCaffeineBucketChangesImpl(
    upsertsByMonth: Map<YearMonth, List<CaffeineEntry>> = emptyMap(),
    removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
) {
    if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
    caffeineBucketStore.applyChanges(upsertsByMonth, removalIdsByMonth)
}
