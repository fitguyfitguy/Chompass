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
}
