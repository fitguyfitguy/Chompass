package app.chompass.services

import app.chompass.AppContainer
import app.chompass.models.FastingSession
import kotlinx.coroutines.flow.first

/**
 * Arms/cancels the **auto-cycle** transition alarms (docs/local/
 * PLAN_FASTING_TRACKER.md §7c): when [fastingAutoWindows] is on, the fast
 * starts automatically the instant the eating window closes and ends the
 * instant it reaches its goal — a self-driving fast → eat cycle with no
 * buttons. The receiver performs the transitions (no notification is posted);
 * this object only schedules them, pure state → plan like the other planners.
 * A missed alarm (device off) heals on the next app open via the Home ticker.
 */
object FastingAutoPlanner {
    /** Next auto-start = eating-window end, when auto mode is on and a window is open. */
    suspend fun nextAutoStartFireMillis(
        container: AppContainer,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val prefs = container.prefs
        if (!prefs.fastingEnabled.first() || !prefs.fastingAutoWindows.first()) return null
        val eatHours = prefs.fastingEatHours.first()
        if (eatHours <= 0) return null
        val session = container.fastingRepository.current()
        if (session.isFasting) return null
        val windowEnds = session.eatingWindowEndsAtMillis(eatHours, nowMillis) ?: return null
        return windowEnds
    }

    /** Next auto-end = goal instant, when auto mode is on and a fast is running. */
    suspend fun nextAutoEndFireMillis(
        container: AppContainer,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val prefs = container.prefs
        if (!prefs.fastingEnabled.first() || !prefs.fastingAutoWindows.first()) return null
        val goalHours = prefs.fastingGoalHours.first()
        if (goalHours <= 0) return null
        val session = container.fastingRepository.current()
        val started = session.startedAtMillis ?: return null
        val goal = started + goalHours * FastingSession.MILLIS_PER_HOUR
        return if (goal > nowMillis) goal else null
    }

    /** Arms both auto transitions to match current state (cancels when not applicable). */
    suspend fun rearm(container: AppContainer) {
        val autoStart = nextAutoStartFireMillis(container)
        if (autoStart != null) {
            container.notifications.scheduleFastingAutoStartAt(autoStart)
        } else {
            container.notifications.cancelFastingAutoStart()
        }
        val autoEnd = nextAutoEndFireMillis(container)
        if (autoEnd != null) {
            container.notifications.scheduleFastingAutoEndAt(autoEnd)
        } else {
            container.notifications.cancelFastingAutoEnd()
        }
    }

    /**
     * Idempotent catch-up: drives any transition whose boundary already passed
     * (missed alarm: device off, app closed, reboot). Called on app start, boot
     * re-arm, the Home minute ticker, and the alarm receiver — the cycle stays
     * on schedule no matter how it was missed.
     */
    suspend fun heal(container: AppContainer) {
        val prefs = container.prefs
        if (!prefs.fastingEnabled.first() || !prefs.fastingAutoWindows.first()) return
        val goal = prefs.fastingGoalHours.first()
        val eat = prefs.fastingEatHours.first()
        val repo = container.fastingRepository
        val now = System.currentTimeMillis()
        val s = repo.current()
        if (s.isFasting) {
            val started = s.startedAtMillis ?: return
            if (goal > 0 && s.elapsedMillis(now) >= goal * FastingSession.MILLIS_PER_HOUR) {
                repo.stop(atGoalMillis = started + goal * FastingSession.MILLIS_PER_HOUR)
            }
        } else if (eat > 0 && s.lastEndedAtMillis != null &&
            s.eatingWindowEndsAtMillis(eat, now) == null
        ) {
            repo.start(auto = true)
        }
    }
}
