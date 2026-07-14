package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import org.codeberg.fitguy.nofud.models.WaterEntry
import org.codeberg.fitguy.nofud.models.WaterQuickPresets

internal val PreferencesStore.waterTrackingEnabledImpl: Flow<Boolean> get() =
    dataStore.data.map { it[Keys.WATER_TRACKING_ENABLED] ?: false }

internal suspend fun PreferencesStore.setWaterTrackingEnabledImpl(v: Boolean) {
    dataStore.edit { it[Keys.WATER_TRACKING_ENABLED] = v }
}

internal val PreferencesStore.waterDailyGoalMlImpl: Flow<Int> get() =
    dataStore.data.map { it[Keys.WATER_DAILY_GOAL_ML] ?: 2_000 }

internal suspend fun PreferencesStore.setWaterDailyGoalMlImpl(v: Int) {
    dataStore.edit { it[Keys.WATER_DAILY_GOAL_ML] = v.coerceAtLeast(1) }
}

internal val PreferencesStore.waterReminderEnabledImpl: Flow<Boolean> get() =
    dataStore.data.map { it[Keys.WATER_REMINDER_ENABLED] ?: false }

internal suspend fun PreferencesStore.setWaterReminderEnabledImpl(v: Boolean) {
    dataStore.edit { it[Keys.WATER_REMINDER_ENABLED] = v }
}

internal val PreferencesStore.waterReminderHourImpl: Flow<Int> get() =
    dataStore.data.map { it[Keys.WATER_REMINDER_HOUR] ?: 14 }

internal suspend fun PreferencesStore.setWaterReminderHourImpl(v: Int) {
    dataStore.edit { it[Keys.WATER_REMINDER_HOUR] = v }
}

internal val PreferencesStore.waterReminderMinuteImpl: Flow<Int> get() =
    dataStore.data.map { it[Keys.WATER_REMINDER_MINUTE] ?: 0 }

internal suspend fun PreferencesStore.setWaterReminderMinuteImpl(v: Int) {
    dataStore.edit { it[Keys.WATER_REMINDER_MINUTE] = v }
}

internal val PreferencesStore.waterQuickPresetsMlImpl: Flow<List<Int>> get() =
    dataStore.data.map { prefs ->
        WaterQuickPresets.fromStorage(prefs[Keys.WATER_QUICK_PRESETS_ML]).amountsMl
    }

internal suspend fun PreferencesStore.setWaterQuickPresetsMlImpl(amountsMl: List<Int>) {
    val presets = WaterQuickPresets(amountsMl).validatedOrDefault()
    dataStore.edit {
        it[Keys.WATER_QUICK_PRESETS_ML] = WaterQuickPresets.toStorage(presets)
    }
}

internal val PreferencesStore.waterEntriesImpl: Flow<List<WaterEntry>> get() = dataStore.data.map { prefs ->
    prefs[Keys.WATER_ENTRIES]?.let {
        runCatching { json.decodeFromString(ListSerializer(WaterEntry.serializer()), it) }.getOrNull()
    } ?: emptyList()
}

internal suspend fun PreferencesStore.setWaterEntriesImpl(entries: List<WaterEntry>) {
    dataStore.edit {
        it[Keys.WATER_ENTRIES] = json.encodeToString(ListSerializer(WaterEntry.serializer()), entries)
    }
}
