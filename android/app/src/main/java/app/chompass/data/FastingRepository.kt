package app.chompass.data

import app.chompass.models.FastingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Local-only intermittent-fasting timer (docs/local/PLAN_FASTING_TRACKER.md).
 * The session is persisted in DataStore as a few scalar fields; the repository
 * is notification-agnostic — [ChompassApp] wires [onSessionChanged] to the
 * goal-alarm planner, same shape as WaterRepository.onEntriesChanged → water
 * reminder chain.
 *
 * Transitions: start (IDLE→FASTING), stop (records the fast), cancel
 * (abandons, no record). The goal is a settings pref, not session state; the
 * session only latches whether the goal notification already fired.
 */
class FastingRepository(private val prefs: PreferencesStore) {
    val session: Flow<FastingSession> get() = prefs.fastingSession

    /** Invoked after every start/stop/cancel so the goal alarm can re-arm. */
    var onSessionChanged: (suspend () -> Unit)? = null

    suspend fun current(): FastingSession = prefs.fastingSession.first()

    /** Starts a new fast (no-op when one is already running). The previous
     *  completed fast (stop record) survives so coach context can still cite it. */
    suspend fun start(nowMillis: Long = System.currentTimeMillis()) {
        val s = current()
        if (s.isFasting) return
        prefs.setFastingSessionFields(
            startedAtMillis = nowMillis,
            lastEndedAtMillis = s.lastEndedAtMillis,
            lastFastStartedAtMillis = s.lastFastStartedAtMillis,
            goalReachedNotified = false,
        )
        onSessionChanged?.invoke()
    }

    /** Ends the running fast, recording it as the last completed fast. */
    suspend fun stop(nowMillis: Long = System.currentTimeMillis()) {
        val s = current()
        val started = s.startedAtMillis ?: return
        prefs.setFastingSessionFields(
            startedAtMillis = null,
            lastEndedAtMillis = nowMillis,
            lastFastStartedAtMillis = started,
            goalReachedNotified = false,
        )
        onSessionChanged?.invoke()
    }

    /** Abandons the running fast without recording it. */
    suspend fun cancel() {
        val s = current()
        if (!s.isFasting) return
        prefs.setFastingSessionFields(
            startedAtMillis = null,
            lastEndedAtMillis = s.lastEndedAtMillis,
            lastFastStartedAtMillis = s.lastFastStartedAtMillis,
            goalReachedNotified = false,
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
        )
    }
}
