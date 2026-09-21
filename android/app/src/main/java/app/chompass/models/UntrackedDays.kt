package app.chompass.models

import java.time.LocalDate

/**
 * Per-day "not tracked" state (Codeberg #106).
 *
 * A marked day is intentionally without reliable intake data. Food logged on
 * that day (optional blind totals) stays visible in the diary but is excluded
 * from averages, trends, the weight-forecast window, and adaptive-goal input.
 * The day is also not a missed logging day for the streak reminder.
 */
data class UntrackedDayRecord(
    val date: LocalDate,
    /** Optional rough kcal kept for personal record; never fed into stats. */
    val kcal: Int? = null,
) {
    init {
        require(kcal == null || kcal >= 0) { "untracked kcal must be >= 0" }
    }
}

object UntrackedDays {
    fun isUntracked(dates: Set<String>, date: LocalDate): Boolean =
        date.toString() in dates

    /**
     * Drop untracked calendar days from a forecast window: they are neither
     * logged days nor sparse gaps.
     */
    fun excludeFromLoggedDates(
        logged: Collection<LocalDate>,
        untracked: Set<LocalDate>,
    ): Set<LocalDate> = logged.filterNot { it in untracked }.toSet()

    fun parseIsoSet(raw: Set<String>): Set<LocalDate> =
        raw.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
}
