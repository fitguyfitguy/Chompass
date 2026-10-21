package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.models.UserProfile
import app.chompass.services.health.ExternalBodyFat
import app.chompass.services.health.HealthConnectManager
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Body-fat mirror of [WeightHcTombstoneTest]: a reading the user deleted must
 * not resurrect when the same Health Connect record re-imports under its
 * deterministic id, non-deleted records still import, and the tombstone list
 * caps at 1000 ids with oldest-first eviction (upstream d03be0ea).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BodyFatHcTombstoneTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var profiles: ProfileRepository
    private lateinit var health: HealthConnectManager
    private lateinit var repo: BodyFatRepository
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    @Before
    fun setUp() = runBlocking {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        profiles = ProfileRepository(prefs)
        health = HealthConnectManager(RuntimeEnvironment.getApplication())
        repo = BodyFatRepository(prefs, profiles, health)
        profiles.save(UserProfile(weightKg = 80.0))
    }

    @Test
    fun deletedImportStaysDeletedOnReimport() = runBlocking {
        // "bf-tombstone-t1" is unique to this test: the DataStore singleton leaks
        // across tests in the sandbox, so a shared seed would tombstone the id a
        // sibling test still needs to import.
        repo.importExternalBodyFats(
            listOf(ExternalBodyFat(time = now, bodyFatFraction = 0.25, clientRecordId = null, recordId = "bf-tombstone-t1"))
        )
        val imported = prefs.bodyFatEntries.first().single()
        repo.deleteEntry(imported.id)
        assertTrue(prefs.bodyFatEntries.first().isEmpty())
        // contains, not equality: the DataStore singleton leaks across sandbox
        // tests, so earlier tests' ids may legitimately still be listed.
        assertTrue(prefs.healthDeletedBodyFatIds.first().contains(imported.id))

        // The same record on the next backfill maps onto the same deterministic
        // id and must be dropped instead of resurrecting.
        repo.importExternalBodyFats(
            listOf(ExternalBodyFat(time = now, bodyFatFraction = 0.25, clientRecordId = null, recordId = "bf-tombstone-t1"))
        )
        assertTrue(prefs.bodyFatEntries.first().isEmpty())
    }

    @Test
    fun deletedOwnRecordStaysDeletedEvenWhenHcDeleteFailed() = runBlocking {
        // Own records restore under their original UUID via the fudai_ client
        // record id; the tombstone applies to that id exactly the same.
        val ownId = UUID.randomUUID()
        repo.importExternalBodyFats(
            listOf(ExternalBodyFat(time = now, bodyFatFraction = 0.24, clientRecordId = HealthConnectManager.CLIENT_PREFIX + ownId))
        )
        repo.deleteEntry(ownId)
        // Simulate the failed HC delete: the record is still there on re-import.
        repo.importExternalBodyFats(
            listOf(ExternalBodyFat(time = now, bodyFatFraction = 0.24, clientRecordId = HealthConnectManager.CLIENT_PREFIX + ownId))
        )
        assertTrue(prefs.bodyFatEntries.first().isEmpty())
    }

    @Test
    fun nonTombstonedRecordsStillImport() = runBlocking {
        repo.importExternalBodyFats(
            listOf(
                ExternalBodyFat(time = now, bodyFatFraction = 0.25, clientRecordId = null, recordId = "bf-tombstone-t3a"),
                ExternalBodyFat(time = now.minusSeconds(3_600), bodyFatFraction = 0.26, clientRecordId = null, recordId = "bf-tombstone-t3b"),
            )
        )
        repo.deleteEntry(prefs.bodyFatEntries.first().single { it.bodyFatFraction == 0.25 }.id)

        // A sibling record from the same backfill is untouched by the tombstone.
        repo.importExternalBodyFats(
            listOf(ExternalBodyFat(time = now.minusSeconds(3_600), bodyFatFraction = 0.26, clientRecordId = null, recordId = "bf-tombstone-t3b"))
        )
        assertEquals(listOf(0.26), prefs.bodyFatEntries.first().map { it.bodyFatFraction })
    }

    @Test
    fun deleteEntryEvictsOldestTombstoneAtCap() = runBlocking {
        val preexisting = List(1000) { UUID.randomUUID() }
        prefs.setHealthDeletedBodyFatIds(preexisting)
        val entry = BodyFatEntry(date = now, bodyFatFraction = 0.22)
        repo.addEntry(entry)
        repo.deleteEntry(entry.id)

        val stored = prefs.healthDeletedBodyFatIds.first()
        assertEquals(1000, stored.size)
        assertEquals(preexisting.drop(1) + entry.id, stored)
    }

    @Test
    fun tombstoneCapEvictsOldestFirst() {
        val appended = List(1001) { UUID.randomUUID() }
        var ids = emptyList<UUID>()
        for (id in appended) ids = appendTombstoneId(ids, id)
        assertEquals(1000, ids.size)
        assertEquals(appended.drop(1), ids)
    }

    @Test
    fun tombstoneIdsRoundTripInOrder() = runBlocking {
        val ids = List(3) { UUID.randomUUID() }
        prefs.setHealthDeletedBodyFatIds(ids)
        assertEquals(ids, prefs.healthDeletedBodyFatIds.first())
        prefs.setHealthDeletedBodyFatIds(emptyList())
        assertEquals(emptyList<UUID>(), prefs.healthDeletedBodyFatIds.first())
    }
}
