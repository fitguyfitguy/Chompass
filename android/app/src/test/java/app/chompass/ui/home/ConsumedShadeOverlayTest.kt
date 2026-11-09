package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The consumed overlay is the eaten extent clipped to the activity zone, and
 * both surfaces draw it with round ends: a [StrokeCap.Round] stroke on the
 * arc, a full round rect on the bar. The round caps themselves cover the
 * zone-start cap behind the span and, ahead of it, the eaten tip and any band
 * cap the tip has reached — so a partial eat never ends square. The span
 * therefore stays exactly [zoneStart, eatenEnd]; the old scheme of growing a
 * butt stroke by cap angles (and the bar's square-end shortcut) is gone.
 */
class ConsumedShadeOverlayTest {
    @Test
    fun partialEatInsideABand_spansFromZoneStartToTheTip() {
        val span = consumedShadeOverlaySpan(
            zoneStart = 250f,
            eatenEnd = 300f,
            arcEnd = 360f,
        )
        assertEquals(250f, span!!.start, 0.01f)
        assertEquals(50f, span.sweep, 0.01f)
    }

    @Test
    fun eatenTipReachingABandEnd_stillEndsAtTheTip() {
        // The band's forward cap is shaded by the overlay's own round end,
        // not by growing the span past the eaten amount.
        val span = consumedShadeOverlaySpan(
            zoneStart = 250f,
            eatenEnd = 330f,
            arcEnd = 360f,
        )
        assertEquals(250f, span!!.start, 0.01f)
        assertEquals(80f, span.sweep, 0.01f)
    }

    @Test
    fun eatenPastTheZoneEnd_clampsToIt() {
        val span = consumedShadeOverlaySpan(
            zoneStart = 250f,
            eatenEnd = 400f,
            arcEnd = 360f,
        )
        assertEquals(250f, span!!.start, 0.01f)
        assertEquals(110f, span.sweep, 0.01f)
    }

    @Test
    fun notYetInTheZone_drawsNothing() {
        assertNull(consumedShadeOverlaySpan(zoneStart = 300f, eatenEnd = 300f, arcEnd = 360f))
        assertNull(consumedShadeOverlaySpan(zoneStart = 300f, eatenEnd = 250f, arcEnd = 360f))
    }
}
