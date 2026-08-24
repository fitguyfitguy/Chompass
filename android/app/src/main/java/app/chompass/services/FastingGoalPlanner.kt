package app.chompass.services

import app.chompass.AppContainer
import app.chompass.models.FastingSession
import kotlinx.coroutines.flow.first

/**
 * Arms/cancels the **break-fast** nudge (the end-of-fast reminder) — pure
 * state → plan, same shape as [WaterReminderPlanner]. Fires [leadMinutes]
 * before the fast reaches its goal (0 = exactly at the goal). Every caller
 * (app start, boot re-arm, session change, settings change) re-derives the
 * arm, so the armed alarm always matches reality.
 *
 * The alarm is a milestone, not an auto-end: when it fires the fast keeps
 * running; the receiver posts once and latches
 * [FastingSession.goalReachedNotified].
 */
object FastingGoalPlanner {
    /**
     * Next fire = started + goal hours − lead, when a fast is running with a
     * goal set, the reminder on, and the goal not already notified. Null when
     * the alarm is not applicable (also when the fire time already passed —
     * the alarm was missed; the Home bar still shows "you can eat now").
     */
    suspend fun nextFireMillis(
        container: AppContainer,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val prefs = container.prefs
        if (!prefs.fastingEnabled.first()) return null
        if (!prefs.fastingGoalNotificationEnabled.first()) return null
        val goalHours = prefs.fastingGoalHours.first()
        if (goalHours <= 0) return null
        val session = container.fastingRepository.current()
        val started = session.startedAtMillis ?: return null
        if (session.goalReachedNotified) return null
        val leadMillis = prefs.fastingEndReminderLeadMinutes.first() * 60_000L
        val fire = started + goalHours * FastingSession.MILLIS_PER_HOUR - leadMillis
        if (fire <= nowMillis) return null
        return fire
    }

    /** Arms the break-fast nudge to match current state (cancels when not applicable). */
    suspend fun rearm(container: AppContainer) {
        val fire = nextFireMillis(container)
        if (fire != null) {
            container.notifications.scheduleFastingGoalAt(fire, container.prefs.fastingEndReminderLeadMinutes.first())
        } else {
            container.notifications.cancelFastingGoal()
        }
    }
}
