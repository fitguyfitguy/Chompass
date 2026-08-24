package app.chompass.data

import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

internal val PreferencesStore.nicotineTrackingEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.NICOTINE_TRACKING_ENABLED, false)
internal suspend fun PreferencesStore.setNicotineTrackingEnabledImpl(v: Boolean) =
    setBoolPref(Keys.NICOTINE_TRACKING_ENABLED, v)

/** Daily count limit (the nicotine analogue of the water goal). 0 = no limit. */
internal val PreferencesStore.nicotineDailyLimitImpl: Flow<Int>
    get() = intPref(Keys.NICOTINE_DAILY_LIMIT, 0)
internal suspend fun PreferencesStore.setNicotineDailyLimitImpl(v: Int) =
    setIntPref(Keys.NICOTINE_DAILY_LIMIT, v.coerceAtLeast(0))

/** Quick-log chips on the Add Food hub (mirrors water quick presets). */
internal val PreferencesStore.nicotineQuickKindsImpl: Flow<List<NicotineKind>>
    get() = stringPref(Keys.NICOTINE_QUICK_KINDS)
        .map { NicotineKind.quickKindsFromStorage(it) }
internal suspend fun PreferencesStore.setNicotineQuickKindsImpl(kinds: List<NicotineKind>) =
    setStringPref(Keys.NICOTINE_QUICK_KINDS, NicotineKind.quickKindsToStorage(kinds))

internal val PreferencesStore.nicotineEntriesImpl: Flow<List<NicotineEntry>>
    get() = nicotineBucketStore.allFlow()

internal suspend fun PreferencesStore.setNicotineEntriesImpl(entries: List<NicotineEntry>) {
    nicotineBucketStore.replaceAll(
        entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
    )
}

/**
 * Month-scoped nicotine write — a log touches exactly one bucket file instead
 * of re-encoding the whole history (same semantics as the water bucket helper).
 */
internal suspend fun PreferencesStore.applyNicotineBucketChangesImpl(
    upsertsByMonth: Map<YearMonth, List<NicotineEntry>> = emptyMap(),
    removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
) {
    if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
    nicotineBucketStore.applyChanges(upsertsByMonth, removalIdsByMonth)
}
