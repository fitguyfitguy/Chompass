package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import kotlinx.coroutines.flow.Flow

// -- Weight entries ---------------------------------------------------
internal val PreferencesStore.weightEntriesImpl: Flow<List<WeightEntry>>
    get() = listPref(Keys.WEIGHT_ENTRIES, WeightEntry.serializer())

internal suspend fun PreferencesStore.setWeightEntriesImpl(entries: List<WeightEntry>) =
    setListPref(Keys.WEIGHT_ENTRIES, WeightEntry.serializer(), entries)

// -- Body fat entries --------------------------------------------------
internal val PreferencesStore.bodyFatEntriesImpl: Flow<List<BodyFatEntry>>
    get() = listPref(Keys.BODY_FAT_ENTRIES, BodyFatEntry.serializer())

internal suspend fun PreferencesStore.setBodyFatEntriesImpl(entries: List<BodyFatEntry>) =
    setListPref(Keys.BODY_FAT_ENTRIES, BodyFatEntry.serializer(), entries)

// -- Body measurement (circumference) entries --------------------------
internal val PreferencesStore.bodyMeasurementsImpl: Flow<List<BodyMeasurement>>
    get() = listPref(Keys.BODY_MEASUREMENTS, BodyMeasurement.serializer())

internal suspend fun PreferencesStore.setBodyMeasurementsImpl(entries: List<BodyMeasurement>) =
    setListPref(Keys.BODY_MEASUREMENTS, BodyMeasurement.serializer(), entries)
