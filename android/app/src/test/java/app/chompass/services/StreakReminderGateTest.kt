package app.chompass.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakReminderGateTest {
    @Test
    fun shouldNotifyStreak_whenNoFoodLoggedToday() {
        assertTrue(shouldNotifyStreak(hasFoodLoggedToday = false))
    }

    @Test
    fun shouldNotifyStreak_skipsWhenFoodAlreadyLogged() {
        assertFalse(shouldNotifyStreak(hasFoodLoggedToday = true))
    }
}
