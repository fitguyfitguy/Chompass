package app.chompass.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.chompass.models.FastingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Maximum selectable fasting goal in hours (wheel upper bound). */
const val MAX_FASTING_GOAL_HOURS = 48

internal val PreferencesStore.fastingEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_ENABLED, false)
internal suspend fun PreferencesStore.setFastingEnabledImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_ENABLED, v)

/** Goal length in hours; 0 = no goal (off by default). */
internal val PreferencesStore.fastingGoalHoursImpl: Flow<Int>
    get() = intPref(Keys.FASTING_GOAL_HOURS, 0)
internal suspend fun PreferencesStore.setFastingGoalHoursImpl(v: Int) =
    setIntPref(Keys.FASTING_GOAL_HOURS, v.coerceIn(0, MAX_FASTING_GOAL_HOURS))

/** Goal-reached notification; only meaningful when the goal > 0. */
internal val PreferencesStore.fastingGoalNotificationEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_GOAL_NOTIFICATION_ENABLED, true)
internal suspend fun PreferencesStore.setFastingGoalNotificationEnabledImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_GOAL_NOTIFICATION_ENABLED, v)

/** Session state as a pure function of the three scalar fields. */
internal val PreferencesStore.fastingSessionImpl: Flow<FastingSession>
    get() = combine(
        longPref(Keys.FASTING_STARTED_AT, 0L),
        longPref(Keys.FASTING_LAST_ENDED_AT, 0L),
        longPref(Keys.FASTING_LAST_FAST_STARTED_AT, 0L),
        boolPref(Keys.FASTING_GOAL_REACHED_NOTIFIED, false),
    ) { started, ended, lastStarted, notified ->
        FastingSession(
            startedAtMillis = started.takeIf { it > 0L },
            lastEndedAtMillis = ended.takeIf { it > 0L },
            lastFastStartedAtMillis = lastStarted.takeIf { it > 0L },
            goalReachedNotified = notified,
        )
    }

/** Writes all four session scalars in one DataStore edit (single-file atomic). */
internal suspend fun PreferencesStore.setFastingSessionFieldsImpl(
    startedAtMillis: Long?,
    lastEndedAtMillis: Long?,
    lastFastStartedAtMillis: Long?,
    goalReachedNotified: Boolean,
) {
    dataStore.edit { prefs ->
        fun putOrRemoveLong(key: Preferences.Key<Long>, v: Long?) {
            if (v == null) prefs.remove(key) else prefs[key] = v
        }
        putOrRemoveLong(Keys.FASTING_STARTED_AT, startedAtMillis)
        putOrRemoveLong(Keys.FASTING_LAST_ENDED_AT, lastEndedAtMillis)
        putOrRemoveLong(Keys.FASTING_LAST_FAST_STARTED_AT, lastFastStartedAtMillis)
        prefs[Keys.FASTING_GOAL_REACHED_NOTIFIED] = goalReachedNotified
    }
}

/** Default start-reminder time: 20:00 (typical after-dinner fast start). */
const val DEFAULT_FASTING_START_REMINDER_HOUR = 20
const val DEFAULT_FASTING_START_REMINDER_MINUTE = 0

internal val PreferencesStore.fastingStartReminderEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_START_REMINDER_ENABLED, false)
internal suspend fun PreferencesStore.setFastingStartReminderEnabledImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_START_REMINDER_ENABLED, v)

internal val PreferencesStore.fastingStartReminderHourImpl: Flow<Int>
    get() = intPref(Keys.FASTING_START_REMINDER_HOUR, DEFAULT_FASTING_START_REMINDER_HOUR)
internal suspend fun PreferencesStore.setFastingStartReminderHourImpl(v: Int) =
    setIntPref(Keys.FASTING_START_REMINDER_HOUR, v.coerceIn(0, 23))

internal val PreferencesStore.fastingStartReminderMinuteImpl: Flow<Int>
    get() = intPref(Keys.FASTING_START_REMINDER_MINUTE, DEFAULT_FASTING_START_REMINDER_MINUTE)
internal suspend fun PreferencesStore.setFastingStartReminderMinuteImpl(v: Int) =
    setIntPref(Keys.FASTING_START_REMINDER_MINUTE, v.coerceIn(0, 59))
