package app.chompass.services

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NextMidnightTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun nextMidnight_rollsToFollowingLocalDay() {
        // 2026-08-13 23:59:59 UTC -> 2026-08-14 00:00:00 UTC
        val now = Instant.parse("2026-08-13T23:59:59Z").toEpochMilli()
        assertEquals(Instant.parse("2026-08-14T00:00:00Z").toEpochMilli(), nextMidnightMillis(now, utc))
    }

    @Test
    fun nextMidnight_earlyMorningAlsoTargetsNextDay() {
        // Just after midnight -> next midnight, never "now".
        val now = Instant.parse("2026-08-13T00:00:01Z").toEpochMilli()
        assertEquals(Instant.parse("2026-08-14T00:00:00Z").toEpochMilli(), nextMidnightMillis(now, utc))
    }

    @Test
    fun nextMidnight_usesProvidedZone() {
        // 2026-08-13 22:30 UTC == 2026-08-14 00:30 in UTC+2 -> midnight 2026-08-15 00:00 UTC+2
        val zone = ZoneId.of("Europe/Berlin")
        val now = Instant.parse("2026-08-13T22:30:00Z").toEpochMilli()
        val expected = Instant.parse("2026-08-14T22:00:00Z").toEpochMilli() // 2026-08-15 00:00 +02:00
        assertEquals(expected, nextMidnightMillis(now, zone))
    }
}
