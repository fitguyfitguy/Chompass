package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the foreground choice for text/icons drawn on the calorie gradient
 * (Codeberg #44). White labels vanish when a light wallpaper yields a
 * near-white Material You primary — the foreground must flip to dark instead.
 */
class AppColorsContrastTest {
    @After
    fun tearDown() {
        AppColors.setThemeColor(AppThemeColor.SYSTEM)
    }

    /** WCAG relative-luminance contrast ratio between two colors. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Dark-mode fixed themes lighten the primary by 25% toward white
     * (Theme.darkColors); both light and dark fixed primaries must keep the
     * pure-white foreground so existing screenshots do not change.
     */
    @Test
    fun onCalorieGradient_staysWhiteForEveryFixedTheme() {
        for (theme in AppThemeColor.entries.filter { !it.usesSystemPalette }) {
            AppColors.setThemeColor(theme, theme.primary)
            assertEquals("light $theme", Color.White, AppColors.onCalorieGradient)
            AppColors.setThemeColor(theme, lerp(theme.primary, Color.White, 0.25f))
            assertEquals("dark $theme", Color.White, AppColors.onCalorieGradient)
        }
    }

    /** A near-white Material You primary (light wallpaper, dark mode tone 80). */
    @Test
    fun onCalorieGradient_turnsDarkOnNearWhitePrimary() {
        AppColors.setThemeColor(AppThemeColor.SYSTEM, Color(0xFFE6E6E6))
        assertEquals(AppColors.OnLight, AppColors.onCalorieGradient)
    }

    /** A mid-light primary is already too light for white text. */
    @Test
    fun onCalorieGradient_turnsDarkOnMidLightPrimary() {
        AppColors.setThemeColor(AppThemeColor.SYSTEM, Color(0xFFB0B0C0))
        assertEquals(AppColors.OnLight, AppColors.onCalorieGradient)
    }

    /** The chosen foreground must keep usable contrast against both gradient points. */
    @Test
    fun onCalorieGradient_foregroundContrastsAgainstBothPoints() {
        // Dark accent (fixed teal, light mode): white text stays against the
        // gradient start (6.4:1) and the 28%-whitened end (3.6:1), the existing look.
        AppColors.setThemeColor(AppThemeColor.TEAL, AppThemeColor.TEAL.primary)
        assertTrue(contrastRatio(AppColors.onCalorieGradient, AppColors.CalorieStart) >= 4.5)
        assertTrue(contrastRatio(AppColors.onCalorieGradient, AppColors.CalorieEnd) >= 3.0)

        // Light accent (light wallpaper): dark text now meets WCAG AA on both points.
        AppColors.setThemeColor(AppThemeColor.SYSTEM, Color(0xFFE6E6E6))
        assertTrue(contrastRatio(AppColors.onCalorieGradient, AppColors.CalorieStart) >= 4.5)
        assertTrue(contrastRatio(AppColors.onCalorieGradient, AppColors.CalorieEnd) >= 4.5)
    }
}
