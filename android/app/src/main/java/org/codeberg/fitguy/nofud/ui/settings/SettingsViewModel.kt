package org.codeberg.fitguy.nofud.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.AIProvider
import org.codeberg.fitguy.nofud.models.AutoBalanceMacro
import org.codeberg.fitguy.nofud.models.DietMode
import org.codeberg.fitguy.nofud.models.FoodLogMacroChip
import org.codeberg.fitguy.nofud.models.HeuristicRuleOverride
import org.codeberg.fitguy.nofud.models.HeuristicServingUnitSettings
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplay
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplayMode
import org.codeberg.fitguy.nofud.models.HomeDisplayPreferences
import org.codeberg.fitguy.nofud.models.HomeTopNutrient
import org.codeberg.fitguy.nofud.models.KetoCarbMode
import org.codeberg.fitguy.nofud.models.OptionalNutrientGoals
import org.codeberg.fitguy.nofud.models.ServingUnitInferenceMode
import org.codeberg.fitguy.nofud.models.SpeechLanguage
import org.codeberg.fitguy.nofud.models.SpeechProvider
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WaterQuickPresets
import org.codeberg.fitguy.nofud.models.WeightEntry
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.codeberg.fitguy.nofud.services.AndroidAppIconManager
import org.codeberg.fitguy.nofud.services.KetoCarbRecommendationService
import org.codeberg.fitguy.nofud.services.WeightAnalysisService
import org.codeberg.fitguy.nofud.services.health.HealthConnectManager
import org.codeberg.fitguy.nofud.services.health.HealthSyncWorker
import org.codeberg.fitguy.nofud.ui.home.FoodLogSortOrder
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val selectedAI: AIProvider = AIProvider.GEMINI,
    val selectedModel: String = AIProvider.GEMINI.defaultModel,
    val maxResponseTokens: Int = 1024,
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
    val weightReminderEnabled: Boolean = true,
    val bodyFatReminderEnabled: Boolean = true,
    val waterTrackingEnabled: Boolean = false,
    val waterDailyGoalMl: Int = 2_000,
    val waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    val waterReminderEnabled: Boolean = false,
    val goalReachedNotificationsEnabled: Boolean = true,
    val appUpdateNotificationsEnabled: Boolean = true,
    val healthConnectEnabled: Boolean = false,
    val healthEnergyGoalsEnabled: Boolean = false,
    val healthBackgroundSyncEnabled: Boolean = false,
    val adaptiveGoalsEnabled: Boolean = false,
    val applyingHealthEnergyGoals: Boolean = false,
    val applyingAdaptiveGoals: Boolean = false,
    val recalculatingGoals: Boolean = false,
    val healthEnergyGoalAlertTitle: String? = null,
    val healthEnergyGoalAlertMessage: String? = null,
    val adaptiveGoalAlertTitle: String? = null,
    val adaptiveGoalAlertMessage: String? = null,
    val apiKeyMasked: String = "",
    val speechApiKeyMasked: String = "",
    val appearanceMode: String = "system",
    val appThemeColor: AppThemeColor = AppThemeColor.SYSTEM,
    val glassBlurEnabled: Boolean = false,
    val foodLogSortOrder: FoodLogSortOrder = FoodLogSortOrder.STANDARD,
    val weekStartsOnMonday: Boolean = true,
    val userContext: String = "",
    val fallbackEnabled: Boolean = false,
    val fallbackProvider: AIProvider = AIProvider.GEMINI,
    val fallbackModel: String = AIProvider.GEMINI.defaultFallbackModel,
    val fallbackApiKeyMasked: String = "",
    val optionalNutrientGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val homeDisplay: HomeDisplayPreferences = HomeDisplayPreferences(),
    val mealSchedule: org.codeberg.fitguy.nofud.models.MealSchedule = org.codeberg.fitguy.nofud.models.MealSchedule.Default,
    /** A goal-relevant input changed since the last Recalculate. Drives a soft nudge on the
     *  Recalculate row; the button stays tappable at all times — this never disables it. */
    val goalsNeedRecalc: Boolean = false
) {
    val heightMetric: Boolean get() = heightUnit == "cm"
    val weightMetric: Boolean get() = weightUnit == "kg"
}

