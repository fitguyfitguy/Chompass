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
    /**
     * Next auto-start = the next occurrence of the daily fast-start clock time
     * (when auto mode is on and no fast is running). The schedule is the
     * anchor: a user who enables auto mode at 21:00 with a 20:00 start gets
     * their fast tomorrow 20:00; one who stops eating at 13:00 starts today
     * 20:00.
     */
    suspend fun nextAutoStartFireMillis(
        container: AppContainer,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val prefs = container.prefs
        if (!prefs.fastingEnabled.first() || !prefs.fastingAutoWindows.first()) return null
        // The cycle is only safe once a goal length is set: without it the
        // auto-started fast could never end (and auto fasts show no buttons).
        if (prefs.fastingGoalHours.first() <= 0) return null
        val session = container.fastingRepository.current()
        if (session.isFasting) return null
        return nextFastingStartMillis(
            hour = prefs.fastingStartHour.first(),
            minute = prefs.fastingStartMinute.first(),
            nowMillis = nowMillis,
        )
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
        if (goal <= 0) return
        val repo = container.fastingRepository
        val now = System.currentTimeMillis()
        val s = repo.current()
        if (s.isFasting) {
            val started = s.startedAtMillis ?: return
            if (s.elapsedMillis(now) >= goal * FastingSession.MILLIS_PER_HOUR) {
                repo.stop(atGoalMillis = started + goal * FastingSession.MILLIS_PER_HOUR)
            }
        } else {
            val hour = prefs.fastingStartHour.first()
            val minute = prefs.fastingStartMinute.first()
            val todayT = nextFastingStartMillis(hour, minute, now)
            val zone = java.time.ZoneId.systemDefault()
            val scheduledStart = if (now >= todayT) todayT else todayT - 24 * 60 * 60_000L
            // Missed-alarm catch-up: the auto-start at [scheduledStart] was
            // skipped (device off, alarm lost, or auto mode just enabled) — so
            // the fast should already be running. Start it at the scheduled
            // instant so the cycle stays on the clock. A user who stopped
            // eating *after* the start time is still in their eating phase and
            // waits for the next one; every other not-fasting state (fresh
            // user included — enabling Auto fast windows is the opt-in to the
            // self-driving cycle) picks the fast up from the scheduled start.
            val lastEnded = s.lastEndedAtMillis
            if ((lastEnded == null || lastEnded < scheduledStart) && now >= scheduledStart) {
                repo.start(nowMillis = scheduledStart, auto = true)
            }
        }
    }
}

/**
 * Next occurrence of the daily clock time [hour]:[minute] strictly after
 * [nowMillis] (or equal, if we are exactly at it). Shared by the auto planner
 * and the start nudge so both agree on "when the fast starts".
 */
suspend fun nextFastingStartMillis(
    hour: Int,
    minute: Int,
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    val zone = java.time.ZoneId.systemDefault()
    val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
    val today = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone).toInstant().toEpochMilli()
    return if (nowMillis <= today) today else today + 24 * 60 * 60_000L
}
