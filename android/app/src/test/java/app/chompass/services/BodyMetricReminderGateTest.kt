package app.chompass.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BodyMetricReminderGateTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun shouldNotifyBodyMetric_whenNoEntryToday() {
        assertTrue(shouldNotifyBodyMetric(hasEntryToday = false))
    }

    @Test
    fun shouldNotifyBodyMetric_skipsWhenEntryAlreadyLogged() {
        assertFalse(shouldNotifyBodyMetric(hasEntryToday = true))
    }

    @Test
    fun entryFallsOnLocalDay_usesZoneNotUtcDate() {
        val day = LocalDate.parse("2026-10-15")
        // 07:00 Berlin (CEST, UTC+2).
        assertTrue(entryFallsOnLocalDay(Instant.parse("2026-10-15T05:00:00Z"), day, berlin))
        // 00:30 Berlin on the 15th is still the 14th in UTC.
        assertTrue(entryFallsOnLocalDay(Instant.parse("2026-10-14T22:30:00Z"), day, berlin))
        assertFalse(entryFallsOnLocalDay(Instant.parse("2026-10-14T22:30:00Z"), day.minusDays(1), berlin))
        // Next local midnight belongs to the 16th.
        assertFalse(entryFallsOnLocalDay(Instant.parse("2026-10-15T22:00:00Z"), day, berlin))
        assertTrue(entryFallsOnLocalDay(Instant.parse("2026-10-15T21:59:59Z"), day, berlin))
    }

    @Test
    fun nextDailyFire_beforeTheHourStaysToday() {
        // 07:30 Berlin -> 08:00 the same morning.
        val now = Instant.parse("2026-10-15T05:30:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-15T06:00:00Z").toEpochMilli(),
            nextDailyFireMillis(8, 0, now, berlin),
        )
    }

    @Test
    fun nextDailyFire_atTheHourArmsTomorrow() {
        val now = Instant.parse("2026-10-15T06:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-16T06:00:00Z").toEpochMilli(),
            nextDailyFireMillis(8, 0, now, berlin),
        )
    }

    @Test
    fun nextDailyFire_afterTheHourArmsTomorrow() {
        // Inexact alarm a few seconds late must not drift off 08:00.
        val now = Instant.parse("2026-10-15T06:07:12Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-16T06:00:00Z").toEpochMilli(),
            nextDailyFireMillis(8, 0, now, berlin),
        )
    }
}