class SettingsViewModel(val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    /** Goal-input fingerprint captured at the last Recalculate (or seeded on first load). */
    private var lastRecalcSignature: String? = null

    /** True when [profile]'s goal inputs differ from the last-recalculated baseline. */
    private fun needsRecalc(profile: org.codeberg.fitguy.nofud.models.UserProfile?): Boolean =
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
            val provider = container.prefs.selectedAIProvider.first()
            val model = provider.supportedModelOrDefault(container.prefs.selectedAIModel.first())
            val speech = container.prefs.selectedSpeechProvider.first()
            val speechLanguage = container.prefs.selectedSpeechLanguage(speech).first()
            val heightUnit = container.prefs.heightUnit.first()
            val weightUnit = container.prefs.weightUnit.first()
            val preferGramsByDefault = container.prefs.preferGramsByDefault.first()
            val notif = container.prefs.notificationsEnabled.first()
            val streakReminder = container.prefs.streakReminderEnabled.first()
            val dailySummary = container.prefs.dailySummaryEnabled.first()
            val weightReminder = container.prefs.weightReminderEnabled.first()
            val bodyFatReminder = container.prefs.bodyFatReminderEnabled.first()
            val waterTracking = container.prefs.waterTrackingEnabled.first()
            val waterGoal = container.prefs.waterDailyGoalMl.first()
            val waterQuickPresets = container.prefs.waterQuickPresetsMl.first()
            val waterReminder = container.prefs.waterReminderEnabled.first()
            val goalReachedNotifications = container.prefs.goalReachedNotificationsEnabled.first()
            val appUpdateNotifications = container.prefs.appUpdateNotificationsEnabled.first()
            val hc = reconcileHealthConnectState()
            val profile = container.profileRepository.current()
            val energyGoals = container.prefs.healthEnergyGoalsEnabled.first() && hc
            val backgroundSync = container.prefs.healthBackgroundSyncEnabled.first() && hc
            val adaptiveGoals = container.prefs.adaptiveGoalsEnabled.first()
            val masked = maskKey(container.keyStore.apiKey(provider))
            val speechMasked = maskKey(container.keyStore.speechApiKey(speech))
            val appearance = container.prefs.appearanceMode.first()
            val appThemeColor = AppThemeColor.fromKey(container.prefs.appThemeColor.first())
            val glassBlurEnabled = container.prefs.glassBlurEnabled.first()
            val foodLogSortOrder = FoodLogSortOrder.fromStorage(container.prefs.foodLogSortOrder.first())
            val weekMon = container.prefs.weekStartsOnMonday.first()
            val userContext = container.prefs.userContext.first()
            val maxTokens = container.prefs.maxResponseTokens.first()
            val servingUnitInferenceMode = container.prefs.servingUnitInferenceMode.first()
            val fbEnabled = container.prefs.fallbackEnabled.first()
            val fbProvider = container.prefs.selectedFallbackProvider.first()
            val fbModel = fbProvider.supportedFallbackModelOrDefault(container.prefs.selectedFallbackModel.first())
            val fbMasked = maskKey(container.keyStore.apiKey(fbProvider))
            val optionalGoals = container.prefs.optionalNutrientGoals.first()
            val homeDisplay = container.prefs.homeDisplayPreferences.first()
            val mealSchedule = container.prefs.mealSchedule.first()
            // Seed the recalc baseline for existing users / first launch so the nudge only fires
            // after a genuine change from here on, never immediately on open.
            val storedSignature = container.prefs.lastRecalcGoalSignature.first()
            lastRecalcSignature = storedSignature ?: profile?.goalInputSignature
            if (storedSignature == null && profile != null) {
                container.prefs.setLastRecalcGoalSignature(profile.goalInputSignature)
            }
            _ui.value = SettingsUiState(
                selectedAI = provider,
                selectedModel = model,
                maxResponseTokens = maxTokens,
                servingUnitInferenceMode = servingUnitInferenceMode,
                selectedSpeech = speech,
                selectedSpeechLanguage = speechLanguage,
                heightUnit = heightUnit,
                weightUnit = weightUnit,
                preferGramsByDefault = preferGramsByDefault,
                profile = profile,
                notificationsEnabled = notif,
                streakReminderEnabled = streakReminder,
                dailySummaryEnabled = dailySummary,
                weightReminderEnabled = weightReminder,
                bodyFatReminderEnabled = bodyFatReminder,
                waterTrackingEnabled = waterTracking,
                waterDailyGoalMl = waterGoal,
                waterQuickPresetsMl = waterQuickPresets,
                waterReminderEnabled = waterReminder,
                goalReachedNotificationsEnabled = goalReachedNotifications,
                appUpdateNotificationsEnabled = appUpdateNotifications,
                healthConnectEnabled = hc,
                healthEnergyGoalsEnabled = energyGoals,
                healthBackgroundSyncEnabled = backgroundSync,
                adaptiveGoalsEnabled = adaptiveGoals,
                apiKeyMasked = masked,
                speechApiKeyMasked = speechMasked,
                appearanceMode = appearance,
                appThemeColor = appThemeColor,
                glassBlurEnabled = glassBlurEnabled,
                foodLogSortOrder = foodLogSortOrder,
                weekStartsOnMonday = weekMon,
                userContext = userContext,
                fallbackEnabled = fbEnabled,
                fallbackProvider = fbProvider,
                fallbackModel = fbModel,
                fallbackApiKeyMasked = fbMasked,
                optionalNutrientGoals = optionalGoals,
                homeDisplay = homeDisplay,
                mealSchedule = mealSchedule,
                goalsNeedRecalc = needsRecalc(profile)
            )
        }

        container.prefs.homeDisplayPreferences
            .onEach { display -> _ui.value = _ui.value.copy(homeDisplay = display) }
            .launchIn(viewModelScope)
    }

    fun setHomeNutrientCardCount(count: Int) {
        viewModelScope.launch {
            container.prefs.setHomeNutrientCardCount(count)
            val nutrients = container.prefs.homeTopNutrients.first()
            container.prefs.setHomeTopNutrients(
                HomeTopNutrient.toStorage(HomeTopNutrient.fromStorage(nutrients, count), count)
            )
        }
    }

    fun setHomeTopNutrients(selection: List<HomeTopNutrient>) {
        viewModelScope.launch {
            val cardCount = container.prefs.homeNutrientCardCount.first()
            container.prefs.setHomeTopNutrients(HomeTopNutrient.toStorage(selection, cardCount))
        }
    }

    fun setHomeShowSteps(enabled: Boolean) {
        viewModelScope.launch { container.prefs.setHomeShowSteps(enabled) }
    }

    fun setHomeShowActiveCalories(enabled: Boolean) {
        viewModelScope.launch { container.prefs.setHomeShowActiveCalories(enabled) }
    }

    fun setHomeStepGoal(goal: Int) {
        viewModelScope.launch { container.prefs.setHomeStepGoal(goal) }
    }

    fun setHomeCalorieDisplayMode(mode: HomeCalorieDisplayMode) {
        viewModelScope.launch { container.prefs.setHomeCalorieDisplayMode(mode.storageKey) }
    }

    fun setFoodLogMacroChips(chips: List<FoodLogMacroChip>) {
        viewModelScope.launch { container.prefs.setFoodLogMacroChips(FoodLogMacroChip.toStorage(chips)) }
    }

    fun setOptionalNutrientGoals(goals: OptionalNutrientGoals) {
        viewModelScope.launch {
            container.prefs.setOptionalNutrientGoals(goals)
            _ui.value = _ui.value.copy(optionalNutrientGoals = goals)
        }
    }

    fun setUserContext(value: String) {
        viewModelScope.launch {
            container.prefs.setUserContext(value)
            _ui.value = _ui.value.copy(userContext = value.trim())
        }
    }

    fun setMaxResponseTokens(v: Int) {
        val clamped = v.coerceAtLeast(1)
        viewModelScope.launch {
            container.prefs.setMaxResponseTokens(clamped)
            _ui.value = _ui.value.copy(maxResponseTokens = clamped)
        }
    }

    fun setServingUnitInferenceMode(mode: ServingUnitInferenceMode) {
        viewModelScope.launch {
            container.prefs.setServingUnitInferenceMode(mode)
            _ui.value = _ui.value.copy(servingUnitInferenceMode = mode)
        }
    }

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
            val newModel = p.supportedFallbackModelOrDefault(current)
            container.prefs.setSelectedFallbackModel(newModel)
            val masked = maskKey(container.keyStore.apiKey(p))
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
            container.keyStore.setApiKey(p, raw.takeIf { it.isNotBlank() })
            _ui.value = _ui.value.copy(fallbackApiKeyMasked = maskKey(raw.takeIf { it.isNotBlank() }))
        }
    }

    fun setAppearanceMode(mode: String) {
        viewModelScope.launch {
            container.prefs.setAppearanceMode(mode)
            _ui.value = _ui.value.copy(appearanceMode = mode)
        }
    }

    fun setAppThemeColor(themeColor: AppThemeColor) {
        viewModelScope.launch {
            container.prefs.setAppThemeColor(themeColor.key)
            AndroidAppIconManager.apply(container.appContext, themeColor)
            _ui.value = _ui.value.copy(appThemeColor = themeColor)
        }
    }

    fun setGlassBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.prefs.setGlassBlurEnabled(enabled)
            _ui.value = _ui.value.copy(glassBlurEnabled = enabled)
        }
    }

    fun setWeekStartsOnMonday(monday: Boolean) {
        viewModelScope.launch {
            container.prefs.setWeekStartsOnMonday(monday)
            _ui.value = _ui.value.copy(weekStartsOnMonday = monday)
        }
    }

    fun setMealSchedule(schedule: org.codeberg.fitguy.nofud.models.MealSchedule) {
        viewModelScope.launch {
            val validated = schedule.validatedOrDefault()
            container.prefs.setMealSchedule(validated)
            _ui.value = _ui.value.copy(mealSchedule = validated)
        }
    }

    fun setFoodLogSortOrder(order: FoodLogSortOrder) {
        viewModelScope.launch {
            container.prefs.setFoodLogSortOrder(order.storageValue)
            _ui.value = _ui.value.copy(foodLogSortOrder = order)
        }
    }

    fun selectProvider(p: AIProvider) {
        viewModelScope.launch {
            container.prefs.setSelectedAIProvider(p)
            container.prefs.setSelectedAIModel(p.defaultModel)
            val masked = maskKey(container.keyStore.apiKey(p))
            _ui.value = _ui.value.copy(selectedAI = p, selectedModel = p.defaultModel, apiKeyMasked = masked)
        }
    }

    fun selectModel(m: String) {
        viewModelScope.launch {
            val model = _ui.value.selectedAI.supportedModelOrDefault(m)
            container.prefs.setSelectedAIModel(model)
            _ui.value = _ui.value.copy(selectedModel = model)
        }
    }

    fun setApiKey(raw: String) {
        viewModelScope.launch {
            val p = _ui.value.selectedAI
            container.keyStore.setApiKey(p, raw.takeIf { it.isNotBlank() })
            _ui.value = _ui.value.copy(apiKeyMasked = maskKey(raw.takeIf { it.isNotBlank() }))
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
            container.keyStore.setSpeechApiKey(p, raw.takeIf { it.isNotBlank() })
            _ui.value = _ui.value.copy(speechApiKeyMasked = maskKey(raw.takeIf { it.isNotBlank() }))
        }
    }

    fun setHeightUnit(v: String) {
        viewModelScope.launch {
            container.prefs.setHeightUnit(v)
            _ui.value = _ui.value.copy(heightUnit = v)
        }
    }

    fun setWeightUnit(v: String) {
        viewModelScope.launch {
            container.prefs.setWeightUnit(v)
            _ui.value = _ui.value.copy(weightUnit = v)
        }
    }

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

    fun setPreferGramsByDefault(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setPreferGramsByDefault(v)
            _ui.value = _ui.value.copy(preferGramsByDefault = v)
        }
    }

    fun setNotificationsEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setNotificationsEnabled(v)
            syncNotificationSchedules()
            _ui.value = _ui.value.copy(notificationsEnabled = v)
        }
    }

    fun setStreakReminderEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setStreakReminderEnabled(v)
            syncNotificationSchedules()
            _ui.value = _ui.value.copy(streakReminderEnabled = v)
        }
    }

    fun setDailySummaryEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setDailySummaryEnabled(v)
            syncNotificationSchedules()
            _ui.value = _ui.value.copy(dailySummaryEnabled = v)
        }
    }

    fun setWeightReminderEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setWeightReminderEnabled(v)
            syncNotificationSchedules()
            _ui.value = _ui.value.copy(weightReminderEnabled = v)
        }
    }

    fun setBodyFatReminderEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setBodyFatReminderEnabled(v)
            syncNotificationSchedules()
            _ui.value = _ui.value.copy(bodyFatReminderEnabled = v)
        }
    }

    fun setGoalReachedNotificationsEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setGoalReachedNotificationsEnabled(v)
            _ui.value = _ui.value.copy(goalReachedNotificationsEnabled = v)
        }
    }

    fun setAppUpdateNotificationsEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setAppUpdateNotificationsEnabled(v)
            _ui.value = _ui.value.copy(appUpdateNotificationsEnabled = v)
        }
    }

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
        if (container.prefs.waterTrackingEnabled.first() && container.prefs.waterReminderEnabled.first()) {
            container.notifications.scheduleWaterReminder(
                container.prefs.waterReminderHour.first(),
                container.prefs.waterReminderMinute.first(),
            )
        } else {
            container.notifications.cancelWaterReminder()
        }
    }

    fun setWaterTrackingEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setWaterTrackingEnabled(v)
            if (!v) {
                container.prefs.setWaterReminderEnabled(false)
                container.notifications.cancelWaterReminder()
            }
            _ui.value = _ui.value.copy(
                waterTrackingEnabled = v,
                waterReminderEnabled = if (v) _ui.value.waterReminderEnabled else false,
            )
        }
    }

    fun setWaterDailyGoalMl(v: Int) {
        viewModelScope.launch {
            container.prefs.setWaterDailyGoalMl(v)
            _ui.value = _ui.value.copy(waterDailyGoalMl = v)
        }
    }

    fun setWaterQuickPresetsMl(amountsMl: List<Int>) {
        viewModelScope.launch {
            val validated = WaterQuickPresets(amountsMl).validatedOrDefault().amountsMl
            container.prefs.setWaterQuickPresetsMl(validated)
            _ui.value = _ui.value.copy(waterQuickPresetsMl = validated)
        }
    }

    fun setWaterReminderEnabled(v: Boolean) {
        viewModelScope.launch {
            container.prefs.setWaterReminderEnabled(v)
            _ui.value = _ui.value.copy(waterReminderEnabled = v)
            syncNotificationSchedules()
        }
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
            if (!enabled) container.prefs.setHealthEnergyGoalsEnabled(false)
            _ui.value = _ui.value.copy(
                healthConnectEnabled = enabled,
                healthEnergyGoalsEnabled = if (enabled) _ui.value.healthEnergyGoalsEnabled else false
            )
        }
    }

    /** Opt-in periodic background sync. Requires Health Connect to already be
     *  connected; enabling schedules the worker, disabling cancels it. Default OFF. */
    fun setHealthBackgroundSyncEnabled(v: Boolean) {
        viewModelScope.launch {
            if (v && !container.prefs.healthConnectEnabled.first()) return@launch
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
            // Adaptive owns the targets while on and auto-recalculates — drop any user locks now so
            // the (disabled) lock controls read as unlocked, even before the first weekly run lands.
            container.profileRepository.current()?.let { cur ->
                if (cur.caloriesLocked || cur.lockedMacros.isNotEmpty()) {
                    container.profileRepository.save(cur.withLocksCleared())
                }
            }
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
            container.imageStore.clearAll()
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
            val result = try {
                container.foodAnalysis.calculateGoals(current, forecast, heightMetric, weightMetric, measuredTdee, container.bodyMeasurementRepository.latestSnapshot())
            } catch (e: Throwable) {
                _ui.value = _ui.value.copy(
                    recalculatingGoals = false,
                    adaptiveGoalAlertTitle = "Couldn't Recalculate",
                    adaptiveGoalAlertMessage = "Fud AI couldn't reach your AI provider, so your goals are unchanged. Check your AI provider and API key in Settings, then try again. (${e.localizedMessage ?: "no response"})"
                )
                return@launch
            }
            // Store the AI's full plan as a fixed snapshot: calories + all three macros. Protein is
            // the AI's choice within a range near the activity multiplier. Freezing carbs and fat too
            // means editing a profile input (weight, pace, …) no longer reshuffles macros — they only
            // change on the next Recalculate.
            val next = current.recalculatedFromFormulas().copy(
                customCalories = result.calories,
                customProtein = result.protein,
                customCarbs = result.carbs,
                customFat = result.fat
            )
            val message = "Updated to ${result.calories} kcal." + (result.reason?.let { " $it" } ?: "")
            container.profileRepository.save(next)
            // Goals are now fresh — capture this input baseline so the recalc nudge clears.
            lastRecalcSignature = next.goalInputSignature
            container.prefs.setLastRecalcGoalSignature(next.goalInputSignature)
            // Also AI-refresh the optional Other Nutrients; keep existing values on failure.
            try {
                val goals = container.foodAnalysis.estimateOptionalNutrientGoals(next)
                container.prefs.setOptionalNutrientGoals(goals)
                _ui.value = _ui.value.copy(optionalNutrientGoals = goals)
            } catch (_: Throwable) { /* keep existing nutrient goals */ }
            val adaptiveResult = container.refreshAdaptiveGoalsIfNeeded(force = false)
            val adaptiveNote = adaptiveResult?.takeIf { it.changed }?.let { "\n\n${it.message}" } ?: ""
            _ui.value = _ui.value.copy(
                recalculatingGoals = false,
                profile = adaptiveResult?.profile ?: next,
                adaptiveGoalAlertTitle = container.appContext.getString(R.string.vm_goals_recalculated),
                adaptiveGoalAlertMessage = message + adaptiveNote,
                goalsNeedRecalc = false
            )
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
            _ui.value = _ui.value.copy(profile = next, goalsNeedRecalc = needsRecalc(next))
        }
    }

    fun updateProfile(update: (org.codeberg.fitguy.nofud.models.UserProfile) -> org.codeberg.fitguy.nofud.models.UserProfile) {
        viewModelScope.launch {
            val current = container.profileRepository.current() ?: return@launch
            val next = update(current)
            container.profileRepository.save(next)
            _ui.value = _ui.value.copy(profile = next, goalsNeedRecalc = needsRecalc(next))
        }
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
            _ui.value = _ui.value.copy(profile = next, goalsNeedRecalc = needsRecalc(next))
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

    fun setCustomBaseUrl(provider: AIProvider, url: String) {
        viewModelScope.launch {
            container.prefs.setCustomBaseUrl(provider, url.takeIf { it.isNotBlank() })
        }
    }

    private fun maskKey(key: String?): String =
        if (key.isNullOrBlank()) "" else key.take(4) + "..." + key.takeLast(4)

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}
