package app.chompass.data

import app.chompass.models.FastingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Local-only intermittent-fasting timer (docs/local/PLAN_FASTING_TRACKER.md).
 * The session is persisted in DataStore as a few scalar fields; the repository
 * is notification-agnostic — [ChompassApp] wires [onSessionChanged] to the
 * alarm planners, same shape as WaterRepository.onEntriesChanged → water
 * reminder chain.
 *
 * Transitions: start (IDLE→FASTING, manual or [auto]), stop (records the fast;
 * [stopAtGoal] makes the end deterministic at the goal time). There is no
 * cancel: a running fast is either stopped or allowed to run its course. The
 * goal is a settings pref, not session state; the session only latches whether
 * its goal notification already fired and whether the current fast was
 * auto-started ([FastingSession.autoStarted] — auto fasts show no manual
 * buttons).
 */
class FastingRepository(private val prefs: PreferencesStore) {
    val session: Flow<FastingSession> get() = prefs.fastingSession

    /** Invoked after every start/stop so the alarm planners can re-arm. */
    var onSessionChanged: (suspend () -> Unit)? = null

    suspend fun current(): FastingSession = prefs.fastingSession.first()

    /**
     * Starts a new fast (no-op when one is already running). The previous
     * completed fast (stop record) survives so coach context can still cite it.
     * [auto] marks the fast as auto-cycle-managed (no manual buttons shown).
     */
    suspend fun start(nowMillis: Long = System.currentTimeMillis(), auto: Boolean = false) {
        val s = current()
        if (s.isFasting) return
        prefs.setFastingSessionFields(
            startedAtMillis = nowMillis,
            lastEndedAtMillis = s.lastEndedAtMillis,
            lastFastStartedAtMillis = s.lastFastStartedAtMillis,
            goalReachedNotified = false,
            autoStarted = auto,
        )
        onSessionChanged?.invoke()
    }

    /**
     * Ends the running fast, recording it as the last completed fast. When
     * [atGoalMillis] is set (auto-cycle end), the end time is the exact goal
     * instant so the next eating window anchors to the schedule, not to when
     * the alarm happened to fire.
     */
    suspend fun stop(atGoalMillis: Long? = null) {
        val s = current()
        val started = s.startedAtMillis ?: return
        val ended = atGoalMillis ?: System.currentTimeMillis()
        prefs.setFastingSessionFields(
            startedAtMillis = null,
            lastEndedAtMillis = ended,
            lastFastStartedAtMillis = started,
            goalReachedNotified = false,
            autoStarted = false,
        )
        onSessionChanged?.invoke()
    }

    /** Latches the goal notification as fired for the current fast (alarm once). */
    suspend fun markGoalReachedNotified() {
        val s = current()
        val started = s.startedAtMillis ?: return
        if (s.goalReachedNotified) return
        prefs.setFastingSessionFields(
            startedAtMillis = started,
            lastEndedAtMillis = s.lastEndedAtMillis,
            lastFastStartedAtMillis = s.lastFastStartedAtMillis,
            goalReachedNotified = true,
            autoStarted = s.autoStarted,
        )
    }
}
