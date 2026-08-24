package app.chompass.data

import app.chompass.models.DailyNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/**
 * Per-day free-text notes (Codeberg #58a). One record per calendar day, stored
 * in month-scoped JSON buckets; the stable per-day id keeps upsert-by-id ==
 * upsert-by-date, and sync tombstones/revives behave like "the note for that
 * day".
 */
class NotesRepository(
    private val prefs: PreferencesStore,
    private val sync: app.chompass.sync.SyncRepository? = null,
) {
    val notes: Flow<List<DailyNote>> = prefs.noteEntries.map { list -> list.sortedBy { it.date } }

    /** Invoked after every write; wired by ChompassApp when something must react. */
    var onNotesChanged: (suspend () -> Unit)? = null

    /** The note for [date], or null when the day has none. */
    suspend fun noteFor(date: LocalDate): DailyNote? =
        prefs.noteEntries.first().firstOrNull { it.date == date }

    /**
     * Sets the note for [date]. An empty (or blank) text clears the day's note
     * instead of storing an empty record — deleting is the same code path as
     * saving, so a cleared note tombstones exactly like an explicit delete.
     */
    suspend fun setNote(date: LocalDate, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            deleteNote(date)
            return
        }
        val note = DailyNote(
            id = DailyNote.idFor(date),
            date = date,
            text = trimmed.take(DailyNote.MAX_TEXT_LENGTH),
        )
        prefs.applyNoteBucketChanges(
            upsertsByMonth = mapOf(note.date.yearMonth() to listOf(note)),
        )
        sync?.touch(note.id, "daily_note")
        onNotesChanged?.invoke()
    }

    /** Removes the note for [date] (no-op when the day has none). */
    suspend fun deleteNote(date: LocalDate) {
        val existing = prefs.noteEntries.first().firstOrNull { it.date == date } ?: return
        prefs.applyNoteBucketChanges(
            removalIdsByMonth = mapOf(existing.date.yearMonth() to setOf(existing.id)),
        )
        sync?.tombstone(existing.id, "daily_note")
        onNotesChanged?.invoke()
    }

    private fun LocalDate.yearMonth(): YearMonth = YearMonth.from(this)
}
