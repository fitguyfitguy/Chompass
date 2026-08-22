package app.chompass.services

import android.util.Log
import app.chompass.AppContainer
import app.chompass.data.KeyStore
import app.chompass.data.Keys
import app.chompass.models.ActivityLevel
import app.chompass.models.AIProvider
import app.chompass.models.CalorieSafety
import app.chompass.models.DietMode
import app.chompass.models.Gender
import app.chompass.models.NutritionConstants
import app.chompass.models.UserProfile
import app.chompass.models.WeightGoal
import app.chompass.services.ai.GoalRecalcTier
import app.chompass.services.ondevice.ModelCatalog
import app.chompass.services.ondevice.ModelDownloadManager
import java.time.LocalDate
import java.time.ZoneId
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Debug-only goal-calculation matrix: drives the REAL production path
 * (FoodAnalysisService.calculateGoals → selected provider) with a fixed
 * scenario matrix, so we can see what the model does with thin/sparse/rich/
 * disagreeing observed data, measured TDEE, keto, locks, and every weight goal.
 *
 * Triggered by the `run_goal_matrix_test` intent extra; `goal_matrix_scenarios`
 * filters to a comma-separated subset; `goal_matrix_tier` (safe|smart|auto) and
 * `goal_matrix_provider` (on_device|gemini|anthropic|openai) pick the tier and
 * provider for the run. With a cloud provider, a forced-fallback scenario
 * (cloud primary → on-device fallback) runs last and logs PASS/FAIL for the
 * per-dispatch tier rule. Results land in logcat tag `GoalMatrix`
 * (op=goal_matrix phase=...). See docs/ON_DEVICE_LLM.md § Goal calculation matrix.
 */
