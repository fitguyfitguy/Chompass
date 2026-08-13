package app.chompass

import android.app.Application
import app.chompass.data.BodyFatRepository
import app.chompass.data.BodyMeasurementRepository
import app.chompass.data.ChatRepository
import app.chompass.data.FoodRepository
import app.chompass.data.KeyStore
import app.chompass.data.ManualActiveRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.data.RecipeRepository
import app.chompass.data.WaterRepository
import app.chompass.data.WeightRepository
import app.chompass.models.AIProvider
import app.chompass.models.CurrentMealSchedule
import app.chompass.models.UserProfile
import app.chompass.services.AdaptiveGoalResult
import app.chompass.services.AdaptiveGoalsService
import app.chompass.services.FoodImageStore
import app.chompass.services.FoodPhotoSession
import app.chompass.services.LauncherShortcuts
import app.chompass.services.NotificationService
import app.chompass.services.ShortcutEntryAction
import app.chompass.services.TestDataSeeder
import app.chompass.services.WaterReminderPlanner
import app.chompass.services.WidgetSnapshotWriter
import app.chompass.services.ai.ChatService
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.grounding.FoodDatabaseSearch
import app.chompass.services.grounding.GroundedFoodEntryService
import app.chompass.services.grounding.GroundedEntryFeature
import app.chompass.services.grounding.SwissFoodIndex
import app.chompass.services.grounding.UsdaFoodIndex
import app.chompass.services.health.HealthConnectManager
import app.chompass.services.health.HealthConnectReadSync
import app.chompass.services.health.HealthSyncWorker
import app.chompass.services.health.HomeActivityReader
import app.chompass.sync.SyncRepository
import app.chompass.services.ondevice.ModelDownloadManager
import app.chompass.services.ondevice.OnDeviceLlmGateway
import app.chompass.services.speech.SpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Application-scoped singleton wiring. Manual DI (no Hilt) — repositories and
 * services are instantiated once and handed to ViewModels via [container].
 */
class ChompassApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Process-lifetime scope for work that must outlive a disposed Compose tree. */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val appScope get() = applicationScope

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        seedDebugGeminiKeyIfNeeded()
        container.notifications.createChannels()
        LauncherShortcuts.publish(this)
        appScope.launch { container.prefs.migrateHomeDisplayLayoutIfNeeded() }
        container.prefs.mealSchedule
            .onEach { CurrentMealSchedule.value = it }
            .launchIn(appScope)
        container.widgetSnapshotWriter.observe().launchIn(appScope)
        // Widgets show "today" — roll the snapshot over just after midnight so
        // a widget left on the home screen never displays yesterday's totals
        // until the app happens to refresh it. Re-armed daily by the receiver;
        // this cold-start arm covers reboots and app updates (issue #16).
        appScope.launch { container.notifications.scheduleWidgetMidnightRefresh() }
        // Older builds removed food rows without removing their JPEGs.
        appScope.launch { container.foodRepository.pruneOrphanedImages() }
        // Re-arm opt-in background Health Connect sync on cold start. KEEP makes
        // this a no-op when the periodic work is already enqueued. Requires the
        // module feature + background-read grant (API varies by Mainline version).
        appScope.launch {
            if (container.prefs.healthBackgroundSyncEnabled.first() &&
                container.health.isAvailable() &&
                container.health.isBackgroundReadAvailable() &&
                container.health.hasBackgroundRead()
            ) {
                HealthSyncWorker.schedule(this@ChompassApp)
            } else if (container.prefs.healthBackgroundSyncEnabled.first()) {
                container.prefs.setHealthBackgroundSyncEnabled(false)
                HealthSyncWorker.cancel(this@ChompassApp)
            }
        }
        // Re-arm the daily weight-log alarm on every cold start. AlarmManager
        // drops scheduled alarms on device reboot and (sometimes) on app
        // updates — without this, a user who enabled Notifications once would
        // silently stop receiving the reminder after the next reboot.
        appScope.launch {
            if (container.prefs.notificationsEnabled.first() &&
                container.notifications.canPostNotifications()
            ) {
                if (container.prefs.streakReminderEnabled.first()) {
                    container.notifications.scheduleStreakReminder(
                        container.prefs.streakReminderHour.first(),
                        container.prefs.streakReminderMinute.first()
                    )
                } else {
                    container.notifications.cancelStreakReminder()
                }
                if (container.prefs.dailySummaryEnabled.first()) {
                    container.notifications.scheduleDailySummary(
                        container.prefs.dailySummaryHour.first(),
                        container.prefs.dailySummaryMinute.first()
                    )
                } else {
                    container.notifications.cancelDailySummary()
                }
                if (container.prefs.weightReminderEnabled.first()) {
                    container.notifications.scheduleWeightReminder()
                } else {
                    container.notifications.cancelWeightReminder()
                }
                // Body-fat reminder only fires for users who've actually opted
                // into body-fat tracking and left that notification type on.
                val profile = container.profileRepository.current()
                if (container.prefs.bodyFatReminderEnabled.first() && profile?.bodyFatPercentage != null) {
                    container.notifications.scheduleBodyFatReminder()
                } else {
                    container.notifications.cancelBodyFatReminder()
                }
                if (container.prefs.waterTrackingEnabled.first() && container.prefs.waterReminderEnabled.first()) {
                    WaterReminderPlanner.rearm(container)
                } else {
                    container.notifications.cancelWaterReminder()
                }
            }
        }
    }

    /**
     * Debug-only: if android/local.properties carries a GEMINI_API_KEY, seed it
     * into the encrypted KeyStore once so testing survives reinstalls without
     * re-typing the key in Settings. Only fills an empty slot — a key you set or
     * change in Settings always wins and is never overwritten on later launches.
     * Release builds compile GEMINI_API_KEY to "" so this is a no-op there.
     */
    private fun seedDebugGeminiKeyIfNeeded() {
        if (!BuildConfig.DEBUG) return
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return
        if (!container.keyStore.apiKey(AIProvider.GEMINI).isNullOrBlank()) return
        container.keyStore.setApiKey(AIProvider.GEMINI, key)
    }
}

