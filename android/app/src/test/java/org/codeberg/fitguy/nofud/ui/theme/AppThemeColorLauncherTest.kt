package org.codeberg.fitguy.nofud.ui.theme

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
}
