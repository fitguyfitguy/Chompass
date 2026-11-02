package app.chompass.ui.components

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the hard-coded mark geometry transcribed from `docs/brand/logo-system.html`.
 *
 * A malformed path constant is the only silent failure mode here: the Canvas would draw
 * nothing (or draw off-canvas) without any error, and the welcome intro would look like a
 * blank screen. Robolectric supplies the Android `Path` implementation the parser needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChompassMarkGeometryTest {
    private fun bounds(d: String): Rect = PathParser().parsePathString(d).toPath().getBounds()

    private fun Rect.isInsideBox() =
        left >= 0f && top >= 0f && right <= ChompassMarkGeometry.Box && bottom <= ChompassMarkGeometry.Box

    @Test
    fun ring_isNonEmptyAndFitsTheMarkBox() {
        val ring = bounds(ChompassMarkGeometry.Ring)

        assertTrue("ring is empty", ring.width > 0f && ring.height > 0f)
        assertTrue("ring too small: $ring", ring.width > 100f && ring.height > 100f)
        assertTrue("ring escapes the 200 box: $ring", ring.isInsideBox())
    }

    @Test
    fun needles_areNonEmptyAndInsideTheRing() {
        val ring = bounds(ChompassMarkGeometry.Ring)

        listOf(
            "needleA" to ChompassMarkGeometry.NeedleA,
            "needleB" to ChompassMarkGeometry.NeedleB,
        ).forEach { (name, path) ->
            val needle = bounds(path)
            assertTrue("$name is empty", needle.width > 0f && needle.height > 0f)
            assertTrue("$name escapes the 200 box: $needle", needle.isInsideBox())
            assertTrue(
                "$name reaches outside the ring: $needle vs $ring",
                needle.left >= ring.left && needle.right <= ring.right &&
                    needle.top >= ring.top && needle.bottom <= ring.bottom,
            )
        }
    }

    @Test
    fun bittenO_isNonEmptyAndFitsItsWordmarkBox() {
        // The wordmark glyph is drawn in a 60-unit box; a path that overflows it would
        // collide with the neighbouring letters.
        val bite = bounds(ChompassMarkGeometry.BittenO)

        assertTrue("bitten o is empty", bite.width > 0f && bite.height > 0f)
        assertTrue(
            "bitten o escapes the 60 box: $bite",
            bite.left >= 0f && bite.top >= 0f &&
                bite.right <= ChompassMarkGeometry.BittenOBox &&
                bite.bottom <= ChompassMarkGeometry.BittenOBox,
        )
    }

    @Test
    fun pivots_sitInsideTheRing() {
        val ring = bounds(ChompassMarkGeometry.Ring)
        listOf(
            "needle pivot" to ChompassMarkGeometry.NeedlePivot,
            "dial pivot" to ChompassMarkGeometry.DialPivot,
        ).forEach { (name, pivot) ->
            assertTrue(
                "$name outside the ring: $pivot vs $ring",
                pivot.x > ring.left && pivot.x < ring.right &&
                    pivot.y > ring.top && pivot.y < ring.bottom,
            )
        }
    }
}
