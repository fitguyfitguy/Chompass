package app.chompass.services

import app.chompass.AppContainer
import kotlinx.coroutines.flow.first

/**
 * Arms/cancels the optional **start-fast** nudge (docs/local/
 * PLAN_FASTING_TRACKER.md §7b). Pure state → alarm: when fasting is enabled,
 * the reminder toggle is on, and an eating window is open ([eatHours] > 0 and
 * the last fast was stopped), schedule a one-shot fire [leadMinutes] before
 * the eating window closes — i.e. X minutes before the next fast period
 * starts. Else cancel. Every caller (app start, boot re-arm, session change,
 * settings change) re-derives the arm.
 *
 * The receiver skips the nudge when a fast is already running (you started
 * early), so the two reminders never contradict each other. With no eating
 * window configured there is no "fast period starts" anchor, so the nudge
 * simply never fires.
 */
object FastingReminderPlanner {
    /**
     * Next start-nudge fire:
     *  - auto mode: the daily fast-start clock time minus the lead (the fast
     *    will start on its own; the nudge is a heads-up).
     *  - manual mode: the open eating-window end minus the lead (the user
     *    starts the fast themselves).
     * Null when the reminder is off, a fast is already running, or there is no
     * anchor (manual mode with no eating window).
     */
    suspend fun nextStartFireMillis(
        container: AppContainer,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val prefs = container.prefs
        if (!prefs.fastingEnabled.first()) return null
        if (!prefs.fastingStartReminderEnabled.first()) return null
        val session = container.fastingRepository.current()
        if (session.isFasting) return null
        val leadMillis = prefs.fastingStartReminderLeadMinutes.first() * 60_000L
        val anchor: Long? = if (prefs.fastingAutoWindows.first()) {
            nextFastingStartMillis(
                hour = prefs.fastingStartHour.first(),
                minute = prefs.fastingStartMinute.first(),
                nowMillis = nowMillis,
            )
        } else {
            val eatHours = prefs.fastingEatHours.first()
            if (eatHours <= 0) return null
            session.eatingWindowEndsAtMillis(eatHours, nowMillis)
        }
        val anchorMillis = anchor ?: return null
        val fire = anchorMillis - leadMillis
        if (fire <= nowMillis) return null
        return fire
    }

    /** Arms the start nudge to match current state (cancels when not applicable). */
    suspend fun rearmStartReminder(container: AppContainer) {
        val fire = nextStartFireMillis(container)
        if (fire != null) {
            container.notifications.scheduleFastingStartReminderAt(
                fire,
                container.prefs.fastingStartReminderLeadMinutes.first(),
                auto = container.prefs.fastingAutoWindows.first(),
            )
        } else {
            container.notifications.cancelFastingStartReminder()
        }
    }
}
