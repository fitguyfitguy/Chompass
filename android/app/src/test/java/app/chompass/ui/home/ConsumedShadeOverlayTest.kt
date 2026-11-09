package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The consumed overlay is a butt stroke. Round caps extend past each band
 * end, so the overlay has to grow by that angle or the tips stay unshaded.
 */
class ConsumedShadeOverlayTest {
    @Test
    fun eatenThroughArcEnd_coversTheOuterRoundCap() {
        val span = consumedShadeOverlaySpan(
            zoneStart = 300f,
            eatenEnd = 360f,
            roundCapEnds = listOf(330f, 360f),
            capExtension = 4f,
        )
        assertEquals(296f, span!!.start, 0.01f)
        assertEquals(68f, span.sweep, 0.01f)
    }

    @Test
    fun partialEat_doesNotPaintPastTheEatenTip() {
        val span = consumedShadeOverlaySpan(
            zoneStart = 250f,
            eatenEnd = 300f,
            roundCapEnds = listOf(280f, 360f),
            capExtension = 4f,
        )
        assertEquals(246f, span!!.start, 0.01f)
        // 280's cap ends at 284, still inside the eaten tip. 360 is uneaten.
        assertEquals(54f, span.sweep, 0.01f)
    }

    @Test
    fun eatenJustReachingABandEnd_coversThatCap() {
        val span = consumedShadeOverlaySpan(
            zoneStart = 250f,
            eatenEnd = 330f,
            roundCapEnds = listOf(330f),
            capExtension = 4f,
        )
        assertEquals(246f, span!!.start, 0.01f)
        assertEquals(88f, span.sweep, 0.01f)
    }

    @Test
    fun notYetInTheZone_drawsNothing() {
        assertNull(
            consumedShadeOverlaySpan(
                zoneStart = 300f,
                eatenEnd = 300f,
                roundCapEnds = listOf(360f),
                capExtension = 4f,
            ),
        )
    }

    @Test
    fun barMatchesARoundEndOnlyWhenTheTipLandsOnIt() {
        assertEquals(6f, consumedBarEndRadius(100f, listOf(80f, 100f), 6f), 0.01f)
        assertEquals(0f, consumedBarEndRadius(90f, listOf(80f, 100f), 6f), 0.01f)
        assertEquals(0f, consumedBarEndRadius(100f, emptyList(), 6f), 0.01f)
    }
}
