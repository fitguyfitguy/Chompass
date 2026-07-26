package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.UserProfile
import app.chompass.ui.onboarding.OnboardingDraft
import app.chompass.ui.theme.AppThemeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val HOME_DISPLAY_LAYOUT_VERSION = 2

// -- User profile -----------------------------------------------------
internal val PreferencesStore.userProfileImpl: Flow<UserProfile?> get() = dataStore.data.map { prefs ->
        prefs[Keys.USER_PROFILE]?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }
    }

internal suspend fun PreferencesStore.setUserProfileImpl(profile: UserProfile) {
        dataStore.edit { it[Keys.USER_PROFILE] = json.encodeToString(UserProfile.serializer(), profile) }
    }

    // -- Onboarding -------------------------------------------------------
internal val PreferencesStore.hasCompletedOnboardingImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
internal suspend fun PreferencesStore.setOnboardingCompletedImpl(value: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

internal val PreferencesStore.onboardingDraftImpl: Flow<OnboardingDraft?> get() = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DRAFT]?.let { runCatching { json.decodeFromString<OnboardingDraft>(it) }.getOrNull() }
    }
internal suspend fun PreferencesStore.setOnboardingDraftImpl(draft: OnboardingDraft?) {
        dataStore.edit {
            if (draft == null) {
                it.remove(Keys.ONBOARDING_DRAFT)
            } else {
                it[Keys.ONBOARDING_DRAFT] = json.encodeToString(OnboardingDraft.serializer(), draft)
            }
        }
    }

internal val PreferencesStore.hasSeenCameraScaleTipImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HAS_SEEN_CAMERA_SCALE_TIP] ?: false }
internal suspend fun PreferencesStore.setHasSeenCameraScaleTipImpl(value: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_CAMERA_SCALE_TIP] = value }
    }

    // -- Units ------------------------------------------------------------
internal val PreferencesStore.useMetricImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.USE_METRIC] ?: true }
internal suspend fun PreferencesStore.setUseMetricImpl(v: Boolean) { dataStore.edit { it[Keys.USE_METRIC] = v } }

    /** "cm" | "ftin". Falls back to the legacy useMetric flag when unset. */
internal val PreferencesStore.heightUnitImpl: Flow<String> get() = dataStore.data.map {
        it[Keys.HEIGHT_UNIT] ?: (if (it[Keys.USE_METRIC] ?: true) "cm" else "ftin")
    }
internal suspend fun PreferencesStore.setHeightUnitImpl(v: String) { dataStore.edit { it[Keys.HEIGHT_UNIT] = v } }

    /** "kg" | "lbs". Falls back to the legacy useMetric flag when unset. */
internal val PreferencesStore.weightUnitImpl: Flow<String> get() = dataStore.data.map {
        it[Keys.WEIGHT_UNIT] ?: (if (it[Keys.USE_METRIC] ?: true) "kg" else "lbs")
    }
internal suspend fun PreferencesStore.setWeightUnitImpl(v: String) { dataStore.edit { it[Keys.WEIGHT_UNIT] = v } }

internal val PreferencesStore.preferGramsByDefaultImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.PREFER_GRAMS_BY_DEFAULT] ?: false }
internal suspend fun PreferencesStore.setPreferGramsByDefaultImpl(v: Boolean) { dataStore.edit { it[Keys.PREFER_GRAMS_BY_DEFAULT] = v } }

    /** "system" | "light" | "dark". Mirrors iOS @AppStorage("appearanceMode"). */
internal val PreferencesStore.appearanceModeImpl: Flow<String> get() = dataStore.data.map { it[Keys.APPEARANCE_MODE] ?: "system" }
internal suspend fun PreferencesStore.setAppearanceModeImpl(v: String) { dataStore.edit { it[Keys.APPEARANCE_MODE] = v } }

    /** User-selected accent; legacy keys are migrated to the curated 8-color set. */
internal val PreferencesStore.appThemeColorImpl: Flow<String> get() = dataStore.data.map {
        AppThemeColor.migrateKey(it[Keys.APP_THEME_COLOR] ?: AppThemeColor.DEFAULT_KEY)
    }
internal suspend fun PreferencesStore.setAppThemeColorImpl(v: String) { dataStore.edit { it[Keys.APP_THEME_COLOR] = v } }

    /** Controls whether glass surfaces try to use a real blur effect (API 31+). Default OFF. */
