package org.codeberg.fitguy.nofud.services

import android.util.Log
import org.codeberg.fitguy.nofud.BuildConfig

/**
 * Debug-only performance logger for the entry-addition pipeline.
 *
 * Emits one structured `key=value` line per measurement under the tag
 * [TAG] so `adb logcat -s FudAIPerf:V` (and scripts/capture_entry_perf.*)
 * can capture and scripts/summarize_entry_perf.py can parse them. Format:
 *
 *   op=analyzeText phase=promptBuild ms=8
 *   op=analyzeText phase=parse ms=3 chars=1830
 *   op=save phase=dataStore ms=41 entries=214
 *   op=net phase=call host=... totalMs=1420 status=200
 *
 * Gated by [BuildConfig.DEBUG] — compiled to no-ops in release (mirrors the
 * gate in NoFUDApp.seedDebugGeminiKeyIfNeeded). [measure] is `inline`, so in a
 * debug build it wraps `suspend` calls with no lambda allocation, and in
 * release the whole call collapses to just `block()`.
 */
object PerfLog {
    const val TAG = "FudAIPerf"

    val enabled = BuildConfig.DEBUG

    /**
     * Times [block] and, when [enabled], logs one line:
     * `op=<op> phase=<phase> ms=<elapsed> [extra]`. [extra] is optional
     * space-separated `key=value` pairs (e.g. `"chars=1830"`). Returns the
     * block's result and always re-throws, so instrumentation never changes
     * behavior. Timing uses [System.nanoTime] (monotonic).
     */
    inline fun <T> measure(op: String, phase: String, extra: String = "", block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val ms = (System.nanoTime() - start) / 1_000_000
            val suffix = if (extra.isEmpty()) "" else " $extra"
            try {
                Log.i(TAG, "op=$op phase=$phase ms=$ms$suffix")
            } catch (_: RuntimeException) {
                // JVM unit tests run without a mocked android.util.Log.
            }
        }
    }

    /** Raw structured emit (already formatted `key=value ...`). Used by [org.codeberg.fitguy.nofud.services.ai.PerfEventListener]. */
    fun event(line: String) {
        if (!enabled) return
        try {
            Log.i(TAG, line)
        } catch (_: RuntimeException) {
            // JVM unit tests run without a mocked android.util.Log.
        }
    }
}
