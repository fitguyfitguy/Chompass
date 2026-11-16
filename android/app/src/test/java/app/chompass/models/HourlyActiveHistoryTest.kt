package app.chompass.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyActiveHistoryTest {
    private val complete = List(HourlyActiveHistory.HOURS) { 0 }
    private val gappy = List(HourlyActiveHistory.HOURS) { hour -> if (hour == 3) -1 else 0 }

    @Test
    fun recentCompleteDay_isReread() {
        assertTrue(HourlyActiveHistory.needsRefresh(complete, daysAgo = 1))
        assertTrue(HourlyActiveHistory.needsRefresh(complete, daysAgo = 2))
        assertFalse(HourlyActiveHistory.needsRefresh(complete, daysAgo = 3))
    }

    @Test
    fun incompleteOlderDay_isReread() {
        assertTrue(HourlyActiveHistory.needsRefresh(gappy, daysAgo = 10))
        assertTrue(HourlyActiveHistory.needsRefresh(null, daysAgo = 10))
    }
}