class GoalCalcMatrixTest(
    private val container: AppContainer,
    private val filter: String? = null,
    private val repeatCount: Int = 1,
    private val tierName: String = "auto",
    private val providerName: String = "on_device",
) {
    private val tag = "GoalMatrix"

    private val providers = mapOf(
        "on_device" to AIProvider.ON_DEVICE,
        "gemini" to AIProvider.GEMINI,
        "anthropic" to AIProvider.ANTHROPIC,
        "openai" to AIProvider.OPENAI,
    )

    private data class Scenario(
        val name: String,
        val profile: UserProfile,
        val forecast: WeightForecast? = null,
        val measuredTdee: Int? = null,
        val note: String,
    )

    /** Birthday for an exact age. */
    private fun profile(age: Int) = UserProfile(
        gender = Gender.MALE,
        birthday = LocalDate.now().minusYears(age.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant(),
        heightCm = 178.0,
        weightKg = 76.0,
    )

    private fun forecast(
        loggedDayAvg: Int,
        observedWeekly: Double?,
        weighIns: Int,
        spanDays: Int,
        foodDays: Int,
        tdee: Int,
        calendarDays: Int = 90,
        disagree: Boolean = false,
    ): WeightForecast {
        val sparse = foodDays.toDouble() / calendarDays < 0.5
        val avg = if (sparse) loggedDayAvg * foodDays / calendarDays else loggedDayAvg
        val implied = observedWeekly?.let {
            loggedDayAvg - NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(it)
        }
        val predicted = avg - tdee
        return WeightForecast(
            avgDailyCalories = avg,
            tdee = tdee,
            dailyEnergyBalance = predicted,
            predictedWeeklyChangeKg = predicted * 7.0 / NutritionConstants.KCAL_PER_KG_BODY_MASS,
            observedWeeklyChangeKg = observedWeekly,
            currentWeightKg = 76.0,
            predictedWeight30dKg = 76.0,
            predictedWeight60dKg = 76.0,
            predictedWeight90dKg = 76.0,
            daysToGoal = null,
            goalReachDate = null,
            hasEnoughData = foodDays >= 2 && weighIns >= 2,
            trendsDisagree = disagree,
            daysOfFoodData = foodDays,
            weightEntriesUsed = weighIns,
            calendarDaysInWindow = calendarDays,
            usesCalendarDayAverage = sparse,
            loggedDayAvgCalories = loggedDayAvg,
            firstLoggedDate = LocalDate.now().minusDays(calendarDays.toLong()),
            lastLoggedDate = LocalDate.now().minusDays(1),
            weightSpanDays = spanDays,
        )
    }

    private val base = profile(30) // BMR ≈ 1728, TDEE(moderate) ≈ 2531

    /** User's reported case: BMR 1730, TDEE 2682 (ACTIVE 1.55), formula 2132, implied 1742. */
    private val userCase = profile(36).copy(
        heightCm = 180.0,
        weightKg = 78.0,
        activityLevel = ActivityLevel.ACTIVE,
    )

    private val scenarios = listOf(
        Scenario(
            name = "no_data",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = null,
            note = "fresh user, no logs: must anchor on formula (~2531-550=1981)",
        ),
        Scenario(
            name = "sparse_up",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = +0.5, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2531, calendarDays = 4),
            note = "REPORTED BUG CASE: 2 weigh-ins blip up => implied ~1740 ≈ BMR; must stay on formula ~1981",
        ),
        Scenario(
            name = "sparse_up_active",
            profile = userCase.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2292, observedWeekly = +0.5, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2682, calendarDays = 4),
            note = "USER-REPORTED numbers: BMR 1730 / ACTIVE TDEE 2682 / implied 1742 vs formula 2132",
        ),
        Scenario(
            name = "medium_up",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = +0.5, weighIns = 6, spanDays = 12, foodDays = 8, tdee = 2531, calendarDays = 12),
            note = "6 weigh-ins / 12 days, blip up: implied ~1740; still thin, expect formula 1981",
        ),
        Scenario(
            name = "medium_up_below_bmr",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2350, observedWeekly = +0.6, weighIns = 8, spanDays = 20, foodDays = 15, tdee = 2531, calendarDays = 20),
            note = "8 weigh-ins / 20 days, +0.6: implied 2350-660=1690 < BMR 1728 => must discard",
        ),
        Scenario(
            name = "sparse_flat",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = 0.0, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2531, calendarDays = 4),
            note = "2 weigh-ins flat: implied ~2290, plausible band vs formula 1981",
        ),
        Scenario(
            name = "sparse_down",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = -0.8, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2531, calendarDays = 4),
            note = "2 weigh-ins drop fast: implied ~3170; thin data should NOT chase it",
        ),
        Scenario(
            name = "rich_consistent",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2100, observedWeekly = -0.4, weighIns = 12, spanDays = 40, foodDays = 40, tdee = 2531),
            note = "rich data agrees with formula: implied ~2540, expect ~formula 1981",
        ),
        Scenario(
            name = "rich_low_empirical",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2100, observedWeekly = 0.0, weighIns = 12, spanDays = 40, foodDays = 40, tdee = 2531),
            note = "rich data, true maintenance lower (2100): empirical should win; 2100-550=1550 < floor 1728 => expect floor",
        ),
        Scenario(
            name = "rich_implied_below_bmr",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2150, observedWeekly = +0.9, weighIns = 12, spanDays = 40, foodDays = 40, tdee = 2531),
            note = "implied ~1160 < BMR: prompt says discard => expect formula 1981",
        ),
        Scenario(
            name = "disagree",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2900, observedWeekly = +0.5, weighIns = 12, spanDays = 40, foodDays = 40, tdee = 2531, disagree = true),
            note = "under-logging warning: trust weight trend / lean formula, not raw intake",
        ),
        Scenario(
            name = "measured_tdee",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = +0.5, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2531, calendarDays = 4),
            measuredTdee = 2500,
            note = "Health Connect measured maintenance 2500 replaces formula => expect ~1950",
        ),
        Scenario(
            name = "keto",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0, dietMode = DietMode.KETO),
            forecast = null,
            note = "keto: carbs fixed 20-50g ceiling, protein >= floor, fat fills",
        ),
        Scenario(
            name = "locked",
            profile = base.copy(
                goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0,
                customCalories = 2400, caloriesLocked = true,
            ),
            forecast = forecast(loggedDayAvg = 2100, observedWeekly = -0.4, weighIns = 12, spanDays = 40, foodDays = 40, tdee = 2531),
            note = "calories locked at 2400: model must return exactly 2400",
        ),
        Scenario(
            name = "maintain_sparse_up",
            profile = base.copy(goal = WeightGoal.MAINTAIN),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = +0.5, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2531, calendarDays = 4),
            note = "maintain + thin blip: expect ~TDEE 2531, NOT BMR 1728",
        ),
        Scenario(
            name = "gain",
            profile = base.copy(goal = WeightGoal.GAIN, weeklyChangeKg = 0.5, goalWeightKg = 80.0),
            forecast = forecast(loggedDayAvg = 2100, observedWeekly = -0.4, weighIns = 12, spanDays = 40, foodDays = 40, tdee = 2531),
            note = "gain 0.5: expect ~2531+550=3081",
        ),
        Scenario(
            name = "bodyfat_km",
            profile = base.copy(goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0, bodyFatPercentage = 0.25),
            forecast = null,
            note = "Katch-McArdle: BMR=370+21.6*0.75*76=1601, TDEE=2346, lose=>1796",
        ),
        Scenario(
            name = "sedentary_sparse_up",
            profile = base.copy(activityLevel = ActivityLevel.SEDENTARY, goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = +0.5, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2074, calendarDays = 4),
            note = "sedentary: TDEE 2074, lose 0.5 => 1524 < floor 1728; formula already at floor",
        ),
        Scenario(
            name = "very_active_sparse_up",
            profile = base.copy(activityLevel = ActivityLevel.VERY_ACTIVE, goal = WeightGoal.LOSE, weeklyChangeKg = 0.5, goalWeightKg = 72.0),
            forecast = forecast(loggedDayAvg = 2290, observedWeekly = +0.5, weighIns = 2, spanDays = 4, foodDays = 2, tdee = 2981, calendarDays = 4),
            note = "very active: TDEE 2981, lose 0.5 => 2431; thin blip must not drag to BMR",
        ),
    )

    suspend fun run() {
        val wanted = filter?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        val list = if (wanted.isNullOrEmpty()) scenarios else scenarios.filter { it.name in wanted }
        val provider = providers[providerName] ?: AIProvider.ON_DEVICE
        val tierOverride = when (tierName) {
            "safe" -> GoalRecalcTier.SAFE
            "smart" -> GoalRecalcTier.SMART
            else -> null // auto: per-dispatch (cloud → SMART, on-device → SAFE)
        }
        Log.i(tag, "op=goal_matrix phase=start scenarios=${list.size} repeat=$repeatCount filter=${filter ?: "all"} tier=$tierName provider=${provider.name}")

        // Force the production dispatch onto the requested provider/tier for the
        // whole run, then restore the user's own settings afterwards. Keys are
        // only ever saved/restored, never logged.
        val prefs = container.prefs
        val keyStore = container.keyStore
        val prevAi = prefs.aiFeaturesEnabled.first()
        val prevProvider = prefs.selectedAIProvider.first()
        val prevModel = prefs.selectedAIModel.first()
        val prevFallback = prefs.fallbackEnabled.first()
        val prevFallbackProvider = prefs.selectedFallbackProvider.first()
        val prevFallbackModel = prefs.selectedFallbackModel.first()
        val prevPrimaryKey = keyStore.apiKey(provider)
        val prevTierOverride = container.foodAnalysis.goalTierOverrideForTest
        prefs.setAiFeaturesEnabled(true)
        prefs.setSelectedAIProvider(provider)
        prefs.setSelectedAIModel(provider.defaultModel)
        prefs.setFallbackEnabled(false)
        container.foodAnalysis.goalTierOverrideForTest = tierOverride
        try {
            list.forEachIndexed { i, s ->
                repeat(repeatCount) { rep -> runScenario(s, i + 1, list.size, rep) }
            }
            // Forced-fallback scenario: cloud primary → on-device fallback. Proves
            // the on-device leg gets the SAFE prompt (per-dispatch tier rule) and
            // the result reports the fallback. Only in auto tier, where the two
            // legs genuinely differ.
            if (provider != AIProvider.ON_DEVICE && tierOverride == null) {
                runFallbackScenario(keyStore, provider, prevPrimaryKey)
            }
            Log.i(tag, "op=goal_matrix phase=done scenarios=${list.size} repeat=$repeatCount")
        } finally {
            container.foodAnalysis.goalTierOverrideForTest = prevTierOverride
            prefs.setAiFeaturesEnabled(prevAi)
            prefs.setSelectedAIProvider(prevProvider)
            prevModel?.let { prefs.setSelectedAIModel(it) }
            prefs.setFallbackEnabled(prevFallback)
            prefs.setSelectedFallbackProvider(prevFallbackProvider)
            if (prevFallbackModel == null) {
                prefs.dataStore.edit { it.remove(Keys.FALLBACK_MODEL) }
            } else {
                prefs.setSelectedFallbackModel(prevFallbackModel)
            }
            if (prevPrimaryKey == null) keyStore.setApiKey(provider, null) else keyStore.setApiKey(provider, prevPrimaryKey)
        }
    }

    /**
     * Cloud-primary → on-device-fallback scenario: the primary key is swapped
     * for an invalid one so the cloud leg fails fast (or NoApiKey when the user
     * has no key), then the on-device leg must answer with the SAFE prompt and
     * the result must report the fallback. Never logs keys; restores the real
     * key and prefs afterwards. Skips (with a log) when the model is not
     * downloaded.
     */
    private suspend fun runFallbackScenario(keyStore: KeyStore, provider: AIProvider, prevPrimaryKey: String?) {
        val prefs = container.prefs
        val entry = ModelCatalog.forModelId(AIProvider.ON_DEVICE.defaultModel)
        if (!ModelDownloadManager(container.appContext).isDownloaded(entry)) {
            Log.i(tag, "op=goal_matrix phase=fallback skip=on_device_model_not_downloaded model=${entry.modelId}")
            return
        }
        val prevFallbackEnabled = prefs.fallbackEnabled.first()
        val prevFallbackProvider = prefs.selectedFallbackProvider.first()
        val prevFallbackModel = prefs.selectedFallbackModel.first()
        prefs.setFallbackEnabled(true)
        prefs.setSelectedFallbackProvider(AIProvider.ON_DEVICE)
        prefs.setSelectedFallbackModel(AIProvider.ON_DEVICE.defaultModel)
        if (prevPrimaryKey != null) keyStore.setApiKey(provider, "goal-matrix-invalid-key")
        try {
            val s = scenarios.first { it.name == "sparse_up" }
            runScenario(s, scenarios.size + 1, scenarios.size + 1, rep = 0, fallback = true)
        } finally {
            if (prevPrimaryKey != null) keyStore.setApiKey(provider, prevPrimaryKey)
            prefs.setFallbackEnabled(prevFallbackEnabled)
            prefs.setSelectedFallbackProvider(prevFallbackProvider)
            if (prevFallbackModel == null) {
                prefs.dataStore.edit { it.remove(Keys.FALLBACK_MODEL) }
            } else {
                prefs.setSelectedFallbackModel(prevFallbackModel)
            }
        }
    }

    private suspend fun runScenario(s: Scenario, index: Int, total: Int, rep: Int, fallback: Boolean = false) {
        val p = s.profile
        val bmr = p.bmr
        val tdee = p.tdee
        val formula = p.dailyCalories
        val floor = CalorieSafety.floorKcal(bmr)
        val ceiling = CalorieSafety.ceilingKcal(tdee, floor)
        val f = s.forecast
        val loggedAvg = f?.loggedDayAvgCalories?.takeIf { it > 0 } ?: f?.avgDailyCalories
        val implied = f?.observedWeeklyChangeKg?.let {
            (loggedAvg ?: 0) - NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(it)
        }
        val observedText = if (f != null && f.hasEnoughData) {
            val intakeBasis = if (f.usesCalendarDayAverage) {
                "avg $loggedAvg kcal/day across ${f.daysOfFoodData} logged days. Sparse coverage: calendar-day average is ${f.avgDailyCalories} kcal over ${f.calendarDaysInWindow} days"
            } else {
                "avg $loggedAvg kcal/day across ${f.daysOfFoodData} logged days"
            }
            "logged=$intakeBasis; observed=${f.observedWeeklyChangeKg?.let { String.format(java.util.Locale.US, "%+.2f kg/week", it) } ?: "n/a"} from ${f.weightEntriesUsed} weigh-ins (span ${f.weightSpanDays}d); implied=~$implied; formulaTDEE=${f.tdee}; disagree=${f.trendsDisagree}"
        } else {
            "none"
        }
        Log.i(
            tag,
            "op=goal_matrix phase=start scenario=${s.name} [$index/$total rep=$rep fallback=$fallback] bmr=${bmr.toInt()} tdee=${tdee.toInt()} " +
                "formula=$formula floor=$floor ceiling=$ceiling measured=${s.measuredTdee} keto=${p.dietMode == DietMode.KETO} " +
                "locked=${p.caloriesLocked} observed=[$observedText] note=${s.note}",
        )
        val startMs = System.currentTimeMillis()
        try {
            val result = withTimeout(150_000) {
                container.foodAnalysis.calculateGoals(
                    profile = p,
                    forecast = f,
                    heightMetric = true,
                    weightMetric = true,
                    measuredTdee = s.measuredTdee,
                    measurement = null,
                )
            }
            val ms = System.currentTimeMillis() - startMs
            val dFormula = result.calories - formula
            val dFloor = result.calories - floor
            val dImplied = implied?.let { result.calories - it }
            Log.i(
                tag,
                "op=goal_matrix phase=result scenario=${s.name} rep=$rep ms=$ms modelCalories=${result.calories} " +
                    "protein=${result.protein} carbs=${result.carbs} fat=${result.fat} " +
                    "tier=${result.tier} provider=${result.provider} model=${result.model} fallbackFired=${result.fallbackFired} " +
                    "dFormula=$dFormula dFloor=$dFloor dImplied=$dImplied reason=${result.reason?.replace(" ", "_") ?: "null"}",
            )
            if (fallback) {
                val ok = result.fallbackFired &&
                    result.tier == GoalRecalcTier.SAFE &&
                    result.provider == AIProvider.ON_DEVICE
                Log.i(
                    tag,
                    "op=goal_matrix phase=fallback ${if (ok) "PASS" else "FAIL"} " +
                        "expected=provider_ON_DEVICE,tier_SAFE,fallbackFired=true " +
                        "got=provider_${result.provider},tier_${result.tier},fallbackFired=${result.fallbackFired} " +
                        "primaryError=${result.primaryError?.replace(" ", "_")?.take(120) ?: "null"}",
                )
            }
        } catch (e: Throwable) {
            val ms = System.currentTimeMillis() - startMs
            Log.e(tag, "op=goal_matrix phase=error scenario=${s.name} rep=$rep ms=$ms err=${e::class.simpleName}: ${e.message}", e)
        }
    }
}
