package app.chompass.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WebDavAutoSyncTest {
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
            shouldAutoSyncWebDav(
                enabled = false,
                configured = true,
                today = today,
                lastSyncAtIso = null,
                lastAutoSyncDayIso = null,
                zone = zone,
            ),
        )
        assertFalse(
            shouldAutoSyncWebDav(
                enabled = true,
                configured = false,
                today = today,
                lastSyncAtIso = null,
                lastAutoSyncDayIso = null,
                zone = zone,
            ),
        )
    }

    @Test
    fun runsOncePerDayWhenEnabled() {
        assertTrue(
            shouldAutoSyncWebDav(
                enabled = true,
                configured = true,
                today = today,
                lastSyncAtIso = null,
                lastAutoSyncDayIso = null,
                zone = zone,
            ),
        )
        assertFalse(
            shouldAutoSyncWebDav(
                enabled = true,
                configured = true,
                today = today,
                lastSyncAtIso = null,
                lastAutoSyncDayIso = "2026-07-31",
                zone = zone,
            ),
        )
    }

    @Test
    fun skipsWhenAlreadySyncedToday() {
        assertFalse(
            shouldAutoSyncWebDav(
                enabled = true,
                configured = true,
                today = today,
                lastSyncAtIso = "2026-07-31T08:00:00Z",
                lastAutoSyncDayIso = null,
                zone = zone,
            ),
        )
    }

    @Test
    fun allowsNextDayAfterYesterdayAttempt() {
        assertTrue(
            shouldAutoSyncWebDav(
                enabled = true,
                configured = true,
                today = today,
                lastSyncAtIso = "2026-07-30T22:00:00Z",
                lastAutoSyncDayIso = "2026-07-30",
                zone = zone,
            ),
        )
    }
}