class AppContainer(app: ChompassApp) {
    val appContext = app.applicationContext
    val prefs = PreferencesStore(app)
    val keyStore: KeyStore by lazy(LazyThreadSafetyMode.NONE) { KeyStore(app) }
    val imageStore = FoodImageStore(app)
    val notifications = NotificationService(app)
    val health = HealthConnectManager(app)
    val homeActivityReader = HomeActivityReader(health, prefs)

    val syncRepository = SyncRepository(prefs, keyStore, appContext = appContext)
    val profileRepository = ProfileRepository(prefs)
    val foodRepository = FoodRepository(prefs, health, imageStore, syncRepository)
    val recipeRepository = RecipeRepository(prefs, foodRepository, syncRepository)
    val weightRepository = WeightRepository(prefs, profileRepository, health, syncRepository)
    val bodyFatRepository = BodyFatRepository(prefs, profileRepository, health, syncRepository)
    val bodyMeasurementRepository = BodyMeasurementRepository(prefs, syncRepository)
    val chatRepository = ChatRepository(prefs)
    val waterRepository = WaterRepository(prefs, health, syncRepository).apply {
        // Re-arm the adaptive reminder chain after every water entry so the next
        // reminder reflects the new pace immediately (issue #3). `app.container`
        // is lateinit but always assigned before any entry can be added.
        onEntriesChanged = { WaterReminderPlanner.rearm(app.container) }
    }
    val manualActiveRepository = ManualActiveRepository(prefs)

