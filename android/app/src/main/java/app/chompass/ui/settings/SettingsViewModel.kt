package app.chompass.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.AIProvider
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.DietMode
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HeuristicRuleOverride
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.models.KetoCarbMode
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.ProteinTargetMode
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.SpeechLanguage
import app.chompass.models.SpeechProvider
import app.chompass.models.UserProfile
import app.chompass.models.ActivityLevel
import app.chompass.models.WaterGoalBreakdown
import app.chompass.models.WaterGoalCalculator
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WeightEntry
import app.chompass.data.OpenRouterReasoningEffort
import app.chompass.data.WeatherRepository
import app.chompass.data.readSettingsHydration
import app.chompass.services.ondevice.ModelCatalog
import app.chompass.services.ondevice.OnDeviceCapability
import app.chompass.models.WeightGoal
import app.chompass.services.KetoCarbRecommendationService
import app.chompass.services.WaterReminderPlanner
import app.chompass.services.WeightAnalysisService
import app.chompass.services.ai.AiError
import app.chompass.services.ai.userMessage
import app.chompass.services.health.HealthConnectManager
import app.chompass.services.health.HealthSyncWorker
import app.chompass.services.weather.OmCity
import app.chompass.ui.home.FoodLogSortOrder
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppThemeColor
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedAI: AIProvider = AIProvider.GEMINI,
    val selectedModel: String = AIProvider.GEMINI.defaultModel,
    /** Resolved vision-model slot for [selectedAI]; empty = primary model handles images too (#195). */
    val visionModel: String = "",
    val maxResponseTokens: Int = 1024,
    val aiReadTimeoutSeconds: Int = app.chompass.data.DEFAULT_AI_READ_TIMEOUT_SECONDS,
    val servingUnitInferenceMode: ServingUnitInferenceMode = ServingUnitInferenceMode.Default,
    val heuristicServingUnitSettings: HeuristicServingUnitSettings = HeuristicServingUnitSettings.Default,
    val selectedSpeech: SpeechProvider = SpeechProvider.NATIVE,
    val selectedSpeechLanguage: SpeechLanguage = SpeechLanguage.defaultFor(SpeechProvider.NATIVE),
    /** "cm" | "ftin" — governs all length display/input. */
    val heightUnit: String = "cm",
    /** "kg" | "lbs" — governs all mass display/input. */
    val weightUnit: String = "kg",
    val preferGramsByDefault: Boolean = false,
    val profile: UserProfile? = null,
    val notificationsEnabled: Boolean = false,
    val streakReminderEnabled: Boolean = false,
    val dailySummaryEnabled: Boolean = false,
    val dailySummaryHour: Int = 21,
    val dailySummaryMinute: Int = 0,
    val weightReminderEnabled: Boolean = true,
    val bodyFatReminderEnabled: Boolean = true,
    val waterTrackingEnabled: Boolean = false,
    val waterDailyGoalMl: Int = 2_000,
    val waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    val waterReminderEnabled: Boolean = false,
    val waterDynamicEnabled: Boolean = false,
    val waterBaseSource: String = WaterGoalCalculator.BASE_SOURCE_WEIGHT,
    val waterManualTempC: Int = 25,
    val weatherSource: String = WeatherRepository.SOURCE_MANUAL,
    val weatherOmCity: OmCity? = null,
    val weatherOmHighC: Int? = null,
    val weatherOmUpdatedAtMillis: Long? = null,
    val waterUseProfileActivity: Boolean = true,
    val waterFoodWaterEnabled: Boolean = false,
    val waterAwakeStartMinutes: Int = 8 * 60,
    val waterAwakeEndMinutes: Int = 21 * 60,
    val waterCupSizeMl: Int = WaterGoalCalculator.DEFAULT_CUP_SIZE_ML,
    /** Today's computed dynamic goal + breakdown; null while the feature is off. */
    val waterDynamicGoalPreview: WaterGoalBreakdown? = null,
    val goalReachedNotificationsEnabled: Boolean = true,
    val appUpdateNotificationsEnabled: Boolean = true,
    val healthConnectEnabled: Boolean = false,
    val healthEnergyGoalsEnabled: Boolean = false,
    val healthBackgroundSyncEnabled: Boolean = false,
    val healthBackgroundReadAvailable: Boolean = false,
    val healthBackgroundReadGranted: Boolean = false,
    val adaptiveGoalsEnabled: Boolean = false,
    val applyingHealthEnergyGoals: Boolean = false,
    val applyingAdaptiveGoals: Boolean = false,
    val recalculatingGoals: Boolean = false,
    val healthEnergyGoalAlertTitle: String? = null,
    val healthEnergyGoalAlertMessage: String? = null,
    val adaptiveGoalAlertTitle: String? = null,
    val adaptiveGoalAlertMessage: String? = null,
    /** Settings → Other Nutrient Goals: in-flight flag for the opt-in "Estimate with AI" call. */
    val estimatingOptionalNutrientGoals: Boolean = false,
    /** Localized error for the "Estimate with AI" call; null = no alert shown. */
    val optionalNutrientEstimateAlertMessage: String? = null,
    val apiKeyMasked: String = "",
    val speechApiKeyMasked: String = "",
    /** Rollout gate + device capability — whether ON_DEVICE should appear as a selectable provider. */
    val onDeviceAvailable: Boolean = false,
    /** ON_DEVICE model ids this device can actually run (E4B gated by the 7 GiB usable-RAM floor). */
    val onDeviceModels: List<String> = emptyList(),
    val appearanceMode: String = "system",
    /** "" = system default, or locale tag like "de", "zh-CN". */
    val appLanguage: String = "",
    /** Codeberg #20 phase 1: show the coach tab in the bottom bar; default ON. */
    val coachTabEnabled: Boolean = true,
    /** Codeberg #20 phase 2: master AI-features switch; default ON. */
    val aiFeaturesEnabled: Boolean = true,
    /** Issue #8 follow-up: release cleartext opt-in for user-entered endpoints; default OFF. */
    val allowInsecureHttp: Boolean = false,
    val appThemeColor: AppThemeColor = AppThemeColor.SYSTEM,
    /** Opt-in: launcher icon stays the brand teal and never swaps aliases (#21). */
    val fixedLauncherIcon: Boolean = false,
    val foodLogSortOrder: FoodLogSortOrder = FoodLogSortOrder.STANDARD,
    val weekStartsOnMonday: Boolean = true,
    /** Settings default Progress range id (`1W`…`All`). */
    val progressDefaultRangeId: String = "1W",
    /** Body-measurement sites with a Progress-tab trend plot; empty = plots off. */
    val progressMeasurementSites: Set<String> = emptySet(),
    val userContext: String = "",
    val fallbackEnabled: Boolean = true,
    val fallbackProvider: AIProvider = AIProvider.GEMINI,
    val fallbackModel: String = AIProvider.GEMINI.defaultFallbackModel,
    val fallbackApiKeyMasked: String = "",
    val geminiGoogleSearchEnabled: Boolean = false,
    val openRouterReasoningEffort: OpenRouterReasoningEffort = OpenRouterReasoningEffort.AUTO,
    val portionClarifyEnabled: Boolean = false,
    val mealConstituentsEnabled: Boolean = true,
    /** Inverted in UI: “Ask for a photo note” = !skipPhotoNotePrompt. */
    val skipPhotoNotePrompt: Boolean = false,
    val optionalNutrientGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val homeDisplay: HomeDisplayPreferences = HomeDisplayPreferences(),
    val mealSchedule: app.chompass.models.MealSchedule = app.chompass.models.MealSchedule.Default,
    /** A goal-relevant input changed since the last Recalculate. Drives a soft nudge on the
     *  Recalculate row; the button stays tappable at all times — this never disables it. */
    val goalsNeedRecalc: Boolean = false,
    /** Dismissible hub suggestions toward beneficial-but-optional setups (§6.3 of the plan). */
    val suggestions: List<SettingsSuggestion> = emptyList(),
) {
    val heightMetric: Boolean get() = heightUnit == "cm"
    val weightMetric: Boolean get() = weightUnit == "kg"
}

