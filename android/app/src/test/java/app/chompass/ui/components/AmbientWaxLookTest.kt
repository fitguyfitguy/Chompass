package app.chompass.ui.components

import androidx.compose.ui.graphics.Color
import app.chompass.ui.theme.AmbientContrast
import app.chompass.ui.theme.AmbientIntensity
import app.chompass.ui.theme.AmbientSaturation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * The shipped wax look is Balanced contrast + Natural saturation, including
 * the OLED dim. Strong must beat that dim, or the control does not fix a
 * field that disappears on true black.
 */
class AmbientWaxLookTest {
    @Test
    fun balanced_matchesShippedAlpha() {
        assertEquals(0.14f, waxFieldAlpha(0.1f, 1f, AmbientContrast.BALANCED, oled = false), 0f)
        assertEquals(0.11f, waxFieldAlpha(0.8f, 1f, AmbientContrast.BALANCED, oled = false), 0f)
        assertEquals(0.14f * 0.55f, waxFieldAlpha(0.1f, 1f, AmbientContrast.BALANCED, oled = true), 0f)
    }

    @Test
    fun strongOnOled_outrunsVividAtTheShippedDim() {
        val oledVivid = waxFieldAlpha(
            0f,
            AmbientIntensity.VIVID.alphaScale,
            AmbientContrast.BALANCED,
            oled = true,
        )
        val oledStrong = waxFieldAlpha(
            0f,
            AmbientIntensity.BALANCED.alphaScale,
            AmbientContrast.STRONG,
            oled = true,
        )
        val darkBalanced = waxFieldAlpha(0f, 1f, AmbientContrast.BALANCED, oled = false)
        assertTrue(oledStrong > oledVivid)
        assertTrue(oledStrong > darkBalanced)
    }

    @Test
    fun soft_isQuieterThanBalanced() {
        val soft = waxFieldAlpha(0f, 1f, AmbientContrast.SOFT, oled = true)
        val balanced = waxFieldAlpha(0f, 1f, AmbientContrast.BALANCED, oled = true)
        assertTrue(soft < balanced)
    }

    @Test
    fun naturalBalanced_leavesColorUntouched() {
        val color = Color(0.2f, 0.55f, 0.9f)
        assertEquals(color, waxDisplayColor(color, AmbientSaturation.NATURAL, AmbientContrast.BALANCED, darkBackground = true))
        assertEquals(color, waxDisplayColor(color, AmbientSaturation.NATURAL, AmbientContrast.BALANCED, darkBackground = false))
        assertEquals(color, waxDisplayColor(color, AmbientSaturation.NATURAL, AmbientContrast.STRONG, darkBackground = false))
    }

    @Test
    fun rich_raisesChromaAndLiftsMuddyDarkColors() {
        val muddy = Color(0.22f, 0.18f, 0.16f)
        val rich = waxDisplayColor(muddy, AmbientSaturation.RICH, AmbientContrast.BALANCED, darkBackground = true)
        assertTrue(chroma(rich) > chroma(muddy))
        assertTrue(maxChannel(rich) >= AmbientSaturation.RICH.darkValueFloor - 1f / 255f)
    }

    @Test
    fun muted_lowersChroma() {
        val vivid = Color(0.9f, 0.1f, 0.15f)
        val muted = waxDisplayColor(vivid, AmbientSaturation.MUTED, AmbientContrast.BALANCED, darkBackground = false)
        assertTrue(chroma(muted) < chroma(vivid))
    }

    @Test
    fun gray_staysGrayWhenLifted() {
        val gray = Color(0.3f, 0.3f, 0.3f)
        val lifted = waxDisplayColor(gray, AmbientSaturation.RICH, AmbientContrast.STRONG, darkBackground = true)
        assertEquals(lifted.red, lifted.green, 1e-4f)
        assertEquals(lifted.green, lifted.blue, 1e-4f)
        assertTrue(lifted.red >= AmbientSaturation.RICH.darkValueFloor - 1f / 255f)
    }

    private fun chroma(color: Color): Float =
        max(color.red, max(color.green, color.blue)) - minOf(color.red, color.green, color.blue)

    private fun maxChannel(color: Color): Float = max(color.red, max(color.green, color.blue))
}
