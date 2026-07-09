package org.codeberg.fitguy.nofud.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.services.update.AndroidUpdateChecker
import org.codeberg.fitguy.nofud.services.update.AndroidUpdateState
import org.codeberg.fitguy.nofud.ui.coach.CoachScreen
import org.codeberg.fitguy.nofud.ui.home.HomeScreen
import org.codeberg.fitguy.nofud.ui.onboarding.OnboardingScreen
import org.codeberg.fitguy.nofud.ui.progress.BodyMeasurementsScreen
import org.codeberg.fitguy.nofud.ui.progress.ProgressScreen
import org.codeberg.fitguy.nofud.ui.settings.CalculationMethodsScreen
import org.codeberg.fitguy.nofud.ui.settings.OptionalNutrientGoalsScreen
import org.codeberg.fitguy.nofud.ui.settings.SettingsScreen

/**
 * Increments each time the app is opened: 1 on cold launch, then +1 on every
 * return from the background (ON_START after a real ON_STOP). Read by the Home
 * gauge + macro bars to replay their fill-from-zero reveal. It lives above the
 * NavHost, so tab switches (which recompose Home) never change it.
 */
val LocalLaunchFillEpoch = compositionLocalOf { 1 }

@Composable
fun NoFUDNavHost(
    container: AppContainer,
    startOnboarding: Boolean
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Hide the bar while a food analysis is in flight so the AnalyzingOverlay
    // is the only thing on screen — matches iOS, where the analyzing sheet
    // covers the tab bar.
    val analyzing by container.analyzingFood.collectAsState()
    val showTabs = currentRoute in NoFUDRoutes.bottomTabs && !analyzing
    val context = LocalContext.current
    val currentVersion = remember(context) { AndroidUpdateChecker.currentVersion(context) }
    var updateAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(currentVersion) {
        val state = AndroidUpdateChecker.check(context, currentVersion)
        updateAvailable = state is AndroidUpdateState.Available
        // A newer version is out — fire a one-shot notification (de-duped per version, gated by the
        // "App Updates" toggle) so the user finds out even without opening the About section.
        if (state is AndroidUpdateState.Available &&
            container.prefs.appUpdateNotificationsEnabled.first() &&
            container.notifications.canPostNotifications() &&
            container.prefs.lastNotifiedUpdateVersion.first() != state.latest
        ) {
            container.notifications.showUpdateAvailable()
            container.prefs.setLastNotifiedUpdateVersion(state.latest)
        }
    }

    // A photo was shared into the app while another tab (or a detail screen) was
    // showing — bring Home forward so it can consume the inbox and start the
    // photo entry flow. No-op during onboarding (HOME isn't on the stack yet);
    // the inbox is sticky, so Home picks it up once it composes.
    val sharedImages by container.sharedImageInbox.collectAsState()
    LaunchedEffect(sharedImages) {
        if (sharedImages.isNotEmpty() && currentRoute != null && currentRoute != NoFUDRoutes.HOME) {
            nav.popBackStack(NoFUDRoutes.HOME, inclusive = false)
        }
    }

    // App-open epoch for the Home fill-from-zero reveal. Bumped only on ON_START
    // that follows an ON_STOP (a genuine background -> foreground return), so
    // transient pauses (notification shade, permission dialog) don't retrigger it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var launchFillEpoch by remember { mutableIntStateOf(1) }
    var hasStopped by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> hasStopped = true
                Lifecycle.Event.ON_START -> if (hasStopped) { launchFillEpoch++; hasStopped = false }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalLaunchFillEpoch provides launchFillEpoch) {
    Scaffold(
        bottomBar = {
            if (showTabs) {
                NoFUDBottomNavBar(
                    currentRoute = currentRoute,
                    showAboutBadge = updateAvailable,
                    onTap = { target ->
                        if (target == currentRoute) return@NoFUDBottomNavBar
                        // Tapping HOME (the start destination) needs popBackStack
                        // — `navigate(HOME) { popUpTo(HOME); launchSingleTop = true }`
                        // is a no-op because NavController sees HOME at the top of
                        // the stack and skips re-emitting currentBackStackEntry, so
                        // the bar stays selected on the previous tab.
                        if (target == NoFUDRoutes.HOME) {
                            nav.popBackStack(NoFUDRoutes.HOME, inclusive = false)
                        } else {
                            nav.navigate(target) {
                                popUpTo(NoFUDRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) NoFUDRoutes.ONBOARDING else NoFUDRoutes.HOME
            ) {
                composable(NoFUDRoutes.ONBOARDING) {
                    OnboardingScreen(container = container, onComplete = {
                        nav.navigate(NoFUDRoutes.HOME) {
                            popUpTo(NoFUDRoutes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(NoFUDRoutes.HOME) { HomeScreen(container = container) }
                composable(NoFUDRoutes.PROGRESS) { ProgressScreen(container = container) }
                composable(NoFUDRoutes.COACH) { CoachScreen(container = container) }
                composable(NoFUDRoutes.SETTINGS) { SettingsScreen(container = container, nav = nav) }
                composable(NoFUDRoutes.OPTIONAL_NUTRIENT_GOALS) {
                    OptionalNutrientGoalsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(NoFUDRoutes.CALCULATION_METHODS) {
                    CalculationMethodsScreen(onBack = { nav.popBackStack() })
                }
                composable(NoFUDRoutes.BODY_MEASUREMENTS) {
                    BodyMeasurementsScreen(container = container, onBack = { nav.popBackStack() })
                }
            }
        }
    }
    }
}

internal fun NavHostController.current(): String? = currentBackStackEntry?.destination?.route
