package app.chompass.models

import java.time.LocalDate

/**
 * Local same-hour active-burn history. Never synced. A slot of -1 means that
 * hour was not sampled; 0 means it burned nothing. A date whose slots are all
 * -1 is omitted. Dates older than [DayTypeActiveStats.HISTORY_KEEP_DAYS] are
 * dropped on write.
 */
object HourlyActiveHistory {
    const val HOURS = 24
    const val MISSING = -1

    fun emptyDay(): List<Int> = List(HOURS) { MISSING }

    /** Past day with a sample in every clock hour. Today is never treated as complete. */
    fun isComplete(slots: List<Int>?): Boolean =
        slots != null && slots.size == HOURS && slots.none { it < 0 }

    fun sanitize(map: Map<String, List<Int>>, today: LocalDate): Map<String, List<Int>> {
        val cutoff = today.minusDays(DayTypeActiveStats.HISTORY_KEEP_DAYS.toLong())
        return map.mapNotNull { (iso, slots) ->
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(cutoff)) return@mapNotNull null
            if (slots.size != HOURS || slots.all { it < 0 }) return@mapNotNull null
            iso to slots
        }.toMap()
    }

    fun mergeDay(
        existing: Map<String, List<Int>>,
        date: LocalDate,
        slots: List<Int>,
        today: LocalDate,
    ): Map<String, List<Int>> {
        val next = existing.toMutableMap()
        if (slots.size == HOURS && slots.any { it >= 0 }) {
            next[date.toString()] = slots
        } else {
            next.remove(date.toString())
        }
        return sanitize(next, today)
    }
}
