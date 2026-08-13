package app.chompass.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.chompass.models.SpeechProvider
import java.time.YearMonth

internal const val FOOD_ENTRIES_BUCKET_PREFIX = "foodEntries_"

internal object Keys {
        val USER_PROFILE = stringPreferencesKey("userProfile")
        val LAST_RECALC_GOAL_SIGNATURE = stringPreferencesKey("lastRecalcGoalSignature")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("hasCompletedOnboarding")
        val ONBOARDING_DRAFT = stringPreferencesKey("onboardingDraft")
        val HAS_SEEN_CAMERA_SCALE_TIP = booleanPreferencesKey("hasSeenCameraScaleTip")
        /** Epoch-millis of first app launch; gates settings Suggestions so new users aren't nagged. */
        val FIRST_LAUNCH_AT = longPreferencesKey("firstLaunchAt")
        /** Dismissed settings-suggestion ids ("water_tracking", "adaptive_goals", ...). */
        val DISMISSED_SUGGESTIONS = stringSetPreferencesKey("dismissedSuggestionIds")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notificationsEnabled")
        val STREAK_ENABLED = booleanPreferencesKey("streakReminderEnabled")
        val STREAK_HOUR = intPreferencesKey("streakReminderHour")
        val STREAK_MINUTE = intPreferencesKey("streakReminderMinute")
        val DAILY_ENABLED = booleanPreferencesKey("dailySummaryEnabled")
        val DAILY_HOUR = intPreferencesKey("dailySummaryHour")
        val DAILY_MINUTE = intPreferencesKey("dailySummaryMinute")
        val WEIGHT_REMINDER_ENABLED = booleanPreferencesKey("weightReminderEnabled")
        val BODY_FAT_REMINDER_ENABLED = booleanPreferencesKey("bodyFatReminderEnabled")
        val GOAL_REACHED_NOTIFICATIONS_ENABLED = booleanPreferencesKey("goalReachedNotificationsEnabled")
        val APP_UPDATE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("appUpdateNotificationsEnabled")
        val WATER_TRACKING_ENABLED = booleanPreferencesKey("waterTrackingEnabled")
        val WATER_DAILY_GOAL_ML = intPreferencesKey("waterDailyGoalMl")
        val WATER_REMINDER_ENABLED = booleanPreferencesKey("waterReminderEnabled")
        val WATER_REMINDER_HOUR = intPreferencesKey("waterReminderHour")
        val WATER_REMINDER_MINUTE = intPreferencesKey("waterReminderMinute")
        // Dynamic goal + adaptive reminders (issue #3); see docs/WATER_DYNAMIC_GOAL_DESIGN.md.
        val WATER_DYNAMIC_ENABLED = booleanPreferencesKey("waterDynamicEnabled")
        /** Base-goal source: WaterGoalCalculator.BASE_SOURCE_WEIGHT or _MANUAL. */
        val WATER_BASE_SOURCE = stringPreferencesKey("waterBaseSource")
        /** Manual expected high °C (no location permission). */
        val WATER_MANUAL_TEMP_C = intPreferencesKey("waterManualTempC")
        val WATER_USE_PROFILE_ACTIVITY = booleanPreferencesKey("waterUseProfileActivity")
        val WATER_FOOD_WATER_ENABLED = booleanPreferencesKey("waterFoodWaterEnabled")
        /** Drinking-window start/end (minutes-of-day via hour+minute pairs). */
        val WATER_AWAKE_START_HOUR = intPreferencesKey("waterAwakeStartHour")
        val WATER_AWAKE_START_MINUTE = intPreferencesKey("waterAwakeStartMinute")
        val WATER_AWAKE_END_HOUR = intPreferencesKey("waterAwakeEndHour")
        val WATER_AWAKE_END_MINUTE = intPreferencesKey("waterAwakeEndMinute")
        /** Standard cup for the reminder-interval math (independent of quick presets). */
        val WATER_CUP_SIZE_ML = intPreferencesKey("waterCupSizeMl")
        // Weather input for the dynamic water goal (issue #3 Phase 5); see
        // docs/WEATHER_INTEGRATION_DESIGN.md. Temperature sources: manual °C
        // wheel or Open-Meteo (city forecast, no key/account/permission).
        /** Temperature source: WeatherRepository.SOURCE_MANUAL / _OPEN_METEO. */
        val WEATHER_SOURCE = stringPreferencesKey("weatherSource")
        /** Selected Open-Meteo city (JSON of OmCity); null = none chosen yet. */
        val WEATHER_OM_CITY = stringPreferencesKey("weatherOmCity")
        /** Today's high °C from the last successful Open-Meteo fetch. */
        val WEATHER_OM_HIGH_C = intPreferencesKey("weatherOmHighC")
        /** Local date (yyyy-MM-dd) the Open-Meteo high applies to; stale when != today. */
        val WEATHER_OM_DATE = stringPreferencesKey("weatherOmDate")
        /** Epoch-millis of the last successful Open-Meteo fetch. */
        val WEATHER_OM_UPDATED_AT = longPreferencesKey("weatherOmUpdatedAt")
        val WATER_QUICK_PRESETS_ML = stringPreferencesKey("waterQuickPresetsMl")
        val WATER_ENTRIES = stringPreferencesKey("waterEntries")
        val MANUAL_ACTIVE_ENTRIES = stringPreferencesKey("manualActiveEntries")
        val LAST_NOTIFIED_UPDATE_VERSION = stringPreferencesKey("lastNotifiedUpdateVersion")
        val HEALTH_CONNECT_ENABLED = booleanPreferencesKey("healthConnectEnabled")
        val HEALTH_TYPES_VERSION = intPreferencesKey("healthTypesVersion")
        val HEALTH_CHANGES_TOKEN = stringPreferencesKey("healthChangesToken")
        val HEALTH_CHANGES_TOKEN_TYPES = stringPreferencesKey("healthChangesTokenTypes")
        val HEALTH_FOOD_RESTORE_DONE = booleanPreferencesKey("healthFoodRestoreDone")
        val HEALTH_HYDRATION_RESTORE_DONE = booleanPreferencesKey("healthHydrationRestoreDone")
        val HEALTH_ENERGY_GOALS_ENABLED = booleanPreferencesKey("healthEnergyGoalsEnabled")
        /** Measured Health Connect active kcal/day average used to split the Energy Burn goal
         *  into a sedentary base for the ADD_ACTIVE home gauge. 0 = not available. */
        val HEALTH_ENERGY_MEASURED_ACTIVE = intPreferencesKey("healthEnergyMeasuredActiveCalories")
        val HEALTH_BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("healthBackgroundSyncEnabled")
        val HEALTH_ENERGY_GOALS_PREVIOUS_TARGETS = stringPreferencesKey("healthEnergyGoalsPreviousTargets")
        val HEALTH_ENERGY_GOALS_LAST_AUTO_REFRESH_DAY = stringPreferencesKey("healthEnergyGoalsLastAutoRefreshDay")
        val ADAPTIVE_GOALS_ENABLED = booleanPreferencesKey("adaptiveGoalsEnabled")
        val REVIEW_PROMPTED_AFTER_FIRST_LOG = booleanPreferencesKey("reviewPromptedAfterFirstLog")
        val ADAPTIVE_GOALS_PREVIOUS_TARGETS = stringPreferencesKey("adaptiveGoalsPreviousTargets")
        val ADAPTIVE_GOALS_LAST_CHECK_DAY = stringPreferencesKey("adaptiveGoalsLastCheckDay")
        val USE_METRIC = booleanPreferencesKey("useMetric")
        val HEIGHT_UNIT = stringPreferencesKey("heightUnit")
        val WEIGHT_UNIT = stringPreferencesKey("weightUnit")
        val PREFER_GRAMS_BY_DEFAULT = booleanPreferencesKey("foodMeasurementPreferGramsByDefault")
        val APPEARANCE_MODE = stringPreferencesKey("appearanceMode")
        val APP_THEME_COLOR = stringPreferencesKey("appThemeColor")
        val GLASS_BLUR_ENABLED = booleanPreferencesKey("glassBlurEnabled")
        val WEEK_STARTS_MONDAY = booleanPreferencesKey("weekStartsOnMonday")
        /** Factory / Settings default for Progress tab range chips (`1W`…`All`). */
        val PROGRESS_DEFAULT_RANGE_ID = stringPreferencesKey("progressDefaultRangeId")
        /** Last Progress range the user selected; null until first chip tap. */
        val PROGRESS_LAST_RANGE_ID = stringPreferencesKey("progressLastRangeId")
        /** Body-measurement sites with a trend plot on the Progress tab; empty = plots off. */
        val PROGRESS_MEASUREMENT_SITES = stringSetPreferencesKey("progressMeasurementSites")
        val MEAL_BREAKFAST_START = intPreferencesKey("mealBreakfastStart")
        val MEAL_LUNCH_START = intPreferencesKey("mealLunchStart")
        val MEAL_DINNER_START = intPreferencesKey("mealDinnerStart")
        val MEAL_SNACK_START = intPreferencesKey("mealSnackStart")
        val LAST_SAVED_MEALS_SEGMENT = stringPreferencesKey("lastRecentsSegment")
        val FOOD_LOG_SORT_ORDER = stringPreferencesKey("foodLogSortOrder")
        val HOME_TOP_NUTRIENTS = stringPreferencesKey("homeTopNutrients")
        val HOME_NUTRIENT_CARD_COUNT = intPreferencesKey("homeNutrientCardCount")
        val HOME_SHOW_STEPS = booleanPreferencesKey("homeShowSteps")
        val HOME_SHOW_ACTIVE_CALORIES = booleanPreferencesKey("homeShowActiveCalories")
        val HOME_STEP_GOAL = intPreferencesKey("homeStepGoal")
        val HOME_CALORIE_DISPLAY_MODE = stringPreferencesKey("homeCalorieDisplayMode")
        val HOME_DISPLAY_LAYOUT_VERSION = intPreferencesKey("homeDisplayLayoutVersion")
        val FOOD_LOG_MACRO_CHIPS = stringPreferencesKey("foodLogMacroChips")
        val OPTIONAL_NUTRIENT_GOALS = stringPreferencesKey("optionalNutrientGoals")
        val SELECTED_AI_PROVIDER = stringPreferencesKey("selectedAIProvider")
        val SELECTED_AI_MODEL = stringPreferencesKey("selectedAIModel")
        val MAX_RESPONSE_TOKENS = intPreferencesKey("maxResponseTokens")
        val AI_READ_TIMEOUT_SECONDS = intPreferencesKey("aiReadTimeoutSeconds")
        val SERVING_UNIT_INFERENCE_MODE = stringPreferencesKey("servingUnitInferenceMode")
        val HEURISTIC_SERVING_UNIT_SETTINGS = stringPreferencesKey("heuristicServingUnitSettings")
        val USER_CONTEXT = stringPreferencesKey("userContext")
        val FALLBACK_ENABLED = booleanPreferencesKey("aiFallbackEnabled")
        val FALLBACK_PROVIDER = stringPreferencesKey("selectedFallbackAIProvider")
        val FALLBACK_MODEL = stringPreferencesKey("selectedFallbackAIModel")
        val GEMINI_GOOGLE_SEARCH_ENABLED = booleanPreferencesKey("geminiGoogleSearchEnabled")
        val PORTION_CLARIFY_ENABLED = booleanPreferencesKey("portionClarifyEnabled")
        /**
         * When true, photo staging does not require a text note before Analyze
         * (user opted out after repeatedly skipping).
         */
        val SKIP_PHOTO_NOTE_PROMPT = booleanPreferencesKey("skipPhotoNotePrompt")
        /** Consecutive photo analyzes submitted with an empty note while the prompt was on. */
        val PHOTO_NOTE_SKIP_COUNT = intPreferencesKey("photoNoteSkipCount")
        /**
         * How many photo staging Analyzes the user has completed. Used to show
         * the accuracy tip card for the first few entries, then collapse to Info.
         */
        val PHOTO_ACCURACY_GUIDE_COUNT = intPreferencesKey("photoAccuracyGuideCount")
        /** Opt-in meal ingredient breakdown from AI (off for on-device / weak models). */
        val MEAL_CONSTITUENTS_ENABLED = booleanPreferencesKey("mealConstituentsEnabled")
        val SELECTED_SPEECH_PROVIDER = stringPreferencesKey("selectedSpeechProvider")
        fun selectedSpeechLanguage(provider: SpeechProvider) =
            stringPreferencesKey("selectedSpeechLanguage_${provider.name}")
        val FOOD_ENTRIES = stringPreferencesKey("foodEntries") // legacy, kept only for one-time migration
        val FOOD_ENTRIES_MIGRATED = booleanPreferencesKey("foodEntriesMigrated")
        fun foodEntriesBucket(month: YearMonth): Preferences.Key<String> =
            stringPreferencesKey(FOOD_ENTRIES_BUCKET_PREFIX + month.toString())
        val FAVORITE_KEYS = stringPreferencesKey("favorites")
        val FAVORITE_ENTRIES = stringPreferencesKey("favoriteFoodEntries")
        val RECIPES = stringPreferencesKey("recipes")
        val PENDING_FOOD_ANALYSIS_DRAFT = stringPreferencesKey("pendingFoodAnalysisDraft")
        val PENDING_FOOD_INPUT_DRAFT = stringPreferencesKey("pendingFoodInputDraft")
        val WEIGHT_ENTRIES = stringPreferencesKey("weightEntries")
        val BODY_FAT_ENTRIES = stringPreferencesKey("bodyFatEntries")
        val BODY_MEASUREMENTS = stringPreferencesKey("bodyMeasurements")
        val CHAT_HISTORY = stringPreferencesKey("coachChatHistory")
        val WIDGET_SNAPSHOT = stringPreferencesKey("widget_snapshot_v1")
        val TEST_SEED_BACKUP = stringPreferencesKey("test_seed_backup_v1")
        val DEBUG_ACTIVITY_DAYS = stringPreferencesKey("debugActivityDays")
        /** Debug-only: show the resting (basal) burn rim in the home hero arc (A/B comparison). */
        val DEBUG_SHOW_RESTING_SHADE = booleanPreferencesKey("debugShowRestingShade")
        /** Debug-only: replay a scripted food-analysis response (demo_ai intent extra; video capture). */
        val DEBUG_DEMO_ANALYSIS = booleanPreferencesKey("debugDemoAnalysis")
        val BARCODE_CACHE = stringPreferencesKey("barcodeLookupCache")
        val ON_DEVICE_MODEL_DOWNLOADED_VERSION = stringPreferencesKey("onDeviceModelDownloadedVersion")
        val ON_DEVICE_DOWNLOAD_OVER_WIFI_ONLY = booleanPreferencesKey("onDeviceDownloadOverWifiOnly")
        val ON_DEVICE_FEATURE_VISIBLE = booleanPreferencesKey("onDeviceFeatureVisible")
        val SYNC_REVISIONS = stringPreferencesKey("syncRevisions")
        val WEBDAV_URL = stringPreferencesKey("webDavUrl")
        val WEBDAV_USERNAME = stringPreferencesKey("webDavUsername")
        /** Opt-in: auto WebDAV sync once per day on app open. */
        val WEBDAV_ENABLED = booleanPreferencesKey("webDavEnabled")
        /** Local calendar day (yyyy-MM-dd) of the last auto-sync attempt. */
        val WEBDAV_AUTO_SYNC_DAY = stringPreferencesKey("webDavAutoSyncDay")
        val LAST_SYNC_AT = stringPreferencesKey("lastSyncAt")
        val LAST_SYNC_ETAG = stringPreferencesKey("lastSyncEtag")
}
