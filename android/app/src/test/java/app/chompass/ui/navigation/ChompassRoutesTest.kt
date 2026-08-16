package app.chompass.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security regression (docs/SECURITY_HARDENING_PLAN.md P2-1): only exactly-known
 * plain routes may be navigated via `chompass://go/<dest>`. `nav.navigate()`
 * throws for unknown destinations, so the whitelist is what keeps an unprivileged
 * app from crashing us with a crafted VIEW intent.
 */
class ChompassRoutesTest {
    @Test
    fun knownPlainRoutes_areGoDestinations() {
        assertTrue(ChompassRoutes.isGoDestination(ChompassRoutes.PROGRESS))
        assertTrue(ChompassRoutes.isGoDestination(ChompassRoutes.HOME))
        assertTrue(ChompassRoutes.isGoDestination(ChompassRoutes.COACH))
        assertTrue(ChompassRoutes.isGoDestination(ChompassRoutes.SETTINGS))
        assertTrue(ChompassRoutes.isGoDestination(ChompassRoutes.SETTINGS_AI))
        assertTrue(ChompassRoutes.isGoDestination(ChompassRoutes.BODY_MEASUREMENTS))
    }

    @Test
    fun unknownDestinations_areNotGoDestinations() {
        assertFalse(ChompassRoutes.isGoDestination("garbage"))
        assertFalse(ChompassRoutes.isGoDestination("progress/evil"))
        assertFalse(ChompassRoutes.isGoDestination("../home"))
        assertFalse(ChompassRoutes.isGoDestination(""))
        assertFalse(ChompassRoutes.isGoDestination("onboarding"))
    }

    @Test
    fun argRoutedScreens_areNotGoDestinations() {
        // Path-only `go` links cannot satisfy ?from={from} patterns; navigating
        // without the arg would throw, so they stay off the whitelist.
        assertFalse(ChompassRoutes.isGoDestination("settings/water"))
        assertFalse(ChompassRoutes.isGoDestination("settings/notifications"))
        assertFalse(ChompassRoutes.isGoDestination("settings/sync"))
    }
}
