package app.chompass.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.chompass.models.AIProvider
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.ChatMessage
import app.chompass.models.FoodEntry
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.ManualActiveEntry
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.PendingFoodAnalysisDraft
import app.chompass.models.PendingFoodInputDraft
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.SpeechLanguage
import app.chompass.models.SpeechProvider
import app.chompass.models.UserProfile
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.models.WidgetSnapshot
import app.chompass.services.health.DebugActivityDay
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

val Context.fudaiDataStore by preferencesDataStore(name = "fudai_prefs")

/**
 * Thin wrapper over DataStore Preferences for all app state except API keys
 * (which live in [KeyStore]). Exposes reactive Flows for reads and suspend
 * functions for writes. Complex values (profile, entries, history) are stored
 * as JSON strings via kotlinx.serialization.
 */
class PreferencesStore(private val appContext: Context) {
    internal val dataStore get() = appContext.fudaiDataStore
    internal val json = Json { ignoreUnknownKeys = true }

    val userProfile: Flow<UserProfile?> get() = userProfileImpl
    suspend fun setUserProfile(profile: UserProfile) = setUserProfileImpl(profile)
    val hasCompletedOnboarding: Flow<Boolean> get() = hasCompletedOnboardingImpl
    suspend fun setOnboardingCompleted(value: Boolean) = setOnboardingCompletedImpl(value)
    val onboardingDraft: Flow<app.chompass.ui.onboarding.OnboardingDraft?> get() = onboardingDraftImpl
    suspend fun setOnboardingDraft(draft: app.chompass.ui.onboarding.OnboardingDraft?) = setOnboardingDraftImpl(draft)
    val hasSeenCameraScaleTip: Flow<Boolean> get() = hasSeenCameraScaleTipImpl
    suspend fun setHasSeenCameraScaleTip(value: Boolean) = setHasSeenCameraScaleTipImpl(value)
    val firstLaunchAt: Flow<Long> get() = firstLaunchAtImpl
    /** Idempotent seed of the first-launch timestamp; keeps the earliest value. */
    suspend fun ensureFirstLaunchAt(nowMillis: Long = System.currentTimeMillis()) = ensureFirstLaunchAtImpl(nowMillis)
    val dismissedSuggestionIds: Flow<Set<String>> get() = dismissedSuggestionIdsImpl
    suspend fun setSuggestionDismissed(id: String, dismissed: Boolean = true) = setSuggestionDismissedImpl(id, dismissed)
    val notificationsEnabled: Flow<Boolean> get() = notificationsEnabledImpl
    suspend fun setNotificationsEnabled(v: Boolean) = setNotificationsEnabledImpl(v)
    val streakReminderEnabled: Flow<Boolean> get() = streakReminderEnabledImpl
    suspend fun setStreakReminderEnabled(v: Boolean) = setStreakReminderEnabledImpl(v)
    val streakReminderHour: Flow<Int> get() = streakReminderHourImpl
    suspend fun setStreakReminderHour(v: Int) = setStreakReminderHourImpl(v)
    val streakReminderMinute: Flow<Int> get() = streakReminderMinuteImpl
    suspend fun setStreakReminderMinute(v: Int) = setStreakReminderMinuteImpl(v)
    val dailySummaryEnabled: Flow<Boolean> get() = dailySummaryEnabledImpl
    suspend fun setDailySummaryEnabled(v: Boolean) = setDailySummaryEnabledImpl(v)
    val dailySummaryHour: Flow<Int> get() = dailySummaryHourImpl
    suspend fun setDailySummaryHour(v: Int) = setDailySummaryHourImpl(v)
    val dailySummaryMinute: Flow<Int> get() = dailySummaryMinuteImpl
    suspend fun setDailySummaryMinute(v: Int) = setDailySummaryMinuteImpl(v)
    val weightReminderEnabled: Flow<Boolean> get() = weightReminderEnabledImpl
    suspend fun setWeightReminderEnabled(v: Boolean) = setWeightReminderEnabledImpl(v)
    val bodyFatReminderEnabled: Flow<Boolean> get() = bodyFatReminderEnabledImpl
    suspend fun setBodyFatReminderEnabled(v: Boolean) = setBodyFatReminderEnabledImpl(v)
    val goalReachedNotificationsEnabled: Flow<Boolean> get() = goalReachedNotificationsEnabledImpl
    suspend fun setGoalReachedNotificationsEnabled(v: Boolean) = setGoalReachedNotificationsEnabledImpl(v)
    val appUpdateNotificationsEnabled: Flow<Boolean> get() = appUpdateNotificationsEnabledImpl
    suspend fun setAppUpdateNotificationsEnabled(v: Boolean) = setAppUpdateNotificationsEnabledImpl(v)
    val waterTrackingEnabled: Flow<Boolean> get() = waterTrackingEnabledImpl
    suspend fun setWaterTrackingEnabled(v: Boolean) = setWaterTrackingEnabledImpl(v)
    val waterDailyGoalMl: Flow<Int> get() = waterDailyGoalMlImpl
    suspend fun setWaterDailyGoalMl(v: Int) = setWaterDailyGoalMlImpl(v)
    val waterReminderEnabled: Flow<Boolean> get() = waterReminderEnabledImpl
    suspend fun setWaterReminderEnabled(v: Boolean) = setWaterReminderEnabledImpl(v)
    val waterReminderHour: Flow<Int> get() = waterReminderHourImpl
    suspend fun setWaterReminderHour(v: Int) = setWaterReminderHourImpl(v)
    val waterReminderMinute: Flow<Int> get() = waterReminderMinuteImpl
    suspend fun setWaterReminderMinute(v: Int) = setWaterReminderMinuteImpl(v)
    val waterDynamicEnabled: Flow<Boolean> get() = waterDynamicEnabledImpl
    suspend fun setWaterDynamicEnabled(v: Boolean) = setWaterDynamicEnabledImpl(v)
    val waterBaseSource: Flow<String> get() = waterBaseSourceImpl
    suspend fun setWaterBaseSource(v: String) = setWaterBaseSourceImpl(v)
    val waterManualTempC: Flow<Int> get() = waterManualTempCImpl
    suspend fun setWaterManualTempC(v: Int) = setWaterManualTempCImpl(v)
    val waterUseProfileActivity: Flow<Boolean> get() = waterUseProfileActivityImpl
    suspend fun setWaterUseProfileActivity(v: Boolean) = setWaterUseProfileActivityImpl(v)
    val waterFoodWaterEnabled: Flow<Boolean> get() = waterFoodWaterEnabledImpl
    suspend fun setWaterFoodWaterEnabled(v: Boolean) = setWaterFoodWaterEnabledImpl(v)
    val waterAwakeStartHour: Flow<Int> get() = waterAwakeStartHourImpl
    suspend fun setWaterAwakeStartHour(v: Int) = setWaterAwakeStartHourImpl(v)
    val waterAwakeStartMinute: Flow<Int> get() = waterAwakeStartMinuteImpl
    suspend fun setWaterAwakeStartMinute(v: Int) = setWaterAwakeStartMinuteImpl(v)
    val waterAwakeEndHour: Flow<Int> get() = waterAwakeEndHourImpl
    suspend fun setWaterAwakeEndHour(v: Int) = setWaterAwakeEndHourImpl(v)
    val waterAwakeEndMinute: Flow<Int> get() = waterAwakeEndMinuteImpl
    suspend fun setWaterAwakeEndMinute(v: Int) = setWaterAwakeEndMinuteImpl(v)
    val waterCupSizeMl: Flow<Int> get() = waterCupSizeMlImpl
    suspend fun setWaterCupSizeMl(v: Int) = setWaterCupSizeMlImpl(v)
    val waterQuickPresetsMl: Flow<List<Int>> get() = waterQuickPresetsMlImpl
    suspend fun setWaterQuickPresetsMl(amountsMl: List<Int>) = setWaterQuickPresetsMlImpl(amountsMl)
    val waterEntries: Flow<List<WaterEntry>> get() = waterEntriesImpl
    suspend fun setWaterEntries(entries: List<WaterEntry>) = setWaterEntriesImpl(entries)
    val manualActiveEntries: Flow<List<ManualActiveEntry>> get() = manualActiveEntriesImpl
    suspend fun setManualActiveEntries(entries: List<ManualActiveEntry>) = setManualActiveEntriesImpl(entries)
    val lastNotifiedUpdateVersion: Flow<String?> get() = lastNotifiedUpdateVersionImpl
    suspend fun setLastNotifiedUpdateVersion(v: String) = setLastNotifiedUpdateVersionImpl(v)
    val healthConnectEnabled: Flow<Boolean> get() = healthConnectEnabledImpl
    suspend fun setHealthConnectEnabled(v: Boolean) = setHealthConnectEnabledImpl(v)
    val healthPermissionsVersion: Flow<Int> get() = healthPermissionsVersionImpl
    suspend fun setHealthPermissionsVersion(v: Int) = setHealthPermissionsVersionImpl(v)
    val healthChangesToken: Flow<String?> get() = healthChangesTokenImpl
    suspend fun setHealthChangesToken(v: String) = setHealthChangesTokenImpl(v)
    suspend fun clearHealthChangesToken() = clearHealthChangesTokenImpl()
    val healthChangesTokenTypes: Flow<Set<String>> get() = healthChangesTokenTypesImpl
    suspend fun setHealthChangesTokenTypes(types: Set<String>) = setHealthChangesTokenTypesImpl(types)
    val healthFoodRestoreDone: Flow<Boolean> get() = healthFoodRestoreDoneImpl
    suspend fun setHealthFoodRestoreDone(v: Boolean) = setHealthFoodRestoreDoneImpl(v)
    val healthHydrationRestoreDone: Flow<Boolean> get() = healthHydrationRestoreDoneImpl
    suspend fun setHealthHydrationRestoreDone(v: Boolean) = setHealthHydrationRestoreDoneImpl(v)
    val healthEnergyGoalsEnabled: Flow<Boolean> get() = healthEnergyGoalsEnabledImpl
    suspend fun setHealthEnergyGoalsEnabled(v: Boolean) = setHealthEnergyGoalsEnabledImpl(v)
    val healthEnergyMeasuredActive: Flow<Int> get() = healthEnergyMeasuredActiveImpl
    suspend fun setHealthEnergyMeasuredActive(v: Int) = setHealthEnergyMeasuredActiveImpl(v)
    suspend fun clearHealthEnergyMeasuredActive() = clearHealthEnergyMeasuredActiveImpl()
    val healthBackgroundSyncEnabled: Flow<Boolean> get() = healthBackgroundSyncEnabledImpl
    suspend fun setHealthBackgroundSyncEnabled(v: Boolean) = setHealthBackgroundSyncEnabledImpl(v)
    val healthEnergyGoalsLastAutoRefreshDay: Flow<String?> get() = healthEnergyGoalsLastAutoRefreshDayImpl
    suspend fun setHealthEnergyGoalsLastAutoRefreshDay(v: String) = setHealthEnergyGoalsLastAutoRefreshDayImpl(v)
    val reviewPromptedAfterFirstLog: Flow<Boolean> get() = reviewPromptedAfterFirstLogImpl
    suspend fun setReviewPromptedAfterFirstLog(v: Boolean) = setReviewPromptedAfterFirstLogImpl(v)
    val adaptiveGoalsEnabled: Flow<Boolean> get() = adaptiveGoalsEnabledImpl
    suspend fun setAdaptiveGoalsEnabled(v: Boolean) = setAdaptiveGoalsEnabledImpl(v)
    val adaptiveGoalsLastCheckDay: Flow<String?> get() = adaptiveGoalsLastCheckDayImpl
    suspend fun setAdaptiveGoalsLastCheckDay(v: String) = setAdaptiveGoalsLastCheckDayImpl(v)
    suspend fun saveAdaptiveGoalPreviousTargetsIfNeeded(profile: UserProfile) = saveAdaptiveGoalPreviousTargetsIfNeededImpl(profile)
    suspend fun restoreAdaptiveGoalPreviousTargets(profile: UserProfile): UserProfile = restoreAdaptiveGoalPreviousTargetsImpl(profile)
    suspend fun clearAdaptiveGoalPreviousTargets() = clearAdaptiveGoalPreviousTargetsImpl()
    suspend fun saveHealthEnergyGoalPreviousTargetsIfNeeded(profile: UserProfile) = saveHealthEnergyGoalPreviousTargetsIfNeededImpl(profile)
    suspend fun restoreHealthEnergyGoalPreviousTargets(profile: UserProfile): UserProfile = restoreHealthEnergyGoalPreviousTargetsImpl(profile)
    suspend fun clearHealthEnergyGoalPreviousTargets() = clearHealthEnergyGoalPreviousTargetsImpl()
    val useMetric: Flow<Boolean> get() = useMetricImpl
    suspend fun setUseMetric(v: Boolean) = setUseMetricImpl(v)
    val heightUnit: Flow<String> get() = heightUnitImpl
    suspend fun setHeightUnit(v: String) = setHeightUnitImpl(v)
    val weightUnit: Flow<String> get() = weightUnitImpl
    suspend fun setWeightUnit(v: String) = setWeightUnitImpl(v)
    val preferGramsByDefault: Flow<Boolean> get() = preferGramsByDefaultImpl
    suspend fun setPreferGramsByDefault(v: Boolean) = setPreferGramsByDefaultImpl(v)
    val appearanceMode: Flow<String> get() = appearanceModeImpl
    suspend fun setAppearanceMode(v: String) = setAppearanceModeImpl(v)
    val appThemeColor: Flow<String> get() = appThemeColorImpl
    suspend fun setAppThemeColor(v: String) = setAppThemeColorImpl(v)
    val glassBlurEnabled: Flow<Boolean> get() = glassBlurEnabledImpl
    suspend fun setGlassBlurEnabled(v: Boolean) = setGlassBlurEnabledImpl(v)
    val weekStartsOnMonday: Flow<Boolean> get() = weekStartsOnMondayImpl
    suspend fun setWeekStartsOnMonday(v: Boolean) = setWeekStartsOnMondayImpl(v)
    val progressDefaultRangeId: Flow<String> get() = progressDefaultRangeIdImpl
    suspend fun setProgressDefaultRangeId(v: String) = setProgressDefaultRangeIdImpl(v)
    val progressLastRangeId: Flow<String?> get() = progressLastRangeIdImpl
    suspend fun setProgressLastRangeId(v: String) = setProgressLastRangeIdImpl(v)
    val mealSchedule: Flow<app.chompass.models.MealSchedule> get() = mealScheduleImpl
    suspend fun setMealSchedule(schedule: app.chompass.models.MealSchedule) = setMealScheduleImpl(schedule)
    val lastSavedMealsSegment: Flow<String> get() = lastSavedMealsSegmentImpl
    suspend fun setLastSavedMealsSegment(v: String) = setLastSavedMealsSegmentImpl(v)
    val foodLogSortOrder: Flow<String> get() = foodLogSortOrderImpl
    suspend fun setFoodLogSortOrder(v: String) = setFoodLogSortOrderImpl(v)
    val homeTopNutrients: Flow<String> get() = homeTopNutrientsImpl
    suspend fun setHomeTopNutrients(v: String) = setHomeTopNutrientsImpl(v)
    val homeNutrientCardCount: Flow<Int> get() = homeNutrientCardCountImpl
    suspend fun setHomeNutrientCardCount(v: Int) = setHomeNutrientCardCountImpl(v)
    val homeShowSteps: Flow<Boolean> get() = homeShowStepsImpl
    suspend fun setHomeShowSteps(v: Boolean) = setHomeShowStepsImpl(v)
    val homeShowActiveCalories: Flow<Boolean> get() = homeShowActiveCaloriesImpl
    suspend fun setHomeShowActiveCalories(v: Boolean) = setHomeShowActiveCaloriesImpl(v)
    val homeStepGoal: Flow<Int> get() = homeStepGoalImpl
    suspend fun setHomeStepGoal(v: Int) = setHomeStepGoalImpl(v)
    val homeCalorieDisplayMode: Flow<String> get() = homeCalorieDisplayModeImpl
    suspend fun setHomeCalorieDisplayMode(v: String) = setHomeCalorieDisplayModeImpl(v)
    val foodLogMacroChips: Flow<String> get() = foodLogMacroChipsImpl
    suspend fun setFoodLogMacroChips(v: String) = setFoodLogMacroChipsImpl(v)
    suspend fun migrateHomeDisplayLayoutIfNeeded() = migrateHomeDisplayLayoutIfNeededImpl()
    val homeDisplayPreferences: Flow<HomeDisplayPreferences> get() = homeDisplayPreferencesImpl
    val optionalNutrientGoals: Flow<OptionalNutrientGoals> get() = optionalNutrientGoalsImpl
    suspend fun setOptionalNutrientGoals(goals: OptionalNutrientGoals) = setOptionalNutrientGoalsImpl(goals)
    val selectedAIProvider: Flow<AIProvider> get() = selectedAIProviderImpl
    suspend fun setSelectedAIProvider(p: AIProvider) = setSelectedAIProviderImpl(p)
    val selectedAIModel: Flow<String?> get() = selectedAIModelImpl
    suspend fun setSelectedAIModel(model: String) = setSelectedAIModelImpl(model)
    fun customBaseUrl(provider: AIProvider): Flow<String?> = customBaseUrlImpl(provider)
    suspend fun setCustomBaseUrl(provider: AIProvider, url: String?) = setCustomBaseUrlImpl(provider, url)
    val maxResponseTokens: Flow<Int> get() = maxResponseTokensImpl
    suspend fun setMaxResponseTokens(v: Int) = setMaxResponseTokensImpl(v)
    val aiReadTimeoutSeconds: Flow<Int> get() = aiReadTimeoutSecondsImpl
    suspend fun setAiReadTimeoutSeconds(v: Int) = setAiReadTimeoutSecondsImpl(v)
    val servingUnitInferenceMode: Flow<ServingUnitInferenceMode> get() = servingUnitInferenceModeImpl
    suspend fun setServingUnitInferenceMode(mode: ServingUnitInferenceMode) = setServingUnitInferenceModeImpl(mode)
    val heuristicServingUnitSettings: Flow<HeuristicServingUnitSettings> get() = heuristicServingUnitSettingsImpl
    suspend fun setHeuristicServingUnitSettings(settings: HeuristicServingUnitSettings) = setHeuristicServingUnitSettingsImpl(settings)
    val userContext: Flow<String> get() = userContextImpl
    suspend fun setUserContext(value: String) = setUserContextImpl(value)
    val lastRecalcGoalSignature: Flow<String?> get() = lastRecalcGoalSignatureImpl
    suspend fun setLastRecalcGoalSignature(value: String) = setLastRecalcGoalSignatureImpl(value)
    val fallbackEnabled: Flow<Boolean> get() = fallbackEnabledImpl
    suspend fun setFallbackEnabled(v: Boolean) = setFallbackEnabledImpl(v)
    val selectedFallbackProvider: Flow<AIProvider> get() = selectedFallbackProviderImpl
    suspend fun setSelectedFallbackProvider(p: AIProvider) = setSelectedFallbackProviderImpl(p)
    val selectedFallbackModel: Flow<String?> get() = selectedFallbackModelImpl
    suspend fun setSelectedFallbackModel(model: String) = setSelectedFallbackModelImpl(model)
    val geminiGoogleSearchEnabled: Flow<Boolean> get() = geminiGoogleSearchEnabledImpl
    suspend fun setGeminiGoogleSearchEnabled(v: Boolean) = setGeminiGoogleSearchEnabledImpl(v)
    val portionClarifyEnabled: Flow<Boolean> get() = portionClarifyEnabledImpl
    suspend fun setPortionClarifyEnabled(v: Boolean) = setPortionClarifyEnabledImpl(v)
    val skipPhotoNotePrompt: Flow<Boolean> get() = skipPhotoNotePromptImpl
    suspend fun setSkipPhotoNotePrompt(v: Boolean) = setSkipPhotoNotePromptImpl(v)
    val photoNoteSkipCount: Flow<Int> get() = photoNoteSkipCountImpl
    suspend fun setPhotoNoteSkipCount(v: Int) = setPhotoNoteSkipCountImpl(v)
    val photoAccuracyGuideCount: Flow<Int> get() = photoAccuracyGuideCountImpl
    suspend fun setPhotoAccuracyGuideCount(v: Int) = setPhotoAccuracyGuideCountImpl(v)
    val mealConstituentsEnabled: Flow<Boolean> get() = mealConstituentsEnabledImpl
    suspend fun setMealConstituentsEnabled(v: Boolean) = setMealConstituentsEnabledImpl(v)
    val selectedSpeechProvider: Flow<SpeechProvider> get() = selectedSpeechProviderImpl
    suspend fun setSelectedSpeechProvider(p: SpeechProvider) = setSelectedSpeechProviderImpl(p)
    fun selectedSpeechLanguage(provider: SpeechProvider): Flow<SpeechLanguage> = selectedSpeechLanguageImpl(provider)
    suspend fun setSelectedSpeechLanguage(provider: SpeechProvider, language: SpeechLanguage) = setSelectedSpeechLanguageImpl(provider, language)
    val onDeviceModelDownloadedVersion: Flow<String?> get() = onDeviceModelDownloadedVersionImpl
    suspend fun setOnDeviceModelDownloadedVersion(version: String?) = setOnDeviceModelDownloadedVersionImpl(version)
    val onDeviceDownloadOverWifiOnly: Flow<Boolean> get() = onDeviceDownloadOverWifiOnlyImpl
    suspend fun setOnDeviceDownloadOverWifiOnly(v: Boolean) = setOnDeviceDownloadOverWifiOnlyImpl(v)
    val onDeviceFeatureVisible: Flow<Boolean> get() = onDeviceFeatureVisibleImpl
    suspend fun setOnDeviceFeatureVisible(v: Boolean) = setOnDeviceFeatureVisibleImpl(v)
    val foodEntries: Flow<List<FoodEntry>> get() = foodEntriesImpl
    fun foodEntriesForMonth(month: YearMonth): Flow<List<FoodEntry>> = foodEntriesForMonthImpl(month)
    suspend fun applyFoodEntryBucketChanges(
        upsertsByMonth: Map<YearMonth, List<FoodEntry>> = emptyMap(),
        removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
        draft: PendingFoodAnalysisDraft? = null,
        clearDraft: Boolean = false,
    ) = applyFoodEntryBucketChangesImpl(upsertsByMonth, removalIdsByMonth, draft, clearDraft)
    suspend fun replaceAllFoodEntries(entries: List<FoodEntry>) = replaceAllFoodEntriesImpl(entries)
    val favoriteKeys: Flow<Set<String>> get() = favoriteKeysImpl
    suspend fun setFavoriteKeys(keys: Set<String>) = setFavoriteKeysImpl(keys)
    val favoriteFoodEntries: Flow<List<FoodEntry>> get() = favoriteFoodEntriesImpl
    suspend fun setFavoriteFoodEntries(entries: List<FoodEntry>) = setFavoriteFoodEntriesImpl(entries)
    val recipes: Flow<List<app.chompass.models.Recipe>> get() = recipesImpl
    suspend fun setRecipes(recipes: List<app.chompass.models.Recipe>) = setRecipesImpl(recipes)
    val pendingFoodAnalysisDraft: Flow<PendingFoodAnalysisDraft?> get() = pendingFoodAnalysisDraftImpl
    suspend fun setPendingFoodAnalysisDraft(draft: PendingFoodAnalysisDraft?) = setPendingFoodAnalysisDraftImpl(draft)
    val barcodeCache: Flow<Map<String, CachedBarcodeProduct>> get() = barcodeCacheImpl
    suspend fun cacheBarcodeLookup(barcode: String, analysis: app.chompass.services.ai.FoodAnalysis) =
        cacheBarcodeLookupImpl(barcode, analysis)
    val pendingFoodInputDraft: Flow<PendingFoodInputDraft?> get() = pendingFoodInputDraftImpl
    suspend fun setPendingFoodInputDraft(draft: PendingFoodInputDraft?) = setPendingFoodInputDraftImpl(draft)
    suspend fun foodImageReferenceFilenames(): Set<String>? = foodImageReferenceFilenamesImpl()
    val weightEntries: Flow<List<WeightEntry>> get() = weightEntriesImpl
    suspend fun setWeightEntries(entries: List<WeightEntry>) = setWeightEntriesImpl(entries)
    val bodyFatEntries: Flow<List<BodyFatEntry>> get() = bodyFatEntriesImpl
    suspend fun setBodyFatEntries(entries: List<BodyFatEntry>) = setBodyFatEntriesImpl(entries)
    val bodyMeasurements: Flow<List<BodyMeasurement>> get() = bodyMeasurementsImpl
    suspend fun setBodyMeasurements(entries: List<BodyMeasurement>) = setBodyMeasurementsImpl(entries)
    val chatHistory: Flow<List<ChatMessage>> get() = chatHistoryImpl
    suspend fun setChatHistory(history: List<ChatMessage>) = setChatHistoryImpl(history)
    val widgetSnapshot: Flow<WidgetSnapshot?> get() = widgetSnapshotImpl
    suspend fun setWidgetSnapshot(snapshot: WidgetSnapshot) = setWidgetSnapshotImpl(snapshot)
    suspend fun clearWidgetSnapshot() = clearWidgetSnapshotImpl()
    val testSeedBackupJson: Flow<String?> get() = testSeedBackupJsonImpl
    suspend fun setTestSeedBackupJson(json: String) = setTestSeedBackupJsonImpl(json)
    suspend fun clearTestSeedBackup() = clearTestSeedBackupImpl()
    suspend fun setDebugActivityDays(days: List<DebugActivityDay>) = setDebugActivityDaysImpl(days)
    suspend fun clearDebugActivityDays() = clearDebugActivityDaysImpl()
    suspend fun debugActivityDaysJson(): String? = debugActivityDaysJsonImpl()
    suspend fun debugActivityDay(date: LocalDate): DebugActivityDay? = debugActivityDayImpl(date)
    val debugShowRestingShade: Flow<Boolean> get() = debugShowRestingShadeImpl
    suspend fun setDebugShowRestingShade(show: Boolean) = setDebugShowRestingShadeImpl(show)
    val debugDemoAnalysis: Flow<Boolean> get() = debugDemoAnalysisImpl
    suspend fun setDebugDemoAnalysis(enabled: Boolean) = setDebugDemoAnalysisImpl(enabled)
    val syncRevisions: Flow<Map<String, SyncRevision>> get() = syncRevisionsImpl
    suspend fun setSyncRevisions(revisions: Map<String, SyncRevision>) = setSyncRevisionsImpl(revisions)
    val webDavUrl: Flow<String> get() = webDavUrlImpl
    suspend fun setWebDavUrl(url: String) = setWebDavUrlImpl(url)
    val webDavUsername: Flow<String> get() = webDavUsernameImpl
    suspend fun setWebDavUsername(username: String) = setWebDavUsernameImpl(username)
    val webDavEnabled: Flow<Boolean> get() = webDavEnabledImpl
    suspend fun setWebDavEnabled(enabled: Boolean) = setWebDavEnabledImpl(enabled)
    val webDavAutoSyncDay: Flow<String?> get() = webDavAutoSyncDayImpl
    suspend fun setWebDavAutoSyncDay(day: String?) = setWebDavAutoSyncDayImpl(day)
    val lastSyncAt: Flow<String?> get() = lastSyncAtImpl
    suspend fun setLastSyncAt(iso: String?) = setLastSyncAtImpl(iso)
    val lastSyncEtag: Flow<String?> get() = lastSyncEtagImpl
    suspend fun setLastSyncEtag(etag: String?) = setLastSyncEtagImpl(etag)
    suspend fun clearAll() = clearAllImpl()
}
