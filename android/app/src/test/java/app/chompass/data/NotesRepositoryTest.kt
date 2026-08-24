package app.chompass.data

import android.app.Application
import app.chompass.models.DailyNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Daily notes (#58a): the id is deterministic per date (so upsert-by-id ==
 * upsert-by-date and sync tombstones collapse), set updates in place, an empty
 * text clears instead of storing a blank record, delete is idempotent, and
 * writes stay month-scoped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NotesRepositoryTest {
    private fun repo(prefs: PreferencesStore) = NotesRepository(prefs)

    @Test
    fun `id is stable per date and format matches the PWA scheme`() = runBlocking {
        val day = LocalDate.of(2026, 7, 24)
        val a = DailyNote.idFor(day)
        val b = DailyNote.idFor(day)
        assertEquals(a, b)
        // Day count since epoch in the low 48 bits: 2026-07-24 -> 0x50b2.
        assertEquals("00000000-0000-0000-0000-0000000050b2", a.toString())
        // Different day, different id.
        assertTrue(DailyNote.idFor(day.plusDays(1)) != a)
    }

    @Test
    fun `set upserts in place per day`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val day = LocalDate.of(2026, 8, 3)

        r.setNote(day, "first draft")
        assertEquals("first draft", r.noteFor(day)?.text)
        val firstId = r.noteFor(day)?.id

        r.setNote(day, "revised")
        val notes = prefs.noteEntries.first()
        assertEquals(1, notes.size)
        assertEquals(firstId, notes[0].id)
        assertEquals(day, notes[0].date)
        assertEquals("revised", notes[0].text)
    }

    @Test
    fun `blank text clears the day note`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val day = LocalDate.of(2026, 8, 3)

        r.setNote(day, "keep this")
        r.setNote(day, "   ")
        assertNull(r.noteFor(day))
        assertEquals(0, prefs.noteEntries.first().size)

        // And deleting an absent day is a no-op.
        r.deleteNote(day)
        r.deleteNote(day.plusDays(1))
        assertEquals(0, prefs.noteEntries.first().size)
    }

    @Test
    fun `text is capped at the max length`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val day = LocalDate.of(2026, 8, 3)

        r.setNote(day, "x".repeat(DailyNote.MAX_TEXT_LENGTH + 500))
        assertEquals(DailyNote.MAX_TEXT_LENGTH, r.noteFor(day)?.text?.length)
    }

    @Test
    fun `notes are month-scoped and isolated per day`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)

        r.setNote(LocalDate.of(2026, 7, 31), "july")
        r.setNote(LocalDate.of(2026, 8, 1), "august")
        r.setNote(LocalDate.of(2026, 8, 2), "august 2")

        val notes = prefs.noteEntries.first()
        assertEquals(3, notes.size)
        assertEquals("july", r.noteFor(LocalDate.of(2026, 7, 31))?.text)
        assertEquals("august", r.noteFor(LocalDate.of(2026, 8, 1))?.text)

        r.deleteNote(LocalDate.of(2026, 8, 1))
        assertEquals(2, prefs.noteEntries.first().size)
        assertNull(r.noteFor(LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun `stable id survives a full replace`() = runBlocking {
        // Mirrors the sync/import path: setNoteEntries replaces the whole set;
        // the deterministic id keeps "the note for the day" identifiable.
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val day = LocalDate.of(2026, 8, 3)
        val r = repo(prefs)
        r.setNote(day, "original")

        val imported = listOf(
            DailyNote(id = DailyNote.idFor(day), date = day, text = "from another device"),
        )
        prefs.setNoteEntries(imported)
        assertEquals("from another device", r.noteFor(day)?.text)
        assertEquals(DailyNote.idFor(day), r.noteFor(day)?.id)
    }
}
