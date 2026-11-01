package app.chompass.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage round-trip for the ambient customization enums. The stored value is
 * what [app.chompass.data.SettingsPrefsHydration] carries into
 * [ChompassTraits], and the phase accumulator multiplies real time by
 * [AmbientSpeed.timeScale], so a non-monotonic scale would make "Lively"
 * drift slower than "Calm".
 */
class AmbientTuningTest {
    @Test
    fun speed_roundTripsEveryEntry() {
        AmbientSpeed.entries.forEach { speed ->
            assertEquals(speed, AmbientSpeed.fromStorage(speed.storage))
        }
    }

    @Test
    fun speed_unknownOrAbsent_fallsBackToDefault() {
        assertEquals(AmbientSpeed.DEFAULT, AmbientSpeed.fromStorage(null))
        assertEquals(AmbientSpeed.DEFAULT, AmbientSpeed.fromStorage(""))
        assertEquals(AmbientSpeed.DEFAULT, AmbientSpeed.fromStorage("bogus"))
    }

    @Test
    fun speed_timeScaleIsMonotonic() {
        assertTrue(AmbientSpeed.CALM.timeScale < AmbientSpeed.STANDARD.timeScale)
        assertTrue(AmbientSpeed.STANDARD.timeScale < AmbientSpeed.LIVELY.timeScale)
    }

    @Test
    fun palette_roundTripsEveryEntry() {
        AmbientPalette.entries.forEach { palette ->
            assertEquals(palette, AmbientPalette.fromStorage(palette.storage))
        }
    }

    @Test
    fun palette_unknownOrAbsent_fallsBackToDefault() {
        assertEquals(AmbientPalette.DEFAULT, AmbientPalette.fromStorage(null))
        assertEquals(AmbientPalette.DEFAULT, AmbientPalette.fromStorage(""))
        assertEquals(AmbientPalette.DEFAULT, AmbientPalette.fromStorage("bogus"))
    }

    @Test
    fun storageKeys_areUniqueAcrossEnums() {
        assertEquals(AmbientSpeed.entries.size, AmbientSpeed.entries.map { it.storage }.distinct().size)
        assertEquals(AmbientPalette.entries.size, AmbientPalette.entries.map { it.storage }.distinct().size)
    }

    @Test
    fun contrast_roundTripsEveryEntry() {
        AmbientContrast.entries.forEach { contrast ->
            assertEquals(contrast, AmbientContrast.fromStorage(contrast.storage))
        }
    }

    @Test
    fun contrast_unknownOrAbsent_fallsBackToDefault() {
        assertEquals(AmbientContrast.DEFAULT, AmbientContrast.fromStorage(null))
        assertEquals(AmbientContrast.DEFAULT, AmbientContrast.fromStorage(""))
        assertEquals(AmbientContrast.DEFAULT, AmbientContrast.fromStorage("bogus"))
    }

    @Test
    fun contrast_balancedMatchesShippedShader() {
        val balanced = AmbientContrast.BALANCED
        assertEquals(1f, balanced.alphaScale, 0f)
        assertEquals(0.55f, balanced.oledAlphaScale, 0f)
        assertEquals(0.85f, balanced.bodyLo, 0f)
        assertEquals(1.15f, balanced.bodyHi, 0f)
        assertEquals(0.3f, balanced.haloLo, 0f)
        assertEquals(0.9f, balanced.haloHi, 0f)
        assertEquals(0.3f, balanced.haloGain, 0f)
        assertEquals(0.75f, balanced.midFloor, 0f)
        assertEquals(1f, balanced.softRadiusScale, 0f)
        assertEquals(0f, balanced.darkValueFloor, 0f)
    }

    @Test
    fun contrast_isMonotonicAndEdgesStayOrdered() {
        assertTrue(AmbientContrast.SOFT.alphaScale < AmbientContrast.BALANCED.alphaScale)
        assertTrue(AmbientContrast.BALANCED.alphaScale < AmbientContrast.STRONG.alphaScale)
        assertTrue(AmbientContrast.SOFT.oledAlphaScale < AmbientContrast.BALANCED.oledAlphaScale)
        assertTrue(AmbientContrast.BALANCED.oledAlphaScale < AmbientContrast.STRONG.oledAlphaScale)
        AmbientContrast.entries.forEach { contrast ->
            assertTrue(contrast.bodyLo < contrast.bodyHi)
            assertTrue(contrast.haloLo < contrast.haloHi)
            assertTrue(contrast.midFloor in 0f..1f)
        }
    }

    @Test
    fun saturation_roundTripsAndStaysOrdered() {
        AmbientSaturation.entries.forEach { saturation ->
            assertEquals(saturation, AmbientSaturation.fromStorage(saturation.storage))
        }
        assertEquals(AmbientSaturation.DEFAULT, AmbientSaturation.fromStorage(null))
        assertEquals(AmbientSaturation.DEFAULT, AmbientSaturation.fromStorage("bogus"))
        assertEquals(1f, AmbientSaturation.NATURAL.chromaScale, 0f)
        assertEquals(0f, AmbientSaturation.NATURAL.darkValueFloor, 0f)
        assertTrue(AmbientSaturation.MUTED.chromaScale < AmbientSaturation.NATURAL.chromaScale)
        assertTrue(AmbientSaturation.NATURAL.chromaScale < AmbientSaturation.RICH.chromaScale)
    }
}
