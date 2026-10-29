package app.chompass.data

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.GoalSource
import app.chompass.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Scale-composition store: per-day manual upsert, by-id file import with the
 * near-duplicate guard, and the one profile side effect — the newest valid lean-mass
 * reading becomes `UserProfile.measuredLeanMassKg` (which then drives Katch-McArdle
 * BMR and the protein cap).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BodyCompositionRepositoryTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var profiles: ProfileRepository
    private lateinit var repo: BodyCompositionRepository
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    @Before
    fun setUp() = runBlocking {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        profiles = ProfileRepository(prefs)
        repo = BodyCompositionRepository(prefs, profiles)
        profiles.save(UserProfile(weightKg = 80.0))
    }

    private fun at(hoursAgo: Long) = now.minusSeconds(hoursAgo * 3_600L)

    @Test
    fun `second metric logged the same day updates that day's single row`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(6), boneMassKg = 3.2))
        repo.upsert(BodyCompositionEntry(date = at(1), totalBodyWaterKg = 46.0))
        val rows = prefs.bodyCompositions.first()
        assertEquals(1, rows.size)
        assertEquals(3.2, rows.single().boneMassKg!!, 0.0001)
        assertEquals(46.0, rows.single().totalBodyWaterKg!!, 0.0001)
    }

    @Test
    fun `a new value for an already-set metric wins on the same day`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(6), leanMassKg = 64.0))
        repo.upsert(BodyCompositionEntry(date = at(1), leanMassKg = 64.8))
        assertEquals(64.8, prefs.bodyCompositions.first().single().leanMassKg!!, 0.0001)
    }

    @Test
    fun `an all-null entry writes nothing`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(1)))
        assertTrue(prefs.bodyCompositions.first().isEmpty())
    }

    @Test
    fun `inserting a lean mass stamps the profile and marks auto goals stale`() = runBlocking {
        // Auto-sourced goal so the re-clamp path is live.
        profiles.save(profiles.current()!!.copy(goalSource = GoalSource.FORMULA))
        repo.upsert(BodyCompositionEntry(date = at(2), leanMassKg = 61.0))
        val profile = profiles.current()!!
        assertEquals(61.0, profile.measuredLeanMassKg!!, 0.0001)
        assertEquals(at(2), profile.leanMassUpdatedAt)
        // Katch-McArdle now runs on the measured reading.
        assertTrue(profile.usesMeasuredLeanMassForBMR)
        assertEquals(370.0 + 21.6 * 61.0, profile.bmr, 0.0001)
    }

    @Test
    fun `a row without lean mass never moves the profile stamp`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(2), boneMassKg = 3.2, visceralFatRating = 8.0))
        val profile = profiles.current()!!
        assertNull(profile.measuredLeanMassKg)
        assertNull(profile.leanMassUpdatedAt)
    }

    @Test
    fun `an implausible lean mass is stored but not stamped onto the profile`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(2), leanMassKg = 6.1))
        assertEquals(6.1, prefs.bodyCompositions.first().single().leanMassKg!!, 0.0001)
        assertNull(profiles.current()!!.measuredLeanMassKg)
    }

    @Test
    fun `newest valid lean mass wins over an older one`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(48), leanMassKg = 63.0))
        repo.upsert(BodyCompositionEntry(date = at(2), leanMassKg = 62.0))
        assertEquals(62.0, profiles.current()!!.measuredLeanMassKg!!, 0.0001)
    }

    @Test
    fun `file import upserts by id and reports the change count`() = runBlocking {
        val id = java.util.UUID.randomUUID()
        val first = listOf(BodyCompositionEntry(id = id, date = at(24), leanMassKg = 64.0))
        assertEquals(1, repo.importFromFile(first))
        assertEquals(0, repo.importFromFile(first))
        val corrected = listOf(BodyCompositionEntry(id = id, date = at(24), leanMassKg = 64.6, boneMassKg = 3.2))
        assertEquals(1, repo.importFromFile(corrected))
        val row = prefs.bodyCompositions.first().single()
        assertEquals(64.6, row.leanMassKg!!, 0.0001)
        assertEquals(3.2, row.boneMassKg!!, 0.0001)
    }

    @Test
    fun `external import maps a deterministic id so re-sync updates in place`() = runBlocking {
        val manager = app.chompass.services.health.HealthConnectManager(RuntimeEnvironment.getApplication())
        val external = listOf(
            app.chompass.services.health.ExternalBodyComposition(
                time = at(3),
                boneMassKg = 3.2,
                totalBodyWaterKg = 45.8,
                leanMassKg = 64.4,
                clientRecordId = null,
                recordId = "hc-record-1",
            )
        )
        val wired = BodyCompositionRepository(prefs, profiles, manager)
        wired.importExternal(external)
        wired.importExternal(external)
        assertEquals(1, prefs.bodyCompositions.first().size)
        assertEquals(64.4, profiles.current()!!.measuredLeanMassKg!!, 0.0001)
    }

    @Test
    fun `delete removes the row and leaves the stamped profile alone`() = runBlocking {
        repo.upsert(BodyCompositionEntry(date = at(2), leanMassKg = 61.0))
        val id = prefs.bodyCompositions.first().single().id
        repo.deleteEntry(id)
        assertTrue(prefs.bodyCompositions.first().isEmpty())
        // No history left to choose from: the profile keeps its last known value
        // rather than silently dropping someone's BMR basis (same rule as body fat).
        assertNotNull(profiles.current()!!.measuredLeanMassKg)
    }
}
