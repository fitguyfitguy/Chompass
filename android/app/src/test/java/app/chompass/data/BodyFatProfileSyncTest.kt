package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyFatMethod
import app.chompass.models.GoalSource
import app.chompass.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Body-fat → profile sync: the newest reading represents the user unless it
 * spikes >5 points over the previous reading within 14 days (then the previous
 * entry stands until a confirming reading), out-of-range fractions are dropped,
 * and an explicit `forceProfile` entry wins only while it is the newest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BodyFatProfileSyncTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var profiles: ProfileRepository
    private lateinit var repo: BodyFatRepository
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    @Before
    fun setUp() = runBlocking {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        profiles = ProfileRepository(prefs)
        repo = BodyFatRepository(prefs, profiles)
        profiles.save(UserProfile(weightKg = 80.0))
    }

    private fun entry(fraction: Double, daysAgo: Long) =
        BodyFatEntry(date = now.minusSeconds(daysAgo * 86_400L), bodyFatFraction = fraction)

    @Test
    fun spikeJump_keepsPreviousReading() = runBlocking {
        repo.addEntry(entry(0.20, 3))
        repo.addEntry(entry(0.27, 1))
        assertEquals(0.20, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun oldPreviousOutsideWindow_newestWins() = runBlocking {
        repo.addEntry(entry(0.20, 30))
        repo.addEntry(entry(0.30, 1))
        assertEquals(0.30, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun confirmingSecondReading_makesSpikeRepresentative() = runBlocking {
        repo.addEntry(entry(0.20, 3))
        repo.addEntry(entry(0.27, 2))
        repo.addEntry(entry(0.27, 1))
        assertEquals(0.27, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun invalidFractionsClampedOrRefused() = runBlocking {
        // 0.90 clamps to the 0.65 ceiling and stores…
        repo.addEntry(entry(0.90, 2))
        assertEquals(0.65, profiles.current()!!.bodyFatPercentage!!, 1e-9)
        // …and the clamped reading participates in the spike guard like any other:
        // 0.22 right after 0.65 is a >5-point drop, so 0.65 stands…
        repo.addEntry(entry(0.22, 1))
        assertEquals(0.65, profiles.current()!!.bodyFatPercentage!!, 1e-9)
        // …until a second confirming reading.
        repo.addEntry(entry(0.22, 0))
        assertEquals(0.22, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun nonFiniteFractionRefused() = runBlocking {
        repo.addEntry(entry(0.20, 2))
        repo.addEntry(entry(Double.NaN, 1))
        assertEquals(1, prefs.bodyFatEntries.first().size)
        assertEquals(0.20, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun forcedEntryWinsOnlyWhileNewest() = runBlocking {
        repo.addEntry(entry(0.20, 3))
        // A past-dated "use as my body fat" cannot pull the profile backward…
        repo.addEntry(entry(0.25, 10), forceProfile = true)
        assertEquals(0.20, profiles.current()!!.bodyFatPercentage!!, 1e-9)
        // …but an explicit today entry drives the profile past the spike guard.
        repo.addEntry(entry(0.28, 0), forceProfile = true)
        assertEquals(0.28, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun sanitize_clampsIntoValidWindow() {
        assertEquals(0.03, sanitizeBodyFatFraction(0.0)!!, 1e-9)
        assertEquals(0.65, sanitizeBodyFatFraction(0.9)!!, 1e-9)
        assertEquals(0.42, sanitizeBodyFatFraction(0.42)!!, 1e-9)
        assertNull(sanitizeBodyFatFraction(Double.NaN))
        assertNull(sanitizeBodyFatFraction(Double.POSITIVE_INFINITY))
    }

    @Test
    fun syncReclampsAutoGoalOnBmrMove() = runBlocking {
        // AI goal stored below the post-reading Katch floor: the sync pulls it in.
        profiles.save(
            UserProfile(
                weightKg = 80.0,
                bodyFatPercentage = 0.20,
                customCalories = 1400,
                goalSource = GoalSource.AI,
            ),
        )
        val before = profiles.current()!!
        val floor = app.chompass.models.CalorieSafety.floorKcal(before.bmr)
        repo.addEntry(entry(0.05, 1))
        val after = profiles.current()!!
        assertTrue(after.effectiveCalories >= floor)
        assertEquals(0.05, after.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun accurateMethod_tightensTheGuard() = runBlocking {
        // DEXA ±1.5 pp: a 2-point jump is suspect until confirmed…
        profiles.save(UserProfile(weightKg = 80.0, bodyFatPercentage = null, bodyFatMethod = BodyFatMethod.DEXA))
        repo.addEntry(entry(0.20, 3))
        repo.addEntry(entry(0.22, 1))
        assertEquals(0.20, profiles.current()!!.bodyFatPercentage!!, 1e-9)
        // …and a confirming reading accepts it.
        repo.addEntry(entry(0.22, 0))
        assertEquals(0.22, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun looseMethod_keepsJumpsWithinAccuracy() = runBlocking {
        // 4-electrode ±5 pp: a 4-point jump is within stated accuracy → accepted.
        profiles.save(UserProfile(weightKg = 80.0, bodyFatPercentage = null, bodyFatMethod = BodyFatMethod.BIA_FOUR))
        repo.addEntry(entry(0.20, 3))
        repo.addEntry(entry(0.24, 1))
        assertEquals(0.24, profiles.current()!!.bodyFatPercentage!!, 1e-9)
    }

    @Test
    fun customAccuracyOverride_wins() {
        val p = UserProfile(bodyFatMethod = BodyFatMethod.BIA_EIGHT, bodyFatAccuracyPoints = 4.0)
        assertEquals(4.0, p.effectiveBodyFatAccuracyPoints, 1e-9)
        assertEquals(3.2, UserProfile(bodyFatMethod = BodyFatMethod.BIA_EIGHT).effectiveBodyFatAccuracyPoints, 1e-9)
        // Legacy default: today's ±5 pp guard.
        assertEquals(5.0, UserProfile().effectiveBodyFatAccuracyPoints, 1e-9)
    }

    @Test
    fun methodDrivesFreshness() {
        val at = Instant.parse("2026-08-10T12:00:00Z")
        val dexa = UserProfile(
            bodyFatPercentage = 0.20,
            bodyFatUpdatedAt = at.minusSeconds(200L * 86_400),
            bodyFatMethod = BodyFatMethod.DEXA,
        )
        val legacy = UserProfile(
            bodyFatPercentage = 0.20,
            bodyFatUpdatedAt = at.minusSeconds(200L * 86_400),
        )
        // A DEXA scan stays fresh ~a year; an undated-method reading is stale at 90 d.
        assertFalse(dexa.isBodyFatStale(at))
        assertTrue(legacy.isBodyFatStale(at))
        assertEquals(365L, dexa.bodyFatRecencyDays)
        assertEquals(90L, legacy.bodyFatRecencyDays)
    }
}
