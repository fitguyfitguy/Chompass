package app.chompass.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.chompass.models.WaterEntry
import app.chompass.models.WaterQuickPresets

internal val PreferencesStore.waterTrackingEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.WATER_TRACKING_ENABLED, false)
internal suspend fun PreferencesStore.setWaterTrackingEnabledImpl(v: Boolean) =
    setBoolPref(Keys.WATER_TRACKING_ENABLED, v)

internal val PreferencesStore.waterDailyGoalMlImpl: Flow<Int>
    get() = intPref(Keys.WATER_DAILY_GOAL_ML, 2_000)
internal suspend fun PreferencesStore.setWaterDailyGoalMlImpl(v: Int) =
    setIntPref(Keys.WATER_DAILY_GOAL_ML, v.coerceAtLeast(1))

internal val PreferencesStore.waterReminderEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.WATER_REMINDER_ENABLED, false)
internal suspend fun PreferencesStore.setWaterReminderEnabledImpl(v: Boolean) =
    setBoolPref(Keys.WATER_REMINDER_ENABLED, v)

internal val PreferencesStore.waterReminderHourImpl: Flow<Int>
    get() = intPref(Keys.WATER_REMINDER_HOUR, 14)
internal suspend fun PreferencesStore.setWaterReminderHourImpl(v: Int) =
    setIntPref(Keys.WATER_REMINDER_HOUR, v)

internal val PreferencesStore.waterReminderMinuteImpl: Flow<Int>
    get() = intPref(Keys.WATER_REMINDER_MINUTE, 0)
internal suspend fun PreferencesStore.setWaterReminderMinuteImpl(v: Int) =
    setIntPref(Keys.WATER_REMINDER_MINUTE, v)

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

internal val PreferencesStore.waterEntriesImpl: Flow<List<WaterEntry>>
    get() = listPref(Keys.WATER_ENTRIES, WaterEntry.serializer())

internal suspend fun PreferencesStore.setWaterEntriesImpl(entries: List<WaterEntry>) =
    setListPref(Keys.WATER_ENTRIES, WaterEntry.serializer(), entries)
