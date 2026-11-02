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
    fun launcherIconThemeFor_nearGrayWallpaperAccentFallsBackToBrandDefault() {
        // Light wallpapers yield low-chroma Material You primaries; the launcher
        // icon must stay the brand icon, never the gray Graphite one (#13).
        assertEquals(
            AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY),
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
    fun resolveLauncherIconTheme_fixedIconAlwaysBrandDefault() {
        // Opt-in workaround (#21): no theme color or wallpaper may move the alias.
        val brand = AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY)
        assertEquals(
            brand,
            AppThemeColor.BLUE.resolveLauncherIconTheme(Color(0xFF0A84FF), fixedIcon = true),
        )
        assertEquals(
            brand,
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
    fun resolveLauncherIconTheme_systemGrayAccentFallsBackToBrandDefault() {
        assertEquals(
            AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY),
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

    @Test
    fun defaultKey_isPlum() {
        assertEquals("plum", AppThemeColor.DEFAULT_KEY)
        assertEquals(AppThemeColor.PLUM, AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY))
    }

    @Test
    fun fromKey_nullAndBlank_isPlum() {
        // Absent prefs (fresh install, or an update from a build without the key)
        // land on the brand default rather than Material You.
        assertEquals(AppThemeColor.PLUM, AppThemeColor.fromKey(null))
        assertEquals(AppThemeColor.PLUM, AppThemeColor.fromKey(""))
        assertEquals(AppThemeColor.PLUM, AppThemeColor.fromKey("   "))
    }

    @Test
    fun fromKey_unknown_isSystem() {
        assertEquals(AppThemeColor.SYSTEM, AppThemeColor.fromKey("chartreuse"))
    }

    @Test
    fun fromKey_legacyCoral_isPink() {
        assertEquals(AppThemeColor.PINK, AppThemeColor.fromKey("coral"))
    }

    @Test
    fun plumAndPeach_liveInMacroPalettes() {
        // Every non-System accent must have a macro palette: SYSTEM reads the default's.
        ThemeMacroPalettes.getValue(AppThemeColor.PLUM)
        ThemeMacroPalettes.getValue(AppThemeColor.PEACH)
        assertEquals(
            ThemeMacroPalettes.getValue(AppThemeColor.PLUM),
            AppThemeColor.SYSTEM.macroPalette,
        )
    }

    @Test
    fun brandAccents_carryFixedSwatchEnds() {
        // Both brand accents own both swatch stops, so the picker never derives one
        // and SYSTEM inherits the default's pair.
        assertEquals(BrandPalette.Fig800, AppThemeColor.PLUM.end)
        assertEquals(Color(0xFF8C2E4A), AppThemeColor.PEACH.end)
        assertEquals(AppThemeColor.PLUM.end, AppThemeColor.SYSTEM.end)
    }
}
