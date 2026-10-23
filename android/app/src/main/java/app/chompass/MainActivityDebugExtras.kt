package app.chompass

import android.content.Intent
import app.chompass.debug.OnDeviceLlmDefaults
import app.chompass.BuildConfig
import app.chompass.services.DefectGroup

/** Valid values for the `goal_matrix_tier` / `goal_matrix_provider` debug extras. */
private val GOAL_MATRIX_TIERS = setOf("safe", "smart", "auto")
private val GOAL_MATRIX_PROVIDERS = setOf("on_device", "gemini", "anthropic", "openai", "openrouter")

/** Valid values for the `seed_profile_variant` debug extra. */
private val PROFILE_VARIANTS = setOf("gain", "maintain", "pinned", "female")

/** 24h `H:MM` / `HH:MM` — the only accepted `set_weight_reminder` shape. */
private val REMINDER_TIME = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

/** Valid values for the `screenshot_appearance` debug extra (release screenshot capture). */
private val SCREENSHOT_APPEARANCES = setOf("light", "dark", "oled")

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
    val seedFull: Boolean = false,
    val seedBusyHome: Boolean = false,
    val seedBodyMetrics: Boolean = false,
    val seedBodyMetricsTwoYears: Boolean = false,
    val seedKetoSettings: Boolean = false,
    /** Debug-only: Training/Rest macro day-type plan on a 2-on/1-off cycle (#60). */
    val seedMacroCycle: Boolean = false,
    /** Debug-only: adds the #60 day-type plan to seed_full (ignored with keto). */
    val seedFullMacroCycle: Boolean = false,
    /** Debug-only: goal-phase examples (Progress v2); composes with seed_full. */
    val seedGoalPhases: Boolean = false,
    val seedActiveCalories: Boolean = false,
    /** Debug-only: seed the analysis queue + prompt history (Codeberg #53). */
    val seedAnalysisQueue: Boolean = false,
    /** Debug-only: one all-macro-zeros named diary row for the fill-missing device pass. */
    val seedGapFixture: Boolean = false,
    /** Debug-only: install the stored-defect fixtures (all six groups). */
    val seedDefects: Boolean = false,
    /** Debug-only: fill-gaps defect rows (all-zero / partial rows, Mealie constituents). */
    val defectGaps: Boolean = false,
    /** Debug-only: stale + matching micros-composition signatures. */
    val defectStaleMicros: Boolean = false,
    /** Debug-only: legacy / hostile rows, unknown enum tokens, ceilings. */
    val defectLegacy: Boolean = false,
    /** Debug-only: whole-day gaps, untracked marks, note-only days. */
    val defectDays: Boolean = false,
    /** Debug-only: sparse reading windows, duplicates, absurd metrics. */
    val defectMetrics: Boolean = false,
    /** Debug-only: fasting session, empty quick kinds, tracker extremes. */
    val defectTrackers: Boolean = false,
    /** Debug-only: profile variant to apply ("gain" | "maintain" | "pinned" | "female"). */
    val seedProfileVariant: String = "",
    /** Debug-only: reset the fill-missing banner once-per-day gate (device-pass reruns). */
    val resetFillMissingBanner: Boolean = false,
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
    /** Debug-only: extra hold after the last demo phase so the overlay can be captured. */
    val demoAiHoldMs: Int = 0,
    /** Debug-only: replay scripted progress, then fail (failure-path capture; needs demo_ai too). */
    val demoAiFail: Boolean = false,
    /** Debug-only: first entry leg truncates mid-JSON, the downshift retry answers macros-only (#97). */
    val demoAiTruncate: Boolean = false,
    /** Debug-only: launch carried demo_ai=false — disarm all scripted demo responses. */
    val demoAiOff: Boolean = false,
    /** Debug-only: drop any pending food-analysis draft so a fresh capture segment starts clean. */
    val clearPendingDraft: Boolean = false,
    val runEntryBenchmark: Boolean = false,
    val entryBenchmarkCount: Int = 3,
    val runRelogBenchmark: Boolean = false,
    val relogBenchmarkCount: Int = 3,
    val runWaterSipBenchmark: Boolean = false,
    val waterSipBenchmarkCount: Int = 1,
    val runLocalEntryBenchmark: Boolean = false,
    val localEntryBenchmarkCount: Int = 3,
    val runHubBenchmark: Boolean = false,
    val runDaySwitchBenchmark: Boolean = false,
    val runFlipBenchmark: Boolean = false,
    val runOnDeviceLlmTest: Boolean = false,
    val onDeviceLlmBackend: String = "gpu",
    val onDeviceLlmMtp: Boolean = false,
    val onDeviceLlmModel: String = OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
    val onDeviceLlmTier: String = "all",
    val onDeviceLlmPrompt: String = "full",
    val onDeviceLlmRepeat: Int = 1,
    val onDeviceLlmClearCache: Boolean = false,
    /** Debug-only: run the goal-calculation matrix (docs/ON_DEVICE_LLM.md). */
    val runGoalMatrixTest: Boolean = false,
    /** Comma-separated scenario filter for [runGoalMatrixTest]; blank = all. */
    val goalMatrixScenarios: String = "",
    /** Repeat count per scenario (1-5) for [runGoalMatrixTest] variance sampling. */
    val goalMatrixRepeat: Int = 1,
    /** Goal-recalc tier for the matrix: "safe" | "smart" | "auto" (per-dispatch). */
    val goalMatrixTier: String = "auto",
    /** Provider for the matrix: "on_device" | "gemini" | "anthropic" | "openai" | "openrouter". */
    val goalMatrixProvider: String = "on_device",
    /** Model id override for the matrix (defaults to the provider's default model). */
    val goalMatrixModel: String = "",
    /** Debug-only: store an OpenRouter API key in the encrypted KeyStore (benchmark key import). */
    val setOpenRouterKey: String = "",
    /** Debug-only: copy the baked Gemini key onto the Gemini profile and activate it. */
    val seedDebugProfileKey: Boolean = false,
    val diagnoseHealthConnect: Boolean = false,
    val previewDailySummary: Boolean = false,
    /** Debug-only: open the meal-builder sheet seeded with 6 ingredients (Codeberg #84 repro). */
    val seedProgressiveMeal: Boolean = false,
    /** Debug-only: Mealie import device pass (list, import 3, log 1, re-import). */
    val runMealieImportTest: Boolean = false,
    val mealieTestUrl: String = "",
    val mealieTestToken: String = "",
    /** Debug-only: barcode handed to the real Home lookup path (`debug_barcode`). */
    val debugBarcode: String = "",
    /** Debug-only: keep the saved-to-favorites snackbar (with Undo) on screen. */
    val debugSnackbarPersistent: Boolean = false,
    /** Debug-only: restore AI profiles wiped by a seed launch (`keep_ai`). */
    val keepAiProfiles: Boolean = false,
    /** Debug-only: `HH:MM` weight reminder time to persist without the wheel. */
    val weightReminderTime: String = "",
    /**
     * Debug-only: persist `light` | `dark` | `oled` appearance + the brand teal
     * palette so release screenshot captures are deterministic (no wallpaper
     * dynamic color, no Settings taps; `scripts/capture_device_screenshots.sh`).
     */
    val screenshotAppearance: String = "",
    /**
     * Debug-only: after seeding, hide the power-user tracker cards / daily note
     * so the published Home frame shows the core UI (hero, macros, meals).
     */
    val screenshotLean: Boolean = false,
    /** Debug-only: delete today's weight readings (`clear_today_weight`). */
    val clearTodayWeight: Boolean = false,
) {
    val hasSeedAction: Boolean
        get() = seedTestData || seedFull || seedBodyMetrics || seedBodyMetricsTwoYears ||
            seedKetoSettings || seedActiveCalories || seedAnalysisQueue || seedMacroCycle ||
            seedGoalPhases
    /** Seeders that write onboarded=true before the heavy diary replace. */
    val writesOnboarded: Boolean get() = hasSeedAction

    /**
     * Stored-defect groups to install: the umbrella flag means all six, a
     * sub-flag its own group, and both together the union.
     */
    val defectGroups: Set<DefectGroup>
        get() = buildSet {
            if (seedDefects) addAll(DefectGroup.entries)
            if (defectGaps) add(DefectGroup.GAPS)
            if (defectStaleMicros) add(DefectGroup.STALE_MICROS)
            if (defectLegacy) add(DefectGroup.LEGACY)
            if (defectDays) add(DefectGroup.DAYS)
            if (defectMetrics) add(DefectGroup.METRICS)
            if (defectTrackers) add(DefectGroup.TRACKERS)
        }
}

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
        seedFull = intent.getBooleanExtra("seed_full", false),
        seedBusyHome = intent.getBooleanExtra("seed_busy_home", false),
        seedBodyMetrics = intent.getBooleanExtra("seed_body_metrics", false),
        seedBodyMetricsTwoYears = intent.getBooleanExtra("seed_body_metrics_2y", false),
        seedKetoSettings = intent.getBooleanExtra("seed_keto_settings", false),
        seedMacroCycle = BuildConfig.DEBUG && intent.getBooleanExtra("seed_macro_cycle", false),
        seedFullMacroCycle = BuildConfig.DEBUG && intent.getBooleanExtra("macro_cycle", false),
        seedGoalPhases = BuildConfig.DEBUG && intent.getBooleanExtra("seed_goal_phases", false),
        seedActiveCalories = intent.getBooleanExtra("seed_active_calories", false),
        seedAnalysisQueue = BuildConfig.DEBUG && intent.getBooleanExtra("seed_analysis_queue", false),
        seedGapFixture = BuildConfig.DEBUG && intent.getBooleanExtra("seed_gap_fixture", false),
        seedDefects = BuildConfig.DEBUG && intent.getBooleanExtra("seed_defects", false),
        defectGaps = BuildConfig.DEBUG && intent.getBooleanExtra("defect_gaps", false),
        defectStaleMicros = BuildConfig.DEBUG && intent.getBooleanExtra("defect_stale_micros", false),
        defectLegacy = BuildConfig.DEBUG && intent.getBooleanExtra("defect_legacy", false),
        defectDays = BuildConfig.DEBUG && intent.getBooleanExtra("defect_days", false),
        defectMetrics = BuildConfig.DEBUG && intent.getBooleanExtra("defect_metrics", false),
        defectTrackers = BuildConfig.DEBUG && intent.getBooleanExtra("defect_trackers", false),
        seedProfileVariant = if (BuildConfig.DEBUG) {
            (intent.getStringExtra("seed_profile_variant") ?: "").takeIf { it in PROFILE_VARIANTS } ?: ""
        } else {
            ""
        },
        resetFillMissingBanner = BuildConfig.DEBUG && intent.getBooleanExtra("reset_fill_missing_banner", false),
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
        demoAiHoldMs = if (BuildConfig.DEBUG) {
            intent.getIntExtra("demo_ai_hold_ms", 0).coerceIn(0, 60_000)
        } else {
            0
        },
        demoAiFail = BuildConfig.DEBUG && intent.getBooleanExtra("demo_ai_fail", false),
        demoAiTruncate = BuildConfig.DEBUG && intent.getBooleanExtra("demo_ai_truncate", false),
        demoAiOff = BuildConfig.DEBUG && intent.hasExtra("demo_ai") && !intent.getBooleanExtra("demo_ai", false),
        runEntryBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_entry_benchmark", false),
        entryBenchmarkCount = intent.getIntExtra("benchmark_count", 3),
        runRelogBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_relog_benchmark", false),
        relogBenchmarkCount = intent.getIntExtra("relog_benchmark_count", 3),
        runWaterSipBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_water_sip_benchmark", false),
        waterSipBenchmarkCount = intent.getIntExtra("water_sip_benchmark_count", 1),
        runLocalEntryBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_local_entry_benchmark", false),
        localEntryBenchmarkCount = intent.getIntExtra("local_entry_benchmark_count", 3),
        runHubBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_hub_benchmark", false),
        runDaySwitchBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_day_switch_benchmark", false),
        runFlipBenchmark = BuildConfig.DEBUG && intent.getBooleanExtra("run_flip_benchmark", false),
        runOnDeviceLlmTest = BuildConfig.DEBUG && intent.getBooleanExtra("run_ondevice_llm_test", false),
        onDeviceLlmBackend = if (presetDaily) "gpu" else intent.getStringExtra("ondevice_llm_backend") ?: "gpu",
        onDeviceLlmMtp = presetDaily || intent.getBooleanExtra("ondevice_llm_mtp", false),
        onDeviceLlmModel = intent.getStringExtra("ondevice_llm_model")
            ?: OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
        onDeviceLlmTier = if (presetDaily) "daily" else intent.getStringExtra("ondevice_llm_tier") ?: "all",
        onDeviceLlmPrompt = if (presetDaily) "fewshot_units" else intent.getStringExtra("ondevice_llm_prompt") ?: "full",
        onDeviceLlmRepeat = intent.getIntExtra("ondevice_llm_repeat", 1).coerceIn(1, 5),
        onDeviceLlmClearCache = intent.getBooleanExtra("ondevice_llm_clear_cache", false),
        runGoalMatrixTest = BuildConfig.DEBUG && intent.getBooleanExtra("run_goal_matrix_test", false),
        goalMatrixScenarios = intent.getStringExtra("goal_matrix_scenarios") ?: "",
        goalMatrixRepeat = intent.getIntExtra("goal_matrix_repeat", 1).coerceIn(1, 5),
        goalMatrixTier = (intent.getStringExtra("goal_matrix_tier") ?: "auto")
            .takeIf { it in GOAL_MATRIX_TIERS } ?: "auto",
        goalMatrixProvider = (intent.getStringExtra("goal_matrix_provider") ?: "on_device")
            .takeIf { it in GOAL_MATRIX_PROVIDERS } ?: "on_device",
        goalMatrixModel = intent.getStringExtra("goal_matrix_model") ?: "",
        setOpenRouterKey = if (BuildConfig.DEBUG) intent.getStringExtra("set_openrouter_key") ?: "" else "",
        seedDebugProfileKey = BuildConfig.DEBUG && intent.getBooleanExtra("seed_debug_profile_key", false),
        diagnoseHealthConnect = BuildConfig.DEBUG &&
            intent.getBooleanExtra("diagnose_health_connect", false),
        previewDailySummary = BuildConfig.DEBUG &&
            intent.getBooleanExtra("preview_daily_summary", false),
        seedProgressiveMeal = BuildConfig.DEBUG &&
            intent.getBooleanExtra("seed_progressive_meal", false),
        runMealieImportTest = BuildConfig.DEBUG &&
            intent.getBooleanExtra("run_mealie_import_test", false),
        mealieTestUrl = if (BuildConfig.DEBUG) intent.getStringExtra("mealie_url") ?: "" else "",
        mealieTestToken = if (BuildConfig.DEBUG) intent.getStringExtra("mealie_token") ?: "" else "",
        debugBarcode = if (BuildConfig.DEBUG) intent.getStringExtra("debug_barcode") ?: "" else "",
        debugSnackbarPersistent = BuildConfig.DEBUG &&
            intent.getBooleanExtra("debug_snackbar_persistent", false),
        keepAiProfiles = BuildConfig.DEBUG && intent.getBooleanExtra("keep_ai", false),
        weightReminderTime = if (BuildConfig.DEBUG) {
            (intent.getStringExtra("set_weight_reminder") ?: "").takeIf { REMINDER_TIME.matches(it) } ?: ""
        } else {
            ""
        },
        clearTodayWeight = BuildConfig.DEBUG && intent.getBooleanExtra("clear_today_weight", false),
        screenshotAppearance = if (BuildConfig.DEBUG) {
            (intent.getStringExtra("screenshot_appearance") ?: "")
                .takeIf { it in SCREENSHOT_APPEARANCES } ?: ""
        } else {
            ""
        },
        screenshotLean = BuildConfig.DEBUG && intent.getBooleanExtra("screenshot_lean", false),
    )
    if (actions.resetOnboarding) intent.removeExtra("reset_onboarding")
    if (actions.seedTestData) intent.removeExtra("seed_test_data")
    if (actions.seedFull) intent.removeExtra("seed_full")
    if (actions.seedBusyHome) intent.removeExtra("seed_busy_home")
    if (actions.seedBodyMetrics) intent.removeExtra("seed_body_metrics")
    if (actions.seedBodyMetricsTwoYears) intent.removeExtra("seed_body_metrics_2y")
    if (actions.seedKetoSettings) intent.removeExtra("seed_keto_settings")
    if (actions.seedMacroCycle) intent.removeExtra("seed_macro_cycle")
    if (actions.seedFullMacroCycle) intent.removeExtra("macro_cycle")
    if (actions.seedGoalPhases) intent.removeExtra("seed_goal_phases")
    if (actions.seedAnalysisQueue) intent.removeExtra("seed_analysis_queue")
    if (actions.seedGapFixture) intent.removeExtra("seed_gap_fixture")
    if (actions.seedDefects) intent.removeExtra("seed_defects")
    if (actions.defectGaps) intent.removeExtra("defect_gaps")
    if (actions.defectStaleMicros) intent.removeExtra("defect_stale_micros")
    if (actions.defectLegacy) intent.removeExtra("defect_legacy")
    if (actions.defectDays) intent.removeExtra("defect_days")
    if (actions.defectMetrics) intent.removeExtra("defect_metrics")
    if (actions.defectTrackers) intent.removeExtra("defect_trackers")
    if (actions.seedProfileVariant.isNotEmpty()) intent.removeExtra("seed_profile_variant")
    if (actions.resetFillMissingBanner) intent.removeExtra("reset_fill_missing_banner")
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
    if (actions.demoAi) {
        intent.removeExtra("demo_ai")
        intent.removeExtra("demo_ai_hold_ms")
    }
    if (actions.demoAiFail) intent.removeExtra("demo_ai_fail")
    if (actions.demoAiTruncate) intent.removeExtra("demo_ai_truncate")
    if (actions.demoAiOff) intent.removeExtra("demo_ai")
    if (actions.seedProgressiveMeal) intent.removeExtra("seed_progressive_meal")
    if (actions.runMealieImportTest) {
        intent.removeExtra("run_mealie_import_test")
        intent.removeExtra("mealie_url")
        intent.removeExtra("mealie_token")
    }
    if (actions.runEntryBenchmark) {
        intent.removeExtra("run_entry_benchmark")
        intent.removeExtra("benchmark_count")
    }
    if (actions.runRelogBenchmark) {
        intent.removeExtra("run_relog_benchmark")
        intent.removeExtra("relog_benchmark_count")
    }
    if (actions.runWaterSipBenchmark) {
        intent.removeExtra("run_water_sip_benchmark")
        intent.removeExtra("water_sip_benchmark_count")
    }
    if (actions.runLocalEntryBenchmark) {
        intent.removeExtra("run_local_entry_benchmark")
        intent.removeExtra("local_entry_benchmark_count")
    }
    if (actions.runHubBenchmark) intent.removeExtra("run_hub_benchmark")
    if (actions.runDaySwitchBenchmark) intent.removeExtra("run_day_switch_benchmark")
    if (actions.runFlipBenchmark) intent.removeExtra("run_flip_benchmark")
    if (actions.diagnoseHealthConnect) intent.removeExtra("diagnose_health_connect")
    if (actions.previewDailySummary) intent.removeExtra("preview_daily_summary")
    if (actions.seedDebugProfileKey) intent.removeExtra("seed_debug_profile_key")
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
    if (actions.runGoalMatrixTest) intent.removeExtra("run_goal_matrix_test")
    if (actions.goalMatrixScenarios.isNotBlank()) intent.removeExtra("goal_matrix_scenarios")
    if (actions.goalMatrixRepeat > 1) intent.removeExtra("goal_matrix_repeat")
    if (actions.goalMatrixTier != "auto") intent.removeExtra("goal_matrix_tier")
    if (actions.goalMatrixProvider != "on_device") intent.removeExtra("goal_matrix_provider")
    if (actions.goalMatrixModel.isNotBlank()) intent.removeExtra("goal_matrix_model")
    if (actions.setOpenRouterKey.isNotEmpty()) intent.removeExtra("set_openrouter_key")
    }
    // Injected once, never on recreate: the barcode and the reminder time are
    // stripped whenever present (as parsed, even when rejected).
    if (intent.hasExtra("debug_barcode")) intent.removeExtra("debug_barcode")
    if (intent.hasExtra("set_weight_reminder")) intent.removeExtra("set_weight_reminder")
    if (actions.debugSnackbarPersistent) intent.removeExtra("debug_snackbar_persistent")
    if (actions.keepAiProfiles) intent.removeExtra("keep_ai")
    if (actions.clearTodayWeight) intent.removeExtra("clear_today_weight")
    if (intent.hasExtra("screenshot_appearance")) intent.removeExtra("screenshot_appearance")
    if (actions.screenshotLean) intent.removeExtra("screenshot_lean")
    return actions
}
