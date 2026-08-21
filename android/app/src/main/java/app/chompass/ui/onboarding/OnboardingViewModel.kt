package app.chompass.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.ActivityLevel
import app.chompass.models.AIProvider
import app.chompass.models.DietMode
import app.chompass.models.Gender
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
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class OnboardingStep {
    WELCOME, GENDER, BIRTHDAY, HEIGHT_WEIGHT, BODY_FAT,
    ACTIVITY, GOAL, DIET_MODE, GOAL_WEIGHT, GOAL_SPEED,
    NOTIFICATIONS, HEALTH_CONNECT, PROVIDER,
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
    bodyFatPercentage = bodyFatPercentage, goalBodyFatPercentage = goalBodyFatPercentage,
    activity = activity, goal = goal, dietMode = dietMode, ketoCarbMode = ketoCarbMode,
    ketoCarbManualTarget = ketoCarbManualTarget, goalWeightKg = goalWeightKg, weeklyChangeKg = weeklyChangeKg,
    notificationsEnabled = notificationsEnabled, healthConnectEnabled = healthConnectEnabled,
    aiProvider = aiProvider, aiModel = aiModel,
    customCalories = customCalories, customProtein = customProtein, customCarbs = customCarbs, customFat = customFat,
)

private fun OnboardingState.applyDraft(draft: OnboardingDraft) = copy(
    step = draft.step, gender = draft.gender, birthday = draft.birthday, heightCm = draft.heightCm,
    weightKg = draft.weightKg, bodyFatPercentage = draft.bodyFatPercentage,
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
            _ui.value = if (draft != null) {
                // The API key itself isn't in the draft; reload it from the KeyStore so the
                // user doesn't have to re-paste it after resuming.
                val restoredKey = container.keyStore.apiKey(draft.aiProvider) ?: ""
                _ui.value.applyDraft(draft).copy(
                    heightMetric = heightMetric, weightMetric = weightMetric, apiKey = restoredKey,
                )
            } else {
                _ui.value.copy(heightMetric = heightMetric, weightMetric = weightMetric)
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
    fun setAiProvider(p: AIProvider) {
        // Persist immediately so the Building Plan AI call (which runs before onboarding
        // completes) can resolve the provider/model. Reset to the provider's default model and
        // reload that provider's stored key.
        viewModelScope.launch {
            container.prefs.setSelectedAIProvider(p)
            container.prefs.setSelectedAIModel(p.defaultModel)
            val existing = container.keyStore.apiKey(p) ?: ""
            _ui.value = _ui.value.copy(
                aiProvider = p,
                aiModel = p.defaultModel,
                apiKey = existing,
                validatedApiKey = "",
                apiKeyTestMessage = "",
                apiKeyTestOk = null,
                apiKeyTesting = false,
            )
        }
    }
    fun setAiModel(m: String) {
        _ui.value = _ui.value.copy(aiModel = m)
        viewModelScope.launch { container.prefs.setSelectedAIModel(m) }
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
            container.keyStore.setApiKey(_ui.value.aiProvider, key.trim().takeIf { it.isNotBlank() })
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
                container.foodAnalysis.calculateGoals(state.buildProfile(), forecast = null, heightMetric = state.heightMetric, weightMetric = state.weightMetric)
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
                container.prefs.setSelectedAIProvider(AIProvider.ON_DEVICE)
                container.prefs.setSelectedAIModel(AIProvider.ON_DEVICE.defaultModel)
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
                container.keyStore.setApiKey(state.aiProvider, null)
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
            container.profileRepository.save(profile)
            container.weightRepository.seedInitialWeightIfEmpty(profile.weightKg)
            // Only seed body fat when the user actually entered one in onboarding
            // (the "Yes I know my body fat %" branch); the "No" branch leaves
            // bodyFatPercentage null and the store stays empty.
            profile.bodyFatPercentage?.let {
                container.bodyFatRepository.seedInitialBodyFatIfEmpty(it)
            }
            container.prefs.setNotificationsEnabled(state.notificationsEnabled)
            container.prefs.setHealthConnectEnabled(state.healthConnectEnabled)
            container.prefs.setSelectedAIProvider(state.aiProvider)
            container.prefs.setSelectedAIModel(state.aiModel)
            if (state.apiKey.isNotBlank()) {
                container.keyStore.setApiKey(state.aiProvider, state.apiKey.trim())
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
