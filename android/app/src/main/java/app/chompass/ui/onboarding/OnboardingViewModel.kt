package app.chompass.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.ActivityLevel
import app.chompass.models.AIProvider
import app.chompass.models.AiProfile
import app.chompass.models.DietMode
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.Gender
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeFoodLogLayout
import app.chompass.models.HomeMacroCardLayout
import app.chompass.models.HomeTopNutrient
import app.chompass.models.InstantSerializer
import app.chompass.models.KetoCarbMode
import app.chompass.models.LocalDateSerializer
import app.chompass.models.UserProfile
import app.chompass.models.WeightGoal
import app.chompass.services.KetoCarbRecommendationService
import app.chompass.services.ai.AiError
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.ai.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Serializable
enum class OnboardingStep {
    WELCOME, GENDER, BIRTHDAY, HEIGHT_WEIGHT, BODY_FAT,
    ACTIVITY, GOAL, DIET_MODE, GOAL_WEIGHT, GOAL_SPEED,
    NOTIFICATIONS, HEALTH_CONNECT, HOME_LOOK, PROVIDER,
    BUILDING_PLAN, PLAN_READY, DISCLAIMERS
}

/** Snapshot of in-progress onboarding answers, persisted to DataStore so the flow can resume
 *  after Android kills the backgrounded process (e.g. the user switches away to copy an API
 *  key) instead of restarting from [OnboardingStep.WELCOME]. Excludes [OnboardingState.apiKey]
 *  (already persisted separately via [AppContainer.keyStore]) and other transient UI-only
 *  fields (validation/submitting flags). */
@Serializable
data class OnboardingDraft(
    val step: OnboardingStep,
    val gender: Gender,
    @Serializable(with = LocalDateSerializer::class)
    val birthday: LocalDate,
    val heightCm: Int,
    val weightKg: Double,
    val bodyFatPercentage: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val bodyFatUpdatedAt: Instant? = null,
    val goalBodyFatPercentage: Double? = null,
    val activity: ActivityLevel,
    val goal: WeightGoal,
    val dietMode: DietMode,
    val ketoCarbMode: KetoCarbMode,
    val ketoCarbManualTarget: Int? = null,
    val goalWeightKg: Double,
    val weeklyChangeKg: Double,
    val notificationsEnabled: Boolean,
    val healthConnectEnabled: Boolean,
    val aiProvider: AIProvider,
    val aiModel: String,
    val customCalories: Int? = null,
    val customProtein: Int? = null,
    val customCarbs: Int? = null,
    val customFat: Int? = null,
)

private fun OnboardingState.toDraft() = OnboardingDraft(
    step = step, gender = gender, birthday = birthday, heightCm = heightCm, weightKg = weightKg,
    bodyFatPercentage = bodyFatPercentage, bodyFatUpdatedAt = bodyFatUpdatedAt,
    goalBodyFatPercentage = goalBodyFatPercentage,
    activity = activity, goal = goal, dietMode = dietMode, ketoCarbMode = ketoCarbMode,
    ketoCarbManualTarget = ketoCarbManualTarget, goalWeightKg = goalWeightKg, weeklyChangeKg = weeklyChangeKg,
    notificationsEnabled = notificationsEnabled, healthConnectEnabled = healthConnectEnabled,
    aiProvider = aiProvider, aiModel = aiModel,
    customCalories = customCalories, customProtein = customProtein, customCarbs = customCarbs, customFat = customFat,
)

