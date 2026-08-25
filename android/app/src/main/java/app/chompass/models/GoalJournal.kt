package app.chompass.models

import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Per-day goal journal (Codeberg #60, docs/local/MACRO_PROFILES_DESIGN.md §
 * Per-day goal journal): the resolved day targets frozen per day as they
 * happen, so history and analysis use actuals instead of today's schedule
 * painted over the past (MACRO-CYCLE-D averages over these entries).
 *
 * Freeze rule: entries for PAST dates are immutable once written; today is
 * live (plan edits / day-type switches rewrite it); future days are never
 * journaled. All helpers here are pure — the store (PreferencesStoreGoalJournal)
 * and write triggers (phase 1) own persistence.
 */
@Serializable
enum class GoalJournalSource { PLAN, MANUAL_SWITCH, OVERRIDE, GAP_FILL }

@Serializable
data class GoalJournalEntry(
    /** ISO local date "2026-09-01" — the key. */
    val date: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** Day-type profile in effect (null = base targets that day). */
    val profileId: String? = null,
    /** Name snapshot at write time — history labels never mutate. */
    val profileName: String? = null,
    /** Last-write-wins merge across devices. */
    val updatedAtMillis: Long = 0L,
    val source: GoalJournalSource = GoalJournalSource.PLAN,
)

object GoalJournal {
    /** Journal retention (days back from today); matches plan docs. */
    const val KEEP_DAYS = 400L

    /**
     * Stable per-day sync record id (daily_notes precedent): epoch day in the
     * low 48 bits, zeros elsewhere. Wire form `00000000-0000-0000-0000-<12
     * lowercase hex digits>`; the PWA mirrors this in `macro-plan.js`
     * (`goalJournalIdFor`). Same date → same id on every device, so sync's
     * merge-by-id is per-day last-write-wins.
     */
    fun idFor(date: LocalDate): UUID {
        val days = date.toEpochDay() and 0x0000FFFFFFFFFFFFL
        return UUID.fromString("00000000-0000-0000-0000-" + days.toString(16).padStart(12, '0'))
    }

    internal fun parseDateOrNull(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso) }.getOrNull()

    private fun entrySignature(e: GoalJournalEntry): String =
        listOf(e.date, e.calories, e.proteinG, e.carbsG, e.fatG, e.profileId, e.profileName, e.source.name)
            .joinToString(",")

    /**
     * Upsert honoring the freeze rule: past entries are immutable; today (and
     * future — never written by triggers, tolerated here) replaces. Always prunes.
     */
    fun upsertRespectingFreeze(
        entries: List<GoalJournalEntry>,
        entry: GoalJournalEntry,
        today: LocalDate,
    ): List<GoalJournalEntry> {
        val date = parseDateOrNull(entry.date)
        if (date != null && date.isBefore(today) && entries.any { it.date == entry.date }) {
            return entries
        }
        return prune(entries.filterNot { it.date == entry.date } + entry, today)
    }

    /** Per-day last-write-wins merge (sync import); deterministic in argument order. */
    fun merge(local: List<GoalJournalEntry>, remote: List<GoalJournalEntry>): List<GoalJournalEntry> =
        (local + remote)
            .groupBy { it.date }
            .values
            .map { group -> group.maxWith(compareBy({ it.updatedAtMillis }, { entrySignature(it) })) }
            .sortedBy { it.date }

    /** Keeps entries dated today − [keepDays] + 1 or later. Malformed dates are dropped. */
    fun prune(
        entries: List<GoalJournalEntry>,
        today: LocalDate,
        keepDays: Long = KEEP_DAYS,
    ): List<GoalJournalEntry> {
        val cutoff = today.minusDays(keepDays - 1)
        return entries.filter { parseDateOrNull(it.date)?.isAfter(cutoff.minusDays(1)) == true }
            .sortedBy { it.date }
    }

    /**
     * Fills missing days between the first journaled day and yesterday from the
     * current plan (best effort, marked GAP_FILL). No entries yet -> nothing to
     * fill (the first entry is today's, written by the enable/edit trigger).
     */
    fun gapFill(
        entries: List<GoalJournalEntry>,
        today: LocalDate,
        nowMillis: Long,
        resolve: (LocalDate) -> ResolvedDayTargets,
    ): List<GoalJournalEntry> {
        val known = entries.mapNotNull { parseDateOrNull(it.date) }.toSet()
        val first = known.minOrNull() ?: return entries
        val start = maxOf(first, today.minusDays(KEEP_DAYS - 1))
        val filled = mutableListOf<GoalJournalEntry>()
        var d = start
        while (d.isBefore(today)) {
            if (d !in known) {
                val resolved = resolve(d)
                filled += GoalJournalEntry(
                    date = d.toString(),
                    calories = resolved.targets.calories,
                    proteinG = resolved.targets.proteinG,
                    carbsG = resolved.targets.carbsG,
                    fatG = resolved.targets.fatG,
                    profileId = resolved.profileId,
                    profileName = resolved.profileName,
                    updatedAtMillis = nowMillis,
                    source = GoalJournalSource.GAP_FILL,
                )
            }
            d = d.plusDays(1)
        }
        return prune(entries + filled, today)
    }
}
