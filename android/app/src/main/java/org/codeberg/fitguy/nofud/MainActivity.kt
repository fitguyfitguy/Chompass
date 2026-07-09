package org.codeberg.fitguy.nofud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.services.AndroidAppIconManager
import org.codeberg.fitguy.nofud.services.MealShare
import org.codeberg.fitguy.nofud.services.InAppReview
import org.codeberg.fitguy.nofud.ui.home.ImportSharedMealSheet
import org.codeberg.fitguy.nofud.ui.navigation.NoFUDNavHost
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import org.codeberg.fitguy.nofud.ui.theme.NoFUDTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    // Shared-meal deep link (issue #107). Non-empty -> the confirm sheet is shown over the app.
    private var pendingSharedMeals by mutableStateOf<List<FoodEntry>>(emptyList())
    private var foregroundSyncJob: Job? = null
    private var lastForegroundSyncAtMs: Long = 0L

    /**
     * Route whatever launched (or re-launched) us: a `fudai://add-meal` link into
     * pending meals, or a system share-sheet image into the photo entry flow.
     */
    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> handleSharedImages(intent)
            else -> {
                val uri = intent.data ?: return
                if (!MealShare.handles(uri)) return
                MealShare.meals(uri)?.let { pendingSharedMeals = it }
            }
        }
    }

    /**
     * Photos shared from another app (camera, gallery). At most two are used —
     * the analysis pipeline composes a pair side-by-side, same as dual capture.
     * Bytes are read off the main thread, then handed to Home via the
     * container's [sharedImageInbox][AppContainer.sharedImageInbox].
     */
    private fun handleSharedImages(intent: Intent) {
        if (intent.type?.startsWith("image/") != true) return
        val uris = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            else ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        }.take(2)
        if (uris.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val images = uris.mapNotNull { uri ->
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            }
            if (images.isNotEmpty()) {
                (application as NoFUDApp).container.sharedImageInbox.value = images
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }
    override fun onStart() {
        super.onStart()
        val now = SystemClock.elapsedRealtime()
        if (foregroundSyncJob?.isActive == true) return
        if (now - lastForegroundSyncAtMs < FOREGROUND_SYNC_MIN_INTERVAL_MS) return
        lastForegroundSyncAtMs = now
        foregroundSyncJob = lifecycleScope.launch {
            // Adaptive Goals auto-runs the full goal calculation about once a week (Energy Burn,
            // when on, supplies the measured-burn anchor it consumes — separate toggle).
            val container = (application as NoFUDApp).container
            container.refreshAdaptiveGoalsIfNeeded()
            // Pull any new external weight / body-fat readings (e.g. a Withings scale)
            // from Health Connect into the app on every foreground (issue #91).
            container.syncHealthConnectReads()
            foregroundSyncJob = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system swaps the splash theme
        // back to Theme.NoFUD before the first frame, preventing a white flash
        // on cold start. The splash uses a transparent foreground mark over
        // the app's light/dark splash background.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        InAppReview.bind(this)

        val container = (application as NoFUDApp).container
        val app = application as NoFUDApp
        // Dev-only seeders for verifying the Progress tab UI without polluting Health Connect.
        // adb shell am start -n org.codeberg.fitguy.nofud/.MainActivity --ez seed_test_data true
        // adb shell am start -n org.codeberg.fitguy.nofud/.MainActivity --ez restore_real_data true
        // Extras are removed after handling so Activity.recreate() (used by Delete All
        // Data) doesn't re-fire the same flag on the next onCreate.
        val shouldResetOnboarding = intent?.getBooleanExtra("reset_onboarding", false) == true
        val shouldSeedTestData = intent?.getBooleanExtra("seed_test_data", false) == true
        // Focused 30-day weight + body-fat seeder for verifying the v3.2 Body
        // Fat chart + segmented Progress toggle without polluting food data.
        // adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez seed_body_metrics true
        val shouldSeedBodyMetrics = intent?.getBooleanExtra("seed_body_metrics", false) == true
        // Long-range variant: 2 years of weight + body-fat for the 1Y / All
        // ranges and the history lists.
        // adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez seed_body_metrics_2y true
        val shouldSeedBodyMetricsTwoYears = intent?.getBooleanExtra("seed_body_metrics_2y", false) == true
        // Focused keto-settings seeder for Diet Mode and carb target debugging.
        // adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez seed_keto_settings true
        val shouldSeedKetoSettings = intent?.getBooleanExtra("seed_keto_settings", false) == true
        val shouldRestoreRealData = intent?.getBooleanExtra("restore_real_data", false) == true
        if (shouldResetOnboarding) intent?.removeExtra("reset_onboarding")
        if (shouldSeedTestData) intent?.removeExtra("seed_test_data")
        if (shouldSeedBodyMetrics) intent?.removeExtra("seed_body_metrics")
        if (shouldSeedBodyMetricsTwoYears) intent?.removeExtra("seed_body_metrics_2y")
        if (shouldSeedKetoSettings) intent?.removeExtra("seed_keto_settings")
        if (shouldRestoreRealData) intent?.removeExtra("restore_real_data")
        // A fudai://add-meal link may have cold-launched us.
        handleShareIntent(intent)
        var startOnboarding by mutableStateOf<Boolean?>(null)
        var initialAppearance by mutableStateOf("system")
        var initialThemeColorKey by mutableStateOf(AppThemeColor.DEFAULT_KEY)
        var initialGlassBlurEnabled by mutableStateOf(false)

        // Hold the splash on screen until the saved profile has loaded from
        // DataStore so Home doesn't briefly render its 2000/150/220/70 fallback
        // goal numbers before snapping to the user's real targets. Onboarding
        // doesn't show those numbers, so we let the splash dismiss immediately
        // in that case.
        var contentReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !contentReady }
        lifecycleScope.launch {
            if (shouldResetOnboarding) {
                app.container.prefs.setOnboardingCompleted(false)
            }
            if (shouldSeedTestData) container.testDataSeeder.seedYear()
            if (shouldSeedBodyMetrics) container.testDataSeeder.seedBodyMetrics()
            if (shouldSeedBodyMetricsTwoYears) container.testDataSeeder.seedTwoYearsBodyMetrics()
            if (shouldSeedKetoSettings) container.testDataSeeder.seedKetoSettings()
            if (shouldRestoreRealData) container.testDataSeeder.restore()

            val resolvedStartOnboarding = !container.prefs.hasCompletedOnboarding.first()
            initialAppearance = container.prefs.appearanceMode.first()
            initialThemeColorKey = container.prefs.appThemeColor.first()
            initialGlassBlurEnabled = container.prefs.glassBlurEnabled.first()
            AndroidAppIconManager.apply(this@MainActivity, AppThemeColor.fromKey(initialThemeColorKey))
            startOnboarding = resolvedStartOnboarding
            if (!resolvedStartOnboarding) {
                container.profileRepository.profile.first { it != null }
            }
            contentReady = true
        }

        setContent {
            val resolvedStartOnboarding = startOnboarding ?: return@setContent
            val appearance by container.prefs.appearanceMode.collectAsState(initial = initialAppearance)
            val themeColorKey by container.prefs.appThemeColor.collectAsState(initial = initialThemeColorKey)
            val glassBlurEnabled by container.prefs.glassBlurEnabled.collectAsState(initial = initialGlassBlurEnabled)
            val themeColor = AppThemeColor.fromKey(themeColorKey)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            NoFUDTheme(
                darkTheme = darkTheme,
                themeColor = themeColor,
                glassBlurEnabled = glassBlurEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NoFUDNavHost(container = container, startOnboarding = resolvedStartOnboarding)

                    if (pendingSharedMeals.isNotEmpty()) {
                        ImportSharedMealSheet(
                            meals = pendingSharedMeals,
                            onAdd = { meals ->
                                lifecycleScope.launch {
                                    meals.forEach { container.foodRepository.addEntry(it) }
                                }
                                pendingSharedMeals = emptyList()
                            },
                            onDismiss = { pendingSharedMeals = emptyList() }
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val FOREGROUND_SYNC_MIN_INTERVAL_MS = 60_000L
    }
}
