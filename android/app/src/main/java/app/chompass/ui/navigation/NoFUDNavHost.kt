package app.chompass.ui.navigation

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
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.chompass.AppContainer
import app.chompass.services.update.AndroidUpdateChecker
import app.chompass.services.update.AndroidUpdateState
import app.chompass.ui.coach.CoachScreen
import app.chompass.ui.home.HomeScreen
import app.chompass.ui.onboarding.OnboardingScreen
import app.chompass.ui.progress.BodyMeasurementsScreen
import app.chompass.ui.progress.ProgressScreen
import app.chompass.ui.settings.AiSettingsScreen
import app.chompass.ui.settings.AppSettingsScreen
import app.chompass.ui.settings.CalculationMethodsScreen
import app.chompass.ui.settings.CustomizeProgressScreen
import app.chompass.ui.settings.DataSettingsScreen
import app.chompass.ui.settings.FoodEntrySettingsScreen
import app.chompass.ui.settings.GoalsSettingsScreen
import app.chompass.ui.settings.HomeDisplaySettingsScreen
import app.chompass.ui.settings.NotificationsSettingsScreen
import app.chompass.ui.settings.OptionalNutrientGoalsScreen
import app.chompass.ui.settings.PersonalSettingsScreen
import app.chompass.ui.settings.SettingsScreen
import app.chompass.ui.settings.SyncSettingsScreen
import app.chompass.ui.settings.WaterSettingsScreen

/**
 * Increments each time the app is opened: 1 on cold launch, then +1 on every
 * return from the background (ON_START after a real ON_STOP). Read by the Home
 * gauge + macro bars to replay their fill-from-zero reveal. It lives above the
 * NavHost, so tab switches (which recompose Home) never change it.
 */
val LocalLaunchFillEpoch = compositionLocalOf { 1 }

