package app.chompass.data

import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Optional caffeine tracker (device-pass revision of the caffeine plan); default off. */
internal val PreferencesStore.caffeineTrackingEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.CAFFEINE_TRACKING_ENABLED, false)
internal suspend fun PreferencesStore.setCaffeineTrackingEnabledImpl(v: Boolean) =
    setBoolPref(Keys.CAFFEINE_TRACKING_ENABLED, v)

/** Daily mg limit (the caffeine analogue of the nicotine count limit). 0 = no limit. */
internal val PreferencesStore.caffeineDailyLimitMgImpl: Flow<Int>
    get() = intPref(Keys.CAFFEINE_DAILY_LIMIT_MG, 400)
internal suspend fun PreferencesStore.setCaffeineDailyLimitMgImpl(v: Int) =
    setIntPref(Keys.CAFFEINE_DAILY_LIMIT_MG, v.coerceAtLeast(0))

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
