package app.chompass.data

import kotlinx.coroutines.flow.Flow

// -- Notifications ----------------------------------------------------
internal val PreferencesStore.notificationsEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.NOTIFICATIONS_ENABLED, false)
internal suspend fun PreferencesStore.setNotificationsEnabledImpl(v: Boolean) =
    setBoolPref(Keys.NOTIFICATIONS_ENABLED, v)

internal val PreferencesStore.streakReminderEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.STREAK_ENABLED, false)
internal suspend fun PreferencesStore.setStreakReminderEnabledImpl(v: Boolean) =
    setBoolPref(Keys.STREAK_ENABLED, v)

internal val PreferencesStore.streakReminderHourImpl: Flow<Int>
    get() = intPref(Keys.STREAK_HOUR, 19)
internal suspend fun PreferencesStore.setStreakReminderHourImpl(v: Int) =
    setIntPref(Keys.STREAK_HOUR, v)

internal val PreferencesStore.streakReminderMinuteImpl: Flow<Int>
    get() = intPref(Keys.STREAK_MINUTE, 0)
internal suspend fun PreferencesStore.setStreakReminderMinuteImpl(v: Int) =
    setIntPref(Keys.STREAK_MINUTE, v)

internal val PreferencesStore.dailySummaryEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.DAILY_ENABLED, false)
internal suspend fun PreferencesStore.setDailySummaryEnabledImpl(v: Boolean) =
    setBoolPref(Keys.DAILY_ENABLED, v)

internal val PreferencesStore.dailySummaryHourImpl: Flow<Int>
    get() = intPref(Keys.DAILY_HOUR, 21)
internal suspend fun PreferencesStore.setDailySummaryHourImpl(v: Int) =
    setIntPref(Keys.DAILY_HOUR, v)

internal val PreferencesStore.dailySummaryMinuteImpl: Flow<Int>
    get() = intPref(Keys.DAILY_MINUTE, 0)
internal suspend fun PreferencesStore.setDailySummaryMinuteImpl(v: Int) =
    setIntPref(Keys.DAILY_MINUTE, v)

internal val PreferencesStore.weightReminderEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.WEIGHT_REMINDER_ENABLED, true)
internal suspend fun PreferencesStore.setWeightReminderEnabledImpl(v: Boolean) =
    setBoolPref(Keys.WEIGHT_REMINDER_ENABLED, v)

internal val PreferencesStore.bodyFatReminderEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.BODY_FAT_REMINDER_ENABLED, true)
internal suspend fun PreferencesStore.setBodyFatReminderEnabledImpl(v: Boolean) =
    setBoolPref(Keys.BODY_FAT_REMINDER_ENABLED, v)

internal val PreferencesStore.goalReachedNotificationsEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.GOAL_REACHED_NOTIFICATIONS_ENABLED, true)
internal suspend fun PreferencesStore.setGoalReachedNotificationsEnabledImpl(v: Boolean) =
    setBoolPref(Keys.GOAL_REACHED_NOTIFICATIONS_ENABLED, v)

internal val PreferencesStore.appUpdateNotificationsEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.APP_UPDATE_NOTIFICATIONS_ENABLED, true)
internal suspend fun PreferencesStore.setAppUpdateNotificationsEnabledImpl(v: Boolean) =
    setBoolPref(Keys.APP_UPDATE_NOTIFICATIONS_ENABLED, v)

/** Last app version a "new update" notification was posted for. */
internal val PreferencesStore.lastNotifiedUpdateVersionImpl: Flow<String?>
    get() = stringPref(Keys.LAST_NOTIFIED_UPDATE_VERSION)
internal suspend fun PreferencesStore.setLastNotifiedUpdateVersionImpl(v: String) =
    setStringPref(Keys.LAST_NOTIFIED_UPDATE_VERSION, v)
