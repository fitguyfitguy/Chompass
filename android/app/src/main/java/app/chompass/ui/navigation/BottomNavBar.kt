package app.chompass.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Extra scroll clearance for lists under a docked overlay: FAB 56dp tall plus
 * 8dp breathing room above [BottomOverlayPadding] (#156 — at max scroll the
 * Add button covered the last row's trailing chips).
 */
val BottomFabScrollClearance = 64.dp

/**
 * Codeberg #158: the classic docked Material 3 navigation bar — the standard Android
 * bottom bar the floating pill replaced, driven by the same [FloatingPillItem]s as the
 * pill. [NavigationBar] consumes the navigation-bar inset itself, so its 80 dp height
 * plus the inset equals [BottomNavScrollPadding] and no content padding constant
 * changes with the style. The update badge mirrors the pill's 8 dp dot.
 */
@Composable
fun ChompassClassicBottomBar(items: List<FloatingPillItem>, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected == true,
                onClick = item.onClick,
                icon = {
                    if (item.badge) {
                        val updateLabel = stringResource(R.string.about_update_available)
                        BadgedBox(
                            badge = {
                                Badge(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .semantics { contentDescription = updateLabel },
                                )
                            },
                        ) {
                            Icon(item.icon, contentDescription = null)
                        }
                    } else {
                        Icon(item.icon, contentDescription = null)
                    }
                },
                label = { Text(item.label) },
            )
        }
    }
}
