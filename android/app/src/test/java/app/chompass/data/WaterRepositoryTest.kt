package app.chompass.data

import android.app.Application
import app.chompass.models.WaterEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Water history editing (#58b): update keeps id/date and replaces the amount,
 * unknown/invalid updates are no-ops, delete is idempotent, and every
 * mutation re-arms the reminder chain via [WaterRepository.onEntriesChanged].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WaterRepositoryTest {
    private fun repo(
        prefs: PreferencesStore,
        changed: MutableList<Int> = mutableListOf(),
    ) = WaterRepository(prefs).apply {
        onEntriesChanged = { changed.add(1) }
    }

    @Test
    fun `update keeps id and date and replaces amount`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val sip = WaterEntry(
            id = UUID.randomUUID(),
            date = Instant.parse("2026-08-03T10:15:00Z"),
            milliliters = 250,
        )
        r.add(sip)

        r.update(sip.id, 500)

        val after = prefs.waterEntries.first()
        assertEquals(1, after.size)
        assertEquals(sip.id, after[0].id)
        assertEquals(sip.date, after[0].date)
        assertEquals(500, after[0].milliliters)
    }

    @Test
    fun `update with unknown id is a no-op`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val sip = WaterEntry(id = UUID.randomUUID(), date = Instant.parse("2026-08-03T10:15:00Z"), milliliters = 250)
        r.add(sip)

        r.update(UUID.randomUUID(), 500)

        assertEquals(listOf(sip), prefs.waterEntries.first())
    }

    @Test
    fun `update with invalid amount is a no-op`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val sip = WaterEntry(id = UUID.randomUUID(), date = Instant.parse("2026-08-03T10:15:00Z"), milliliters = 250)
        r.add(sip)

        r.update(sip.id, 0)

        assertEquals(listOf(sip), prefs.waterEntries.first())
    }

    @Test
    fun `delete is idempotent`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val sip = WaterEntry(id = UUID.randomUUID(), date = Instant.parse("2026-08-03T10:15:00Z"), milliliters = 250)
        r.add(sip)

        r.delete(sip.id)
        r.delete(sip.id)

        assertEquals(emptyList<WaterEntry>(), prefs.waterEntries.first())
    }

    @Test
    fun `add update and delete all re-arm the reminder chain`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val changed = mutableListOf<Int>()
        val r = repo(prefs, changed)
        val sip = WaterEntry(id = UUID.randomUUID(), date = Instant.parse("2026-08-03T10:15:00Z"), milliliters = 250)

        r.add(sip)
        r.update(sip.id, 300)
        r.delete(sip.id)

        assertTrue(changed.size >= 3)
    }
}
