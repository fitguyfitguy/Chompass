package app.chompass.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.chompass.models.FastingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Maximum selectable fasting goal in hours (wheel upper bound). */
const val MAX_FASTING_GOAL_HOURS = 48
/** Maximum selectable eating-window length in hours. */
const val MAX_FASTING_EAT_HOURS = 24
/** Maximum reminder lead in minutes (wheels step by 5). */
const val MAX_FASTING_REMINDER_LEAD_MINUTES = 120

internal val PreferencesStore.fastingEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_ENABLED, false)
internal suspend fun PreferencesStore.setFastingEnabledImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_ENABLED, v)

/** Goal length in hours; 0 = no goal (off by default). */
internal val PreferencesStore.fastingGoalHoursImpl: Flow<Int>
    get() = intPref(Keys.FASTING_GOAL_HOURS, 0)
internal suspend fun PreferencesStore.setFastingGoalHoursImpl(v: Int) =
    setIntPref(Keys.FASTING_GOAL_HOURS, v.coerceIn(0, MAX_FASTING_GOAL_HOURS))

/** Eating-window length in hours (completes the fast → eat cycle); 0 = not set. */
internal val PreferencesStore.fastingEatHoursImpl: Flow<Int>
    get() = intPref(Keys.FASTING_EAT_HOURS, 0)
internal suspend fun PreferencesStore.setFastingEatHoursImpl(v: Int) =
    setIntPref(Keys.FASTING_EAT_HOURS, v.coerceIn(0, MAX_FASTING_EAT_HOURS))

/** Goal-reached notification; only meaningful when the goal > 0. */
internal val PreferencesStore.fastingGoalNotificationEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_GOAL_NOTIFICATION_ENABLED, true)
internal suspend fun PreferencesStore.setFastingGoalNotificationEnabledImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_GOAL_NOTIFICATION_ENABLED, v)

/** Session state as a pure function of the scalar fields. */
internal val PreferencesStore.fastingSessionImpl: Flow<FastingSession>
    get() = combine(
        longPref(Keys.FASTING_STARTED_AT, 0L),
        longPref(Keys.FASTING_LAST_ENDED_AT, 0L),
        longPref(Keys.FASTING_LAST_FAST_STARTED_AT, 0L),
        boolPref(Keys.FASTING_GOAL_REACHED_NOTIFIED, false),
        boolPref(Keys.FASTING_AUTO_STARTED, false),
    ) { started, ended, lastStarted, notified, auto ->
        FastingSession(
            startedAtMillis = started.takeIf { it > 0L },
            lastEndedAtMillis = ended.takeIf { it > 0L },
            lastFastStartedAtMillis = lastStarted.takeIf { it > 0L },
            goalReachedNotified = notified,
            autoStarted = auto,
        )
    }

/** Writes the session scalars in one DataStore edit (single-file atomic). */
internal suspend fun PreferencesStore.setFastingSessionFieldsImpl(
    startedAtMillis: Long?,
    lastEndedAtMillis: Long? = null,
    lastFastStartedAtMillis: Long? = null,
    goalReachedNotified: Boolean = false,
    autoStarted: Boolean = false,
) {
    dataStore.edit { prefs ->
        fun putOrRemoveLong(key: Preferences.Key<Long>, v: Long?) {
            if (v == null) prefs.remove(key) else prefs[key] = v
        }
        putOrRemoveLong(Keys.FASTING_STARTED_AT, startedAtMillis)
        putOrRemoveLong(Keys.FASTING_LAST_ENDED_AT, lastEndedAtMillis)
        putOrRemoveLong(Keys.FASTING_LAST_FAST_STARTED_AT, lastFastStartedAtMillis)
        prefs[Keys.FASTING_GOAL_REACHED_NOTIFIED] = goalReachedNotified
        prefs[Keys.FASTING_AUTO_STARTED] = autoStarted
    }
}

/** Auto-cycle toggle (off by default); needs an eating window to anchor to. */
internal val PreferencesStore.fastingAutoWindowsImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_AUTO_WINDOWS, false)
internal suspend fun PreferencesStore.setFastingAutoWindowsImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_AUTO_WINDOWS, v)

/** Daily fast-start clock time (20:00 default); anchors auto mode + start nudge. */
const val DEFAULT_FASTING_START_HOUR = 20
const val DEFAULT_FASTING_START_MINUTE = 0

internal val PreferencesStore.fastingStartHourImpl: Flow<Int>
    get() = intPref(Keys.FASTING_START_HOUR, DEFAULT_FASTING_START_HOUR)
internal suspend fun PreferencesStore.setFastingStartHourImpl(v: Int) =
    setIntPref(Keys.FASTING_START_HOUR, v.coerceIn(0, 23))

internal val PreferencesStore.fastingStartMinuteImpl: Flow<Int>
    get() = intPref(Keys.FASTING_START_MINUTE, DEFAULT_FASTING_START_MINUTE)
internal suspend fun PreferencesStore.setFastingStartMinuteImpl(v: Int) =
    setIntPref(Keys.FASTING_START_MINUTE, v.coerceIn(0, 59))

/** Default lead (minutes before the window closes) for the start-fast nudge. */
const val DEFAULT_FASTING_START_REMINDER_LEAD_MINUTES = 15
/** Default lead (minutes before the fast ends) for the break-fast nudge. */
const val DEFAULT_FASTING_END_REMINDER_LEAD_MINUTES = 15

/** 0 = off; the nudge fires this many minutes before the eating window closes. */
internal val PreferencesStore.fastingStartReminderEnabledImpl: Flow<Boolean>
    get() = boolPref(Keys.FASTING_START_REMINDER_ENABLED, false)
internal suspend fun PreferencesStore.setFastingStartReminderEnabledImpl(v: Boolean) =
    setBoolPref(Keys.FASTING_START_REMINDER_ENABLED, v)

internal val PreferencesStore.fastingStartReminderLeadMinutesImpl: Flow<Int>
    get() = intPref(Keys.FASTING_START_REMINDER_LEAD_MINUTES, DEFAULT_FASTING_START_REMINDER_LEAD_MINUTES)
internal suspend fun PreferencesStore.setFastingStartReminderLeadMinutesImpl(v: Int) =
    setIntPref(Keys.FASTING_START_REMINDER_LEAD_MINUTES, v.coerceIn(0, MAX_FASTING_REMINDER_LEAD_MINUTES))

internal val PreferencesStore.fastingEndReminderLeadMinutesImpl: Flow<Int>
    get() = intPref(Keys.FASTING_END_REMINDER_LEAD_MINUTES, DEFAULT_FASTING_END_REMINDER_LEAD_MINUTES)
internal suspend fun PreferencesStore.setFastingEndReminderLeadMinutesImpl(v: Int) =
    setIntPref(Keys.FASTING_END_REMINDER_LEAD_MINUTES, v.coerceIn(0, MAX_FASTING_REMINDER_LEAD_MINUTES))
