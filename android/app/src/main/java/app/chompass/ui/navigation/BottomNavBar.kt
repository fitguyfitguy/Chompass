package app.chompass.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chompass.R

data class BottomTab(val route: String, val icon: ImageVector, @get:StringRes val labelRes: Int)

val BottomTabs = listOf(
    BottomTab(ChompassRoutes.HOME, Icons.Filled.Home, R.string.nav_home),
    BottomTab(ChompassRoutes.PROGRESS, Icons.Filled.BarChart, R.string.nav_progress),
    BottomTab(ChompassRoutes.COACH, Icons.Filled.Forum, R.string.nav_coach),
    BottomTab(ChompassRoutes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
)

/** Codeberg #20 phase 1: drops the coach tab when the user hides it. */
fun bottomTabs(showCoachTab: Boolean = true): List<BottomTab> =
    if (showCoachTab) BottomTabs else BottomTabs.filter { it.route != ChompassRoutes.COACH }

/**
 * Content padding above the floating bottom pill: nav inset + 16 + 64 dp. The
 * pill takes the navigation-bar inset itself, so this must repeat it — a plain
 * constant left the last scroll item sitting `inset` low, its bottom corners
 * hidden behind the pill and its straight edges cut at the screen bottom.
 */
val BottomNavScrollPadding: Dp
    @Composable get() = 80.dp + with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

val BottomNavDockedControlPadding = 72.dp

/** Docked overlays (FAB, chips, snackbar) sit this far above the bottom pill. */
val BottomOverlayPadding = BottomNavDockedControlPadding + 16.dp
