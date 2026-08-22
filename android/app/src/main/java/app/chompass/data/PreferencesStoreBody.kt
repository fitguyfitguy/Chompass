package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

// -- Weight entries ---------------------------------------------------
internal val PreferencesStore.weightEntriesImpl: Flow<List<WeightEntry>>
    get() = flow {
        migrateBucketsToFilesIfNeeded()
        emitAll(weightBucketStore.allFlow())
    }

internal suspend fun PreferencesStore.setWeightEntriesImpl(entries: List<WeightEntry>) {
    migrateBucketsToFilesIfNeeded()
    weightBucketStore.replaceAll(
        entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
    )
}

/** Month-scoped weight write (one bucket file) — the daily weigh-in path. */
internal suspend fun PreferencesStore.applyWeightBucketChangesImpl(
    upsertsByMonth: Map<YearMonth, List<WeightEntry>> = emptyMap(),
    removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
) {
    if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
    migrateBucketsToFilesIfNeeded()
    weightBucketStore.applyChanges(upsertsByMonth, removalIdsByMonth)
}

// -- Body fat entries --------------------------------------------------
internal val PreferencesStore.bodyFatEntriesImpl: Flow<List<BodyFatEntry>>
    get() = flow {
        migrateBucketsToFilesIfNeeded()
        emitAll(bodyFatBucketStore.allFlow())
    }

internal suspend fun PreferencesStore.setBodyFatEntriesImpl(entries: List<BodyFatEntry>) {
    migrateBucketsToFilesIfNeeded()
    bodyFatBucketStore.replaceAll(
        entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
    )
}

/** Month-scoped body-fat write (one bucket file). */
internal suspend fun PreferencesStore.applyBodyFatBucketChangesImpl(
    upsertsByMonth: Map<YearMonth, List<BodyFatEntry>> = emptyMap(),
    removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
) {
    if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
    migrateBucketsToFilesIfNeeded()
    bodyFatBucketStore.applyChanges(upsertsByMonth, removalIdsByMonth)
}

// -- Body measurement (circumference) entries --------------------------
internal val PreferencesStore.bodyMeasurementsImpl: Flow<List<BodyMeasurement>>
    get() = flow {
        migrateBucketsToFilesIfNeeded()
        emitAll(measurementBucketStore.allFlow())
    }

internal suspend fun PreferencesStore.setBodyMeasurementsImpl(entries: List<BodyMeasurement>) {
    migrateBucketsToFilesIfNeeded()
    measurementBucketStore.replaceAll(
        entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
    )
}

/** Month-scoped measurement write (one bucket file). */
internal suspend fun PreferencesStore.applyMeasurementBucketChangesImpl(
    upsertsByMonth: Map<YearMonth, List<BodyMeasurement>> = emptyMap(),
    removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
) {
    if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
    migrateBucketsToFilesIfNeeded()
    measurementBucketStore.applyChanges(upsertsByMonth, removalIdsByMonth)
}