@Composable
fun ChompassNavHost(
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
    val showTabs = currentRoute in ChompassRoutes.bottomTabs && !analyzing
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
    // showing — bring Home forward so it can consume the share inbox and open
    // FoodPhotoSession review. No-op during onboarding (HOME isn't on the stack yet);
    // the inbox is sticky, so Home picks it up once it composes. RESUMED-only
    // so a stopped duplicate MainActivity cannot mutate this nav controller.
    // In-app gallery does not use sharedImageInbox (Activity FoodPhotoSession).
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedImages by container.sharedImageInbox.collectAsState()
    LaunchedEffect(sharedImages, currentRoute, lifecycleOwner) {
        if (sharedImages.isEmpty()) return@LaunchedEffect
        if (currentRoute == null || currentRoute == ChompassRoutes.HOME) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.sharedImageInbox.value.isEmpty()) return@repeatOnLifecycle
            val route = nav.currentBackStackEntry?.destination?.route
            if (route != null && route != ChompassRoutes.HOME) {
                nav.popBackStack(ChompassRoutes.HOME, inclusive = false)
            }
        }
    }
    val shortcutEntry by container.shortcutEntryInbox.collectAsState()
    LaunchedEffect(shortcutEntry, currentRoute, lifecycleOwner) {
        if (shortcutEntry == null) return@LaunchedEffect
        if (currentRoute == null || currentRoute == ChompassRoutes.HOME) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.shortcutEntryInbox.value == null) return@repeatOnLifecycle
            val route = nav.currentBackStackEntry?.destination?.route
            if (route != null && route != ChompassRoutes.HOME) {
                nav.popBackStack(ChompassRoutes.HOME, inclusive = false)
            }
        }
    }
    // Notification tap destination (Codeberg #27): navigate once, clear the inbox.
    val launchDestination by container.launchDestinationInbox.collectAsState()
    LaunchedEffect(launchDestination, lifecycleOwner) {
        val dest = launchDestination ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.launchDestinationInbox.value != dest) return@repeatOnLifecycle
            val route = nav.currentBackStackEntry?.destination?.route
            // Onboarding has no HOME on the stack yet; ignore the tap there.
            if (route == null || route == ChompassRoutes.ONBOARDING) return@repeatOnLifecycle
            if (route != ChompassRoutes.HOME && route != dest) {
                nav.popBackStack(ChompassRoutes.HOME, inclusive = false)
            }
            if (container.launchDestinationInbox.value != dest) return@repeatOnLifecycle
            if (route != dest) {
                nav.navigate(dest) {
                    popUpTo(ChompassRoutes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            container.launchDestinationInbox.value = null
        }
    }

    // App-open epoch for the Home fill-from-zero reveal. Bumped only on ON_START
    // that follows an ON_STOP (a genuine background -> foreground return), so
    // transient pauses (notification shade, permission dialog) don't retrigger it.
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
    // Codeberg #20 phase 1: hide the coach tab (View-only) without touching any
    // data path; the master AI-off switch will gate callAi later.
    val coachTabEnabled by container.prefs.coachTabEnabled.collectAsState(initial = true)
    Scaffold(
        bottomBar = {
            if (showTabs) {
                ChompassBottomNavBar(
                    currentRoute = currentRoute,
                    showAboutBadge = updateAvailable,
                    showCoachTab = coachTabEnabled,
                    onTap = { target ->
                        if (target == currentRoute) return@ChompassBottomNavBar
                        // Tapping HOME (the start destination) needs popBackStack
                        // — `navigate(HOME) { popUpTo(HOME); launchSingleTop = true }`
                        // is a no-op because NavController sees HOME at the top of
                        // the stack and skips re-emitting currentBackStackEntry, so
                        // the bar stays selected on the previous tab.
                        if (target == ChompassRoutes.HOME) {
                            nav.popBackStack(ChompassRoutes.HOME, inclusive = false)
                        } else {
                            nav.navigate(target) {
                                popUpTo(ChompassRoutes.HOME) { saveState = true }
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
                startDestination = if (startOnboarding) ChompassRoutes.ONBOARDING else ChompassRoutes.HOME
            ) {
                composable(ChompassRoutes.ONBOARDING) {
                    OnboardingScreen(container = container, onComplete = {
                        nav.navigate(ChompassRoutes.HOME) {
                            popUpTo(ChompassRoutes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(ChompassRoutes.HOME) {
                    HomeScreen(
                        container = container,
                        onOpenSettings = {
                            nav.navigate(ChompassRoutes.SETTINGS) {
                                popUpTo(ChompassRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(ChompassRoutes.PROGRESS) { ProgressScreen(container = container) }
                composable(ChompassRoutes.COACH) {
                    if (coachTabEnabled) {
                        CoachScreen(container = container)
                    } else {
                        // Deep link or stale back stack with the tab off: land Home.
                        LaunchedEffect(Unit) {
                            nav.navigate(ChompassRoutes.HOME) {
                                popUpTo(ChompassRoutes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                composable(ChompassRoutes.SETTINGS) { SettingsScreen(container = container, nav = nav) }
                composable(ChompassRoutes.SETTINGS_PERSONAL) {
                    PersonalSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ChompassRoutes.SETTINGS_GOALS) {
                    GoalsSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ChompassRoutes.SETTINGS_APP) {
                    AppSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ChompassRoutes.SETTINGS_AI) {
                    AiSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ChompassRoutes.SETTINGS_DATA) {
                    DataSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ChompassRoutes.SETTINGS_FOOD) {
                    FoodEntrySettingsScreen(
                        container = container,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    route = ChompassRoutes.SETTINGS_WATER,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    WaterSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ChompassRoutes.SETTINGS_NOTIFICATIONS,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    NotificationsSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ChompassRoutes.SETTINGS_SYNC,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "data"
                    }),
                ) { entry ->
                    SyncSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "data",
                    )
                }
                composable(ChompassRoutes.OPTIONAL_NUTRIENT_GOALS) {
                    OptionalNutrientGoalsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(ChompassRoutes.HOME_DISPLAY) {
                    HomeDisplaySettingsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(ChompassRoutes.CUSTOMIZE_PROGRESS) {
                    CustomizeProgressScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(ChompassRoutes.CALCULATION_METHODS) {
                    CalculationMethodsScreen(onBack = { nav.popBackStack() })
                }
                composable(ChompassRoutes.BODY_MEASUREMENTS) {
                    BodyMeasurementsScreen(container = container, onBack = { nav.popBackStack() })
                }
            }
        }
    }
    }
}

internal fun NavHostController.current(): String? = currentBackStackEntry?.destination?.route
