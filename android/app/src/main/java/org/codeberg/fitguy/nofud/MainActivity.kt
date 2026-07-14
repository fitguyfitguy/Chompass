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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.services.AndroidAppIconManager
import org.codeberg.fitguy.nofud.services.EntryPerfBenchmark
import org.codeberg.fitguy.nofud.services.MealShare
import org.codeberg.fitguy.nofud.services.OnDeviceLlmSmokeTest
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
        handleDebugIntentExtras(intent, (application as NoFUDApp).container)
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

    override fun onResume() {
        super.onResume()
        val container = (application as NoFUDApp).container
        lifecycleScope.launch {
            val themeColor = AppThemeColor.fromKey(container.prefs.appThemeColor.first())
            if (themeColor.usesSystemPalette) {
                AndroidAppIconManager.apply(this@MainActivity, themeColor)
            }
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
        // Dev-only seeders / benchmarks — see handleDebugIntentExtras() for adb examples.
        // Extras are consumed there so Activity.recreate() doesn't re-fire them.
        val debugActions = consumeDebugIntentExtras(intent)
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
            launchDebugIntentActions(debugActions, container, app)

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
            LaunchedEffect(themeColorKey) {
                AndroidAppIconManager.apply(this@MainActivity, themeColor)
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

    /**
     * Debug-only intent extras (seeders, benchmarks, on-device LLM smoke test).
     * Parsed once and stripped so [Activity.recreate] does not re-fire them.
     *
     * When the activity is already foreground ([launchMode] singleTop), adb delivers
     * extras via [onNewIntent] — not [onCreate]. Both paths call [handleDebugIntentExtras].
     */
    private data class DebugIntentActions(
        val resetOnboarding: Boolean = false,
        val seedTestData: Boolean = false,
        val seedBodyMetrics: Boolean = false,
        val seedBodyMetricsTwoYears: Boolean = false,
        val seedKetoSettings: Boolean = false,
        val restoreRealData: Boolean = false,
        val runEntryBenchmark: Boolean = false,
        val entryBenchmarkCount: Int = 3,
        val runOnDeviceLlmTest: Boolean = false,
        val onDeviceLlmBackend: String = "gpu",
        val onDeviceLlmMtp: Boolean = false,
    )

    private fun consumeDebugIntentExtras(intent: Intent?): DebugIntentActions {
        intent ?: return DebugIntentActions()
        val actions = DebugIntentActions(
            resetOnboarding = intent.getBooleanExtra("reset_onboarding", false),
            seedTestData = intent.getBooleanExtra("seed_test_data", false),
            seedBodyMetrics = intent.getBooleanExtra("seed_body_metrics", false),
            seedBodyMetricsTwoYears = intent.getBooleanExtra("seed_body_metrics_2y", false),
            seedKetoSettings = intent.getBooleanExtra("seed_keto_settings", false),
            restoreRealData = intent.getBooleanExtra("restore_real_data", false),
            runEntryBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_entry_benchmark", false),
            entryBenchmarkCount = intent.getIntExtra("benchmark_count", 3),
            runOnDeviceLlmTest = BuildConfig.DEBUG && intent.getBooleanExtra("run_ondevice_llm_test", false),
            onDeviceLlmBackend = intent.getStringExtra("ondevice_llm_backend") ?: "gpu",
            onDeviceLlmMtp = intent.getBooleanExtra("ondevice_llm_mtp", false),
        )
        if (actions.resetOnboarding) intent.removeExtra("reset_onboarding")
        if (actions.seedTestData) intent.removeExtra("seed_test_data")
        if (actions.seedBodyMetrics) intent.removeExtra("seed_body_metrics")
        if (actions.seedBodyMetricsTwoYears) intent.removeExtra("seed_body_metrics_2y")
        if (actions.seedKetoSettings) intent.removeExtra("seed_keto_settings")
        if (actions.restoreRealData) intent.removeExtra("restore_real_data")
        if (actions.runEntryBenchmark) intent.removeExtra("run_entry_benchmark")
        if (actions.runOnDeviceLlmTest) {
            intent.removeExtra("run_ondevice_llm_test")
            intent.removeExtra("ondevice_llm_backend")
            intent.removeExtra("ondevice_llm_mtp")
        }
        return actions
    }

    /** Handles debug extras from cold start ([onCreate]) or warm relaunch ([onNewIntent]). */
    private fun handleDebugIntentExtras(intent: Intent?, container: AppContainer) {
        val actions = consumeDebugIntentExtras(intent)
        launchDebugIntentActions(actions, container, application as NoFUDApp)
    }

    private fun launchDebugIntentActions(
        actions: DebugIntentActions,
        container: AppContainer,
        app: NoFUDApp,
    ) {
        if (actions == DebugIntentActions()) return
        lifecycleScope.launch {
            if (actions.resetOnboarding) {
                app.container.prefs.setOnboardingCompleted(false)
            }
            if (actions.seedTestData) container.testDataSeeder.seedYear()
            if (actions.seedBodyMetrics) container.testDataSeeder.seedBodyMetrics()
            if (actions.seedBodyMetricsTwoYears) container.testDataSeeder.seedTwoYearsBodyMetrics()
            if (actions.seedKetoSettings) container.testDataSeeder.seedKetoSettings()
            if (actions.restoreRealData) container.testDataSeeder.restore()

            // Independent of seeding/onboarding: benchmarks only need the AI provider + key.
            if (actions.runEntryBenchmark) {
                lifecycleScope.launch {
                    EntryPerfBenchmark(container).run(actions.entryBenchmarkCount)
                }
            }
            if (actions.runOnDeviceLlmTest) {
                lifecycleScope.launch {
                    OnDeviceLlmSmokeTest(
                        container,
                        backendName = actions.onDeviceLlmBackend,
                        enableMtp = actions.onDeviceLlmMtp,
                    ).run()
                }
            }
        }
    }
}