internal val PreferencesStore.glassBlurEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.GLASS_BLUR_ENABLED] ?: false }
internal suspend fun PreferencesStore.setGlassBlurEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.GLASS_BLUR_ENABLED] = v } }

    /** false = Sunday, true = Monday (default). Mirrors iOS @AppStorage("weekStartsOnMonday"). */
internal val PreferencesStore.weekStartsOnMondayImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.WEEK_STARTS_MONDAY] ?: true }
internal suspend fun PreferencesStore.setWeekStartsOnMondayImpl(v: Boolean) { dataStore.edit { it[Keys.WEEK_STARTS_MONDAY] = v } }

    /** "RECENTS" | "FREQUENT" | "FAVORITES". Mirrors iOS @AppStorage("lastRecentsSegment"). */
internal val PreferencesStore.lastSavedMealsSegmentImpl: Flow<String> get() = dataStore.data.map { it[Keys.LAST_SAVED_MEALS_SEGMENT] ?: "RECENTS" }
internal suspend fun PreferencesStore.setLastSavedMealsSegmentImpl(v: String) { dataStore.edit { it[Keys.LAST_SAVED_MEALS_SEGMENT] = v } }

    /** "standard" | "latestMealsFirst". Mirrors iOS @AppStorage("foodLogSortOrder"). */
internal val PreferencesStore.foodLogSortOrderImpl: Flow<String> get() = dataStore.data.map { it[Keys.FOOD_LOG_SORT_ORDER] ?: "standard" }
internal suspend fun PreferencesStore.setFoodLogSortOrderImpl(v: String) { dataStore.edit { it[Keys.FOOD_LOG_SORT_ORDER] = v } }

    /** Comma-separated [HomeTopNutrient.storageKey] values for the home nutrient cards. */
internal val PreferencesStore.homeTopNutrientsImpl: Flow<String> get() = dataStore.data.map {
        it[Keys.HOME_TOP_NUTRIENTS] ?: HomeTopNutrient.DefaultStorageValue
    }
internal suspend fun PreferencesStore.setHomeTopNutrientsImpl(v: String) {
        dataStore.edit { it[Keys.HOME_TOP_NUTRIENTS] = v }
    }

internal val PreferencesStore.homeNutrientCardCountImpl: Flow<Int> get() = dataStore.data.map {
        it[Keys.HOME_NUTRIENT_CARD_COUNT] ?: HomeDisplayPreferences.DEFAULT_NUTRIENT_CARD_COUNT
    }
internal suspend fun PreferencesStore.setHomeNutrientCardCountImpl(v: Int) {
        val safe = v.coerceIn(
            HomeDisplayPreferences.MIN_NUTRIENT_CARD_COUNT,
            HomeDisplayPreferences.MAX_NUTRIENT_CARD_COUNT
        )
        dataStore.edit { it[Keys.HOME_NUTRIENT_CARD_COUNT] = safe }
    }

internal val PreferencesStore.homeShowStepsImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HOME_SHOW_STEPS] ?: false }
internal suspend fun PreferencesStore.setHomeShowStepsImpl(v: Boolean) { dataStore.edit { it[Keys.HOME_SHOW_STEPS] = v } }

internal val PreferencesStore.homeShowActiveCaloriesImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HOME_SHOW_ACTIVE_CALORIES] ?: false }
internal suspend fun PreferencesStore.setHomeShowActiveCaloriesImpl(v: Boolean) { dataStore.edit { it[Keys.HOME_SHOW_ACTIVE_CALORIES] = v } }

internal val PreferencesStore.homeStepGoalImpl: Flow<Int> get() = dataStore.data.map {
        it[Keys.HOME_STEP_GOAL] ?: HomeDisplayPreferences.DEFAULT_STEP_GOAL
    }
internal suspend fun PreferencesStore.setHomeStepGoalImpl(v: Int) {
        val safe = v.coerceIn(HomeDisplayPreferences.MIN_STEP_GOAL, HomeDisplayPreferences.MAX_STEP_GOAL)
        dataStore.edit { it[Keys.HOME_STEP_GOAL] = safe }
    }

internal val PreferencesStore.homeCalorieDisplayModeImpl: Flow<String> get() = dataStore.data.map {
        it[Keys.HOME_CALORIE_DISPLAY_MODE] ?: HomeCalorieDisplayMode.Default.storageKey
    }