private fun OnboardingState.applyDraft(draft: OnboardingDraft) = copy(
    step = draft.step, gender = draft.gender, birthday = draft.birthday, heightCm = draft.heightCm,
    weightKg = draft.weightKg, bodyFatPercentage = draft.bodyFatPercentage,
    bodyFatUpdatedAt = draft.bodyFatUpdatedAt,
    goalBodyFatPercentage = draft.goalBodyFatPercentage,
    activity = draft.activity, goal = draft.goal, dietMode = draft.dietMode, ketoCarbMode = draft.ketoCarbMode,
    ketoCarbManualTarget = draft.ketoCarbManualTarget, goalWeightKg = draft.goalWeightKg,
    weeklyChangeKg = draft.weeklyChangeKg,
    notificationsEnabled = draft.notificationsEnabled, healthConnectEnabled = draft.healthConnectEnabled,
    aiProvider = draft.aiProvider, aiModel = draft.aiModel,
    customCalories = draft.customCalories, customProtein = draft.customProtein,
    customCarbs = draft.customCarbs, customFat = draft.customFat,
)

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val gender: Gender = Gender.MALE,
    val birthday: LocalDate = LocalDate.now().minusYears(25),
    val heightCm: Int = 175,
    val weightKg: Double = 70.0,
    val bodyFatPercentage: Double? = null,
    /** When the body-fat reading was entered; mirrors UserProfile.bodyFatUpdatedAt so the
     *  90-day Katch-McArdle recency gate survives onboarding resume. */
    val bodyFatUpdatedAt: Instant? = null,
    /** Optional target body-fat fraction. Only meaningful when bodyFatPercentage
     *  is non-null (i.e. user picked "Yes I know my body fat" + opted into
     *  setting a goal). Display-only — does NOT participate in BMR/TDEE/macro math. */
    val goalBodyFatPercentage: Double? = null,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: WeightGoal = WeightGoal.MAINTAIN,
    val dietMode: DietMode = DietMode.STANDARD,
    val ketoCarbMode: KetoCarbMode = KetoCarbMode.ADAPTIVE,
    val ketoCarbManualTarget: Int? = null,
    val goalWeightKg: Double = 70.0,
    /** 0.25 (slow), 0.5 (moderate), 1.0 (fast) kg/week */
    val weeklyChangeKg: Double = 0.5,
    /** iOS defaults onboarding to Imperial; match that. Seeded from the persisted
     *  heightUnit/weightUnit prefs in init, and the single Imperial|Metric toggle
     *  writes both together so they stay coherent during onboarding. */
    val heightMetric: Boolean = false,
    val weightMetric: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val healthConnectEnabled: Boolean = false,
    val homeDisplay: HomeDisplayPreferences = HomeDisplayPreferences(),
    val aiProvider: AIProvider = AIProvider.GEMINI,
    val aiModel: String = AIProvider.GEMINI.defaultModel,
    val apiKey: String = "",
    /** Last Gemini key string that passed the lightweight models-list probe. */
    val validatedApiKey: String = "",
    val apiKeyTesting: Boolean = false,
    val apiKeyTestMessage: String = "",
    val apiKeyTestOk: Boolean? = null,
    val submitting: Boolean = false,
    /** Manual overrides applied on the Plan Ready step. Null = use formula default. */
    val customCalories: Int? = null,
    val customProtein: Int? = null,
    val customCarbs: Int? = null,
    val customFat: Int? = null
) {
    /** DISCLAIMERS is the final step (safety + accuracy notices after the plan). */
    val isLastStep: Boolean get() = step == OnboardingStep.DISCLAIMERS

    /** Provider step is skippable: empty cloud keys open the skip dialog in the UI.
     *  Disable only while a Gemini probe is in flight. */
    val canAdvance: Boolean get() = when (step) {
        OnboardingStep.PROVIDER -> !apiKeyTesting
        else -> true
    }

    /** Continue/Skip should warn and optionally switch to on-device when leaving without a key. */
    val needsAiSkipConfirm: Boolean
        get() = step == OnboardingStep.PROVIDER &&
            aiProvider.requiresApiKey &&
            apiKey.trim().isEmpty()

    fun buildProfile(): UserProfile = UserProfile(
        gender = gender,
        birthday = birthday.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        heightCm = heightCm.toDouble(),
        weightKg = weightKg,
        activityLevel = activity,
        goal = goal,
        dietMode = dietMode,
        ketoCarbMode = ketoCarbMode,
        ketoCarbManualTarget = ketoCarbManualTarget,
        bodyFatPercentage = bodyFatPercentage,
        bodyFatUpdatedAt = bodyFatUpdatedAt,
        goalBodyFatPercentage = if (bodyFatPercentage != null) goalBodyFatPercentage else null,
        weeklyChangeKg = if (goal == WeightGoal.MAINTAIN) null else weeklyChangeKg,
        goalWeightKg = if (goal == WeightGoal.MAINTAIN) null else goalWeightKg,
        customCalories = customCalories,
        customProtein = customProtein,
        customCarbs = customCarbs,
        customFat = customFat
    )
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingState())
    val ui: StateFlow<OnboardingState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val heightMetric = container.prefs.heightUnit.first() == "cm"
            val weightMetric = container.prefs.weightUnit.first() == "kg"
            val draft = container.prefs.onboardingDraft.first()
            val homeDisplay = container.prefs.homeDisplayPreferences.first()
            _ui.value = if (draft != null) {
                // The API key itself isn't in the draft; reload it from the KeyStore so the
                // user doesn't have to re-paste it after resuming. Keys live on the active
                // profile now, so resolve the profile (not the draft's provider).
                val profileId = container.prefs.activeAiProfile.first()?.id
                val restoredKey = profileId?.let { container.keyStore.profileApiKey(it) } ?: ""
                _ui.value.applyDraft(draft).copy(
                    heightMetric = heightMetric, weightMetric = weightMetric, apiKey = restoredKey,
                    homeDisplay = homeDisplay,
                )
            } else {
                _ui.value.copy(
                    heightMetric = heightMetric,
                    weightMetric = weightMetric,
                    homeDisplay = homeDisplay,
                )
            }
        }
        // Persist the in-progress draft so onboarding can resume where the user left off if the
        // OS reclaims memory and kills the process while the app is backgrounded.
        viewModelScope.launch {
            ui.drop(1).debounce(400).collect { state ->
                container.prefs.setOnboardingDraft(state.toDraft())
            }
        }
    }

    fun setGender(v: Gender) { _ui.value = _ui.value.copy(gender = v) }
    fun setBirthday(v: LocalDate) { _ui.value = _ui.value.copy(birthday = v) }
    fun setHeight(cm: Int) { _ui.value = _ui.value.copy(heightCm = cm) }
    fun setWeight(kg: Double) { _ui.value = _ui.value.copy(weightKg = kg, goalWeightKg = kg) }
    fun setBodyFat(pct: Double?) {
        // Clear the goal alongside the current value so a stale goal doesn't
        // linger when the user backs out of "Yes I know my body fat".
        _ui.value = _ui.value.copy(
            bodyFatPercentage = pct,
            bodyFatUpdatedAt = if (pct == null) null else Instant.now(),
            goalBodyFatPercentage = if (pct == null) null else _ui.value.goalBodyFatPercentage
        )
    }
    fun setGoalBodyFat(pct: Double?) { _ui.value = _ui.value.copy(goalBodyFatPercentage = pct) }
    fun setActivity(v: ActivityLevel) { _ui.value = _ui.value.copy(activity = v) }
    fun setGoal(v: WeightGoal) {
        val defaultGoalWeight = when (v) {
            WeightGoal.LOSE -> _ui.value.weightKg - 5
            WeightGoal.GAIN -> _ui.value.weightKg + 5
            WeightGoal.MAINTAIN -> _ui.value.weightKg
        }
        _ui.value = _ui.value.copy(goal = v, goalWeightKg = defaultGoalWeight)
    }
    fun setGoalWeight(v: Double) { _ui.value = _ui.value.copy(goalWeightKg = v) }
    fun setDietMode(v: DietMode) { _ui.value = _ui.value.copy(dietMode = v) }
    fun setKetoCarbMode(v: KetoCarbMode) { _ui.value = _ui.value.copy(ketoCarbMode = v) }
    fun setKetoCarbManualTarget(v: Int?) {
        _ui.value = _ui.value.copy(
            ketoCarbManualTarget = v?.let { KetoCarbRecommendationService.clampManualNetCarbs(it) }
        )
    }
    fun setWeeklyChange(v: Double) { _ui.value = _ui.value.copy(weeklyChangeKg = v) }
    fun setNotificationsEnabled(v: Boolean) {
        _ui.value = _ui.value.copy(notificationsEnabled = v)
    }
    fun setHealthConnectEnabled(v: Boolean) {
        _ui.value = _ui.value.copy(healthConnectEnabled = v)
    }
    fun setHomeCalorieDisplayMode(mode: HomeCalorieDisplayMode) {
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(calorieDisplayMode = mode))
        viewModelScope.launch { container.prefs.setHomeCalorieDisplayMode(mode.storageKey) }
    }
    fun setHomeShowActiveCalories(enabled: Boolean) {
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(showActiveCalories = enabled))
        viewModelScope.launch { container.prefs.setHomeShowActiveCalories(enabled) }
    }
    fun setHomeShowSteps(enabled: Boolean) {
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(showSteps = enabled))
        viewModelScope.launch { container.prefs.setHomeShowSteps(enabled) }
    }
    fun setHomeStepGoal(goal: Int) {
        val safe = goal.coerceIn(HomeDisplayPreferences.MIN_STEP_GOAL, HomeDisplayPreferences.MAX_STEP_GOAL)
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(stepGoal = safe))
        viewModelScope.launch { container.prefs.setHomeStepGoal(goal) }
    }
    fun setHomeTopNutrients(selection: List<HomeTopNutrient>) {
        val shown = HomeTopNutrient.normalized(selection)
        _ui.value = _ui.value.copy(
            homeDisplay = _ui.value.homeDisplay.copy(
                nutrientCardCount = shown.size,
                homeTopNutrients = shown,
            )
        )
        viewModelScope.launch {
            container.prefs.setHomeTopNutrients(HomeTopNutrient.toStorage(shown))
        }
    }
    fun setFoodLogMacroChips(chips: List<FoodLogMacroChip>) {
        _ui.value = _ui.value.copy(
            homeDisplay = _ui.value.homeDisplay.copy(
                foodLogMacroChips = FoodLogMacroChip.normalized(chips),
            )
        )
        viewModelScope.launch {
            container.prefs.setFoodLogMacroChips(FoodLogMacroChip.toStorage(chips))
        }
    }
    fun setHomeCalorieHeroLayout(layout: HomeCalorieHeroLayout?) {
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(calorieHeroLayout = layout))
        viewModelScope.launch { container.prefs.setHomeCalorieHeroLayout(layout?.storageKey) }
    }
    fun setHomeMacroCardLayout(layout: HomeMacroCardLayout?) {
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(macroCardLayout = layout))
        viewModelScope.launch { container.prefs.setHomeMacroCardLayout(layout?.storageKey) }
    }
    fun setHomeFoodLogLayout(layout: HomeFoodLogLayout?) {
        _ui.value = _ui.value.copy(homeDisplay = _ui.value.homeDisplay.copy(foodLogLayout = layout))
        viewModelScope.launch { container.prefs.setHomeFoodLogLayout(layout?.storageKey) }
    }
    /**
     * Active profile id for onboarding writes, creating a default profile named after
     * [provider] first when the store is still empty (fresh install before migration
     * had a chance to seed one).
     */
    private suspend fun activeProfileIdFor(provider: AIProvider): UUID {
        container.prefs.activeAiProfile.first()?.let { return it.id }
        val created = AiProfile(
            id = UUID.randomUUID(),
            name = container.appContext.getString(provider.displayNameRes),
            provider = provider,
            model = provider.defaultModel,
            createdAt = Instant.now(),
        )
        container.prefs.mutateProfiles { it.add(created) }
        container.prefs.setActiveAiProfileId(created.id.toString())
        return created.id
    }

    fun setAiProvider(p: AIProvider) {
        if (_ui.value.aiProvider == p) return
        // A provider change drops the key, base URL, and vision model because
        // they belong to the old host; re-selecting the current provider is a no-op.
        // Blank the draft before the store write so complete() cannot race-save
        // the previous provider's key onto the new host.
        _ui.value = _ui.value.copy(
            aiProvider = p,
            aiModel = p.defaultModel,
            apiKey = "",
            validatedApiKey = "",
            apiKeyTestMessage = "",
            apiKeyTestOk = null,
            apiKeyTesting = false,
        )
        viewModelScope.launch {
            val switched = container.prefs.switchActiveProfileProvider(
                container.keyStore, p, p.defaultModel,
            )
            if (!switched) activeProfileIdFor(p)
        }
    }
    fun setAiModel(m: String) {
        _ui.value = _ui.value.copy(aiModel = m)
        viewModelScope.launch { container.prefs.editActiveProfile { it.copy(model = m) } }
    }
    fun setApiKey(key: String) {
        val prev = _ui.value
        val clearedValidation = prev.validatedApiKey.isNotEmpty() && key.trim() != prev.validatedApiKey
        _ui.value = prev.copy(
            apiKey = key,
            validatedApiKey = if (clearedValidation) "" else prev.validatedApiKey,
            apiKeyTestMessage = if (clearedValidation) "" else prev.apiKeyTestMessage,
            apiKeyTestOk = if (clearedValidation) null else prev.apiKeyTestOk,
        )
        // Persist immediately so the in-onboarding AI plan calc can use it.
        viewModelScope.launch {
            val profileId = activeProfileIdFor(_ui.value.aiProvider)
            container.keyStore.setProfileApiKey(profileId, key.trim().takeIf { it.isNotBlank() })
        }
    }

    /** Lightweight Gemini probe (or mark non-Gemini keys ready). Optionally advances on success. */
    fun testApiKey(advanceOnSuccess: Boolean = false) {
        val state = _ui.value
        if (state.step != OnboardingStep.PROVIDER) return
        val key = state.apiKey.trim()
        if (key.isEmpty()) {
            _ui.value = state.copy(
                apiKeyTestOk = false,
                apiKeyTestMessage = container.appContext.getString(R.string.onboarding_api_key_empty),
            )
            return
        }
        if (state.aiProvider != AIProvider.GEMINI) {
            _ui.value = state.copy(
                validatedApiKey = key,
                apiKeyTestOk = true,
                apiKeyTestMessage = container.appContext.getString(R.string.onboarding_api_key_non_gemini_ok),
            )
            if (advanceOnSuccess) advanceStep()
            return
        }
        if (state.validatedApiKey == key) {
            _ui.value = state.copy(
                apiKeyTestOk = true,
                apiKeyTestMessage = container.appContext.getString(R.string.onboarding_api_key_works),
            )
            if (advanceOnSuccess) advanceStep()
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(apiKeyTesting = true, apiKeyTestMessage = container.appContext.getString(R.string.onboarding_api_key_testing), apiKeyTestOk = null)
            val result = runCatching {
                GeminiClient.validateApiKey(
                    client = FoodAnalysisService.defaultClient,
                    baseUrl = AIProvider.GEMINI.baseUrl,
                    apiKey = key,
                )
            }
            if (result.isSuccess) {
                _ui.value = _ui.value.copy(
                    apiKeyTesting = false,
                    validatedApiKey = key,
                    apiKeyTestOk = true,
                    apiKeyTestMessage = container.appContext.getString(R.string.onboarding_api_key_works),
                )
                if (advanceOnSuccess) advanceStep()
            } else {
                val err = result.exceptionOrNull()
                val message = if (err is AiError.Network) {
                    container.appContext.getString(R.string.onboarding_api_key_network)
                } else {
                    container.appContext.getString(R.string.onboarding_api_key_rejected)
                }
                _ui.value = _ui.value.copy(
                    apiKeyTesting = false,
                    validatedApiKey = "",
                    apiKeyTestOk = false,
                    apiKeyTestMessage = message,
                )
            }
        }
    }
    /** The single Imperial|Metric segmented control writes BOTH unit prefs coherently:
     *  Imperial -> ftin + lbs, Metric -> cm + kg. */
    fun setUseMetric(v: Boolean) {
        _ui.value = _ui.value.copy(heightMetric = v, weightMetric = v)
        viewModelScope.launch {
            container.prefs.setHeightUnit(if (v) "cm" else "ftin")
            container.prefs.setWeightUnit(if (v) "kg" else "lbs")
        }
    }

    fun setCustomCalories(v: Int?) { planEdited = true; _ui.value = _ui.value.copy(customCalories = v) }
    fun setCustomProtein(v: Int?) { planEdited = true; _ui.value = _ui.value.copy(customProtein = v) }
    fun setCustomCarbs(v: Int?) { planEdited = true; _ui.value = _ui.value.copy(customCarbs = v) }
    fun setCustomFat(v: Int?) { planEdited = true; _ui.value = _ui.value.copy(customFat = v) }

    /** The user hand-tuned the plan — Adaptive Goals then stays off at completion
     *  so its first weekly run can't overwrite their numbers. */
    private var planEdited = false

    /** Building Plan step: compute calorie + macro targets with AI (forecast is null for a new
     *  user). On success seeds the custom targets the Plan Ready screen shows; on failure leaves
     *  them null so the formula values are used. Calls [onDone] either way. */
    fun buildPlanWithAI(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            val result = runCatching {
                // A cold on-device engine load (60-90 s+ of uninterruptible
                // native init + generation) would hold the "Setting up" screen
                // at 100% with no feedback. Formula targets are the documented
                // fallback anyway, so only run the AI plan calc when the
                // on-device engine is already warm.
                if (state.aiProvider == AIProvider.ON_DEVICE &&
                    !container.onDeviceLlmGateway.isLoaded
                ) {
                    null
                } else {
                    container.foodAnalysis.calculateGoals(state.buildProfile(), forecast = null, heightMetric = state.heightMetric, weightMetric = state.weightMetric)
                }
            }.getOrNull()
            if (result != null) {
                _ui.value = _ui.value.copy(
                    customCalories = result.calories,
                    customProtein = result.protein,
                    customFat = result.fat,
                    customCarbs = result.carbs,
                )
            }
            onDone()
        }
    }

    fun next() {
        if (_ui.value.step == OnboardingStep.DISCLAIMERS) return
        val state = _ui.value
        // Empty cloud key: UI shows the skip dialog; do not advance here.
        if (state.needsAiSkipConfirm) return
        if (state.step == OnboardingStep.PROVIDER &&
            state.aiProvider.requiresApiKey &&
            state.aiProvider == AIProvider.GEMINI &&
            state.apiKey.trim().isNotEmpty()
        ) {
            testApiKey(advanceOnSuccess = true)
            return
        }
        if (state.step == OnboardingStep.PROVIDER &&
            state.aiProvider.requiresApiKey &&
            state.aiProvider != AIProvider.GEMINI &&
            state.apiKey.trim().isNotEmpty() &&
            state.validatedApiKey != state.apiKey.trim()
        ) {
            // Mark non-Gemini keys ready without a network probe.
            testApiKey(advanceOnSuccess = true)
            return
        }
        advanceStep()
    }

    /**
     * Leave the provider step without a cloud key. When [useOnDevice] is true, select
     * on-device AI; otherwise keep the current provider with an empty key (set up later
     * in Settings). Mirrors PWA "Skip for now".
     */
    fun skipAiSetup(useOnDevice: Boolean) {
        viewModelScope.launch {
            val state = _ui.value
            if (state.step != OnboardingStep.PROVIDER) return@launch
            if (useOnDevice) {
                activeProfileIdFor(AIProvider.ON_DEVICE)
                container.prefs.editActiveProfile {
                    it.copy(provider = AIProvider.ON_DEVICE, model = AIProvider.ON_DEVICE.defaultModel)
                }
                _ui.value = state.copy(
                    aiProvider = AIProvider.ON_DEVICE,
                    aiModel = AIProvider.ON_DEVICE.defaultModel,
                    apiKey = "",
                    validatedApiKey = "",
                    apiKeyTestMessage = "",
                    apiKeyTestOk = null,
                    apiKeyTesting = false,
                )
            } else {
                container.prefs.activeAiProfile.first()?.let {
                    container.keyStore.setProfileApiKey(it.id, null)
                }
                _ui.value = state.copy(
                    apiKey = "",
                    validatedApiKey = "",
                    apiKeyTestMessage = "",
                    apiKeyTestOk = null,
                    apiKeyTesting = false,
                )
            }
            advanceStep()
        }
    }

    private fun advanceStep() {
        if (_ui.value.step == OnboardingStep.DISCLAIMERS) return
        val nextStep = OnboardingStep.values().getOrNull(_ui.value.step.ordinal + 1) ?: return
        _ui.value = _ui.value.copy(step = nextStep)
    }

    fun back() {
        val prevStep = OnboardingStep.values().getOrNull(_ui.value.step.ordinal - 1) ?: return
        _ui.value = _ui.value.copy(step = prevStep)
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(submitting = true)
            val state = _ui.value
            val profile = state.buildProfile()
            container.profileRepository.saveFromOnboarding(profile)
            container.weightRepository.seedInitialWeightIfEmpty(profile.weightKg)
            // Only seed body fat when the user actually entered one in onboarding
            // (the "Yes I know my body fat %" branch); the "No" branch leaves
            // bodyFatPercentage null and the store stays empty.
            profile.bodyFatPercentage?.let {
                container.bodyFatRepository.seedInitialBodyFatIfEmpty(
                    it,
                    at = profile.bodyFatUpdatedAt ?: Instant.now()
                )
            }
            container.prefs.setNotificationsEnabled(state.notificationsEnabled)
            container.prefs.setHealthConnectEnabled(state.healthConnectEnabled)
            // Resolve (or lazily create) the onboarding profile, then persist the final
            // provider/model choice and the entered key onto it.
            val profileId = activeProfileIdFor(state.aiProvider)
            container.prefs.editActiveProfile {
                it.copy(provider = state.aiProvider, model = state.aiModel)
            }
            if (state.apiKey.isNotBlank()) {
                container.keyStore.setProfileApiKey(profileId, state.apiKey.trim())
            }
            // New installs start with Energy Burn on, and Adaptive Goals on unless the
            // user hand-tuned their plan (adaptive would overwrite it). Existing users are
            // untouched — these prefs are only written here and by the Settings toggles.
            // Onboarding just calculated goals, so stamp the weekly adaptive check as done;
            // the first auto-run lands next week.
            if (!planEdited) container.prefs.setAdaptiveGoalsEnabled(true)
            container.prefs.setHealthEnergyGoalsEnabled(true)
            container.prefs.setAdaptiveGoalsLastCheckDay(LocalDate.now().toString())
            container.prefs.setOnboardingCompleted(true)
            container.prefs.setOnboardingDraft(null)
            onDone()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnboardingViewModel(container) as T
    }
}
