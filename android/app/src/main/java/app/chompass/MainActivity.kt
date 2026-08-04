package app.chompass

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import app.chompass.models.FoodEntry
import app.chompass.services.AndroidAppIconManager
import app.chompass.debug.OnDeviceLlmDebugConfig
import app.chompass.debug.OnDeviceLlmDebugLauncher
import app.chompass.debug.OnDeviceLlmDefaults
import app.chompass.services.EntryPerfBenchmark
import app.chompass.services.FoodPhotoSession
import app.chompass.services.LauncherShortcuts
import app.chompass.services.MealShare
import app.chompass.services.ShortcutEntryAction
import app.chompass.services.InAppReview
import app.chompass.services.health.HealthConnectDiagnostics
import app.chompass.ui.home.ImportSharedMealSheet
import app.chompass.ui.navigation.ChompassNavHost
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.ChompassTheme
import app.chompass.ui.theme.widgetAccentColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class MainActivity : ComponentActivity() {
    // Shared-meal deep link (issue #107). Non-empty -> the confirm sheet is shown over the app.
    private var pendingSharedMeals by mutableStateOf<List<FoodEntry>>(emptyList())
    private var foregroundSyncJob: Job? = null
    private var lastForegroundSyncAtMs: Long = 0L
    /** Bumped when Theme Color is System so Compose re-reads Material You schemes. */
    private var systemPaletteEpoch by mutableIntStateOf(0)
    private var lastSystemPrimaryArgb: Int? = null
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null
    private var systemPaletteRefreshJob: Job? = null

    /**
     * Activity-owned Photo Picker for food entry. Registered here (not in Home
     * composition) so results survive Dialog dismiss / Compose dispose and never
     * need the share-sheet [AppContainer.sharedImageInbox] as a fallback.
     * Property initializer — must register before the Activity is CREATED.
     */
    private val foodGalleryPicker =
        registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = FoodPhotoSession.MAX_IMAGES)
        ) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            val session = (application as ChompassApp).container.foodPhotoSession
            val remaining = session.remainingSlots()
            if (remaining == 0) return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val imported = uris.take(remaining).mapNotNull { uri ->
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
                withContext(Dispatchers.Main) {
                    if (imported.isEmpty()) {
                        Log.w(PHOTO_IMPORT_TAG, "gallery pick: ${uris.size} uri(s), 0 readable")
                        session.signalImportFailed()
                    } else {
                        session.stageFromImport(imported)
                    }
                }
            }
        }

    /**
     * Open the system Photo Picker for food photos. Call after dismissing any
     * camera Dialog (Home posts onto the decor view) so the Activity Result is not lost.
     */
    fun launchFoodGalleryPick() {
        foodGalleryPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * Route whatever launched (or re-launched) us: a `fudai://add-meal` link into
     * pending meals, a system share-sheet image into the photo entry flow, or a
     * launcher long-press shortcut into Camera / Voice / Barcode.
     */
    private fun handleLaunchIntent(intent: Intent?) {
        intent ?: return
        handleShortcutIntent(intent)
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
     * Launcher shortcuts set a dedicated action (and a redundant extra). Consume
     * the extras so Activity.recreate() does not re-open the sheet.
     */
    private fun handleShortcutIntent(intent: Intent) {
        val action = ShortcutEntryAction.fromAction(intent.action)
            ?: ShortcutEntryAction.fromIntentExtra(intent.getStringExtra(LauncherShortcuts.EXTRA_SHORTCUT))
            ?: return
        intent.removeExtra(LauncherShortcuts.EXTRA_SHORTCUT)
        // Reset action so a later recreate does not re-fire via fromAction().
        if (intent.action?.startsWith("app.chompass.action.SHORTCUT_") == true) {
            intent.action = Intent.ACTION_MAIN
        }
        (application as ChompassApp).container.shortcutEntryInbox.value = action
    }

    /**
     * Photos shared from another app (camera, gallery). Up to 10 images enter
     * the multi-photo review sheet (same cap as in-app gallery pick).
     * Bytes are read off the main thread, then handed to Home via
     * [AppContainer.sharedImageInbox] (share-ins only — not in-app gallery).
     */
    private fun handleSharedImages(intent: Intent) {
        val type = intent.type
        val looksLikeImage = type == null || type == "*/*" || type.startsWith("image/")
        if (!looksLikeImage) return
        val uris = buildList {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.let(::add)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.let(::addAll)
                }
            }
            // Some senders put the URI only in ClipData.
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let(::add)
                }
            }
        }.distinct().take(10)
        if (uris.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val images = uris.mapNotNull { uri ->
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            }
            if (images.isNotEmpty()) {
                (application as ChompassApp).container.sharedImageInbox.value = images
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
        handleDebugIntentExtras(intent, (application as ChompassApp).container)
    }
    override fun onStart() {
        super.onStart()
        registerWallpaperColorsListener()
        val now = SystemClock.elapsedRealtime()
        if (foregroundSyncJob?.isActive == true) return
        if (now - lastForegroundSyncAtMs < FOREGROUND_SYNC_MIN_INTERVAL_MS) return
        lastForegroundSyncAtMs = now
        foregroundSyncJob = lifecycleScope.launch {
            // Adaptive Goals auto-runs the full goal calculation about once a week (Energy Burn,
            // when on, supplies the measured-burn anchor it consumes — separate toggle).
            val container = (application as ChompassApp).container
            container.refreshAdaptiveGoalsIfNeeded()
            // Pull any new external weight / body-fat readings (e.g. a Withings scale)
            // from Health Connect into the app on every foreground (issue #91).
            container.syncHealthConnectReads()
            // Opt-in WebDAV: at most once per local day on open (manual Sync now always available).
            container.syncRepository.maybeAutoSyncWebDav()
            foregroundSyncJob = null
        }
    }

    override fun onStop() {
        unregisterWallpaperColorsListener()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshSystemPalette()
    }

    /**
     * When Theme Color is System, re-read Material You primary, refresh the Compose
     * theme (via [systemPaletteEpoch]), re-map the launcher icon, and rewrite widgets
     * if the primary hex changed.
     */
    private fun refreshSystemPalette() {
        if (systemPaletteRefreshJob?.isActive == true) return
        systemPaletteRefreshJob = lifecycleScope.launch {
            try {
                val container = (application as ChompassApp).container
                val themeColor = AppThemeColor.fromKey(container.prefs.appThemeColor.first())
                if (!themeColor.usesSystemPalette) return@launch
                val primaryArgb = themeColor.widgetAccentColors(this@MainActivity).first.toArgb()
                val primaryChanged = lastSystemPrimaryArgb != null && lastSystemPrimaryArgb != primaryArgb
                lastSystemPrimaryArgb = primaryArgb
                // Only remount the Compose tree when Material You primary actually
                // changed. Bumping on every onResume was wiping Home sheet state
                // (Voice / Camera / Barcode shortcuts) right after delivery.
                if (primaryChanged) {
                    systemPaletteEpoch++
                    container.widgetSnapshotWriter.refresh()
                }
                AndroidAppIconManager.apply(this@MainActivity, themeColor)
            } finally {
                systemPaletteRefreshJob = null
            }
        }
    }

    private fun registerWallpaperColorsListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (wallpaperColorsListener != null) return
        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            refreshSystemPalette()
        }
        wallpaperColorsListener = listener
        WallpaperManager.getInstance(this).addOnColorsChangedListener(
            listener,
            Handler(Looper.getMainLooper()),
        )
    }

    private fun unregisterWallpaperColorsListener() {
        val listener = wallpaperColorsListener ?: return
        wallpaperColorsListener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WallpaperManager.getInstance(this).removeOnColorsChangedListener(listener)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system swaps the splash theme
        // back to Theme.Chompass before the first frame, preventing a white flash
        // on cold start. The splash uses a transparent foreground mark over
        // the app's light/dark splash background.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        InAppReview.bind(this)

        val container = (application as ChompassApp).container
        val app = application as ChompassApp
        // Dev-only seeders / benchmarks — see handleDebugIntentExtras() for adb examples.
        // Extras are consumed there so Activity.recreate() doesn't re-fire them.
        val debugActions = consumeDebugIntentExtras(intent)
        // A fudai://add-meal link or launcher shortcut may have cold-launched us.
        handleLaunchIntent(intent)
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
            val paletteEpoch = systemPaletteEpoch
            key(themeColorKey, paletteEpoch) {
                ChompassTheme(
                    darkTheme = darkTheme,
                    themeColor = themeColor,
                    glassBlurEnabled = glassBlurEnabled
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ChompassNavHost(container = container, startOnboarding = resolvedStartOnboarding)

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
    }

    private companion object {
        const val FOREGROUND_SYNC_MIN_INTERVAL_MS = 60_000L
        const val PHOTO_IMPORT_TAG = "Chompass"
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
        val seedActiveCalories: Boolean = false,
        val activeTodayOverride: Int = 0,
        val restoreRealData: Boolean = false,
        val runEntryBenchmark: Boolean = false,
        val entryBenchmarkCount: Int = 3,
        val runOnDeviceLlmTest: Boolean = false,
        val onDeviceLlmBackend: String = "gpu",
        val onDeviceLlmMtp: Boolean = false,
        val onDeviceLlmModel: String = OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
        val onDeviceLlmTier: String = "all",
        val onDeviceLlmPrompt: String = "full",
        val onDeviceLlmRepeat: Int = 1,
        val onDeviceLlmClearCache: Boolean = false,
        val diagnoseHealthConnect: Boolean = false,
    )

    private fun consumeDebugIntentExtras(intent: Intent?): DebugIntentActions {
        intent ?: return DebugIntentActions()
        val presetDaily = intent.getStringExtra("ondevice_llm_preset")?.lowercase() == "daily"
        val actions = DebugIntentActions(
            resetOnboarding = intent.getBooleanExtra("reset_onboarding", false),
            seedTestData = intent.getBooleanExtra("seed_test_data", false),
            seedBodyMetrics = intent.getBooleanExtra("seed_body_metrics", false),
            seedBodyMetricsTwoYears = intent.getBooleanExtra("seed_body_metrics_2y", false),
            seedKetoSettings = intent.getBooleanExtra("seed_keto_settings", false),
            seedActiveCalories = intent.getBooleanExtra("seed_active_calories", false),
            activeTodayOverride = intent.getIntExtra("active_today_override", 0),
            restoreRealData = intent.getBooleanExtra("restore_real_data", false),
            runEntryBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_entry_benchmark", false),
            entryBenchmarkCount = intent.getIntExtra("benchmark_count", 3),
            runOnDeviceLlmTest = BuildConfig.DEBUG && intent.getBooleanExtra("run_ondevice_llm_test", false),
            onDeviceLlmBackend = if (presetDaily) "gpu" else intent.getStringExtra("ondevice_llm_backend") ?: "gpu",
            onDeviceLlmMtp = presetDaily || intent.getBooleanExtra("ondevice_llm_mtp", false),
            onDeviceLlmModel = intent.getStringExtra("ondevice_llm_model")
                ?: OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
            onDeviceLlmTier = if (presetDaily) "daily" else intent.getStringExtra("ondevice_llm_tier") ?: "all",
            onDeviceLlmPrompt = if (presetDaily) "fewshot_units" else intent.getStringExtra("ondevice_llm_prompt") ?: "full",
            onDeviceLlmRepeat = intent.getIntExtra("ondevice_llm_repeat", 1).coerceIn(1, 5),
            onDeviceLlmClearCache = intent.getBooleanExtra("ondevice_llm_clear_cache", false),
            diagnoseHealthConnect = BuildConfig.DEBUG &&
                intent.getBooleanExtra("diagnose_health_connect", false),
        )
        if (actions.resetOnboarding) intent.removeExtra("reset_onboarding")
        if (actions.seedTestData) intent.removeExtra("seed_test_data")
        if (actions.seedBodyMetrics) intent.removeExtra("seed_body_metrics")
        if (actions.seedBodyMetricsTwoYears) intent.removeExtra("seed_body_metrics_2y")
        if (actions.seedKetoSettings) intent.removeExtra("seed_keto_settings")
        if (actions.seedActiveCalories) {
            intent.removeExtra("seed_active_calories")
            intent.removeExtra("active_today_override")
        }
        if (actions.restoreRealData) intent.removeExtra("restore_real_data")
        if (actions.runEntryBenchmark) intent.removeExtra("run_entry_benchmark")
        if (actions.diagnoseHealthConnect) intent.removeExtra("diagnose_health_connect")
        if (actions.runOnDeviceLlmTest) {
            intent.removeExtra("run_ondevice_llm_test")
            intent.removeExtra("ondevice_llm_backend")
            intent.removeExtra("ondevice_llm_mtp")
            intent.removeExtra("ondevice_llm_model")
            intent.removeExtra("ondevice_llm_tier")
            intent.removeExtra("ondevice_llm_prompt")
            intent.removeExtra("ondevice_llm_repeat")
            intent.removeExtra("ondevice_llm_clear_cache")
            intent.removeExtra("ondevice_llm_preset")
        }
        return actions
    }

    /** Handles debug extras from cold start ([onCreate]) or warm relaunch ([onNewIntent]). */
    private fun handleDebugIntentExtras(intent: Intent?, container: AppContainer) {
        val actions = consumeDebugIntentExtras(intent)
        launchDebugIntentActions(actions, container, application as ChompassApp)
    }

    private fun launchDebugIntentActions(
        actions: DebugIntentActions,
        container: AppContainer,
        app: ChompassApp,
    ) {
        if (actions == DebugIntentActions()) return
        lifecycleScope.launch {
            if (actions.resetOnboarding) {
                app.container.prefs.setOnboardingCompleted(false)
                app.container.prefs.setOnboardingDraft(null)
            }
            if (actions.seedTestData) container.testDataSeeder.seedYear()
            if (actions.seedBodyMetrics) container.testDataSeeder.seedBodyMetrics()
            if (actions.seedBodyMetricsTwoYears) container.testDataSeeder.seedTwoYearsBodyMetrics()
            if (actions.seedKetoSettings) container.testDataSeeder.seedKetoSettings()
            if (actions.seedActiveCalories) {
                container.testDataSeeder.seedActiveCalories(
                    actions.activeTodayOverride.takeIf { it > 0 }
                )
            }
            if (actions.restoreRealData) container.testDataSeeder.restore()

            // Independent of seeding/onboarding: benchmarks only need the AI provider + key.
            if (actions.runEntryBenchmark) {
                lifecycleScope.launch {
                    EntryPerfBenchmark(container).run(actions.entryBenchmarkCount)
                }
            }
            if (actions.runOnDeviceLlmTest) {
                OnDeviceLlmDebugLauncher.launchIfRequested(
                    scope = lifecycleScope,
                    container = container,
                    config = OnDeviceLlmDebugConfig(
                        enabled = true,
                        backendName = actions.onDeviceLlmBackend,
                        enableMtp = actions.onDeviceLlmMtp,
                        modelFilename = actions.onDeviceLlmModel,
                        tier = actions.onDeviceLlmTier,
                        promptMode = actions.onDeviceLlmPrompt,
                        repeatCount = actions.onDeviceLlmRepeat,
                        clearCache = actions.onDeviceLlmClearCache,
                    ),
                )
            }
            if (actions.diagnoseHealthConnect) {
                HealthConnectDiagnostics.log(
                    this@MainActivity,
                    container.health,
                )
            }
        }
    }
}
