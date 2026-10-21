package app.chompass.data

import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.services.health.ExternalWeight
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
 * Health Connect deletion tombstones (upstream d03be0ea): a weigh-in the user
 * deleted must not resurrect when the same Health Connect record re-imports
 * under its deterministic id — backfill or live changes path, fudai_-tagged
 * or not. Non-deleted records still import, and the tombstone list caps at
 * 1000 ids with oldest-first eviction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WeightHcTombstoneTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var profiles: ProfileRepository
    private lateinit var health: HealthConnectManager
    private lateinit var repo: WeightRepository
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    @Before
    fun setUp() = runBlocking {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        profiles = ProfileRepository(prefs)
        health = HealthConnectManager(RuntimeEnvironment.getApplication())
        repo = WeightRepository(prefs, profiles, health)
        profiles.save(UserProfile(weightKg = 80.0))
    }

    @Test
    fun deletedImportStaysDeletedOnReimport() = runBlocking {
        // "wt-tombstone-t1" is unique to this test: the DataStore singleton leaks
        // across tests in the sandbox, so a shared seed would tombstone the id a
        // sibling test still needs to import.
        repo.importExternalWeights(
            listOf(ExternalWeight(time = now, weightKg = 82.5, clientRecordId = null, recordId = "wt-tombstone-t1"))
        )
        val imported = prefs.weightEntries.first().single()
        repo.deleteEntry(imported.id)
        assertTrue(prefs.weightEntries.first().isEmpty())
        // contains, not equality: the DataStore singleton leaks across sandbox
        // tests, so earlier tests' ids may legitimately still be listed.
        assertTrue(prefs.healthDeletedWeightIds.first().contains(imported.id))

        // The same record on the next backfill maps onto the same deterministic
        // id and must be dropped instead of resurrecting.
        repo.importExternalWeights(
            listOf(ExternalWeight(time = now, weightKg = 82.5, clientRecordId = null, recordId = "wt-tombstone-t1"))
        )
        assertTrue(prefs.weightEntries.first().isEmpty())
    }

    @Test
    fun deletedOwnRecordStaysDeletedEvenWhenHcDeleteFailed() = runBlocking {
        // Own records restore under their original UUID via the fudai_ client
        // record id; the tombstone applies to that id exactly the same.
        val ownId = UUID.randomUUID()
        repo.importExternalWeights(
            listOf(ExternalWeight(time = now, weightKg = 82.0, clientRecordId = HealthConnectManager.CLIENT_PREFIX + ownId))
        )
        repo.deleteEntry(ownId)
        // Simulate the failed HC delete: the record is still there on re-import.
        repo.importExternalWeights(
            listOf(ExternalWeight(time = now, weightKg = 82.0, clientRecordId = HealthConnectManager.CLIENT_PREFIX + ownId))
        )
        assertTrue(prefs.weightEntries.first().isEmpty())
    }

    @Test
    fun nonTombstonedRecordsStillImport() = runBlocking {
        repo.importExternalWeights(
            listOf(
                ExternalWeight(time = now, weightKg = 82.5, clientRecordId = null, recordId = "wt-tombstone-t3a"),
                ExternalWeight(time = now.minusSeconds(3_600), weightKg = 83.0, clientRecordId = null, recordId = "wt-tombstone-t3b"),
            )
        )
        repo.deleteEntry(prefs.weightEntries.first().single { it.weightKg == 82.5 }.id)

        // A sibling record from the same backfill is untouched by the tombstone.
        repo.importExternalWeights(
            listOf(ExternalWeight(time = now.minusSeconds(3_600), weightKg = 83.0, clientRecordId = null, recordId = "wt-tombstone-t3b"))
        )
        assertEquals(listOf(83.0), prefs.weightEntries.first().map { it.weightKg })
    }

    @Test
    fun deleteEntryEvictsOldestTombstoneAtCap() = runBlocking {
        val preexisting = List(1000) { UUID.randomUUID() }
        prefs.setHealthDeletedWeightIds(preexisting)
        val entry = WeightEntry(date = now, weightKg = 81.0)
        repo.addEntry(entry)
        repo.deleteEntry(entry.id)

        val stored = prefs.healthDeletedWeightIds.first()
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
        prefs.setHealthDeletedWeightIds(ids)
        assertEquals(ids, prefs.healthDeletedWeightIds.first())
        prefs.setHealthDeletedWeightIds(emptyList())
        assertEquals(emptyList<UUID>(), prefs.healthDeletedWeightIds.first())
    }
}
