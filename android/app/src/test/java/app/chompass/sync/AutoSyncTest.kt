package app.chompass.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AutoSyncTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 31)

    @Test
    fun tracksRevisionsWhenUrlIsSetRegardlessOfAutoSync() {
        assertFalse(shouldTrackSyncRevisions(""))
        assertFalse(shouldTrackSyncRevisions("   "))
        assertTrue(shouldTrackSyncRevisions("https://dav.example/chompass.json"))
        assertTrue(shouldTrackSyncRevisions("dav.example/chompass.json"))
    }

    @Test
    fun skipsWhenDisabledOrNotConfigured() {
        assertFalse(
            shouldAutoRunOnOpen(
                enabled = false,
                configured = true,
                today = today,
                lastRunAtIso = null,
                lastAutoRunDayIso = null,
                zone = zone,
            ),
        )
        assertFalse(
            shouldAutoRunOnOpen(
                enabled = true,
                configured = false,
                today = today,
                lastRunAtIso = null,
                lastAutoRunDayIso = null,
                zone = zone,
            ),
        )
    }

    @Test
    fun runsOncePerDayWhenEnabled() {
        assertTrue(
            shouldAutoRunOnOpen(
                enabled = true,
                configured = true,
                today = today,
                lastRunAtIso = null,
                lastAutoRunDayIso = null,
                zone = zone,
            ),
        )
        assertFalse(
            shouldAutoRunOnOpen(
                enabled = true,
                configured = true,
                today = today,
                lastRunAtIso = null,
                lastAutoRunDayIso = "2026-07-31",
                zone = zone,
            ),
        )
    }

    @Test
    fun skipsWhenAlreadySyncedToday() {
        assertFalse(
            shouldAutoRunOnOpen(
                enabled = true,
                configured = true,
                today = today,
                lastRunAtIso = "2026-07-31T08:00:00Z",
                lastAutoRunDayIso = null,
                zone = zone,
            ),
        )
    }

    @Test
    fun allowsNextDayAfterYesterdayAttempt() {
        assertTrue(
            shouldAutoRunOnOpen(
                enabled = true,
                configured = true,
                today = today,
                lastRunAtIso = "2026-07-30T22:00:00Z",
                lastAutoRunDayIso = "2026-07-30",
                zone = zone,
            ),
        )
    }
}
