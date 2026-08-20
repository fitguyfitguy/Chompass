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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.chompass.models.FoodEntry
import app.chompass.services.AndroidAppIconManager
import app.chompass.debug.OnDeviceLlmDebugConfig
import app.chompass.debug.OnDeviceLlmDebugLauncher
import app.chompass.services.EntryPerfBenchmark
import app.chompass.services.PerfBenchRequest
import app.chompass.services.PerfLog
import app.chompass.services.FoodPhotoSession
import app.chompass.services.LauncherShortcuts
import app.chompass.services.MealShare
import app.chompass.services.ShortcutEntryAction
import app.chompass.services.InAppReview
import app.chompass.services.health.HealthConnectDiagnostics
import app.chompass.utils.LocaleHelper
import app.chompass.ui.home.ImportSharedMealSheet
import app.chompass.ui.navigation.ChompassNavHost
import app.chompass.ui.navigation.ChompassRoutes
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
    /** Last applied app-language tag ("" = system). Guards against recreate loops on locale change. */
    private var appliedAppLanguage: String = ""

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
                val imported = mutableListOf<ByteArray>()
                var batchBytes = 0L
                for (uri in uris.take(remaining)) {
                    if (batchBytes >= MAX_IMPORT_BATCH_BYTES) break
                    val bytes = runCatching {
                        contentResolver.openInputStream(uri)
                            ?.use { readBytesCapped(it, MAX_IMPORT_IMAGE_BYTES) }
                    }.getOrNull() ?: continue
                    if (bytes.isNotEmpty()) {
                        imported += bytes
                        batchBytes += bytes.size
                    }
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
                if (!MealShare.handles(uri)) {
                    handleLaunchDestination(uri, intent)
                    return
                }
                // Decode off the main thread: `d` is attacker-controlled (any app
                // can fire a VIEW intent), so a crafted link must not block
                // onCreate/onNewIntent. MealShare caps payload size + row counts.
                lifecycleScope.launch(Dispatchers.Default) {
                    MealShare.meals(uri)?.let { pendingSharedMeals = it }
                }
            }
        }
    }

    /**
     * Notification tap destination (Codeberg #27): `chompass://go/<dest>` set by
     * [ChompassLaunchIntents.openApp]. Consumed here so a later [Activity.recreate]
     * or resume does not re-navigate; the nav host routes it once.
     */
    private fun handleLaunchDestination(uri: android.net.Uri, intent: Intent) {
        if (uri.scheme != "chompass" || uri.host != "go") return
        val dest = uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() } ?: return
        // Whitelist only known routes: `nav.navigate(dest)` throws for unknown
        // destinations and arg-routed subscreens can't be built from a path-only
        // link — an attacker firing `chompass://go/<garbage>` must not crash us.
        if (!ChompassRoutes.isGoDestination(dest)) return
        intent.data = null // consume: one navigation per tap
        (application as ChompassApp).container.launchDestinationInbox.value = dest
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
            val images = mutableListOf<ByteArray>()
            var batchBytes = 0L
            for (uri in uris) {
                if (batchBytes >= MAX_IMPORT_BATCH_BYTES) break
                val bytes = runCatching {
                    contentResolver.openInputStream(uri)
                        ?.use { readBytesCapped(it, MAX_IMPORT_IMAGE_BYTES) }
                }.getOrNull() ?: continue
                if (bytes.isNotEmpty()) {
                    images += bytes
                    batchBytes += bytes.size
                }
            }
            if (images.isNotEmpty()) {
                (application as ChompassApp).container.sharedImageInbox.value = images
            }
        }
    }

    /**
     * Reads up to [maxBytes] from [input]; null when the stream exceeds the cap.
     * Attacker-supplied image bytes (share sheet, any app) must not be read into
     * memory unbounded — 10 × multi-hundred-MB files would OOM the process.
     */
    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
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
        // Launcher-icon alias swaps must not run while the task is in the foreground:
        // disabling the alias the current task was launched through makes some launchers
        // (MIUI, One UI) tear the task down or force-stop the app (#13, #21). Swap only
        // when leaving for real (skip config-change recreation) and off the main thread,
        // so the icon is already updated when the user looks at the launcher. If the
        // process dies before the swap lands, the next cold start applies it anyway.
        if (!isChangingConfigurations) {
            lifecycleScope.launch(Dispatchers.IO) {
                val container = (application as ChompassApp).container
                val themeColor = AppThemeColor.fromKey(container.prefs.appThemeColor.first())
                val fixedIcon = container.prefs.fixedLauncherIcon.first()
                // Re-check we are still backgrounded before touching any alias.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                AndroidAppIconManager.apply(this@MainActivity, themeColor, fixedIcon)
            }
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshSystemPalette()
    }

    /**
     * When Theme Color is System, re-read Material You primary and refresh the Compose
     * theme (via [systemPaletteEpoch]) plus the widgets if the primary hex changed.
     * The launcher icon is not remapped here: alias swaps are deferred to [onStop]
     * (see there) so a running task is never torn down by launchers that react to
     * component changes (#13).
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

        // Hold the splash on screen until the saved profile has loaded from
        // DataStore so Home doesn't briefly render its 2000/150/220/70 fallback
        // goal numbers before snapping to the user's real targets. Onboarding
        // doesn't show those numbers, so we let the splash dismiss immediately
        // in that case.
        var contentReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !contentReady }
        lifecycleScope.launch {
            val splashAt = if (PerfLog.enabled) System.nanoTime() else 0L
            launchDebugIntentActions(debugActions, container, app)

            // Seed extras write onboarded + profile first, then the heavy replaceAll.
            // Wait only for that fast prefix so first-install --full skips onboarding
            // without holding the splash across a year of diary writes.
            if (debugActions.writesOnboarded) {
                container.prefs.hasCompletedOnboarding.first { it }
            }

            val snap = PerfLog.measure("coldStart", "prefsSnapshot") {
                container.prefs.readColdStartPrefs()
            }
            initialAppearance = snap.appearanceMode
            initialThemeColorKey = snap.appThemeColor
            AndroidAppIconManager.apply(
                this@MainActivity,
                AppThemeColor.fromKey(initialThemeColorKey),
                snap.fixedLauncherIcon,
            )
            appliedAppLanguage = snap.appLanguage
            LocaleHelper.apply(this@MainActivity, snap.appLanguage)
            startOnboarding = !snap.onboarded
            if (snap.onboarded) {
                container.profileRepository.profile.first { it != null }
            }
            contentReady = true
            if (PerfLog.enabled) {
                val ms = (System.nanoTime() - splashAt) / 1_000_000
                PerfLog.event("op=coldStart phase=splashReady ms=$ms")
            }
            // Write after splash dismiss so it does not contend with first paint.
            container.prefs.ensureFirstLaunchAt()
        }

        setContent {
            val resolvedStartOnboarding = startOnboarding ?: return@setContent
            val appearance by container.prefs.appearanceMode.collectAsState(initial = initialAppearance)
            val themeColorKey by container.prefs.appThemeColor.collectAsState(initial = initialThemeColorKey)
            val themeColor = AppThemeColor.fromKey(themeColorKey)
            val appLanguage by container.prefs.appLanguage.collectAsState(initial = appliedAppLanguage)
            // Apply per-app language when preference changes. The activity field
            // guard prevents an infinite recreate loop: after the system (or legacy
            // recreate) relaunches us, the flow re-emits the same tag which now
            // equals appliedAppLanguage, so nothing fires again.
            androidx.compose.runtime.LaunchedEffect(appLanguage) {
                if (appLanguage != appliedAppLanguage) {
                    appliedAppLanguage = appLanguage
                    LocaleHelper.apply(this@MainActivity, appLanguage)
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        this@MainActivity.recreate()
                    }
                }
            }
            val systemDark = isSystemInDarkTheme()
            val oledTheme = appearance == "oled"
            val darkTheme = when (appearance) {
                "light" -> false
                "dark", "oled" -> true
                else -> systemDark
            }
            // Codeberg #28: log the resolved theme inputs on every composition so
            // a device pass can tell a stale appearance value from a ROM that
            // delivers uiMode without recreating the activity.
            Log.d("ChompassTheme", "appearance=$appearance systemDark=$systemDark darkTheme=$darkTheme oledTheme=$oledTheme themeColor=$themeColorKey")
            val paletteEpoch = systemPaletteEpoch
            key(themeColorKey, paletteEpoch) {
                ChompassTheme(
                    darkTheme = darkTheme,
                    oledTheme = oledTheme,
                    themeColor = themeColor,
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
        /** Per-image ingest cap (bytes) for share-in / gallery photos. */
        const val MAX_IMPORT_IMAGE_BYTES = 25 * 1024 * 1024
        /** Total staging cap for one share/gallery batch (10 photos ÷ cap each). */
        const val MAX_IMPORT_BATCH_BYTES = 150 * 1024 * 1024
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
        Log.d(PHOTO_IMPORT_TAG, "Debug extras: $actions")
        lifecycleScope.launch {
            if (actions.resetOnboarding) {
                app.container.prefs.setOnboardingCompleted(false)
                app.container.prefs.setOnboardingDraft(null)
            }
            if (actions.seedFull) {
                container.testDataSeeder.seedFullyUtilized(
                    keto = actions.seedKetoSettings,
                    busyHome = actions.seedBusyHome,
                )
            } else {
                if (actions.seedTestData) container.testDataSeeder.seedYear()
                if (actions.seedBodyMetrics) container.testDataSeeder.seedBodyMetrics()
                if (actions.seedBodyMetricsTwoYears) container.testDataSeeder.seedTwoYearsBodyMetrics()
                if (actions.seedKetoSettings) container.testDataSeeder.seedKetoSettings()
            }
            if (actions.seedActiveCalories) {
                runCatching { container.testDataSeeder.seedActiveCalories(actions.activeTodayOverride) }
                    .onFailure { Log.e(PHOTO_IMPORT_TAG, "seedActiveCalories failed", it) }
            }
            if (actions.setGaugeMode.isNotEmpty()) {
                runCatching { container.testDataSeeder.setGaugeMode(actions.setGaugeMode) }
                    .onFailure { Log.e(PHOTO_IMPORT_TAG, "setGaugeMode failed", it) }
            }
            if (actions.setShowSteps) container.testDataSeeder.setShowSteps(true)
            actions.setShowActiveCalories?.let {
                runCatching { container.testDataSeeder.setShowActiveCalories(it) }
                    .onFailure { Log.e(PHOTO_IMPORT_TAG, "setShowActiveCalories failed", it) }
            }
            if (actions.clearDebugActivity) {
                runCatching { container.testDataSeeder.clearDebugActivity() }
                    .onFailure { Log.e(PHOTO_IMPORT_TAG, "clearDebugActivity failed", it) }
            }
            actions.setShowRestingShade?.let { container.testDataSeeder.setShowRestingShade(it) }
            if (actions.seedOverGoal) container.testDataSeeder.seedOverGoal()
            if (actions.restoreRealData) container.testDataSeeder.restore()
            if (actions.demoAi) container.prefs.setDebugDemoAnalysis(true)
            if (actions.clearPendingDraft) {
                container.prefs.setPendingFoodAnalysisDraft(null)
                container.prefs.setPendingFoodInputDraft(null)
            }

            // Flippidity benches go through HomeViewModel (chip cache, uiAck).
            // Gemini analyze+save stays on the repo path (no Home UI).
            val flipReq = when {
                actions.runFlipBenchmark -> PerfBenchRequest.Flip(
                    relog = actions.relogBenchmarkCount,
                    local = actions.localEntryBenchmarkCount,
                    sips = actions.waterSipBenchmarkCount,
                )
                actions.runRelogBenchmark -> PerfBenchRequest.Relog(actions.relogBenchmarkCount)
                actions.runLocalEntryBenchmark ->
                    PerfBenchRequest.LocalEntry(actions.localEntryBenchmarkCount)
                actions.runWaterSipBenchmark ->
                    PerfBenchRequest.WaterSip(actions.waterSipBenchmarkCount)
                actions.runHubBenchmark -> PerfBenchRequest.HubOpen()
                actions.runDaySwitchBenchmark -> PerfBenchRequest.DaySwitch()
                else -> null
            }
            if (flipReq != null) container.perfBenchInbox.value = flipReq
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
            if (actions.previewDailySummary) {
                runCatching { container.notifications.postDailySummaryNow() }
                    .onFailure { Log.e(PHOTO_IMPORT_TAG, "previewDailySummary failed", it) }
            }
            Log.d(PHOTO_IMPORT_TAG, "debug actions complete")
        }
    }
}
