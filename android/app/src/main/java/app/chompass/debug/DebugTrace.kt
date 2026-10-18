package app.chompass.debug

import android.util.Log
import app.chompass.BuildConfig

/**
 * Debug-only assertion channel for device passes: one greppable
 * `op=<area> phase=<transition> k=v …` line per state change.
 *
 *   adb logcat -c; adb logcat -s ChompassDebug:V
 *
 * No-op in release (same gate as PerfLog); not routed through PerfLog because
 * scripts/summarize_entry_perf.py consumes `FudAIPerf` lines and would ingest
 * these events. Values are single-token: callers sanitize spaces.
 */
object DebugTrace {
    const val TAG = "ChompassDebug"
    val enabled = BuildConfig.DEBUG

    fun event(line: String) {
        if (!enabled) return
        try {
            Log.d(TAG, line)
        } catch (_: RuntimeException) {
            // JVM unit tests run without an android.util.Log implementation.
        }
    }
}
