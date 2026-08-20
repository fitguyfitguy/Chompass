package app.chompass.data

import androidx.datastore.preferences.core.Preferences
import app.chompass.models.AIProvider
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.models.MealSchedule
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.SpeechLanguage
import app.chompass.models.SpeechProvider
import app.chompass.models.WaterGoalCalculator
import app.chompass.models.WaterQuickPresets
import app.chompass.ui.theme.AppThemeColor
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** One-shot Settings hydration from a single DataStore snapshot. */
internal data class SettingsPrefsHydration(
    val selectedAI: AIProvider,
    val selectedModelRaw: String?,
    val visionModelRaw: String?,
    val selectedSpeech: SpeechProvider,
    val selectedSpeechLanguage: SpeechLanguage,
    val heightUnit: String,
    val weightUnit: String,
    val preferGramsByDefault: Boolean,
    val notificationsEnabled: Boolean,
    val streakReminderEnabled: Boolean,
    val dailySummaryEnabled: Boolean,
    val dailySummaryHour: Int,
    val dailySummaryMinute: Int,
    val weightReminderEnabled: Boolean,
    val bodyFatReminderEnabled: Boolean,
    val waterTrackingEnabled: Boolean,
    val waterDailyGoalMl: Int,
    val waterQuickPresetsMl: List<Int>,
    val waterReminderEnabled: Boolean,
    val waterDynamicEnabled: Boolean,
    val waterBaseSource: String,
    val waterManualTempC: Int,
    val waterUseProfileActivity: Boolean,
    val waterFoodWaterEnabled: Boolean,
    val waterAwakeStartMinutes: Int,
    val waterAwakeEndMinutes: Int,
    val waterCupSizeMl: Int,
    val goalReachedNotificationsEnabled: Boolean,
    val appUpdateNotificationsEnabled: Boolean,
    val healthEnergyGoalsEnabled: Boolean,
    val healthBackgroundSyncEnabled: Boolean,
    val adaptiveGoalsEnabled: Boolean,
    val onDeviceFeatureVisible: Boolean,
    val appearanceMode: String,
    val appLanguage: String,
    val coachTabEnabled: Boolean,
    val aiFeaturesEnabled: Boolean,
    val allowInsecureHttp: Boolean,
    val appThemeColorKey: String,
    val fixedLauncherIcon: Boolean,
    val foodLogSortOrderRaw: String,
    val weekStartsOnMonday: Boolean,
    val progressDefaultRangeId: String,
    val progressMeasurementSites: Set<String>,
    val userContext: String,
    val maxResponseTokens: Int,
    val aiReadTimeoutSeconds: Int,
    val servingUnitInferenceMode: ServingUnitInferenceMode,
    val heuristicServingUnitSettings: HeuristicServingUnitSettings,
    val fallbackEnabled: Boolean,
    val fallbackProvider: AIProvider,
    val fallbackModelRaw: String?,
    val geminiGoogleSearchEnabled: Boolean,
    val openRouterReasoningEffort: OpenRouterReasoningEffort,
    val portionClarifyEnabled: Boolean,
    val mealConstituentsEnabled: Boolean,
    val skipPhotoNotePrompt: Boolean,
    val optionalNutrientGoals: OptionalNutrientGoals,
    val homeDisplay: HomeDisplayPreferences,
    val mealSchedule: MealSchedule,
    val lastRecalcGoalSignature: String?,
)

internal suspend fun PreferencesStore.readSettingsHydration(): SettingsPrefsHydration =
    dataStore.data.first().toSettingsHydration(json)

