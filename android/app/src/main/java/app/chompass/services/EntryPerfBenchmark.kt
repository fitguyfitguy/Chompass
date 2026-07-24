package app.chompass.services

import android.util.Log
import app.chompass.AppContainer
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.services.ai.toFoodEntry

/**
 * Debug-only benchmark: fires a handful of real text food-analysis requests
 * through the normal pipeline so the FudAIPerf instrumentation (promptBuild →
 * net → parse → save phases) emits a comparable batch on demand — without
 * hand-driving the UI. Triggered by the `run_entry_benchmark` intent extra in
 * MainActivity (see scripts/perf_entry_benchmark.sh).
 *
 * Requires a working AI provider + key (the Gemini key is seeded from
 * secrets.properties on debug launch). Each request hits the live API, so this
 * is a real end-to-end latency measurement, not a mock.
 */
class EntryPerfBenchmark(private val container: AppContainer) {

    /**
     * Runs [count] analyses of the sample descriptions (cycled). When [persist]
     * is true each result is written via FoodRepository.addEntry so the save
     * phase is timed too. Emits `op=benchmark` markers around the batch and per
     * entry; the closing `phase=done` line lets the capture script stop cleanly.
     */
    suspend fun run(count: Int, persist: Boolean = true) {
        Log.i(PerfLog.TAG, "op=benchmark phase=start count=$count persist=$persist")
        var ok = 0
        var fail = 0
        for (i in 0 until count) {
            val description = SAMPLES[i % SAMPLES.size]
            val startNs = System.nanoTime()
            try {
                val analysis = container.foodAnalysis.analyzeText(description)
                if (persist) {
                    container.foodRepository.addEntry(
                        analysis.toFoodEntry(FoodSource.TEXT_INPUT, MealType.currentMeal)
                    )
                }
                ok++
                val ms = (System.nanoTime() - startNs) / 1_000_000
                Log.i(PerfLog.TAG, "op=benchmark phase=entry i=$i ms=$ms status=ok")
            } catch (e: Throwable) {
                fail++
                Log.w(PerfLog.TAG, "op=benchmark phase=entry i=$i status=fail err=${e.message}")
            }
        }
        Log.i(PerfLog.TAG, "op=benchmark phase=done count=$count ok=$ok fail=$fail")
    }

    companion object {
        /** Short, unambiguous descriptions that exercise text analysis + serving inference. */
        private val SAMPLES = listOf(
            "2 scrambled eggs with buttered toast",
            "grande caffe latte with whole milk",
            "chicken caesar salad",
            "medium banana",
            "bowl of oatmeal with blueberries",
        )
    }
}
