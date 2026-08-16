package app.chompass

import android.content.Intent
import app.chompass.debug.OnDeviceLlmDefaults
import app.chompass.BuildConfig

/**
 * Debug-only intent extras (seeders, benchmarks, on-device LLM smoke test).
 * Parsed once and stripped so `Activity.recreate()` does not re-fire them.
 *
 * When the activity is already foreground (`launchMode` singleTop), adb delivers
 * extras via `onNewIntent` — not `onCreate`. Both paths call [consumeDebugIntentExtras].
 *
 * Security: the whole surface is inert when [debugEnabled] is false (release).
 * `MainActivity` is exported for legitimate deep links, so ANY installed app
 * could deliver these extras — seeding sample data over the real diary or
 * resetting onboarding — unless release builds ignore them. Gating happens at
 * the top here (defense in depth with the per-field `BuildConfig.DEBUG` guards
 * kept from the original implementation).
 */
internal data class DebugIntentActions(
    val resetOnboarding: Boolean = false,
    val seedTestData: Boolean = false,
    val seedBodyMetrics: Boolean = false,
    val seedBodyMetricsTwoYears: Boolean = false,
    val seedKetoSettings: Boolean = false,
    val seedActiveCalories: Boolean = false,
    /** Null = extra absent; Int = explicit today override (0 = measured-zero morning). */
    val activeTodayOverride: Int? = null,
    val setGaugeMode: String = "",
    val setShowSteps: Boolean = false,
    /** Null = extra absent; Boolean = explicit on/off for the STATIC active caption. */
    val setShowActiveCalories: Boolean? = null,
    val clearDebugActivity: Boolean = false,
    /** Null = extra absent; Boolean = explicit on/off for the debug hero-arc A/B. */
    val setShowRestingShade: Boolean? = null,
    val seedOverGoal: Boolean = false,
    val restoreRealData: Boolean = false,
    /** Debug-only: replay a scripted food-analysis response (usage-video capture). */
    val demoAi: Boolean = false,
    /** Debug-only: drop any pending food-analysis draft so a fresh capture segment starts clean. */
    val clearPendingDraft: Boolean = false,
    val runEntryBenchmark: Boolean = false,
    val entryBenchmarkCount: Int = 3,
    val runOnDeviceLlmTest: Boolean = false,
    val onDeviceLlmBackend: String = "gpu",
    val onDeviceLlmMtp: Boolean = false,
    val onDeviceLlmModel: String = OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
    val onDeviceLlmTier: String = "all",
    val onDeviceLlmPrompt: String = "full",
    val onDeviceLlmRepeat: Int = 1,
    val onDeviceLlmClearCache: Boolean = false,
    val diagnoseHealthConnect: Boolean = false,
)

/**
 * Reads (and strips) debug extras. INERT IN RELEASE: pass
 * [debugEnabled]=false (the default wires `BuildConfig.DEBUG`) and every flag
 * comes back false, so `launchDebugIntentActions` no-ops before touching data.
 */
