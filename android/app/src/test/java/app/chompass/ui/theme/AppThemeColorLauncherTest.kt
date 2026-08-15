package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeColorLauncherTest {
    @Test
    fun nearestLauncherIconTheme_mapsBlueAccentToBlueIcon() {
        assertEquals(
            AppThemeColor.BLUE,
            nearestLauncherIconTheme(Color(0xFF0A84FF)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsPinkAccentToBabyPinkIcon() {
        assertEquals(
            AppThemeColor.PINK,
            nearestLauncherIconTheme(Color(0xFFFF8FAB)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsNeutralGrayToGraphiteIcon() {
        assertEquals(
            AppThemeColor.NEUTRAL,
            nearestLauncherIconTheme(Color(0xFF8E8E93)),
        )
    }

    @Test
    fun launcherIconThemeFor_nearGrayWallpaperAccentFallsBackToTeal() {
        // Light wallpapers yield low-chroma Material You primaries; the launcher
        // icon must stay the brand teal, never the gray Graphite one (#13).
        assertEquals(
            AppThemeColor.TEAL,
            launcherIconThemeFor(Color(0xFF8E8E93)),
        )
    }

    @Test
    fun launcherIconThemeFor_saturatedAccentStillMapsByHue() {
        assertEquals(
            AppThemeColor.BLUE,
            launcherIconThemeFor(Color(0xFF0A84FF)),
        )
    }

    @Test
    fun resolveLauncherIconTheme_fixedIconAlwaysTeal() {
        // Opt-in workaround (#21): no theme color or wallpaper may move the alias.
        assertEquals(
            AppThemeColor.TEAL,
            AppThemeColor.BLUE.resolveLauncherIconTheme(Color(0xFF0A84FF), fixedIcon = true),
        )
        assertEquals(
            AppThemeColor.TEAL,
            AppThemeColor.SYSTEM.resolveLauncherIconTheme(Color(0xFF0A84FF), fixedIcon = true),
        )
    }

    @Test
    fun resolveLauncherIconTheme_fixedColorMapsOneToOneIgnoringAccent() {
        assertEquals(
            AppThemeColor.BLUE,
            AppThemeColor.BLUE.resolveLauncherIconTheme(Color(0xFF8E8E93)),
        )
    }

    @Test
    fun resolveLauncherIconTheme_systemGrayAccentFallsBackToTeal() {
        assertEquals(
            AppThemeColor.TEAL,
            AppThemeColor.SYSTEM.resolveLauncherIconTheme(Color(0xFF8E8E93)),
        )
    }

    @Test
    fun resolveLauncherIconTheme_systemSaturatedAccentMapsByHue() {
        assertEquals(
            AppThemeColor.BLUE,
            AppThemeColor.SYSTEM.resolveLauncherIconTheme(Color(0xFF0A84FF)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsTealMaterialYouPrimaryToTeal() {
        // Typical Material You teal/cyan primary — must not collapse onto pink.
        assertEquals(
            AppThemeColor.TEAL,
            nearestLauncherIconTheme(Color(0xFF006A60)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsGreenMaterialYouPrimaryToGreen() {
        assertEquals(
            AppThemeColor.GREEN,
            nearestLauncherIconTheme(Color(0xFF386A20)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsPurpleMaterialYouPrimaryToPurple() {
        assertEquals(
            AppThemeColor.PURPLE,
            nearestLauncherIconTheme(Color(0xFFAF52DE)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsIndigoMaterialYouPrimaryToIndigo() {
        assertEquals(
            AppThemeColor.INDIGO,
            nearestLauncherIconTheme(Color(0xFF5856D6)),
        )
    }

    @Test
    fun nearestLauncherIconTheme_mapsOrangeMaterialYouPrimaryToOrange() {
        assertEquals(
            AppThemeColor.ORANGE,
            nearestLauncherIconTheme(Color(0xFF8B5000)),
        )
    }
}
