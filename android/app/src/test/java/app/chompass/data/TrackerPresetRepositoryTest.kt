package app.chompass.data

import android.app.Application
import app.chompass.models.CaffeineEntry
import app.chompass.models.HabitPresetDomain
import app.chompass.models.NicotineEntry
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
import java.time.Instant
import java.util.UUID

/**
 * Preset-aware repository + prefs behavior (#55 follow-up): deleting a custom
 * preset moves its logs to Other in place (same id/date, amounts kept), and
 * the catalog prefs round-trip validated catalogs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TrackerPresetRepositoryTest {
    @Test
    fun `caffeine reassignKind moves only matching kind`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val repo = CaffeineRepository(prefs)
        val keep = CaffeineEntry(
            id = UUID.randomUUID(),
            date = Instant.parse("2026-08-03T10:15:00Z"),
            kind = "coffee",
            mg = 95.0,
        )
        val moved = CaffeineEntry(
            id = UUID.randomUUID(),
            date = Instant.parse("2026-08-03T12:00:00Z"),
            kind = "t_abcd1234",
            mg = 65.0,
        )
        repo.add(keep)
        repo.add(moved)

        repo.reassignKind("t_abcd1234")

        val after = prefs.caffeineEntries.first().sortedBy { it.date }
        assertEquals(2, after.size)
        // Same id/date/mg; only the kind moved.
        assertEquals(moved.id, after[1].id)
        assertEquals(moved.date, after[1].date)
        assertEquals(65.0, after[1].mg, 0.001)
        assertEquals("other", after[1].kind)
        assertEquals("coffee", after[0].kind)
    }

    @Test
    fun `nicotine reassignKind keeps count and mg`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val repo = NicotineRepository(prefs)
        val moved = NicotineEntry(
            id = UUID.randomUUID(),
            date = Instant.parse("2026-08-03T18:30:00Z"),
            kind = "t_51fa9410",
            count = 2,
            mg = 6.0,
        )
        repo.add(moved)

        repo.reassignKind("t_51fa9410")

        val after = prefs.nicotineEntries.first().single()
        assertEquals(moved.id, after.id)
        assertEquals("other", after.kind)
        assertEquals(2, after.count)
        assertEquals(6.0, after.mg!!, 0.001)

        // Reassigning a kind nobody logged is a no-op.
        repo.reassignKind("t_nope0000")
        assertEquals(1, prefs.nicotineEntries.first().size)
    }

    @Test
    fun `preset catalogs round-trip through prefs`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        var catalog = HabitPresetDomain.CAFFEINE.defaultCatalog
            .withLabel("tea", "Matcha")
            .addCustom(HabitPresetDomain.CAFFEINE, "Espresso", defaultMg = 65.0)

        prefs.setCaffeinePresets(catalog)
        assertEquals(catalog, prefs.caffeinePresets.first())
        assertTrue(catalog.validate(HabitPresetDomain.CAFFEINE) == null)
        assertEquals("Matcha", prefs.caffeinePresets.first().def("tea")!!.label)

        // Nicotine: defaults when never written.
        assertEquals(
            HabitPresetDomain.NICOTINE.defaultCatalog,
            prefs.nicotinePresets.first(),
        )

        // Corrupt stored JSON resets to the builtin defaults.
        prefs.setStringPref(
            androidx.datastore.preferences.core.stringPreferencesKey("nicotinePresetsJson"),
            "{broken",
        )
        Unit
        assertEquals(HabitPresetDomain.NICOTINE.defaultCatalog, prefs.nicotinePresets.first())
        assertNull(HabitPresetDomain.NICOTINE.defaultCatalog.presets.first().label.takeIf { it.isNotEmpty() })
    }
}
