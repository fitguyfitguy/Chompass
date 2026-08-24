package app.chompass.services

import app.chompass.AppContainer
import kotlinx.coroutines.flow.first

/**
 * Arms/cancels the optional daily **start-fast** reminder (docs/local/
 * PLAN_FASTING_TRACKER.md §7b). Pure state → alarm: when fasting is enabled
 * and the reminder toggle is on, schedule the daily fixed-time nudge; else
 * cancel. Every caller (app start, boot re-arm, settings change) re-derives
 * the arm, so it always matches reality. The goal-reached alarm is separate
 * ([FastingGoalPlanner]); this is the "your eating window is over" nudge.
 *
 * The receiver skips the nudge when a fast is already running, so the two
 * reminders never contradict each other.
 */
object FastingReminderPlanner {
    /** Arms the daily start reminder to match current state (cancels when off). */
    suspend fun rearmStartReminder(container: AppContainer) {
        if (container.prefs.fastingEnabled.first() &&
            container.prefs.fastingStartReminderEnabled.first()
        ) {
            val goalHours = container.prefs.fastingGoalHours.first()
            container.notifications.scheduleFastingStartReminder(
                hour = container.prefs.fastingStartReminderHour.first(),
                minute = container.prefs.fastingStartReminderMinute.first(),
                goalHours = goalHours,
            )
        } else {
            container.notifications.cancelFastingStartReminder()
        }
    }
}
