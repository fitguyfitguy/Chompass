package app.chompass.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// -- Notifications ----------------------------------------------------
internal val PreferencesStore.notificationsEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: false }
internal suspend fun PreferencesStore.setNotificationsEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = v } }

internal val PreferencesStore.streakReminderEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.STREAK_ENABLED] ?: false }
internal suspend fun PreferencesStore.setStreakReminderEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.STREAK_ENABLED] = v } }

internal val PreferencesStore.streakReminderHourImpl: Flow<Int> get() = dataStore.data.map { it[Keys.STREAK_HOUR] ?: 19 }
internal suspend fun PreferencesStore.setStreakReminderHourImpl(v: Int) { dataStore.edit { it[Keys.STREAK_HOUR] = v } }

internal val PreferencesStore.streakReminderMinuteImpl: Flow<Int> get() = dataStore.data.map { it[Keys.STREAK_MINUTE] ?: 0 }
internal suspend fun PreferencesStore.setStreakReminderMinuteImpl(v: Int) { dataStore.edit { it[Keys.STREAK_MINUTE] = v } }

internal val PreferencesStore.dailySummaryEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.DAILY_ENABLED] ?: false }
internal suspend fun PreferencesStore.setDailySummaryEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.DAILY_ENABLED] = v } }

internal val PreferencesStore.dailySummaryHourImpl: Flow<Int> get() = dataStore.data.map { it[Keys.DAILY_HOUR] ?: 21 }
internal suspend fun PreferencesStore.setDailySummaryHourImpl(v: Int) { dataStore.edit { it[Keys.DAILY_HOUR] = v } }

internal val PreferencesStore.dailySummaryMinuteImpl: Flow<Int> get() = dataStore.data.map { it[Keys.DAILY_MINUTE] ?: 0 }
internal suspend fun PreferencesStore.setDailySummaryMinuteImpl(v: Int) { dataStore.edit { it[Keys.DAILY_MINUTE] = v } }

internal val PreferencesStore.weightReminderEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.WEIGHT_REMINDER_ENABLED] ?: true }
internal suspend fun PreferencesStore.setWeightReminderEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.WEIGHT_REMINDER_ENABLED] = v } }

internal val PreferencesStore.bodyFatReminderEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.BODY_FAT_REMINDER_ENABLED] ?: true }
internal suspend fun PreferencesStore.setBodyFatReminderEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.BODY_FAT_REMINDER_ENABLED] = v } }

internal val PreferencesStore.goalReachedNotificationsEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.GOAL_REACHED_NOTIFICATIONS_ENABLED] ?: true }
internal suspend fun PreferencesStore.setGoalReachedNotificationsEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.GOAL_REACHED_NOTIFICATIONS_ENABLED] = v } }

internal val PreferencesStore.appUpdateNotificationsEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.APP_UPDATE_NOTIFICATIONS_ENABLED] ?: true }
internal suspend fun PreferencesStore.setAppUpdateNotificationsEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.APP_UPDATE_NOTIFICATIONS_ENABLED] = v } }

    /// Last app version a "new update" notification was posted for — so it fires at most once per
    /// version even though the update check runs on every launch.
internal val PreferencesStore.lastNotifiedUpdateVersionImpl: Flow<String?> get() = dataStore.data.map { it[Keys.LAST_NOTIFIED_UPDATE_VERSION] }
internal suspend fun PreferencesStore.setLastNotifiedUpdateVersionImpl(v: String) { dataStore.edit { it[Keys.LAST_NOTIFIED_UPDATE_VERSION] = v } }

    
