package app.chompass.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import app.chompass.R

data class BottomTab(val route: String, val icon: ImageVector, @get:StringRes val labelRes: Int)

val BottomTabs = listOf(
    BottomTab(ChompassRoutes.HOME, Icons.Filled.Home, R.string.nav_home),
    BottomTab(ChompassRoutes.PROGRESS, Icons.Filled.BarChart, R.string.nav_progress),
    BottomTab(ChompassRoutes.COACH, Icons.Filled.Forum, R.string.nav_coach),
    BottomTab(ChompassRoutes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
)

/** Content padding above the standard M3 navigation bar. */
val BottomNavScrollPadding = 80.dp

val BottomNavDockedControlPadding = 72.dp

@Composable
fun ChompassBottomNavBar(
    currentRoute: String?,
    showAboutBadge: Boolean = false,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        BottomTabs.forEach { tab ->
            val selected = tab.route == currentRoute
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onTap(tab.route) },
                icon = {
                    if (showAboutBadge && tab.route == ChompassRoutes.SETTINGS) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    modifier = Modifier
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape),
                                )
                            },
                        ) {
                            Icon(tab.icon, contentDescription = label)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = label)
                    }
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
