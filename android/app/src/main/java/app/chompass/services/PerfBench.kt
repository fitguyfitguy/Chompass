package app.chompass.services

/**
 * Debug-only inbox so benches run through [app.chompass.ui.home.HomeViewModel]
 * (chip cache, relog, day list, water ring) instead of talking to repositories
 * behind the UI. Sticky: survives the splash until Home collects it.
 */
sealed class PerfBenchRequest {
    data class Relog(val count: Int) : PerfBenchRequest()
    data class LocalEntry(val count: Int) : PerfBenchRequest()
    data class WaterSip(val count: Int) : PerfBenchRequest()
    data class DaySwitch(val count: Int = 1) : PerfBenchRequest()
    data class HubOpen(val count: Int = 1) : PerfBenchRequest()
    data class Flip(
        val relog: Int = 3,
        val local: Int = 3,
        val sips: Int = 1,
    ) : PerfBenchRequest()
}