internal fun Preferences.toSettingsHydration(json: Json): SettingsPrefsHydration {
    val metric = this[Keys.USE_METRIC] ?: true
    val provider = AIProvider.entries.firstOrNull { it.name == this[Keys.SELECTED_AI_PROVIDER] }
        ?: AIProvider.GEMINI
    val speech = SpeechProvider.entries.firstOrNull { it.name == this[Keys.SELECTED_SPEECH_PROVIDER] }
        ?: SpeechProvider.NATIVE
    val fallbackProvider = AIProvider.entries.firstOrNull { it.name == this[Keys.FALLBACK_PROVIDER] }
        ?: AIProvider.GEMINI
    val cardCount = this[Keys.HOME_NUTRIENT_CARD_COUNT] ?: HomeDisplayPreferences.DEFAULT_NUTRIENT_CARD_COUNT
    val nutrientsRaw = this[Keys.HOME_TOP_NUTRIENTS] ?: HomeTopNutrient.DefaultStorageValue
    return SettingsPrefsHydration(
        selectedAI = provider,
        selectedModelRaw = this[Keys.SELECTED_AI_MODEL],
        visionModelRaw = this[Keys.visionModel(provider)],
        selectedSpeech = speech,
        selectedSpeechLanguage = SpeechLanguage.entries.firstOrNull {
            it.name == this[Keys.selectedSpeechLanguage(speech)]
        } ?: SpeechLanguage.defaultFor(speech),
        heightUnit = this[Keys.HEIGHT_UNIT] ?: if (metric) "cm" else "ftin",
        weightUnit = this[Keys.WEIGHT_UNIT] ?: if (metric) "kg" else "lbs",
        preferGramsByDefault = this[Keys.PREFER_GRAMS_BY_DEFAULT] ?: false,
        notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: false,
        streakReminderEnabled = this[Keys.STREAK_ENABLED] ?: false,
        dailySummaryEnabled = this[Keys.DAILY_ENABLED] ?: false,
        dailySummaryHour = this[Keys.DAILY_HOUR] ?: 21,
        dailySummaryMinute = this[Keys.DAILY_MINUTE] ?: 0,
        weightReminderEnabled = this[Keys.WEIGHT_REMINDER_ENABLED] ?: true,
        bodyFatReminderEnabled = this[Keys.BODY_FAT_REMINDER_ENABLED] ?: true,
        waterTrackingEnabled = this[Keys.WATER_TRACKING_ENABLED] ?: false,
        waterDailyGoalMl = this[Keys.WATER_DAILY_GOAL_ML] ?: 2_000,
        waterQuickPresetsMl = WaterQuickPresets.fromStorage(this[Keys.WATER_QUICK_PRESETS_ML]).amountsMl,
        waterReminderEnabled = this[Keys.WATER_REMINDER_ENABLED] ?: false,
        waterDynamicEnabled = this[Keys.WATER_DYNAMIC_ENABLED] ?: false,
        waterBaseSource = this[Keys.WATER_BASE_SOURCE] ?: WaterGoalCalculator.BASE_SOURCE_WEIGHT,
        waterManualTempC = this[Keys.WATER_MANUAL_TEMP_C] ?: 25,
        waterUseProfileActivity = this[Keys.WATER_USE_PROFILE_ACTIVITY] ?: true,
        waterFoodWaterEnabled = this[Keys.WATER_FOOD_WATER_ENABLED] ?: false,
        waterAwakeStartMinutes = (this[Keys.WATER_AWAKE_START_HOUR] ?: 8) * 60 +
            (this[Keys.WATER_AWAKE_START_MINUTE] ?: 0),
        waterAwakeEndMinutes = (this[Keys.WATER_AWAKE_END_HOUR] ?: 21) * 60 +
            (this[Keys.WATER_AWAKE_END_MINUTE] ?: 0),
        waterCupSizeMl = this[Keys.WATER_CUP_SIZE_ML] ?: WaterGoalCalculator.DEFAULT_CUP_SIZE_ML,
        goalReachedNotificationsEnabled = this[Keys.GOAL_REACHED_NOTIFICATIONS_ENABLED] ?: true,
        appUpdateNotificationsEnabled = this[Keys.APP_UPDATE_NOTIFICATIONS_ENABLED] ?: true,
        healthEnergyGoalsEnabled = this[Keys.HEALTH_ENERGY_GOALS_ENABLED] ?: false,
        healthBackgroundSyncEnabled = this[Keys.HEALTH_BACKGROUND_SYNC_ENABLED] ?: false,
        adaptiveGoalsEnabled = this[Keys.ADAPTIVE_GOALS_ENABLED] ?: false,
        onDeviceFeatureVisible = this[Keys.ON_DEVICE_FEATURE_VISIBLE] ?: true,
        appearanceMode = this[Keys.APPEARANCE_MODE] ?: "system",
        appLanguage = this[Keys.APP_LANGUAGE] ?: "",
        coachTabEnabled = this[Keys.COACH_TAB_ENABLED] ?: true,
        aiFeaturesEnabled = this[Keys.AI_FEATURES_ENABLED] ?: true,
        allowInsecureHttp = this[Keys.ALLOW_INSECURE_HTTP] ?: false,
        appThemeColorKey = AppThemeColor.migrateKey(this[Keys.APP_THEME_COLOR] ?: AppThemeColor.DEFAULT_KEY),
        fixedLauncherIcon = this[Keys.FIXED_LAUNCHER_ICON] ?: false,
        foodLogSortOrderRaw = this[Keys.FOOD_LOG_SORT_ORDER] ?: "standard",
        weekStartsOnMonday = this[Keys.WEEK_STARTS_MONDAY] ?: true,
        progressDefaultRangeId = this[Keys.PROGRESS_DEFAULT_RANGE_ID] ?: "1W",
        progressMeasurementSites = this[Keys.PROGRESS_MEASUREMENT_SITES] ?: emptySet(),
        userContext = this[Keys.USER_CONTEXT].orEmpty(),
        maxResponseTokens = clampMaxResponseTokens(this[Keys.MAX_RESPONSE_TOKENS] ?: 1024),
        aiReadTimeoutSeconds = clampAiReadTimeoutSeconds(
            this[Keys.AI_READ_TIMEOUT_SECONDS] ?: DEFAULT_AI_READ_TIMEOUT_SECONDS
        ),
        servingUnitInferenceMode = ServingUnitInferenceMode.fromStorage(this[Keys.SERVING_UNIT_INFERENCE_MODE]),
        heuristicServingUnitSettings = this[Keys.HEURISTIC_SERVING_UNIT_SETTINGS]?.let {
            runCatching { json.decodeFromString(HeuristicServingUnitSettings.serializer(), it) }.getOrNull()
        } ?: HeuristicServingUnitSettings.Default,
        fallbackEnabled = this[Keys.FALLBACK_ENABLED] ?: true,
        fallbackProvider = fallbackProvider,
        fallbackModelRaw = this[Keys.FALLBACK_MODEL],
        geminiGoogleSearchEnabled = this[Keys.GEMINI_GOOGLE_SEARCH_ENABLED] ?: false,
        openRouterReasoningEffort = OpenRouterReasoningEffort.fromStorage(this[Keys.OPENROUTER_REASONING_EFFORT]),
        portionClarifyEnabled = this[Keys.PORTION_CLARIFY_ENABLED] ?: true,
        mealConstituentsEnabled = this[Keys.MEAL_CONSTITUENTS_ENABLED] ?: true,
        skipPhotoNotePrompt = this[Keys.SKIP_PHOTO_NOTE_PROMPT] ?: false,
        optionalNutrientGoals = this[Keys.OPTIONAL_NUTRIENT_GOALS]?.let {
            runCatching { json.decodeFromString(OptionalNutrientGoals.serializer(), it) }.getOrNull()
        } ?: OptionalNutrientGoals.Default,
        homeDisplay = HomeDisplayPreferences(
            nutrientCardCount = cardCount,
            homeTopNutrients = HomeTopNutrient.fromStorage(nutrientsRaw, cardCount),
            showSteps = this[Keys.HOME_SHOW_STEPS] ?: false,
            showActiveCalories = this[Keys.HOME_SHOW_ACTIVE_CALORIES] ?: false,
            stepGoal = this[Keys.HOME_STEP_GOAL] ?: HomeDisplayPreferences.DEFAULT_STEP_GOAL,
            calorieDisplayMode = HomeCalorieDisplayMode.fromStorage(this[Keys.HOME_CALORIE_DISPLAY_MODE]),
            foodLogMacroChips = FoodLogMacroChip.fromStorage(this[Keys.FOOD_LOG_MACRO_CHIPS]),
        ),
        mealSchedule = MealSchedule(
            breakfastStartMinutes = this[Keys.MEAL_BREAKFAST_START] ?: MealSchedule.DEFAULT_BREAKFAST_START,
            lunchStartMinutes = this[Keys.MEAL_LUNCH_START] ?: MealSchedule.DEFAULT_LUNCH_START,
            dinnerStartMinutes = this[Keys.MEAL_DINNER_START] ?: MealSchedule.DEFAULT_DINNER_START,
            snackStartMinutes = this[Keys.MEAL_SNACK_START] ?: MealSchedule.DEFAULT_SNACK_START,
        ).validatedOrDefault(),
        lastRecalcGoalSignature = this[Keys.LAST_RECALC_GOAL_SIGNATURE],
    )
}
