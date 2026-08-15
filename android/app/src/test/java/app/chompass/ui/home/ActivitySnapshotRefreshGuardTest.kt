package app.chompass.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Last-requested-wins guard for the home activity snapshot (Codeberg #22).
 * Health Connect reads are slow and day-dependent, so an older day's read may
 * finish after a newer day's — without the guard the stale snapshot would
 * overwrite the newer one ("previous day's active calories stick").
 */
class ActivitySnapshotRefreshGuardTest {
    @Test
    fun begin_firstCallIsCurrent() {
        val guard = ActivitySnapshotRefreshGuard()
        assertTrue(guard.isCurrent(guard.begin()))
    }

    @Test
    fun begin_twice_onlyLastRequestIsCurrent() {
        val guard = ActivitySnapshotRefreshGuard()
        val first = guard.begin()
        val second = guard.begin()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
    }

    @Test
    fun begin_threeTimes_onlyMostRecentWins() {
        val guard = ActivitySnapshotRefreshGuard()
        val first = guard.begin()
        val second = guard.begin()
        val third = guard.begin()
        assertFalse(guard.isCurrent(first))
        assertFalse(guard.isCurrent(second))
        assertTrue(guard.isCurrent(third))
    }

    @Test
    fun isCurrent_zeroTokenBeforeFirstBegin() {
        val guard = ActivitySnapshotRefreshGuard()
        // Generation starts at 0, so the initial token is current until the
        // first begin(). Production tokens always come from begin() (>= 1).
        assertTrue(guard.isCurrent(0))
    }

    @Test
    fun begin_invalidatesInitialZeroToken() {
        val guard = ActivitySnapshotRefreshGuard()
        guard.begin()
        assertFalse(guard.isCurrent(0))
        assertFalse(guard.isCurrent(-1))
    }
}