/** One row of the Settings hub Suggestions card. */
data class SettingsSuggestion(
    val id: String,
    val title: String,
    val actionLabel: String,
    val targetRoute: String,
)

class SettingsViewModel(val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    /** Cached inputs for the Suggestions engine (see refreshSuggestions). */
    private var firstLaunchAt: Long = 0L
    private var webDavUrl: String = ""
    private var dismissedSuggestionIds: Set<String> = emptySet()

    /** Goal-input fingerprint captured at the last Recalculate (or seeded on first load). */
    private var lastRecalcSignature: String? = null

    /** True when [profile]'s goal inputs differ from the last-recalculated baseline. */
    private fun needsRecalc(profile: app.chompass.models.UserProfile?): Boolean =
        lastRecalcSignature != null && profile != null && lastRecalcSignature != profile.goalInputSignature

    init {
        viewModelScope.launch {
            container.prefs.optionalNutrientGoals.collect { goals ->
                _ui.value = _ui.value.copy(optionalNutrientGoals = goals)
            }
        }

        viewModelScope.launch {
            container.prefs.heuristicServingUnitSettings.collect { settings ->
                _ui.value = _ui.value.copy(heuristicServingUnitSettings = settings)
            }
        }

        viewModelScope.launch {
            val snap = container.prefs.readSettingsHydration()
            val provider = snap.selectedAI
            val model = provider.supportedModelOrDefault(snap.selectedModelRaw)
            val vision = snap.visionModelRaw
                ?.takeIf { it.isNotBlank() }
                ?.let { provider.supportedModelOrDefault(it) }
                .orEmpty()
            val speech = snap.selectedSpeech
            val weather = container.weatherRepository.state.first()
            val hc = reconcileHealthConnectState()
            val profile = container.profileRepository.current()
            val energyGoals = snap.healthEnergyGoalsEnabled && hc
            var backgroundSync = snap.healthBackgroundSyncEnabled && hc
            if (backgroundSync &&
                !(container.health.isBackgroundReadAvailable() && container.health.hasBackgroundRead())
            ) {
                container.prefs.setHealthBackgroundSyncEnabled(false)
                HealthSyncWorker.cancel(container.appContext)
                backgroundSync = false
            }
            val onDeviceAvailable = snap.onDeviceFeatureVisible &&
                OnDeviceCapability.isSupported(container.appContext)
            val onDeviceModels = if (provider == AIProvider.ON_DEVICE && onDeviceAvailable) {
                // E4B is the OOM-risk model (GPU+GPU vision killed the process on
                // Pixel 9a); 6 GB devices get E2B only. Static ON_DEVICE.models stays
                // complete so supportedModelOrDefault still resolves a persisted E4B.
                AIProvider.ON_DEVICE.models.filter { modelId ->
                    OnDeviceCapability.isModelSupported(container.appContext, ModelCatalog.forModelId(modelId))
                }
            } else {
                emptyList()
            }
            val effectiveModel = if (model !in onDeviceModels && onDeviceModels.isNotEmpty()) onDeviceModels.first() else model
            val masked = maskKey(container.keyStore.apiKey(provider))
            val speechMasked = maskKey(container.keyStore.speechApiKey(speech))
            val fbProvider = snap.fallbackProvider
            val fbModel = fbProvider.supportedFallbackModelOrDefault(snap.fallbackModelRaw)
            val fbMasked = maskKey(container.keyStore.fallbackApiKey(fbProvider))
            lastRecalcSignature = snap.lastRecalcGoalSignature ?: profile?.goalInputSignature
            if (snap.lastRecalcGoalSignature == null && profile != null) {
                container.prefs.setLastRecalcGoalSignature(profile.goalInputSignature)
            }
            _ui.value = SettingsUiState(
                selectedAI = provider,
                selectedModel = effectiveModel,
                visionModel = vision,
                maxResponseTokens = snap.maxResponseTokens,
                aiReadTimeoutSeconds = snap.aiReadTimeoutSeconds,
                servingUnitInferenceMode = snap.servingUnitInferenceMode,
                heuristicServingUnitSettings = snap.heuristicServingUnitSettings,
                selectedSpeech = speech,
                selectedSpeechLanguage = snap.selectedSpeechLanguage,
                heightUnit = snap.heightUnit,
                weightUnit = snap.weightUnit,
                preferGramsByDefault = snap.preferGramsByDefault,
                profile = profile,
                notificationsEnabled = snap.notificationsEnabled,
                streakReminderEnabled = snap.streakReminderEnabled,
                dailySummaryEnabled = snap.dailySummaryEnabled,
                dailySummaryHour = snap.dailySummaryHour,
                dailySummaryMinute = snap.dailySummaryMinute,
                weightReminderEnabled = snap.weightReminderEnabled,
                bodyFatReminderEnabled = snap.bodyFatReminderEnabled,
                waterTrackingEnabled = snap.waterTrackingEnabled,
                waterDailyGoalMl = snap.waterDailyGoalMl,
                waterQuickPresetsMl = snap.waterQuickPresetsMl,
                waterReminderEnabled = snap.waterReminderEnabled,
                waterDynamicEnabled = snap.waterDynamicEnabled,
                waterBaseSource = snap.waterBaseSource,
                waterManualTempC = snap.waterManualTempC,
                weatherSource = weather.source,
                weatherOmCity = weather.omCity,
                weatherOmHighC = weather.omHighC,
                weatherOmUpdatedAtMillis = weather.omUpdatedAtMillis,
                waterUseProfileActivity = snap.waterUseProfileActivity,
                waterFoodWaterEnabled = snap.waterFoodWaterEnabled,
                waterAwakeStartMinutes = snap.waterAwakeStartMinutes,
                waterAwakeEndMinutes = snap.waterAwakeEndMinutes,
                waterCupSizeMl = snap.waterCupSizeMl,
                waterDynamicGoalPreview = if (snap.waterDynamicEnabled) computeWaterGoalPreview(
                    manualGoalMl = snap.waterDailyGoalMl,
                    source = snap.waterBaseSource,
                    tempC = snap.waterManualTempC,
                    useActivity = snap.waterUseProfileActivity,
                    foodWater = snap.waterFoodWaterEnabled,
                ) else null,
                goalReachedNotificationsEnabled = snap.goalReachedNotificationsEnabled,
                appUpdateNotificationsEnabled = snap.appUpdateNotificationsEnabled,
                healthConnectEnabled = hc,
                healthEnergyGoalsEnabled = energyGoals,
                healthBackgroundSyncEnabled = backgroundSync,
                healthBackgroundReadAvailable = container.health.isBackgroundReadAvailable(),
                healthBackgroundReadGranted = container.health.hasBackgroundRead(),
                adaptiveGoalsEnabled = snap.adaptiveGoalsEnabled,
                apiKeyMasked = masked,
                speechApiKeyMasked = speechMasked,
                onDeviceAvailable = onDeviceAvailable,
                onDeviceModels = onDeviceModels,
                appearanceMode = snap.appearanceMode,
                appLanguage = snap.appLanguage,
                coachTabEnabled = snap.coachTabEnabled,
                aiFeaturesEnabled = snap.aiFeaturesEnabled,
                allowInsecureHttp = snap.allowInsecureHttp,
                appThemeColor = AppThemeColor.fromKey(snap.appThemeColorKey),
                fixedLauncherIcon = snap.fixedLauncherIcon,
                foodLogSortOrder = FoodLogSortOrder.fromStorage(snap.foodLogSortOrderRaw),
                weekStartsOnMonday = snap.weekStartsOnMonday,
                progressDefaultRangeId = snap.progressDefaultRangeId,
                progressMeasurementSites = snap.progressMeasurementSites,
                userContext = snap.userContext,
                fallbackEnabled = snap.fallbackEnabled,
                fallbackProvider = fbProvider,
                fallbackModel = fbModel,
                fallbackApiKeyMasked = fbMasked,
                geminiGoogleSearchEnabled = snap.geminiGoogleSearchEnabled,
                openRouterReasoningEffort = snap.openRouterReasoningEffort,
                portionClarifyEnabled = snap.portionClarifyEnabled,
                mealConstituentsEnabled = snap.mealConstituentsEnabled,
                skipPhotoNotePrompt = snap.skipPhotoNotePrompt,
                optionalNutrientGoals = snap.optionalNutrientGoals,
                homeDisplay = snap.homeDisplay,
                mealSchedule = snap.mealSchedule,
                goalsNeedRecalc = needsRecalc(profile)
            )
        }

        container.prefs.homeDisplayPreferences
            .onEach { display -> _ui.value = _ui.value.copy(homeDisplay = display) }
            .launchIn(viewModelScope)

        // Weather input state (source, shared-app cache, Open-Meteo cache).
        // Single reactive collector keeps the water preview + status rows live
        // when a weather app broadcasts or Open-Meteo refreshes.
        viewModelScope.launch {
            container.weatherRepository.state.collect { weather ->
                _ui.update {
                    it.copy(
                        weatherSource = weather.source,
                        weatherOmCity = weather.omCity,
                        weatherOmHighC = weather.omHighC,
                        weatherOmUpdatedAtMillis = weather.omUpdatedAtMillis,
                    )
                }
                refreshWaterDynamicPreview()
            }
        }

        // Suggestions engine: re-derive whenever the ui state or its inputs change.
        viewModelScope.launch {
            _ui.collect { refreshSuggestions() }
        }
        viewModelScope.launch {
            container.prefs.firstLaunchAt.collect { firstLaunchAt = it; refreshSuggestions() }
        }
        viewModelScope.launch {
            container.prefs.webDavUrl.collect { webDavUrl = it; refreshSuggestions() }
        }
        viewModelScope.launch {
            container.prefs.dismissedSuggestionIds.collect {
                dismissedSuggestionIds = it
                refreshSuggestions()
            }
        }
    }

    /**
     * Derives the hub Suggestions list (max 3, priority order). Each row is
     * dismissible and auto-hides once its condition resolves. Gated by install
     * age so fresh users are never nagged; nothing is enabled here — rows only
     * navigate to the owning screen where the user taps the real toggle.
     */
    private fun refreshSuggestions() {
        val state = _ui.value
        val ageDays = if (firstLaunchAt > 0L) {
            (System.currentTimeMillis() - firstLaunchAt) / MILLIS_PER_DAY
        } else {
            Long.MAX_VALUE
        }
        val appContext = container.appContext
        fun suggest(
            id: String,
            titleRes: Int,
            actionRes: Int,
            route: String,
            show: Boolean,
        ): SettingsSuggestion? {
            if (!show || id in dismissedSuggestionIds) return null
            return SettingsSuggestion(
                id = id,
                title = appContext.getString(titleRes),
                actionLabel = appContext.getString(actionRes),
                targetRoute = route,
            )
        }
        _ui.value = state.copy(
            suggestions = listOfNotNull(
                suggest(
                    id = "water_reminders",
                    titleRes = R.string.settings_suggestion_water_reminders,
                    actionRes = R.string.settings_suggestion_action_setup,
                    route = ChompassRoutes.waterRoute("app"),
                    show = state.waterTrackingEnabled && !state.waterReminderEnabled,
                ),
                suggest(
                    id = "water_tracking",
                    titleRes = R.string.settings_suggestion_water_tracking,
                    actionRes = R.string.settings_suggestion_action_turn_on,
                    route = ChompassRoutes.waterRoute("app"),
                    show = !state.waterTrackingEnabled && ageDays >= SUGGEST_WATER_TRACKING_DAYS,
                ),
                suggest(
                    id = "adaptive_goals",
                    titleRes = R.string.settings_suggestion_adaptive_goals,
                    actionRes = R.string.settings_suggestion_action_turn_on,
                    route = ChompassRoutes.SETTINGS_GOALS,
                    show = !state.adaptiveGoalsEnabled && ageDays >= SUGGEST_OPTIMIZATION_DAYS &&
                        state.profile != null,
                ),
                suggest(
                    id = "health_connect",
                    titleRes = R.string.settings_suggestion_health_connect,
                    actionRes = R.string.settings_suggestion_action_connect,
                    route = ChompassRoutes.SETTINGS_DATA,
                    show = !state.healthConnectEnabled && ageDays >= SUGGEST_OPTIMIZATION_DAYS &&
                        state.profile != null,
                ),
                suggest(
                    id = "notifications",
                    titleRes = R.string.settings_suggestion_notifications,
                    actionRes = R.string.settings_suggestion_action_turn_on,
                    route = ChompassRoutes.notificationsRoute("app"),
                    show = !state.notificationsEnabled && ageDays >= SUGGEST_OPTIMIZATION_DAYS,
                ),
                suggest(
                    id = "backup",
                    titleRes = R.string.settings_suggestion_backup,
                    actionRes = R.string.settings_suggestion_action_setup,
                    route = ChompassRoutes.syncRoute("data"),
                    show = webDavUrl.isBlank() && ageDays >= SUGGEST_BACKUP_DAYS,
                ),
            ).take(MAX_SUGGESTIONS),
        )
    }

    fun dismissSuggestion(id: String) = viewModelScope.launch {
        container.prefs.setSuggestionDismissed(id)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val SUGGEST_WATER_TRACKING_DAYS = 3L
        const val SUGGEST_OPTIMIZATION_DAYS = 7L
        const val SUGGEST_BACKUP_DAYS = 14L
        const val MAX_SUGGESTIONS = 3
    }

    fun setHomeNutrientCardCount(count: Int) = launchPref {
        container.prefs.setHomeNutrientCardCount(count)
        val nutrients = container.prefs.homeTopNutrients.first()
        container.prefs.setHomeTopNutrients(
            HomeTopNutrient.toStorage(HomeTopNutrient.fromStorage(nutrients, count), count)
        )
    }

    fun setHomeTopNutrients(selection: List<HomeTopNutrient>) = launchPref {
        val cardCount = container.prefs.homeNutrientCardCount.first()
        container.prefs.setHomeTopNutrients(HomeTopNutrient.toStorage(selection, cardCount))
    }

    fun setHomeShowSteps(enabled: Boolean) = launchPref {
        container.prefs.setHomeShowSteps(enabled)
    }

    fun setHomeShowActiveCalories(enabled: Boolean) = launchPref {
        container.prefs.setHomeShowActiveCalories(enabled)
    }

    fun setHomeStepGoal(goal: Int) = launchPref {
        container.prefs.setHomeStepGoal(goal)
    }

    fun setHomeCalorieDisplayMode(mode: HomeCalorieDisplayMode) = launchPref {
        container.prefs.setHomeCalorieDisplayMode(mode.storageKey)
    }

    fun setFoodLogMacroChips(chips: List<FoodLogMacroChip>) = launchPref {
        container.prefs.setFoodLogMacroChips(FoodLogMacroChip.toStorage(chips))
    }

    fun setOptionalNutrientGoals(goals: OptionalNutrientGoals) = updateUiPref(
        { container.prefs.setOptionalNutrientGoals(goals) },
        { copy(optionalNutrientGoals = goals) },
    )

    /**
     * Settings → Other Nutrient Goals → "Estimate with AI": one user-initiated
     * Gemini round-trip (the same prompt the old Recalculate auto-call used).
     * Runs only when tapped, never in the background and never from Recalculate,
     * so manually set optional goals stay durable. Replaces every optional goal
     * on success; on failure the existing values are left untouched.
     */
    fun estimateOptionalNutrientGoals() {
        viewModelScope.launch {
            if (_ui.value.estimatingOptionalNutrientGoals) return@launch
            _ui.value = _ui.value.copy(estimatingOptionalNutrientGoals = true)
            val profile = container.profileRepository.current()
            val result = try {
                container.foodAnalysis.estimateOptionalNutrientGoals(profile)
            } catch (e: AiError) {
                Log.e("Chompass", "estimateOptionalNutrientGoals failed", e)
                _ui.value = _ui.value.copy(
                    estimatingOptionalNutrientGoals = false,
                    optionalNutrientEstimateAlertMessage = e.userMessage(container.appContext)
                )
                return@launch
            } catch (e: Throwable) {
                Log.e("Chompass", "estimateOptionalNutrientGoals failed", e)
                _ui.value = _ui.value.copy(
                    estimatingOptionalNutrientGoals = false,
                    optionalNutrientEstimateAlertMessage = e.localizedMessage
                        ?: container.appContext.getString(R.string.ai_error_provider_error)
                )
                return@launch
            }
            container.prefs.setOptionalNutrientGoals(result)
            _ui.value = _ui.value.copy(
                estimatingOptionalNutrientGoals = false,
                optionalNutrientGoals = result
            )
        }
    }

    fun dismissOptionalNutrientEstimateAlert() {
        _ui.value = _ui.value.copy(optionalNutrientEstimateAlertMessage = null)
    }

    fun setUserContext(value: String) = updateUiPref(
        { container.prefs.setUserContext(value) },
        { copy(userContext = value.trim()) },
    )

    fun setMaxResponseTokens(v: Int) {
        val clamped = app.chompass.data.clampMaxResponseTokens(v)
        updateUiPref(
            { container.prefs.setMaxResponseTokens(clamped) },
            { copy(maxResponseTokens = clamped) },
        )
    }

    fun setAiReadTimeoutSeconds(v: Int) {
        val clamped = app.chompass.data.clampAiReadTimeoutSeconds(v)
        updateUiPref(
            { container.prefs.setAiReadTimeoutSeconds(clamped) },
            { copy(aiReadTimeoutSeconds = clamped) },
        )
    }

    fun setGeminiGoogleSearchEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setGeminiGoogleSearchEnabled(v) },
        { copy(geminiGoogleSearchEnabled = v) },
    )

    fun setOpenRouterReasoningEffort(e: OpenRouterReasoningEffort) = updateUiPref(
        { container.prefs.setOpenRouterReasoningEffort(e) },
        { copy(openRouterReasoningEffort = e) },
    )

    fun setPortionClarifyEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setPortionClarifyEnabled(v) },
        { copy(portionClarifyEnabled = v) },
    )

    fun setAskPhotoNotePrompt(ask: Boolean) = updateUiPref(
        {
            container.prefs.setSkipPhotoNotePrompt(!ask)
            if (ask) container.prefs.setPhotoNoteSkipCount(0)
        },
        { copy(skipPhotoNotePrompt = !ask) },
    )

    fun setMealConstituentsEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setMealConstituentsEnabled(v) },
        { copy(mealConstituentsEnabled = v) },
    )

    fun setServingUnitInferenceMode(mode: ServingUnitInferenceMode) = updateUiPref(
        { container.prefs.setServingUnitInferenceMode(mode) },
        { copy(servingUnitInferenceMode = mode) },
    )

    fun setHeuristicRuleEnabled(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = _ui.value.heuristicServingUnitSettings
            val existing = current.overrides[ruleId] ?: HeuristicRuleOverride()
            val updated = current.copy(overrides = current.overrides + (ruleId to existing.copy(enabled = enabled)))
            container.prefs.setHeuristicServingUnitSettings(updated)
        }
    }

    /** [gramsPerUnit] null (or blank in the UI) resets the rule back to its built-in default. */
    fun setHeuristicRuleGramsPerUnit(ruleId: String, gramsPerUnit: Double?) {
        viewModelScope.launch {
            val current = _ui.value.heuristicServingUnitSettings
            val existing = current.overrides[ruleId] ?: HeuristicRuleOverride()
            val updated = current.copy(
                overrides = current.overrides + (ruleId to existing.copy(gramsPerUnit = gramsPerUnit))
            )
            container.prefs.setHeuristicServingUnitSettings(updated)
        }
    }

    fun setFallbackEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setFallbackEnabled(v)
            if (v && container.prefs.selectedFallbackModel.first().isNullOrBlank()) {
                val provider = container.prefs.selectedFallbackProvider.first()
                val model = provider.defaultFallbackModel
                container.prefs.setSelectedFallbackModel(model)
                _ui.value = _ui.value.copy(fallbackEnabled = v, fallbackModel = model)
            } else {
                _ui.value = _ui.value.copy(fallbackEnabled = v)
            }
        }
    }

    fun selectFallbackProvider(p: AIProvider) {
        viewModelScope.launch {
            container.prefs.setSelectedFallbackProvider(p)
            // Reset model to provider default if old model isn't in the new provider's list.
            val current = _ui.value.fallbackModel
            val newModel = if (p == AIProvider.ON_DEVICE) {
                // On-device fallback defaults to the catalog's canonical model
                // (mirrors the primary picker's defaultModel).
                ModelCatalog.default.modelId
            } else {
                p.supportedFallbackModelOrDefault(current)
            }
            container.prefs.setSelectedFallbackModel(newModel)
            val masked = maskKey(container.keyStore.fallbackApiKey(p))
            _ui.value = _ui.value.copy(fallbackProvider = p, fallbackModel = newModel, fallbackApiKeyMasked = masked)
        }
    }

    fun selectFallbackModel(m: String) {
        viewModelScope.launch {
            val model = _ui.value.fallbackProvider.supportedFallbackModelOrDefault(m)
            container.prefs.setSelectedFallbackModel(model)
            _ui.value = _ui.value.copy(fallbackModel = model)
        }
    }

    fun setFallbackApiKey(raw: String) {
        viewModelScope.launch {
            val p = _ui.value.fallbackProvider
            val trimmed = raw.trim().takeIf { it.isNotEmpty() }
            container.keyStore.setFallbackApiKey(p, trimmed)
            _ui.value = _ui.value.copy(fallbackApiKeyMasked = maskKey(trimmed))
        }
    }

    fun setAppearanceMode(mode: String) = updateUiPref(
        { container.prefs.setAppearanceMode(mode) },
        { copy(appearanceMode = mode) },
    )

    fun setAppLanguage(languageTag: String) = updateUiPref(
        { container.prefs.setAppLanguage(languageTag) },
        { copy(appLanguage = languageTag) },
    )

    fun setCoachTabEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setCoachTabEnabled(v) },
        { copy(coachTabEnabled = v) },
    )

    /** Codeberg #20 phase 2: master AI-features switch. Off = no data to any LLM provider. */
    fun setAiFeaturesEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setAiFeaturesEnabled(v) },
        { copy(aiFeaturesEnabled = v) },
    )

    /** Issue #8 follow-up: allow http:// URLs for user-entered endpoints in release builds. */
    fun setAllowInsecureHttp(v: Boolean) = updateUiPref(
        { container.prefs.setAllowInsecureHttp(v) },
        { copy(allowInsecureHttp = v) },
    )

    fun setAppThemeColor(themeColor: AppThemeColor) = updateUiPref(
        { container.prefs.setAppThemeColor(themeColor.key) },
        { copy(appThemeColor = themeColor) },
    )

    fun setFixedLauncherIcon(enabled: Boolean) = updateUiPref(
        { container.prefs.setFixedLauncherIcon(enabled) },
        { copy(fixedLauncherIcon = enabled) },
    )

    fun setWeekStartsOnMonday(monday: Boolean) = updateUiPref(
        { container.prefs.setWeekStartsOnMonday(monday) },
        { copy(weekStartsOnMonday = monday) },
    )

    fun setProgressDefaultRangeId(rangeId: String) = updateUiPref(
        { container.prefs.setProgressDefaultRangeId(rangeId) },
        { copy(progressDefaultRangeId = rangeId) },
    )

    fun setProgressMeasurementSites(sites: Set<String>) = updateUiPref(
        { container.prefs.setProgressMeasurementSites(sites) },
        { copy(progressMeasurementSites = sites) },
    )

    fun setMealSchedule(schedule: app.chompass.models.MealSchedule) {
        val validated = schedule.validatedOrDefault()
        updateUiPref(
            { container.prefs.setMealSchedule(validated) },
            { copy(mealSchedule = validated) },
        )
    }

    fun setFoodLogSortOrder(order: FoodLogSortOrder) = updateUiPref(
        { container.prefs.setFoodLogSortOrder(order.storageValue) },
        { copy(foodLogSortOrder = order) },
    )

    fun selectProvider(p: AIProvider) {
        viewModelScope.launch {
            container.prefs.setSelectedAIProvider(p)
            container.prefs.setSelectedAIModel(p.defaultModel)
            val masked = maskKey(container.keyStore.apiKey(p))
            val vision = container.prefs.visionModel(p).first()
                ?.takeIf { it.isNotBlank() }
                ?.let { p.supportedModelOrDefault(it) }
                .orEmpty()
            _ui.value = _ui.value.copy(selectedAI = p, selectedModel = p.defaultModel, apiKeyMasked = masked, visionModel = vision)
        }
    }

    /** Frees the resident on-device engine (~1-2GB) — Settings "Unload model" action. */
    fun unloadOnDeviceModel() {
        viewModelScope.launch { container.onDeviceLlmGateway.unload() }
    }

    fun deleteOnDeviceModel() {
        viewModelScope.launch {
            val entry = ModelCatalog.forModelId(_ui.value.selectedModel)
            container.onDeviceLlmGateway.unload()
            container.onDeviceModelDownloadManager.delete(entry)
            val stillDownloaded = ModelCatalog.entries.any { container.onDeviceModelDownloadManager.isDownloaded(it) }
            if (!stillDownloaded) {
                container.prefs.setOnDeviceModelDownloadedVersion(null)
            }
        }
    }

    fun startOnDeviceModelDownload() {
        viewModelScope.launch {
            // Codeberg #20 phase 2: with the master AI switch off, don't even
            // schedule the model download (the worker skips it as a backstop).
            if (container.prefs.aiFeaturesEnabled.first() == false) return@launch
            val entry = ModelCatalog.forModelId(_ui.value.selectedModel)
            val overWifiOnly = container.prefs.onDeviceDownloadOverWifiOnly.first()
            container.onDeviceModelDownloadManager.startDownload(entry, overWifiOnly)
        }
    }

    fun cancelOnDeviceModelDownload() {
        container.onDeviceModelDownloadManager.cancelDownload()
    }

    fun setOnDeviceDownloadOverWifiOnly(v: Boolean) {
        viewModelScope.launch { container.prefs.setOnDeviceDownloadOverWifiOnly(v) }
    }

    fun selectModel(m: String) {
        viewModelScope.launch {
            val prev = _ui.value.selectedModel
            val model = _ui.value.selectedAI.supportedModelOrDefault(m)
            container.prefs.setSelectedAIModel(model)
            if (_ui.value.selectedAI == AIProvider.ON_DEVICE && prev != model) {
                container.onDeviceLlmGateway.unload()
            }
            _ui.value = _ui.value.copy(selectedModel = model)
        }
    }

    /** Sets the per-provider vision-model slot; null/blank clears it back to "same as Model". */
    fun selectVisionModel(m: String?) {
        viewModelScope.launch {
            val provider = _ui.value.selectedAI
            val model = m?.takeIf { it.isNotBlank() }?.let { provider.supportedModelOrDefault(it) }
            container.prefs.setVisionModel(provider, model)
            _ui.value = _ui.value.copy(visionModel = model.orEmpty())
        }
    }

    fun setApiKey(raw: String) {
        viewModelScope.launch {
            val p = _ui.value.selectedAI
            val trimmed = raw.trim().takeIf { it.isNotEmpty() }
            container.keyStore.setApiKey(p, trimmed)
            _ui.value = _ui.value.copy(apiKeyMasked = maskKey(trimmed))
        }
    }

    fun selectSpeech(p: SpeechProvider) {
        viewModelScope.launch {
            container.prefs.setSelectedSpeechProvider(p)
            // Re-pull the masked key for the new provider so the API Key row
            // reflects whether the freshly selected provider has a key saved.
            val masked = maskKey(container.keyStore.speechApiKey(p))
            val language = container.prefs.selectedSpeechLanguage(p).first()
            _ui.value = _ui.value.copy(
                selectedSpeech = p,
                selectedSpeechLanguage = language,
                speechApiKeyMasked = masked
            )
        }
    }

    fun selectSpeechLanguage(language: SpeechLanguage) {
        viewModelScope.launch {
            val provider = _ui.value.selectedSpeech
            container.prefs.setSelectedSpeechLanguage(provider, language)
            _ui.value = _ui.value.copy(selectedSpeechLanguage = language)
        }
    }

    fun setSpeechApiKey(raw: String) {
        viewModelScope.launch {
            val p = _ui.value.selectedSpeech
            val trimmed = raw.trim().takeIf { it.isNotEmpty() }
            container.keyStore.setSpeechApiKey(p, trimmed)
            _ui.value = _ui.value.copy(speechApiKeyMasked = maskKey(trimmed))
        }
    }

    fun setHeightUnit(v: String) = updateUiPref(
        { container.prefs.setHeightUnit(v) },
        { copy(heightUnit = v) },
    )

    fun setWeightUnit(v: String) = updateUiPref(
        { container.prefs.setWeightUnit(v) },
        { copy(weightUnit = v) },
    )

    fun setDietMode(mode: DietMode) {
        updateProfile { it.copy(dietMode = mode) }
    }

    fun setKetoCarbMode(mode: KetoCarbMode) {
        updateProfile { it.copy(ketoCarbMode = mode) }
    }

    fun setKetoCarbManualTarget(target: Int?) {
        updateProfile {
            it.copy(ketoCarbManualTarget = target?.let(KetoCarbRecommendationService::clampManualNetCarbs))
        }
    }

    fun setPreferGramsByDefault(v: Boolean) = updateUiPref(
        { container.prefs.setPreferGramsByDefault(v) },
        { copy(preferGramsByDefault = v) },
    )

    fun setNotificationsEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setNotificationsEnabled(v)
            syncNotificationSchedules()
        },
        { copy(notificationsEnabled = v) },
    )

    fun setStreakReminderEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setStreakReminderEnabled(v)
            syncNotificationSchedules()
        },
        { copy(streakReminderEnabled = v) },
    )

    fun setDailySummaryEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setDailySummaryEnabled(v)
            syncNotificationSchedules()
        },
        { copy(dailySummaryEnabled = v) },
    )

    fun setDailySummaryTime(hour: Int, minute: Int) = updateUiPref(
        {
            container.prefs.setDailySummaryHour(hour)
            container.prefs.setDailySummaryMinute(minute)
            syncNotificationSchedules()
        },
        { copy(dailySummaryHour = hour, dailySummaryMinute = minute) },
    )

    fun setWeightReminderEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setWeightReminderEnabled(v)
            syncNotificationSchedules()
        },
        { copy(weightReminderEnabled = v) },
    )

    fun setBodyFatReminderEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setBodyFatReminderEnabled(v)
            syncNotificationSchedules()
        },
        { copy(bodyFatReminderEnabled = v) },
    )

    fun setGoalReachedNotificationsEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setGoalReachedNotificationsEnabled(v) },
        { copy(goalReachedNotificationsEnabled = v) },
    )

    fun setAppUpdateNotificationsEnabled(v: Boolean) = updateUiPref(
        { container.prefs.setAppUpdateNotificationsEnabled(v) },
        { copy(appUpdateNotificationsEnabled = v) },
    )

    private suspend fun syncNotificationSchedules() {
        val enabled = container.prefs.notificationsEnabled.first()
        if (!enabled || !container.notifications.canPostNotifications()) {
            container.notifications.cancelStreakReminder()
            container.notifications.cancelDailySummary()
            container.notifications.cancelWeightReminder()
            container.notifications.cancelBodyFatReminder()
            container.notifications.cancelWaterReminder()
            return
        }

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

        val profile = container.profileRepository.current()
        if (container.prefs.bodyFatReminderEnabled.first() && profile?.bodyFatPercentage != null) {
            container.notifications.scheduleBodyFatReminder()
        } else {
            container.notifications.cancelBodyFatReminder()
        }
        // Water uses the adaptive chain (interval from goal ÷ cup ÷ awake window,
        // recomputed after every entry, issue #3). rearm cancels when off.
        WaterReminderPlanner.rearm(container)
    }

    fun setWaterTrackingEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setWaterTrackingEnabled(v)
            if (!v) {
                container.prefs.setWaterReminderEnabled(false)
                container.notifications.cancelWaterReminder()
            }
        },
        { copy(waterTrackingEnabled = v, waterReminderEnabled = if (v) waterReminderEnabled else false) },
    )

    fun setWaterDailyGoalMl(v: Int) = updateUiPref(
        {
            container.prefs.setWaterDailyGoalMl(v)
            refreshWaterDynamicPreview()
        },
        { copy(waterDailyGoalMl = v) },
    )

    fun setWaterQuickPresetsMl(amountsMl: List<Int>) {
        val validated = WaterQuickPresets(amountsMl).validatedOrDefault().amountsMl
        updateUiPref(
            { container.prefs.setWaterQuickPresetsMl(validated) },
            { copy(waterQuickPresetsMl = validated) },
        )
    }

    fun setWaterReminderEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setWaterReminderEnabled(v)
            syncNotificationSchedules()
        },
        { copy(waterReminderEnabled = v) },
    )

    fun setWaterDynamicEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setWaterDynamicEnabled(v)
            refreshWaterDynamicPreview()
        },
        { copy(waterDynamicEnabled = v) },
    )

    fun setWaterBaseSource(v: String) = updateUiPref(
        {
            container.prefs.setWaterBaseSource(v)
            refreshWaterDynamicPreview()
        },
        { copy(waterBaseSource = v) },
    )

    fun setWaterManualTempC(v: Int) = updateUiPref(
        {
            container.prefs.setWaterManualTempC(v)
            refreshWaterDynamicPreview()
        },
        { copy(waterManualTempC = v) },
    )

    fun setWeatherSource(v: String) {
        updateUiPref(
            {
                container.weatherRepository.setSource(v)
                syncNotificationSchedules()
                refreshWaterDynamicPreview()
            },
            { copy(weatherSource = v) },
        )
    }

    /** Picks an Open-Meteo city and fetches today's high immediately. */
    fun selectWeatherCity(city: OmCity) {
        viewModelScope.launch {
            container.weatherRepository.selectOmCity(city)
            syncNotificationSchedules()
            refreshWaterDynamicPreview()
        }
    }

    /** Re-fetches today's high from Open-Meteo and re-arms the reminder chain. */
    fun refreshWeatherNow() {
        viewModelScope.launch {
            container.weatherRepository.refreshOpenMeteo()
            syncNotificationSchedules()
            refreshWaterDynamicPreview()
        }
    }

    suspend fun searchWeatherCities(query: String): List<OmCity> =
        container.openMeteo.searchCities(query)

    fun setWaterUseProfileActivity(v: Boolean) = updateUiPref(
        {
            container.prefs.setWaterUseProfileActivity(v)
            refreshWaterDynamicPreview()
        },
        { copy(waterUseProfileActivity = v) },
    )

    fun setWaterFoodWaterEnabled(v: Boolean) = updateUiPref(
        {
            container.prefs.setWaterFoodWaterEnabled(v)
            refreshWaterDynamicPreview()
        },
        { copy(waterFoodWaterEnabled = v) },
    )

    fun setWaterAwakeStartMinutes(v: Int) = updateUiPref(
        {
            container.prefs.setWaterAwakeStartHour(v / 60)
            container.prefs.setWaterAwakeStartMinute(v % 60)
            syncNotificationSchedules()
        },
        { copy(waterAwakeStartMinutes = v) },
    )

    fun setWaterAwakeEndMinutes(v: Int) = updateUiPref(
        {
            container.prefs.setWaterAwakeEndHour(v / 60)
            container.prefs.setWaterAwakeEndMinute(v % 60)
            syncNotificationSchedules()
        },
        { copy(waterAwakeEndMinutes = v) },
    )

    fun setWaterCupSizeMl(v: Int) = updateUiPref(
        {
            container.prefs.setWaterCupSizeMl(v)
            syncNotificationSchedules()
        },
        { copy(waterCupSizeMl = v) },
    )

    /** Recomputes the Settings preview of today's dynamic goal after any input change. */
    private suspend fun refreshWaterDynamicPreview() {
        if (!container.prefs.waterDynamicEnabled.first()) {
            _ui.value = _ui.value.copy(waterDynamicGoalPreview = null)
            return
        }
        _ui.value = _ui.value.copy(
            waterDynamicGoalPreview = computeWaterGoalPreview(
                manualGoalMl = container.prefs.waterDailyGoalMl.first(),
                source = container.prefs.waterBaseSource.first(),
                tempC = container.weatherRepository.state.first().effectiveHighC,
                useActivity = container.prefs.waterUseProfileActivity.first(),
                foodWater = container.prefs.waterFoodWaterEnabled.first(),
            ),
        )
    }

    private suspend fun computeWaterGoalPreview(
        manualGoalMl: Int,
        source: String,
        tempC: Int,
        useActivity: Boolean,
        foodWater: Boolean,
    ): WaterGoalBreakdown {
        val foodGrams = container.foodRepository.entriesForDate(LocalDate.now()).first()
            .let(WaterGoalCalculator::estimateDiaryGrams)
        val profile = container.profileRepository.current()
        return WaterGoalCalculator.breakdown(
            baseSource = source,
            weightKg = profile?.weightKg,
            manualBaseMl = manualGoalMl,
            expectedHighC = tempC,
            activityLevel = profile?.activityLevel ?: ActivityLevel.SEDENTARY,
            useProfileActivity = useActivity,
            foodGramsToday = foodGrams,
            foodWaterEnabled = foodWater,
        )
    }

    fun setHealthConnectEnabled(v: Boolean) {
        viewModelScope.launch {
            if (!v) {
                val restored = if (container.prefs.healthEnergyGoalsEnabled.first()) {
                    container.profileRepository.current()
                        ?.let { container.prefs.restoreHealthEnergyGoalPreviousTargets(it) }
                } else {
                    null
                }
                if (restored != null) {
                    container.profileRepository.save(restored)
                    container.prefs.clearHealthEnergyGoalPreviousTargets()
                }
                container.prefs.setHealthConnectEnabled(false)
                container.prefs.setHealthEnergyGoalsEnabled(false)
                container.prefs.clearHealthEnergyMeasuredActive()
                // Disconnecting Health Connect stops any background sync too.
                container.prefs.setHealthBackgroundSyncEnabled(false)
                HealthSyncWorker.cancel(container.appContext)
                _ui.value = _ui.value.copy(
                    profile = restored ?: _ui.value.profile,
                    healthConnectEnabled = false,
                    healthEnergyGoalsEnabled = false,
                    healthBackgroundSyncEnabled = false
                )
                return@launch
            }

            val enabled = container.health.isAvailable() && container.health.hasAnyPermission()
            container.prefs.setHealthConnectEnabled(enabled)
            if (enabled) {
                backfillHealthConnect()
                container.syncHealthConnectReads()
                container.prefs.setHealthPermissionsVersion(HealthConnectManager.CURRENT_TYPES_VERSION)
            }
            if (!enabled) {
                container.prefs.setHealthEnergyGoalsEnabled(false)
                container.prefs.clearHealthEnergyMeasuredActive()
            }
            _ui.value = _ui.value.copy(
                healthConnectEnabled = enabled,
                healthEnergyGoalsEnabled = if (enabled) _ui.value.healthEnergyGoalsEnabled else false
            )
        }
    }

    /** Opt-in periodic background sync. Requires Health Connect to already be
     *  connected and [HealthConnectManager.isBackgroundReadAvailable]; the Settings
     *  UI requests [HealthConnectManager.backgroundReadPermission] before calling this.
     *  Enabling schedules the worker, disabling cancels it. Default OFF. */
    fun setHealthBackgroundSyncEnabled(v: Boolean) {
        viewModelScope.launch {
            if (v && !container.prefs.healthConnectEnabled.first()) return@launch
            if (v && !container.health.isBackgroundReadAvailable()) return@launch
            if (v && !container.health.hasBackgroundRead()) return@launch
            container.prefs.setHealthBackgroundSyncEnabled(v)
            if (v) HealthSyncWorker.schedule(container.appContext)
            else HealthSyncWorker.cancel(container.appContext)
            _ui.value = _ui.value.copy(healthBackgroundSyncEnabled = v)
        }
    }

    private suspend fun reconcileHealthConnectState(): Boolean {
        if (!container.health.isAvailable()) {
            container.prefs.setHealthConnectEnabled(false)
            return false
        }

        val granted = container.health.hasAnyPermission()
        val stored = container.prefs.healthConnectEnabled.first()
        val version = container.prefs.healthPermissionsVersion.first()
        container.prefs.setHealthConnectEnabled(granted)
        if (!granted) {
            if (container.prefs.healthEnergyGoalsEnabled.first()) {
                container.profileRepository.current()?.let { current ->
                    val restored = container.prefs.restoreHealthEnergyGoalPreviousTargets(current)
                    container.profileRepository.save(restored)
                }
                container.prefs.clearHealthEnergyGoalPreviousTargets()
            }
            container.prefs.setHealthEnergyGoalsEnabled(false)
            container.prefs.clearHealthEnergyMeasuredActive()
            // Losing all permissions also stops background sync.
            container.prefs.setHealthBackgroundSyncEnabled(false)
            HealthSyncWorker.cancel(container.appContext)
        }

        // "Connected" is now any-permission, so revoking ONLY the energy reads leaves granted=true
        // and skips the block above. Tear Energy Burn down independently on its own capability so
        // the toggle doesn't lie about an anchor that can no longer refresh.
        if (granted && container.prefs.healthEnergyGoalsEnabled.first() && !container.health.hasEnergyRead()) {
            container.profileRepository.current()?.let { current ->
                val restored = container.prefs.restoreHealthEnergyGoalPreviousTargets(current)
                container.profileRepository.save(restored)
            }
            container.prefs.clearHealthEnergyGoalPreviousTargets()
            container.prefs.setHealthEnergyGoalsEnabled(false)
            container.prefs.clearHealthEnergyMeasuredActive()
        }

        if (granted && (!stored || version < HealthConnectManager.CURRENT_TYPES_VERSION)) {
            backfillHealthConnect()
            container.prefs.setHealthPermissionsVersion(HealthConnectManager.CURRENT_TYPES_VERSION)
        }

        // Pull external weigh-ins / body-fat readings whenever Settings reloads while connected.
        if (granted) container.syncHealthConnectReads()

        return granted
    }

    /**
     * Energy Burn toggle. It owns no targets — it just flips a flag that the goal calc consults:
     * when on, the calc anchors maintenance to the measured Health Connect burn instead of the
     * formula TDEE. Turning it on requires Health Connect with enough energy data. Either way we
     * re-run the calc so the new (or removed) anchor applies immediately.
     */
    fun setHealthEnergyGoalsEnabled(v: Boolean) {
        viewModelScope.launch {
            if (v) {
                val granted = container.health.isAvailable() && container.health.hasEnergyRead()
                if (!granted) {
                    showHealthEnergyGoalAlert(
                        title = container.appContext.getString(R.string.vm_health_connect_needed),
                        message = container.appContext.getString(R.string.vm_health_connect_needed_msg)
                    )
                    return@launch
                }
                container.prefs.setHealthConnectEnabled(true)
                container.prefs.setHealthPermissionsVersion(HealthConnectManager.CURRENT_TYPES_VERSION)
                if (container.health.readRecentEnergySummary(days = 14) == null) {
                    showHealthEnergyGoalAlert(
                        title = container.appContext.getString(R.string.vm_not_enough_energy),
                        message = container.appContext.getString(R.string.vm_not_enough_energy_msg)
                    )
                    return@launch
                }
            }
            container.prefs.setHealthEnergyGoalsEnabled(v)
            if (!v) container.prefs.clearHealthEnergyMeasuredActive()
            _ui.value = _ui.value.copy(
                healthEnergyGoalsEnabled = v,
                healthConnectEnabled = if (v) true else _ui.value.healthConnectEnabled
            )
            // Re-run the goal calc so the new (or removed) measured anchor takes effect now.
            recalculateGoals()
        }
    }

    fun dismissHealthEnergyGoalAlert() {
        _ui.value = _ui.value.copy(
            healthEnergyGoalAlertTitle = null,
            healthEnergyGoalAlertMessage = null
        )
    }

    fun setAdaptiveGoalsEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setAdaptiveGoalsEnabled(v)
            if (!v) {
                val current = container.profileRepository.current()
                val restored = current?.let { container.prefs.restoreAdaptiveGoalPreviousTargets(it) }
                if (restored != null) {
                    container.profileRepository.save(restored)
                }
                container.prefs.clearAdaptiveGoalPreviousTargets()
                _ui.value = _ui.value.copy(
                    profile = restored ?: _ui.value.profile,
                    adaptiveGoalsEnabled = false,
                    applyingAdaptiveGoals = false
                )
                return@launch
            }

            _ui.value = _ui.value.copy(
                adaptiveGoalsEnabled = true,
                applyingAdaptiveGoals = true
            )
            val result = container.refreshAdaptiveGoalsIfNeeded(force = true)
            _ui.value = _ui.value.copy(
                profile = result?.profile ?: container.profileRepository.current() ?: _ui.value.profile,
                adaptiveGoalsEnabled = true,
                applyingAdaptiveGoals = false,
                adaptiveGoalAlertTitle = container.appContext.getString(R.string.settings_adaptive_goals),
                adaptiveGoalAlertMessage = result?.message
                    ?: container.appContext.getString(R.string.vm_adaptive_on_message)
            )
        }
    }

    fun dismissAdaptiveGoalAlert() {
        _ui.value = _ui.value.copy(
            adaptiveGoalAlertTitle = null,
            adaptiveGoalAlertMessage = null
        )
    }

    private fun showHealthEnergyGoalAlert(title: String, message: String) {
        _ui.value = _ui.value.copy(
            healthEnergyGoalsEnabled = false,
            healthEnergyGoalAlertTitle = title,
            healthEnergyGoalAlertMessage = message
        )
    }

    /** Push existing local entries OUT to Health Connect. Each section is gated on its own
     *  WRITE permission, so a partial grant (e.g. weight-write only) still backfills what it can. */
    private suspend fun backfillHealthConnect() {
        val caps = container.health.capabilities()
        if (caps.nutritionWrite) {
            container.foodRepository.entries.first().forEach { entry ->
                container.health.updateNutrition(entry)
            }
        }
        if (caps.weightWrite) {
            container.weightRepository.entries.first().forEach { entry ->
                container.health.deleteWeight(entry.id)
                container.health.writeWeight(entry)
            }
        }
        if (caps.bodyFatWrite) {
            container.bodyFatRepository.entries.first().forEach { entry ->
                container.health.deleteBodyFat(entry.id)
                container.health.writeBodyFat(entry)
            }
        }
        if (caps.heightWrite) {
            container.profileRepository.current()?.heightCm?.let { container.health.writeHeight(it) }
        }
        if (caps.hydrationWrite) {
            // Water entries are immutable (no edit path), but insertRecords doesn't
            // dedupe on clientRecordId — delete-then-write keeps a re-connect from
            // stacking duplicates, same as the weight backfill.
            container.waterRepository.entries.first().forEach { entry ->
                container.health.deleteHydration(entry.id)
                container.health.writeHydration(entry)
            }
        }
    }

    fun deleteAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            container.prefs.clearAll()
            container.keyStore.clearAll()
            container.imageStore.clearAll()
            onComplete()
        }
    }

    fun clearFoodLog() {
        viewModelScope.launch {
            container.foodRepository.clear()
        }
    }

    fun recalculateGoals() {
        viewModelScope.launch {
            if (_ui.value.recalculatingGoals) return@launch
            val current = container.profileRepository.current() ?: return@launch
            _ui.value = _ui.value.copy(recalculatingGoals = true)
            val heightMetric = container.prefs.heightUnit.first() == "cm"
            val weightMetric = container.prefs.weightUnit.first() == "kg"
            // Empirical signal: recent logged intake + observed weight trend, so the AI can
            // estimate true maintenance (hit-and-trial) instead of trusting the formula alone.
            val forecast = WeightAnalysisService.compute(
                weights = container.weightRepository.entries.first(),
                foods = container.foodRepository.entries.first(),
                profile = current
            )
            // Energy Burn toggle: anchor maintenance to the user's measured Health Connect burn.
            val measuredTdee = container.measuredEnergyTdeeIfEnabled(current)
            // AI-only — no formula fallback. If the AI provider is unavailable, leave the
            // existing goals untouched and tell the user so they can fix their key and retry.
            Log.d("Chompass", "recalculateGoals: calling calculateGoals")
            val result = try {
                container.foodAnalysis.calculateGoals(current, forecast, heightMetric, weightMetric, measuredTdee, container.bodyMeasurementRepository.latestSnapshot())
            } catch (e: Throwable) {
                Log.e("Chompass", "recalculateGoals failed", e)
                _ui.value = _ui.value.copy(
                    recalculatingGoals = false,
                    adaptiveGoalAlertTitle = "Couldn't Recalculate",
                    adaptiveGoalAlertMessage = "Fud AI couldn't reach your AI provider, so your goals are unchanged. Check your AI provider and API key in Settings, then try again. (${e.localizedMessage ?: "no response"})"
                )
                return@launch
            }
            Log.d("Chompass", "recalculateGoals: got ${result.calories} kcal")
            // Write unlocked fields only. Locked calories/macros survive Recalculate.
            val next = current.applyingAiGoals(
                calories = result.calories,
                protein = result.protein,
                carbs = result.carbs,
                fat = result.fat,
            )
            val message = container.appContext.getString(R.string.vm_goals_updated, result.calories) +
                (result.reason?.let { " $it" } ?: "")
            container.profileRepository.save(next)
            lastRecalcSignature = next.goalInputSignature
            container.prefs.setLastRecalcGoalSignature(next.goalInputSignature)
            // Show the result now. Optional-nutrient AI is a second Gemini round-trip
            // (can 503 / sit for minutes) and must not hold the spinner or the dialog.
            _ui.value = _ui.value.copy(
                recalculatingGoals = false,
                profile = next,
                adaptiveGoalAlertTitle = container.appContext.getString(R.string.vm_goals_recalculated),
                adaptiveGoalAlertMessage = message,
                goalsNeedRecalc = false
            )
            // Optional-nutrient AI is a separate Gemini call. Do not run it here:
            // it kept the screen feeling busy after Recalculate had already finished.
        }
    }

    /**
     * Settings → Weight save: writes a WeightEntry (so the chart, Coach forecast,
     * and Health Connect sync see the change) and clears goalWeightKg if the new
     * current weight makes the goal direction impossible. Does NOT recompute calorie
     * or macro goals — those change only via Recalculate Goals (AI) or the weekly
     * Adaptive pass. Mirrors iOS ContentView.swift `case .editWeight`.
     */
    fun saveCurrentWeight(newKg: Double) {
        viewModelScope.launch {
            val current = container.profileRepository.current() ?: return@launch
            val gw = current.goalWeightKg
            val mismatch = gw != null && (
                (current.goal == WeightGoal.LOSE && gw >= newKg) ||
                (current.goal == WeightGoal.GAIN && gw <= newKg)
            )
            // WeightRepository.addEntry syncs profile.weightKg to the new value internally.
            container.weightRepository.addEntry(WeightEntry(weightKg = newKg))
            val refreshed = container.profileRepository.current() ?: return@launch
            val next = refreshed.copy(
                goalWeightKg = if (mismatch) null else refreshed.goalWeightKg
            )
            container.profileRepository.save(next)
            applyProfile(next)
        }
    }

    fun updateProfile(update: (app.chompass.models.UserProfile) -> app.chompass.models.UserProfile) {
        viewModelScope.launch {
            val current = container.profileRepository.current() ?: return@launch
            val next = update(current)
            container.profileRepository.save(next)
            applyProfile(next)
        }
    }

    /**
     * Mirrors a just-saved profile into [SettingsUiState], re-deriving the
     * recalculate nudge. Every path that persists a profile ends here, so the
     * nudge can never fall out of sync with what was written.
     */
    private fun applyProfile(next: app.chompass.models.UserProfile) {
        _ui.value = _ui.value.copy(profile = next, goalsNeedRecalc = needsRecalc(next))
    }

    /** Persist a preference then mirror it into [SettingsUiState]. */
    private inline fun updateUiPref(
        crossinline persist: suspend () -> Unit,
        crossinline reduce: SettingsUiState.() -> SettingsUiState,
    ) {
        viewModelScope.launch {
            persist()
            _ui.value = _ui.value.reduce()
        }
    }

    private inline fun launchPref(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /** Applies a calorie-goal edit: locked macros stay, unlocked macros rescale to the new total.
     *  Saving a value the user chose locks it (the lock icon / Reset button then releases it). */
    fun editCaloriesGoal(newCalories: Int) {
        updateProfile { it.applyCaloriesEdit(newCalories).copy(caloriesLocked = true) }
    }

    /** Applies a macro-goal edit through the rebalance engine, then locks the macro the user just
     *  set (honoring the max-2 cap — silently left unlocked if two macros are already locked).
     *  Invokes [onBlocked] and changes nothing when calories is locked and neither other macro can
     *  absorb the change (both locked). */
    fun editMacroGoal(macro: AutoBalanceMacro, newGrams: Int, onBlocked: () -> Unit) {
        viewModelScope.launch {
            val current = container.profileRepository.current() ?: return@launch
            val rebalanced = current.applyMacroEdit(macro, newGrams)
            if (rebalanced == null) {
                onBlocked()
                return@launch
            }
            val next = if (rebalanced.isMacroLocked(macro)) rebalanced else rebalanced.toggledMacroLock(macro)
            container.profileRepository.save(next)
            applyProfile(next)
        }
    }

    /** "Reset to Auto-balance" from the picker: release the macro's lock and re-derive it as the
     *  balancing remainder. */
    fun resetMacroLock(macro: AutoBalanceMacro) {
        updateProfile { it.resetMacroToBalance(macro) }
    }

    /** "Reset to Auto-balance" from the calories picker: release the calories lock and snap the
     *  total to the sum of the macros. */
    fun resetCaloriesLock() {
        updateProfile { it.resetCaloriesToBalance() }
    }

    fun setProteinTargetMode(mode: ProteinTargetMode) {
        updateProfile { it.withProteinTargetMode(mode) }
    }

    fun setCustomBaseUrl(provider: AIProvider, url: String) = launchPref {
        container.prefs.setCustomBaseUrl(provider, url.takeIf { it.isNotBlank() })
    }

    /** Fallback-slot base URL: stored under its own key so a fallback that reuses the
     *  primary provider (e.g. a second OpenAI-compatible endpoint) can't clobber the
     *  primary's URL — see customBaseURL_fallback_* in PreferencesStoreAi. */
    fun setFallbackCustomBaseUrl(provider: AIProvider, url: String) = launchPref {
        container.prefs.setFallbackCustomBaseUrl(provider, url.takeIf { it.isNotBlank() })
    }

    private fun maskKey(key: String?): String =
        if (key.isNullOrBlank()) "" else key.take(4) + "..." + key.takeLast(4)

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}
