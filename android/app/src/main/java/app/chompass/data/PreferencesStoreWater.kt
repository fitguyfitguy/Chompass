package app.chompass.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.chompass.models.WaterEntry
import app.chompass.models.WaterGoalCalculator
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

// -- Dynamic goal + adaptive reminders (issue #3) --------------------
// Defaults keep the feature off and match today's behavior; see
// docs/WATER_DYNAMIC_GOAL_DESIGN.md for the formulas (WATER-DYN-A/B/C).

internal val PreferencesStore.waterDynamicEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.WATER_DYNAMIC_ENABLED, false)
internal suspend fun PreferencesStore.setWaterDynamicEnabledImpl(v: Boolean) =
    setBoolPref(Keys.WATER_DYNAMIC_ENABLED, v)

internal val PreferencesStore.waterBaseSourceImpl: Flow<String>
    get() = stringPref(Keys.WATER_BASE_SOURCE)
        .map { it ?: WaterGoalCalculator.BASE_SOURCE_WEIGHT }
internal suspend fun PreferencesStore.setWaterBaseSourceImpl(v: String) =
    setStringPref(Keys.WATER_BASE_SOURCE, v)

internal val PreferencesStore.waterManualTempCImpl: Flow<Int>
    get() = intPref(Keys.WATER_MANUAL_TEMP_C, 25)
internal suspend fun PreferencesStore.setWaterManualTempCImpl(v: Int) =
    setIntPref(Keys.WATER_MANUAL_TEMP_C, v.coerceIn(-10, 45))

internal val PreferencesStore.waterUseProfileActivityImpl: Flow<Boolean>
    get() = boolPref(Keys.WATER_USE_PROFILE_ACTIVITY, true)
internal suspend fun PreferencesStore.setWaterUseProfileActivityImpl(v: Boolean) =
    setBoolPref(Keys.WATER_USE_PROFILE_ACTIVITY, v)

internal val PreferencesStore.waterFoodWaterEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.WATER_FOOD_WATER_ENABLED, false)
internal suspend fun PreferencesStore.setWaterFoodWaterEnabledImpl(v: Boolean) =
    setBoolPref(Keys.WATER_FOOD_WATER_ENABLED, v)

internal val PreferencesStore.waterAwakeStartHourImpl: Flow<Int>
    get() = intPref(Keys.WATER_AWAKE_START_HOUR, 8)
internal suspend fun PreferencesStore.setWaterAwakeStartHourImpl(v: Int) =
    setIntPref(Keys.WATER_AWAKE_START_HOUR, v.coerceIn(0, 23))

internal val PreferencesStore.waterAwakeStartMinuteImpl: Flow<Int>
    get() = intPref(Keys.WATER_AWAKE_START_MINUTE, 0)
internal suspend fun PreferencesStore.setWaterAwakeStartMinuteImpl(v: Int) =
    setIntPref(Keys.WATER_AWAKE_START_MINUTE, v.coerceIn(0, 59))

internal val PreferencesStore.waterAwakeEndHourImpl: Flow<Int>
    get() = intPref(Keys.WATER_AWAKE_END_HOUR, 21)
internal suspend fun PreferencesStore.setWaterAwakeEndHourImpl(v: Int) =
    setIntPref(Keys.WATER_AWAKE_END_HOUR, v.coerceIn(0, 23))

internal val PreferencesStore.waterAwakeEndMinuteImpl: Flow<Int>
    get() = intPref(Keys.WATER_AWAKE_END_MINUTE, 0)
internal suspend fun PreferencesStore.setWaterAwakeEndMinuteImpl(v: Int) =
    setIntPref(Keys.WATER_AWAKE_END_MINUTE, v.coerceIn(0, 59))

internal val PreferencesStore.waterCupSizeMlImpl: Flow<Int>
    get() = intPref(Keys.WATER_CUP_SIZE_ML, WaterGoalCalculator.DEFAULT_CUP_SIZE_ML)
internal suspend fun PreferencesStore.setWaterCupSizeMlImpl(v: Int) =
    setIntPref(Keys.WATER_CUP_SIZE_ML, v.coerceIn(50, 1_000))

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
