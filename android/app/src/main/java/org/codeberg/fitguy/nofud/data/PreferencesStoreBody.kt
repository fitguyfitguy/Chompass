package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import org.codeberg.fitguy.nofud.models.BodyFatEntry
import org.codeberg.fitguy.nofud.models.BodyMeasurement
import org.codeberg.fitguy.nofud.models.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

// -- Weight entries ---------------------------------------------------
internal val PreferencesStore.weightEntriesImpl: Flow<List<WeightEntry>> get() = dataStore.data.map { prefs ->
        prefs[Keys.WEIGHT_ENTRIES]?.let {
            runCatching { json.decodeFromString(ListSerializer(WeightEntry.serializer()), it) }.getOrNull()
        } ?: emptyList()
    }

internal suspend fun PreferencesStore.setWeightEntriesImpl(entries: List<WeightEntry>) {
        dataStore.edit { it[Keys.WEIGHT_ENTRIES] = json.encodeToString(ListSerializer(WeightEntry.serializer()), entries) }
    }

    // -- Body fat entries --------------------------------------------------
internal val PreferencesStore.bodyFatEntriesImpl: Flow<List<BodyFatEntry>> get() = dataStore.data.map { prefs ->
        prefs[Keys.BODY_FAT_ENTRIES]?.let {
            runCatching { json.decodeFromString(ListSerializer(BodyFatEntry.serializer()), it) }.getOrNull()
        } ?: emptyList()
    }

internal suspend fun PreferencesStore.setBodyFatEntriesImpl(entries: List<BodyFatEntry>) {
        dataStore.edit { it[Keys.BODY_FAT_ENTRIES] = json.encodeToString(ListSerializer(BodyFatEntry.serializer()), entries) }
    }

    // -- Body measurement (circumference) entries --------------------------
internal val PreferencesStore.bodyMeasurementsImpl: Flow<List<BodyMeasurement>> get() = dataStore.data.map { prefs ->
        prefs[Keys.BODY_MEASUREMENTS]?.let {
            runCatching { json.decodeFromString(ListSerializer(BodyMeasurement.serializer()), it) }.getOrNull()
        } ?: emptyList()
    }

internal suspend fun PreferencesStore.setBodyMeasurementsImpl(entries: List<BodyMeasurement>) {
        dataStore.edit { it[Keys.BODY_MEASUREMENTS] = json.encodeToString(ListSerializer(BodyMeasurement.serializer()), entries) }
    }

    