internal suspend fun PreferencesStore.setHomeCalorieDisplayModeImpl(v: String) {
        dataStore.edit { it[Keys.HOME_CALORIE_DISPLAY_MODE] = v }
    }

internal val PreferencesStore.foodLogMacroChipsImpl: Flow<String> get() = dataStore.data.map {
        it[Keys.FOOD_LOG_MACRO_CHIPS] ?: FoodLogMacroChip.DefaultStorageValue
    }
internal suspend fun PreferencesStore.setFoodLogMacroChipsImpl(v: String) {
        dataStore.edit { it[Keys.FOOD_LOG_MACRO_CHIPS] = v }
    }

    /**
     * One-shot migration from the first home-layout prototype (separate activity
     * cards on by default, settings gated on Health Connect). Resets activity
     * cards off; burn stays integrated in the calorie gauge via [HOME_CALORIE_DISPLAY_MODE].
     */
internal suspend fun PreferencesStore.migrateHomeDisplayLayoutIfNeededImpl() {
        dataStore.edit { prefs ->
            if ((prefs[Keys.HOME_DISPLAY_LAYOUT_VERSION] ?: 0) >= HOME_DISPLAY_LAYOUT_VERSION) return@edit
            prefs[Keys.HOME_SHOW_STEPS] = false
            prefs[Keys.HOME_SHOW_ACTIVE_CALORIES] = false
            if (prefs[Keys.HOME_CALORIE_DISPLAY_MODE] == null) {
                prefs[Keys.HOME_CALORIE_DISPLAY_MODE] = HomeCalorieDisplayMode.Default.storageKey
            }
            prefs[Keys.HOME_DISPLAY_LAYOUT_VERSION] = HOME_DISPLAY_LAYOUT_VERSION
        }
    }

internal val PreferencesStore.homeDisplayPreferencesImpl: Flow<HomeDisplayPreferences> get() = dataStore.data.map { prefs ->
        val cardCount = prefs[Keys.HOME_NUTRIENT_CARD_COUNT] ?: HomeDisplayPreferences.DEFAULT_NUTRIENT_CARD_COUNT
        val nutrientsRaw = prefs[Keys.HOME_TOP_NUTRIENTS] ?: HomeTopNutrient.DefaultStorageValue
        HomeDisplayPreferences(
            nutrientCardCount = cardCount,
            homeTopNutrients = HomeTopNutrient.fromStorage(nutrientsRaw, cardCount),
            showSteps = prefs[Keys.HOME_SHOW_STEPS] ?: false,
            showActiveCalories = prefs[Keys.HOME_SHOW_ACTIVE_CALORIES] ?: false,
            stepGoal = prefs[Keys.HOME_STEP_GOAL] ?: HomeDisplayPreferences.DEFAULT_STEP_GOAL,
            calorieDisplayMode = HomeCalorieDisplayMode.fromStorage(prefs[Keys.HOME_CALORIE_DISPLAY_MODE]),
            foodLogMacroChips = FoodLogMacroChip.fromStorage(prefs[Keys.FOOD_LOG_MACRO_CHIPS]),
        )
    }

    /** Goals for nutrients outside the calorie/protein/carb/fat calculator. */
internal val PreferencesStore.optionalNutrientGoalsImpl: Flow<OptionalNutrientGoals> get() = dataStore.data.map { prefs ->
        prefs[Keys.OPTIONAL_NUTRIENT_GOALS]?.let {
            runCatching { json.decodeFromString<OptionalNutrientGoals>(it) }.getOrNull()
        } ?: OptionalNutrientGoals.Default
    }
internal suspend fun PreferencesStore.setOptionalNutrientGoalsImpl(goals: OptionalNutrientGoals) {
        dataStore.edit {
            it[Keys.OPTIONAL_NUTRIENT_GOALS] =
                json.encodeToString(OptionalNutrientGoals.serializer(), goals)
        }
    }

// -- Recalculate nudge -----------------------------------------------
// Fingerprint of the goal inputs at the last Recalculate. When it differs from the current
// profile, Settings shows a soft "recalculate suggested" hint. null = no baseline yet.
internal val PreferencesStore.lastRecalcGoalSignatureImpl: Flow<String?> get() = dataStore.data.map { it[Keys.LAST_RECALC_GOAL_SIGNATURE] }
internal suspend fun PreferencesStore.setLastRecalcGoalSignatureImpl(value: String) {
    dataStore.edit { it[Keys.LAST_RECALC_GOAL_SIGNATURE] = value }
}