internal fun consumeDebugIntentExtras(
    intent: Intent?,
    debugEnabled: Boolean = BuildConfig.DEBUG,
): DebugIntentActions {
    if (!debugEnabled) return DebugIntentActions()
    intent ?: return DebugIntentActions()
    val presetDaily = intent.getStringExtra("ondevice_llm_preset")?.lowercase() == "daily"
    val actions = DebugIntentActions(
        resetOnboarding = intent.getBooleanExtra("reset_onboarding", false),
        seedTestData = intent.getBooleanExtra("seed_test_data", false),
        seedBodyMetrics = intent.getBooleanExtra("seed_body_metrics", false),
        seedBodyMetricsTwoYears = intent.getBooleanExtra("seed_body_metrics_2y", false),
        seedKetoSettings = intent.getBooleanExtra("seed_keto_settings", false),
        seedActiveCalories = intent.getBooleanExtra("seed_active_calories", false),
        activeTodayOverride = intent.getIntExtra("active_today_override", Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE },
        setGaugeMode = intent.getStringExtra("set_gauge_mode") ?: "",
        setShowSteps = intent.getBooleanExtra("set_show_steps", false),
        setShowActiveCalories = if (intent.hasExtra("set_show_active_calories")) {
            intent.getBooleanExtra("set_show_active_calories", false)
        } else {
            null
        },
        clearDebugActivity = intent.getBooleanExtra("clear_debug_activity", false),
        setShowRestingShade = if (intent.hasExtra("set_show_resting_shade")) {
            intent.getBooleanExtra("set_show_resting_shade", false)
        } else {
            null
        },
        seedOverGoal = intent.getBooleanExtra("seed_over_goal", false),
        restoreRealData = intent.getBooleanExtra("restore_real_data", false),
        demoAi = BuildConfig.DEBUG && intent.getBooleanExtra("demo_ai", false),
        clearPendingDraft = BuildConfig.DEBUG && intent.getBooleanExtra("clear_pending_draft", false),
        runEntryBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_entry_benchmark", false),
        entryBenchmarkCount = intent.getIntExtra("benchmark_count", 3),
        runOnDeviceLlmTest = BuildConfig.DEBUG && intent.getBooleanExtra("run_ondevice_llm_test", false),
        onDeviceLlmBackend = if (presetDaily) "gpu" else intent.getStringExtra("ondevice_llm_backend") ?: "gpu",
        onDeviceLlmMtp = presetDaily || intent.getBooleanExtra("ondevice_llm_mtp", false),
        onDeviceLlmModel = intent.getStringExtra("ondevice_llm_model")
            ?: OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
        onDeviceLlmTier = if (presetDaily) "daily" else intent.getStringExtra("ondevice_llm_tier") ?: "all",
        onDeviceLlmPrompt = if (presetDaily) "fewshot_units" else intent.getStringExtra("ondevice_llm_prompt") ?: "full",
        onDeviceLlmRepeat = intent.getIntExtra("ondevice_llm_repeat", 1).coerceIn(1, 5),
        onDeviceLlmClearCache = intent.getBooleanExtra("ondevice_llm_clear_cache", false),
        diagnoseHealthConnect = BuildConfig.DEBUG &&
            intent.getBooleanExtra("diagnose_health_connect", false),
    )
    if (actions.resetOnboarding) intent.removeExtra("reset_onboarding")
    if (actions.seedTestData) intent.removeExtra("seed_test_data")
    if (actions.seedBodyMetrics) intent.removeExtra("seed_body_metrics")
    if (actions.seedBodyMetricsTwoYears) intent.removeExtra("seed_body_metrics_2y")
    if (actions.seedKetoSettings) intent.removeExtra("seed_keto_settings")
    if (actions.seedActiveCalories) {
        intent.removeExtra("seed_active_calories")
        intent.removeExtra("active_today_override")
    }
    if (actions.setGaugeMode.isNotEmpty()) intent.removeExtra("set_gauge_mode")
    if (actions.setShowSteps) intent.removeExtra("set_show_steps")
    if (actions.setShowActiveCalories != null) intent.removeExtra("set_show_active_calories")
    if (actions.clearDebugActivity) intent.removeExtra("clear_debug_activity")
    if (actions.setShowRestingShade != null) intent.removeExtra("set_show_resting_shade")
    if (actions.seedOverGoal) intent.removeExtra("seed_over_goal")
    if (actions.restoreRealData) intent.removeExtra("restore_real_data")
    if (actions.demoAi) intent.removeExtra("demo_ai")
    if (actions.clearPendingDraft) intent.removeExtra("clear_pending_draft")
    if (actions.runEntryBenchmark) intent.removeExtra("run_entry_benchmark")
    if (actions.diagnoseHealthConnect) intent.removeExtra("diagnose_health_connect")
    if (actions.runOnDeviceLlmTest) {
        intent.removeExtra("run_ondevice_llm_test")
        intent.removeExtra("ondevice_llm_backend")
        intent.removeExtra("ondevice_llm_mtp")
        intent.removeExtra("ondevice_llm_model")
        intent.removeExtra("ondevice_llm_tier")
        intent.removeExtra("ondevice_llm_prompt")
        intent.removeExtra("ondevice_llm_repeat")
        intent.removeExtra("ondevice_llm_clear_cache")
        intent.removeExtra("ondevice_llm_preset")
    }
    return actions
}
