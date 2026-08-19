package app.chompass.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure resolution of the appearance-mode string to a dark flag (OLED mode plan). */
class AppearanceResolutionTest {
    @Test
    fun explicitLight_isNeverDark() {
        assertFalse(appearanceIsDark("light", systemDark = true))
        assertFalse(appearanceIsDark("light", systemDark = false))
    }

    @Test
    fun explicitDark_isAlwaysDark() {
        assertTrue(appearanceIsDark("dark", systemDark = true))
        assertTrue(appearanceIsDark("dark", systemDark = false))
    }

    @Test
    fun oled_isAlwaysDark() {
        assertTrue(appearanceIsDark("oled", systemDark = true))
        assertTrue(appearanceIsDark("oled", systemDark = false))
    }

    @Test
    fun system_followsDevice() {
        assertTrue(appearanceIsDark("system", systemDark = true))
        assertFalse(appearanceIsDark("system", systemDark = false))
    }

    @Test
    fun null_followsDevice() {
        // Legacy snapshots carry no appearanceMode → widgets follow the system.
        assertTrue(appearanceIsDark(null, systemDark = true))
        assertFalse(appearanceIsDark(null, systemDark = false))
    }

    @Test
    fun unknownValue_followsDevice() {
        // Unknown stored values fall through to system (no migration needed).
        assertTrue(appearanceIsDark("sepia", systemDark = true))
        assertFalse(appearanceIsDark("sepia", systemDark = false))
    }
}
