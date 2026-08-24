package app.chompass.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The daily fast-start clock anchor (docs/local/PLAN_FASTING_TRACKER.md §7d):
 * nextFastingStartMillis is shared by the auto-start alarm, the start nudge,
 * and the Home bar, so all three must agree on "when the fast starts".
 */
class FastingStartTimeTest {
    private fun at(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun nextStart_isTodayWhenNowIsBeforeIt() = runBlocking {
        // 19:00 Europe/Berlin on a day, start time 20:00 → today 20:00.
        val now = at("2026-08-24T19:00:00+02:00")
        val next = nextFastingStartMillis(20, 0, now)
        val zoned = Instant.ofEpochMilli(next).atZone(ZoneId.of("Europe/Berlin"))
        assertEquals("2026-08-24", zoned.toLocalDate().toString())
        assertEquals(20, zoned.hour)
    }

    @Test
    fun nextStart_isTomorrowWhenNowIsPastIt() = runBlocking {
        val now = at("2026-08-24T21:00:00+02:00")
        val next = nextFastingStartMillis(20, 0, now)
        val zoned = Instant.ofEpochMilli(next).atZone(ZoneId.of("Europe/Berlin"))
        assertEquals("2026-08-25", zoned.toLocalDate().toString())
        assertEquals(20, zoned.hour)
    }

    @Test
    fun nextStart_honorsMinutes() = runBlocking {
        val now = at("2026-08-24T07:30:00+02:00")
        val next = nextFastingStartMillis(8, 15, now)
        val zoned = Instant.ofEpochMilli(next).atZone(ZoneId.of("Europe/Berlin"))
        assertEquals(8, zoned.hour)
        assertEquals(15, zoned.minute)
    }

    @Test
    fun nextStart_isNotTomorrowExactlyAtTheBoundary() = runBlocking {
        // At exactly 20:00 the start is "now" (returned as today); callers
        // treat >= now as due.
        val now = at("2026-08-24T20:00:00+02:00")
        val next = nextFastingStartMillis(20, 0, now)
        val zoned = Instant.ofEpochMilli(next).atZone(ZoneId.of("Europe/Berlin"))
        assertEquals("2026-08-24", zoned.toLocalDate().toString())
        assertEquals(now, next)
    }
}