    val onDeviceLlmGateway = OnDeviceLlmGateway(appContext, prefs)
    val onDeviceModelDownloadManager = ModelDownloadManager(appContext)
    val foodAnalysis = FoodAnalysisService(prefs, keyStore, onDeviceGateway = onDeviceLlmGateway)
    /**
     * Offline food-database indexes + the gated grounded orchestrator.
     * USDA (CC0) and Swiss (federal open data) SQLite assets ship in all build
     * types and power the Add Food "Search food" sheet. [groundedFoodEntry]
     * stays gated behind [GroundedEntryFeature.ENABLED] (false in shipping builds).
     */
    val usdaFoodIndex: UsdaFoodIndex by lazy(LazyThreadSafetyMode.NONE) {
        check(UsdaFoodIndex.assetAvailable(appContext)) {
            "USDA SQLite missing from APK assets"
        }
        UsdaFoodIndex(appContext)
    }
    val swissFoodIndex: SwissFoodIndex by lazy(LazyThreadSafetyMode.NONE) {
        check(SwissFoodIndex.assetAvailable(appContext)) {
            "Swiss food SQLite missing from APK assets"
        }
        SwissFoodIndex(appContext)
    }
    val foodDatabaseSearch: FoodDatabaseSearch by lazy(LazyThreadSafetyMode.NONE) {
        FoodDatabaseSearch(prefs, usdaFoodIndex, swissFoodIndex)
    }
    val groundedFoodEntry: GroundedFoodEntryService by lazy(LazyThreadSafetyMode.NONE) {
        check(GroundedEntryFeature.ENABLED) {
            "GroundedFoodEntryService is disabled (GroundedEntryFeature.ENABLED=false)"
        }
        GroundedFoodEntryService(
            foodAnalysis = foodAnalysis,
            foodRepository = foodRepository,
            prefs = prefs,
            usdaIndex = usdaFoodIndex,
        )
    }
    val chatService = ChatService(prefs, keyStore, foodAnalysis)
    val speechService = SpeechService(prefs, keyStore)

    val widgetSnapshotWriter = WidgetSnapshotWriter(app, prefs, foodRepository, profileRepository, homeActivityReader, waterRepository)
    val testDataSeeder = TestDataSeeder(this)

    private val healthConnectReadSync = HealthConnectReadSync(
        prefs = prefs,
        health = health,
        foodRepository = foodRepository,
        weightRepository = weightRepository,
        bodyFatRepository = bodyFatRepository,
        waterRepository = waterRepository,
    )
    private val adaptiveGoals = AdaptiveGoalsService(
        prefs = prefs,
        health = health,
        profileRepository = profileRepository,
        foodRepository = foodRepository,
        weightRepository = weightRepository,
        bodyMeasurementRepository = bodyMeasurementRepository,
        foodAnalysis = foodAnalysis,
    )

    /**
     * App-scoped flag set by [HomeViewModel] while a food analysis request is
     * in flight. The bottom nav reads this so the bar can hide during the
     * AnalyzingOverlay (matches iOS, where the analyzing sheet covers the
     * tab bar).
     */
    val analyzingFood: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Photos from the system share sheet only (ACTION_SEND / ACTION_SEND_MULTIPLE).
     * [MainActivity] fills it; a resumed Home merges into [foodPhotoSession].
     * Do **not** write in-app gallery picks here — that caused NavHost/Home races
     * and “gallery returns to bare Home” bugs.
     */
    val sharedImageInbox: MutableStateFlow<List<ByteArray>> = MutableStateFlow(emptyList())

    /**
     * In-app camera / gallery staging + multi-photo review sheet state.
     * Activity-registered Photo Picker writes here; share-ins merge via Home.
     */
    val foodPhotoSession: FoodPhotoSession = FoodPhotoSession()

    /**
     * Launcher shortcut destination for Home (Camera / Voice / Barcode).
     * [MainActivity] fills it; Home opens the matching entry UI and clears it
     * only when that UI dismisses. Sticky like [sharedImageInbox] so delivery
     * survives onboarding and Compose remounts (theme / palette refresh).
     */
    val shortcutEntryInbox: MutableStateFlow<ShortcutEntryAction?> = MutableStateFlow(null)

    /** See [HealthConnectReadSync.sync]. */
    suspend fun syncHealthConnectReads() = healthConnectReadSync.sync()

    /** See [AdaptiveGoalsService.measuredEnergyTdeeIfEnabled]. */
    suspend fun measuredEnergyTdeeIfEnabled(profile: UserProfile) =
        adaptiveGoals.measuredEnergyTdeeIfEnabled(profile)

    /** See [AdaptiveGoalsService.refreshIfNeeded]. */
    suspend fun refreshAdaptiveGoalsIfNeeded(force: Boolean = false): AdaptiveGoalResult? =
        adaptiveGoals.refreshIfNeeded(force)
}
