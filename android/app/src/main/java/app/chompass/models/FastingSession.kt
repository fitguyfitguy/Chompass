package app.chompass.models

import kotlinx.serialization.Serializable

/**
 * Intermittent-fasting timer session (docs/local/PLAN_FASTING_TRACKER.md).
 * State is a pure function of the persisted fields — there is no persisted
 * "state" enum, so restarts, process death and boot re-arm all re-derive it
 * from [startedAtMillis]. Local-only: never synced, exported, or written to
 * Health Connect (unlike water/nicotine, a fasting window has no "day"
 * concept and a running timer is device truth).
 *
 * The goal (hours) is a settings pref, not part of the session; the session
 * only latches whether its goal notification already fired.
 */
@Serializable
data class FastingSession(
    /** Epoch-millis the current fast started; null = not fasting (IDLE). */
    val startedAtMillis: Long? = null,
    /** Epoch-millis the last completed fast ended (stop only; cancel discards). */
    val lastEndedAtMillis: Long? = null,
    /** Epoch-millis the last completed fast started (paired with [lastEndedAtMillis]). */
    val lastFastStartedAtMillis: Long? = null,
    /** One-shot latch so the goal notification fires once per fast. */
    val goalReachedNotified: Boolean = false,
) {
    val isFasting: Boolean get() = startedAtMillis != null

    /** Elapsed millis of the running fast; 0 when idle. */
    fun elapsedMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        if (startedAtMillis == null) 0L else (nowMillis - startedAtMillis).coerceAtLeast(0L)

    /** True when [goalHours] > 0 and the running fast already passed it. */
    fun goalReached(goalHours: Int, nowMillis: Long = System.currentTimeMillis()): Boolean =
        goalHours > 0 && isFasting && elapsedMillis(nowMillis) >= goalHours * MILLIS_PER_HOUR

    /** Duration of the last completed fast; 0 when none recorded yet. */
    fun lastFastDurationMillis(): Long {
        val started = lastFastStartedAtMillis ?: return 0L
        val ended = lastEndedAtMillis ?: return 0L
        return (ended - started).coerceAtLeast(0L)
    }

    companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
    }
}
