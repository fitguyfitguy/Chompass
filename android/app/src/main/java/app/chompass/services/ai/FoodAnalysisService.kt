package app.chompass.services.ai

import app.chompass.data.KeyStore
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.R
import app.chompass.models.BodyMeasurement
import app.chompass.models.DietMode
import app.chompass.models.FoodEntry
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.GoalFormulaReference
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.NutritionConstants
import app.chompass.models.ServingUnitHeuristics
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.ServingUnitOption
import app.chompass.models.UserProfile
import app.chompass.BuildConfig
import app.chompass.services.OffPromptContext
import app.chompass.services.PerfLog
import app.chompass.services.WeightForecast
import app.chompass.ui.home.AnalysisPreviewSource
import app.chompass.ui.home.EntryAnalysisPhase
import app.chompass.ui.home.FoodAnalysisProgress
import app.chompass.services.health.HealthEnergySummary
import app.chompass.services.ondevice.OnDeviceLlmGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.Locale
import app.chompass.models.UnitFormat

// Shared lines of the entry-analysis prompts ("lean" wording, A/B-validated in
// docs/benchmarks/food_accuracy — lean_units2 variant). Keep in sync with the
// production_* builders in docs/benchmarks/food_accuracy/prompts.py.
private const val ENTRY_JSON_SCHEMA =
    """{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"<single specific food emoji>","sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[]}"""

private const val ENTRY_JSON_SCHEMA_WITH_CONSTITUENTS =
    """{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"<single specific food emoji>","sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[],"constituents":[{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"...","unit_options":[]}]}"""

private const val ENTRY_NUTRIENT_UNITS =
    "Calories are integers; other nutrients are numbers (grams for protein/carbs/fat/sugars/fiber/fats/omega-3; " +
        "mg for cholesterol, sodium, potassium, calcium, iron, magnesium, zinc, vitamin C, vitamin E; " +
        "mcg for vitamins A, D, B12, K and folate). serving_size_grams is the estimated total weight in grams."

private const val ENTRY_UNIT_OPTIONS_RULE =
    """unit_options entries look like {"unit":"slice","quantity":2,"grams_per_unit":180}: """ +
        "the natural non-gram unit (slice, piece, cup, ml, tbsp, can) with quantity covering " +
        "the whole analyzed amount and its weight per unit. Use [] only when no non-gram unit " +
        "fits; never use g/grams as a unit."

private const val ENTRY_CONSTITUENTS_RULE =
    "constituents is optional. For multi-item meals, list each distinct edible item " +
        "(egg, toast, butter, drink, side) with its own macros, serving_size_grams, and " +
        "unit_options when a non-gram unit is obvious. Keep top-level fields as the meal " +
        "total. Constituent grams MUST sum to serving_size_grams within ±5%. Constituent " +
        "calories/protein/carbs/fat MUST each sum to the matching meal total within ±5%. " +
        "Include every named or clearly implied edible item; do not invent extras. Use [] " +
        "for a single undivided food."

private const val ENTRY_EMOJI_NULL_RULE =
    "For \"emoji\" pick the single most specific food emoji for this dish. " +
        "Use null for any nutrient you cannot estimate."

/**
 * Single-shot food / text / nutrition-label analysis. Port of iOS GeminiService.
 * Routes the call to the right per-format client based on the user's selected provider.
 */
class FoodAnalysisService(
    private val prefs: PreferencesStore? = null,
    private val keyStore: KeyStore? = null,
    private val okHttp: OkHttpClient = defaultClient,
    private val onDeviceGateway: OnDeviceLlmGateway? = null,
    internal val callAiDelegate: (suspend (prompt: String, imageBytesList: List<ByteArray>, op: String) -> String)? = null,
    internal val inferenceModeForTest: ServingUnitInferenceMode? = null,
) {
    init {
        require((prefs != null && keyStore != null) || callAiDelegate != null) {
            "FoodAnalysisService requires prefs and keyStore unless callAiDelegate is provided"
        }
    }

    suspend fun estimateOptionalNutrientGoals(profile: UserProfile?): OptionalNutrientGoals {
        val profileContext = profile?.let {
            """
                Profile:
                - age: ${it.age}
                - gender: ${it.gender.name.lowercase()}
                - height_cm: ${String.format(java.util.Locale.US, "%.1f", it.heightCm)}
                - weight_kg: ${String.format(java.util.Locale.US, "%.1f", it.weightKg)}
                - activity_level: ${it.activityLevel.name.lowercase()}
                - weight_goal: ${it.goal.name.lowercase()}
                - daily_calories: ${it.effectiveCalories}
                - daily_protein_g: ${it.effectiveProtein}
                - daily_carbs_g: ${it.effectiveCarbs}
                - daily_fat_g: ${it.effectiveFat}
                - diet_mode: ${it.dietMode.name.lowercase()}${if (it.dietMode == DietMode.KETO) " (net carbs capped at ${it.ketoActiveCarbTarget} g/day; keep sugar and added_sugar goals low and consistent with keto)" else ""}
            """.trimIndent()
        } ?: "No user profile is available. Use conservative general adult defaults."
        val prompt = """
            Estimate practical daily goals for nutrients outside the app's calorie/protein/carbs/fat calculator.

            $profileContext

            Return ONLY JSON in this exact shape:
            {"sugar":50,"added_sugar":25,"fiber":30,"saturated_fat":20,"cholesterol":300,"sodium":2300,"potassium":3500,"trans_fat":0,"calcium":1000,"iron":18,"magnesium":400,"zinc":11,"vitamin_a":900,"vitamin_c":90,"vitamin_d":20,"vitamin_b12":3,"vitamin_e":15,"vitamin_k":120,"folate":400,"omega_3":2}

            Rules:
            - Do not return calories, protein, carbs, or fat.
            - Keep this independent from macro calculation; only estimate the listed optional nutrient goals.
            - sugar, added_sugar, fiber, saturated_fat, trans_fat, and omega_3 are grams per day.
            - cholesterol, sodium, potassium, calcium, iron, magnesium, zinc, vitamin_c, and vitamin_e are milligrams per day.
            - vitamin_a, vitamin_d, vitamin_b12, vitamin_k, and folate are micrograms per day.
            - Use realistic non-medical nutrition targets for an average adult adjusted by profile and calorie target.
            - Keep added_sugar and saturated_fat near or below 10% of calories when possible.
            - Fiber should generally scale around 14g per 1000 kcal, with a practical adult range.
            - Sodium should usually stay near general adult guidance unless the profile strongly suggests otherwise.
            - Potassium, calcium, iron, magnesium, zinc, vitamins, folate, and omega-3 should use practical daily targets, not food-log intake.
            - Use integers only.
        """.trimIndent()
        return FoodJsonParser.parseOptionalNutrientGoals(callAi(prompt, imageBytes = null))
    }

    suspend fun suggestHealthEnergyGoals(
        profile: UserProfile,
        energy: HealthEnergySummary,
        heightMetric: Boolean,
        weightMetric: Boolean
    ): HealthEnergyGoalSuggestion {
        val weight = if (weightMetric) {
            String.format(java.util.Locale.US, "%.1f kg", profile.weightKg)
        } else {
            String.format(java.util.Locale.US, "%.1f lb", UnitFormat.kgToLbs(profile.weightKg))
        }
        val height = if (heightMetric) {
            String.format(java.util.Locale.US, "%.0f cm", profile.heightCm)
        } else {
            String.format(java.util.Locale.US, "%.1f in", UnitFormat.cmToInches(profile.heightCm))
        }
        val bodyFat = profile.bodyFatPercentage
            ?.let { "${(it * 100).toInt()}%" }
            ?: "not set"
        val goalWeight = profile.goalWeightKg?.let { kg ->
            if (weightMetric) String.format(java.util.Locale.US, "%.1f kg", kg)
            else String.format(java.util.Locale.US, "%.1f lb", UnitFormat.kgToLbs(kg))
        } ?: "not set"
        val healthTotalLine = energy.totalAverageCalories
            ?.let { "$it kcal/day from active + basal energy" }
            ?: "total energy unavailable; estimate total burn from app BMR + Health Connect active energy"

        val prompt = """
            You are setting a daily calorie target for a food tracking app.
            Return ONLY valid JSON with these exact keys:
            {"calories":2000,"reason":"Short reason under 100 characters"}

            Use Health Connect energy as the primary activity signal, but keep the app's existing formula as a sanity check.
            If Health Connect total energy is unavailable, estimate total daily burn from app BMR plus Health Connect active energy.
            Apply the user's weight goal and weekly change preference to choose the calorie target.
            Keep calories practical for a consumer food tracker: 800-6000 kcal.
            Do not set protein, carbs, or fat; the app keeps macros unlocked on auto-balance unless the user manually locks them.
            Use integers only for calories. Do not include any other keys.

            User profile:
            - Gender: ${profile.gender.name.lowercase()}
            - Age: ${profile.age}
            - Height: $height
            - Weight: $weight
            - Activity level setting: ${profile.activityLevel.name.lowercase()}
            - Weight goal: ${profile.goal.name.lowercase()}
            - Weekly change preference: ${profile.weeklyChangeKg?.let { String.format(java.util.Locale.US, "%.2f kg/week", it) } ?: "maintain"}
            - Goal weight: $goalWeight
            - Body fat: $bodyFat
            ${dietModeLine(profile)}

            Existing app formula:
            - BMR: ${profile.bmr.toInt()} kcal/day
            - TDEE: ${profile.tdee.toInt()} kcal/day
            - Formula calorie target: ${profile.dailyCalories} kcal/day

            Health Connect energy from ${energy.daysUsed} of the last ${energy.requestedDays} completed days:
            - Active energy average: ${energy.activeAverageCalories} kcal/day
            - Basal energy average: ${energy.basalAverageCalories?.let { "$it kcal/day" } ?: "not available"}
            - Health total: $healthTotalLine
        """.trimIndent()
        return FoodJsonParser.parseHealthEnergyGoalSuggestion(callAi(prompt, imageBytes = null))
    }

    /**
     * AI-driven daily target calculation (port of iOS GeminiService.calculateGoals). Sends the
     * app's formulas, the profile, and — when available — recent logged intake + observed weight
     * trend so the model can estimate true maintenance empirically (hit-and-trial) rather than
     * trusting the formula alone. Caller falls back to the formula when this throws.
     */
    suspend fun calculateGoals(
        profile: UserProfile,
        forecast: WeightForecast?,
        heightMetric: Boolean,
        weightMetric: Boolean,
        measuredTdee: Int? = null,
        measurement: BodyMeasurement? = null
    ): GoalCalculation {
        val weight = if (weightMetric) String.format(Locale.US, "%.1f kg", profile.weightKg)
            else String.format(Locale.US, "%.1f lb", UnitFormat.kgToLbs(profile.weightKg))
        val height = if (heightMetric) String.format(Locale.US, "%.0f cm", profile.heightCm)
            else String.format(Locale.US, "%.1f in", UnitFormat.cmToInches(profile.heightCm))
        val bodyFat = profile.bodyFatPercentage?.let { "${(it * 100).toInt()}%" } ?: "not set"
        val goalWeight = profile.goalWeightKg?.let { kg ->
            if (weightMetric) String.format(Locale.US, "%.1f kg", kg) else String.format(Locale.US, "%.1f lb", UnitFormat.kgToLbs(kg))
        } ?: "not set"
        val weekly = profile.weeklyChangeKg?.let { String.format(Locale.US, "%.2f kg/week", it) } ?: "not set (maintain)"
        val bmrMethod = if (profile.usesBodyFatForBMR) "Katch-McArdle (body fat known and enabled)" else "Mifflin-St Jeor"

        val observedSection = buildString {
            if (forecast != null && forecast.hasEnoughData) {
                appendLine()
                appendLine("OBSERVED DATA: from the user's OWN logs (prefer this over the formula when reliable):")
                val intakeBasis = if (forecast.usesCalendarDayAverage) {
                    "avg ${forecast.avgDailyCalories} kcal/day spread across ${forecast.calendarDaysInWindow} calendar days (${forecast.daysOfFoodData} logged days; sparse logging)"
                } else {
                    "avg ${forecast.avgDailyCalories} kcal/day across ${forecast.daysOfFoodData} logged days"
                }
                appendLine("- Logged intake: $intakeBasis")
                val obs = forecast.observedWeeklyChangeKg
                if (obs != null) {
                    val obsStr = if (weightMetric) String.format(Locale.US, "%+.2f kg/week", obs)
                        else String.format(Locale.US, "%+.2f lb/week", UnitFormat.kgToLbs(obs))
                    val empiricalTdee = forecast.avgDailyCalories -
                        NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(obs)
                    appendLine("- Observed weight trend: $obsStr from ${forecast.weightEntriesUsed} weigh-ins")
                    appendLine("- Implied actual maintenance (logged intake minus the weekly change): ~$empiricalTdee kcal/day")
                } else {
                    appendLine("- Observed weight trend: not enough weigh-ins yet to measure")
                }
                appendLine("- Formula TDEE for comparison: ${forecast.tdee} kcal/day")
                if (forecast.trendsDisagree) {
                    appendLine("- WARNING: logged intake and the real weight trend DISAGREE. The user is likely under-logging. Trust the weight trend over raw logged calories.")
                }
                append("HIT-AND-TRIAL: when this observed data is reliable, estimate true maintenance from intake and the real weight trend, then apply the goal + weekly-change target to THAT maintenance instead of the formula TDEE. If data is thin or trends disagree, lean on the formula/weight trend accordingly. Keep calories within 800-6000.")
            }
        }

        // Energy Burn toggle: when on (and Health Connect has enough data) this measured
        // maintenance replaces the formula TDEE as the calorie anchor.
        val measuredSection = if (measuredTdee != null) {
            "\nMEASURED ENERGY BURN: the user's REAL maintenance from Health Connect (14-day average of active + basal calories). Use THIS as the maintenance/TDEE anchor INSTEAD of the formula TDEE: $measuredTdee kcal/day. Apply the weight goal and weekly-change adjustment to this measured maintenance. Still sanity-check it against the observed weight trend."
        } else ""

        // Optional tape-measure circumferences + derived metrics. Extra signal only — never overrides
        // the formulas. A shrinking waist alongside flat/declining weight implies recomposition.
        val measurementsSummary = measurement?.promptSummary(profile.gender, profile.heightCm)
        val measurementsSection = if (measurementsSummary != null) {
            "\nBODY MEASUREMENTS: the user's latest tape-measure circumferences and the metrics derived from them. Use as extra signal: a shrinking waist with steady or falling weight suggests recomposition, so keep protein high and don't over-cut. Treat the US-Navy body-fat figure as a rough estimate, not exact.\n$measurementsSummary"
        } else ""

        val prompt = """
            You are the goal calculator for a calorie & macro tracking app. Using the FORMULAS, the USER PROFILE, and any OBSERVED DATA below, compute the user's daily targets.
            Return ONLY valid JSON with these exact keys (integers, plus a short reason):
            {"calories":2000,"protein":150,"carbs":200,"fat":60,"reason":"Short reason under 100 characters"}

            Use the app's formulas as the basis. When OBSERVED DATA is present and reliable, prefer the empirical maintenance estimate it implies over the formula TDEE.
            FORMULAS
            - BMR (Mifflin-St Jeor): base = 10*weightKg + 6.25*heightCm - 5*age - 161; if male add 166; female/other use base.
            - BMR (Katch-McArdle, used when body fat is known and enabled): 370 + 21.6 * (1 - bodyFatFraction) * weightKg.
            - TDEE = BMR * activity multiplier. Multipliers: ${GoalFormulaReference.activityMultipliersLine()}.
            - Calorie target = TDEE + adjustment. adjustment = 0 for maintain; ${GoalFormulaReference.calorieAdjustmentLine()}.
            - Protein: aim NEAR the formula protein value shown below. That value is the activity multiplier (${GoalFormulaReference.proteinPerKgLine()} g/kg; +0.2 if losing) applied to the user's ${if (profile.bodyFatPercentage != null) "lean body mass" else "full bodyweight"}. You may choose a value within about ±15% of it based on the weight goal and the observed history (lean toward the higher end during a calorie deficit to preserve muscle). Do NOT scale protein down just to fit a lower calorie target.
            - Fat: 0.6 g/kg of full bodyweight.
            - Carbs: the calories remaining after protein (4 kcal/g) and fat (9 kcal/g), divided by 4. Keep 4*protein + 4*carbs + 9*fat approximately equal to calories.
            BMR method in effect for this user: $bmrMethod.
            Keep calories within 800-6000. Use integers only. Output no keys other than calories, protein, carbs, fat, reason.

            USER PROFILE
            - Gender: ${profile.gender.name.lowercase()}
            - Age: ${profile.age}
            - Height: $height
            - Weight: $weight
            - Body fat: $bodyFat
            - Activity level: ${profile.activityLevel.name.lowercase()}
            - Weight goal: ${profile.goal.name.lowercase()}
            - Weekly change preference: $weekly
            - Goal weight: $goalWeight
            ${dietModeLine(profile)}
            ${ketoGoalRulesSection(profile)}
            APP FORMULA REFERENCE (already computed deterministically; use as the anchor)
            - BMR: ${profile.bmr.toInt()} kcal/day
            - TDEE: ${profile.tdee.toInt()} kcal/day
            - Formula calorie target: ${profile.dailyCalories} kcal/day
            - Formula macros: ${profile.proteinGoal} g protein, ${profile.carbsGoal} g carbs, ${profile.fatGoal} g fat
            $measuredSection
            $measurementsSection
            $observedSection
        """.trimIndent()
        return FoodJsonParser.parseGoalCalculation(callAi(prompt, imageBytes = null))
    }

    suspend fun suggestMealWhatIf(
        entry: FoodEntry,
        dayEntries: List<FoodEntry>,
        profile: UserProfile,
        weightMetric: Boolean
    ): String {
        val beforeCalories = dayEntries.sumOf { it.calories }
        val beforeProtein = dayEntries.sumOf { it.protein }
        val beforeCarbs = dayEntries.sumOf { it.carbs }
        val beforeFat = dayEntries.sumOf { it.fat }
        val afterCalories = beforeCalories + entry.calories
        val afterProtein = beforeProtein + entry.protein
        val afterCarbs = beforeCarbs + entry.carbs
        val afterFat = beforeFat + entry.fat
        val weight = if (weightMetric) {
            String.format(Locale.US, "%.1f kg", profile.weightKg)
        } else {
            String.format(Locale.US, "%.1f lb", UnitFormat.kgToLbs(profile.weightKg))
        }
        val bodyFat = profile.bodyFatPercentage
            ?.let { "${(it * 100).toInt()}%" }
            ?: "not set"
        fun grams(value: Double) = String.format(Locale.US, "%.1fg", value)

        val prompt = """
            The user tapped "What if?" before logging a meal in a nutrition tracker.
            Return 2-4 short sentences, no markdown, under 90 words.
            Explain how this meal changes today's calorie/protein/carbs/fat totals compared with the user's goals, then give one practical action: log it as-is, reduce portion, replace part of it, or adjust the next meal.
            Stay practical and non-medical.${if (profile.dietMode == DietMode.KETO) "\nThe user follows a KETO diet: the carb goal below is a hard net-carb ceiling of ${profile.ketoActiveCarbTarget}g/day, not a target to fill. Frame the advice around staying under it (carb-heavy meals deserve a swap or smaller portion; high fat is expected and fine)." else ""}

            User profile:
            - Gender: ${profile.gender.name.lowercase()}
            - Age: ${profile.age}
            - Weight: $weight
            - Activity level: ${profile.activityLevel.name.lowercase()}
            - Weight goal: ${profile.goal.name.lowercase()}
            - Body fat: $bodyFat
            ${dietModeLine(profile)}

            Daily goals:
            - Calories: ${profile.effectiveCalories} kcal
            - Protein: ${profile.effectiveProtein}g
            - Carbs: ${profile.effectiveCarbs}g
            - Fat: ${profile.effectiveFat}g

            Today's totals before this meal:
            - Calories: $beforeCalories kcal
            - Protein: ${grams(beforeProtein)}
            - Carbs: ${grams(beforeCarbs)}
            - Fat: ${grams(beforeFat)}

            Meal being reviewed:
            - Name: ${entry.name}
            - Calories: ${entry.calories} kcal
            - Protein: ${grams(entry.protein)}
            - Carbs: ${grams(entry.carbs)}
            - Fat: ${grams(entry.fat)}

            Today's totals if logged:
            - Calories: $afterCalories kcal
            - Protein: ${grams(afterProtein)}
            - Carbs: ${grams(afterCarbs)}
            - Fat: ${grams(afterFat)}
        """.trimIndent()
        return callAi(prompt, imageBytes = null).trim()
    }

    private suspend fun mealConstituentsRequested(): Boolean {
        val store = prefs ?: return true
        if (!store.mealConstituentsEnabled.first()) return false
        // Local Gemma / on-device models fail the constituents reconcile gate.
        return store.selectedAIProvider.first() != AIProvider.ON_DEVICE
    }

    private suspend fun entryJsonSchema(): String =
        if (mealConstituentsRequested()) ENTRY_JSON_SCHEMA_WITH_CONSTITUENTS else ENTRY_JSON_SCHEMA

    private suspend fun entryConstituentsRuleOrEmpty(): String =
        if (mealConstituentsRequested()) ENTRY_CONSTITUENTS_RULE else ""

    private suspend fun parseEntryFood(raw: String): FoodAnalysis {
        val parsed = FoodJsonParser.parseFood(raw)
        return if (mealConstituentsRequested()) parsed else parsed.copy(constituents = emptyList())
    }

    suspend fun analyzeText(
        description: String,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): FoodAnalysis {
        val schema = entryJsonSchema()
        val constituentsRule = entryConstituentsRuleOrEmpty()
        val prompt = buildString {
            appendLine("Estimate the nutritional content for: $description")
            appendLine("Respond ONLY with JSON:")
            appendLine(schema)
            appendLine(ENTRY_NUTRIENT_UNITS)
            appendLine(ENTRY_UNIT_OPTIONS_RULE)
            if (constituentsRule.isNotEmpty()) appendLine(constituentsRule)
            append(ENTRY_EMOJI_NULL_RULE)
        }.trimIndent()
        val raw = callAi(prompt, null, op = "analyzeText", onProgress = onProgress)
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Parsing))
        val analysis = PerfLog.measure("analyzeText", "parse", "chars=${raw.length}") { parseEntryFood(raw) }
        return finalizeAnalysis(analysis, imageBytes = null, description = description, onProgress = onProgress)
    }

    private suspend fun entryResponseBlock(): String {
        val schema = entryJsonSchema()
        val constituentsRule = entryConstituentsRuleOrEmpty()
        return buildString {
            appendLine("Respond ONLY with JSON:")
            appendLine(schema)
            appendLine(ENTRY_NUTRIENT_UNITS)
            appendLine(ENTRY_UNIT_OPTIONS_RULE)
            if (constituentsRule.isNotEmpty()) appendLine(constituentsRule)
            append(ENTRY_EMOJI_NULL_RULE)
        }
    }

    suspend fun analyzeAuto(
        imageBytes: ByteArray,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): FoodAnalysis {
        var prompt = """
            Analyze this image. It could be either a photo of food OR a nutrition facts label.
            If it's a food photo: estimate the nutritional content of the visible food.
            If a utensil, hand, coin, or common object is visible next to the food, use it as a size reference to refine your portion estimate.
            If it's a nutrition label: read the values and calculate for one serving size as listed on the label.
            ${entryResponseBlock()}
        """.trimIndent()
        prompt = appendOffBarcodeContext(prompt, listOf(imageBytes), onProgress)
        val raw = callAi(prompt, imageBytes, op = "analyzeAuto", onProgress = onProgress)
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Parsing))
        val analysis = PerfLog.measure("analyzeAuto", "parse", "chars=${raw.length}") { parseEntryFood(raw) }
        return finalizeAnalysis(analysis, imageBytes = imageBytes, description = null, onProgress = onProgress)
    }

    suspend fun analyzeFood(
        imageBytes: ByteArray,
        description: String? = null,
        singleIngredient: Boolean = false,
        confirmedPortionGrams: Double? = null,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): FoodAnalysis {
        val responseBlock = entryResponseBlock()
        var prompt = if (singleIngredient) {
            """
            Analyze this food image. It is a single weighed ingredient being added to a meal.
            Estimate only the visible item on its own (do not invent other ingredients).
            If a utensil, hand, coin, or common object is visible next to the food, use it as a size reference to refine your portion estimate.
            $responseBlock
            """.trimIndent()
        } else {
            """
            Analyze this food image. Estimate the nutritional content of the visible food.
            If a utensil, hand, coin, or common object is visible next to the food, use it as a size reference to refine your portion estimate.
            $responseBlock
            """.trimIndent()
        }
        prompt = appendUserMealContext(prompt, description, confirmedPortionGrams)
        prompt = appendOffBarcodeContext(prompt, listOf(imageBytes), onProgress)
        val raw = callAi(prompt, imageBytes, op = "analyzeFood", onProgress = onProgress)
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Parsing))
        val analysis = PerfLog.measure("analyzeFood", "parse", "chars=${raw.length}") { parseEntryFood(raw) }
        return finalizeAnalysis(analysis, imageBytes = imageBytes, description = description, onProgress = onProgress)
    }

    suspend fun analyzeFood(
        imageBytesList: List<ByteArray>,
        description: String? = null,
        singleIngredient: Boolean = false,
        confirmedPortionGrams: Double? = null,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): FoodAnalysis {
        if (imageBytesList.filter { it.isNotEmpty() }.size <= 1 && singleIngredient) {
            val only = imageBytesList.firstOrNull { it.isNotEmpty() } ?: throw AiError.InvalidResponse
            return analyzeFood(
                only,
                description,
                singleIngredient = true,
                confirmedPortionGrams = confirmedPortionGrams,
                onProgress = onProgress,
            )
        }
        val responseBlock = entryResponseBlock()
        var prompt = if (singleIngredient) {
            """
            Analyze these food images. They show a single weighed ingredient being added to a meal.
            Estimate only that ingredient (do not invent other meal components or double-count).
            If a utensil, hand, coin, or common object is visible next to the food in any image, use it as a size reference to refine your portion estimate.
            $responseBlock
            """.trimIndent()
        } else {
            """
            Analyze these food images together. They are different angles or supporting photos of the same meal.
            Use all images to estimate the total nutritional content for the serving shown. Do not double-count the meal across images.
            If a utensil, hand, coin, or common object is visible next to the food in any image, use it as a size reference to refine your portion estimate.
            $responseBlock
            """.trimIndent()
        }
        prompt = appendUserMealContext(prompt, description, confirmedPortionGrams)
        val images = imageBytesList.filter { it.isNotEmpty() }
        if (images.isEmpty()) throw AiError.InvalidResponse
        prompt = appendOffBarcodeContext(prompt, images, onProgress)
        val raw = callAi(prompt, images, op = "analyzeFoodMulti", onProgress = onProgress)
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Parsing))
        val analysis = PerfLog.measure("analyzeFoodMulti", "parse", "chars=${raw.length}") { parseEntryFood(raw) }
        return finalizeAnalysis(analysis, imageBytes = images.first(), description = description, onProgress = onProgress)
    }

    /**
     * Append free-form user note (identity / cooking hints) and, separately, a
     * controlled confirmed-portion instruction so grams are not mixed into the
     * free-form note string.
     */
    internal fun appendUserMealContext(
        prompt: String,
        description: String?,
        confirmedPortionGrams: Double?,
    ): String {
        var next = prompt
        if (!description.isNullOrBlank()) {
            next += "\n\nAdditional context from the user about this meal: $description\nUse this context to improve accuracy of identification, portion size, and nutrition estimates."
        }
        val grams = confirmedPortionGrams?.takeIf { it > 0 }
        if (grams != null) {
            val formatted = String.format(Locale.US, "%.1f", grams).trimEnd('0').trimEnd('.')
            next += "\n\nUser-confirmed total edible portion: $formatted g. Treat this as ground truth for serving_size_grams and scale all nutrients to that mass."
        }
        return next
    }

    /**
     * Best-effort still-image barcode → Open Food Facts → soft prompt context.
     * Never blocks analysis on miss/timeout; images are still sent to the model.
     */
    private suspend fun appendOffBarcodeContext(
        prompt: String,
        imageBytesList: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ): String {
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.LookingUpBarcode))
        val offContext = OffPromptContext.collectFromImages(imageBytesList, prefs) ?: return prompt
        return "$prompt\n\n$offContext"
    }

    /**
     * Recognition-only pass for grounded entry. Identifies meal components and
     * portion hints; must NOT invent nutrient totals (those come from USDA/OFF/history).
     */
    suspend fun recognizeFoodComponents(
        description: String? = null,
        imageBytesList: List<ByteArray> = emptyList(),
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): app.chompass.models.FoodRecognitionResult {
        val hasImages = imageBytesList.any { it.isNotEmpty() }
        val text = description?.trim().orEmpty()
        if (!hasImages && text.isEmpty()) throw AiError.InvalidResponse
        val prompt = buildString {
            appendLine("Identify the food(s) in this meal for a nutrition database lookup.")
            appendLine("Do NOT estimate calories, protein, carbs, fat, or micronutrients.")
            appendLine("Focus on identity, brands, preparation, barcodes if visible, and portion hints.")
            appendLine("Respond ONLY with JSON:")
            appendLine(
                """{"meal_name":"...","emoji":"<single food emoji or null>","notes":null,"components":[{"name":"...","brand":null,"preparation":null,"estimated_grams":null,"portion_hint":null,"barcode":null,"quantity":null,"unit":null}]}"""
            )
            appendLine("Rules:")
            appendLine("- Split distinct foods into separate components (e.g. eggs + toast + butter).")
            appendLine("- estimated_grams is the edible amount in grams when reasonably guessable; else null.")
            appendLine("- portion_hint is a short phrase like \"1 large egg\" or \"2 slices\".")
            appendLine("- unit should be a non-gram household unit when clear (slice, cup, tbsp, piece, ml).")
            appendLine("- barcode is digits only when a package barcode is readable; else null.")
            appendLine("- Use null for unknown optional fields. Keep meal_name short and human-readable.")
            if (text.isNotEmpty()) {
                appendLine()
                appendLine("User description: $text")
            }
            if (hasImages) {
                appendLine()
                appendLine("Use the attached image(s) as the primary visual evidence.")
            }
        }
        val images = imageBytesList.filter { it.isNotEmpty() }
        val raw = callAi(prompt, images, op = "recognizeFood", onProgress = onProgress)
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Parsing))
        return PerfLog.measure("recognizeFood", "parse", "chars=${raw.length}") {
            FoodJsonParser.parseRecognition(raw)
        }
    }

    /**
     * True when the selected primary provider can run the grounded tool loop
     * (cloud BYOK). On-device falls back to deterministic retrieve/rank.
     */
    suspend fun supportsGroundedToolLoop(): Boolean {
        if (callAiDelegate != null) return false
        val primary = prefs!!.selectedAIProvider.first()
        return primary.apiFormat != AIProvider.ApiFormat.ON_DEVICE
    }

    /**
     * Bounded tool-use grounding: model searches USDA/history/barcode then
     * calls finalize_grounding. Nutrients are not invented in the loop.
     */
    suspend fun runGroundedToolLoop(
        tools: app.chompass.services.grounding.GroundingTools,
        userMessage: String,
        imageBytesList: List<ByteArray> = emptyList(),
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): GroundedToolLoop.LoopResult {
        if (callAiDelegate != null) {
            throw AiError.Api("Grounded tool loop requires a live AI provider.", messageRes = R.string.ai_error_grounded_requires_provider)
        }
        val primary = prefs!!.selectedAIProvider.first()
        if (primary.apiFormat == AIProvider.ApiFormat.ON_DEVICE) {
            throw AiError.Api("Grounded tool loop is not available for on-device models.", messageRes = R.string.ai_error_grounded_on_device)
        }
        val primaryModel = primary.supportedModelOrDefault(prefs.selectedAIModel.first())
        val primaryBaseUrl = prefs.customBaseUrl(primary).first()?.takeIf { it.isNotEmpty() }?.let(AiHttp::normalizeCustomBaseUrl) ?: primary.baseUrl
        val primaryKey = AiHttp.sanitizeApiKey(keyStore!!.apiKey(primary))
        if (primary.requiresApiKey && primaryKey.isNullOrEmpty()) throw AiError.NoApiKey
        val maxTokens = prefs.maxResponseTokens.first()
        val readTimeoutSeconds = prefs.aiReadTimeoutSeconds.first()
        val httpClient = AiHttp.clientForProvider(okHttp, primary, readTimeoutSeconds)
        val context = prefs.userContext.first()
        val languageLine = nonEnglishResponseLanguage()?.let {
            "Write human-readable meal/component names in $it when natural. Keep tool JSON keys and source_id values unchanged.\n\n"
        } ?: ""
        val contextLine = if (context.isNotBlank()) "User context: $context\n\n" else ""
        val message = languageLine + contextLine + userMessage
        return try {
            GroundedToolLoop.run(
                client = httpClient,
                provider = primary,
                model = primaryModel,
                baseUrl = primaryBaseUrl,
                apiKey = primaryKey,
                maxTokens = maxTokens,
                tools = tools,
                userMessage = message,
                imageBytesList = imageBytesList,
                onProgress = onProgress,
            )
        } catch (primaryError: Throwable) {
            val fallback = currentFallbackConfig(primary, primaryModel) ?: throw primaryError
            val fallbackClient = AiHttp.clientForProvider(okHttp, fallback.provider, readTimeoutSeconds)
            GroundedToolLoop.run(
                client = fallbackClient,
                provider = fallback.provider,
                model = fallback.model,
                baseUrl = fallback.baseUrl,
                apiKey = fallback.apiKey,
                maxTokens = maxTokens,
                tools = tools,
                userMessage = message,
                imageBytesList = imageBytesList,
                onProgress = onProgress,
            )
        }
    }

    suspend fun analyzeNutritionLabel(imageBytes: ByteArray, servingGrams: Double): FoodAnalysis {
        val prompt = """
            Read this nutrition facts label and extract per-100g values. If the label only shows per-serving, normalize using the serving size listed on the label.
            Respond ONLY with JSON:
            {"name":"...","calories_per_100g":0.0,"protein_per_100g":0.0,"carbs_per_100g":0.0,"fat_per_100g":0.0,"serving_size_grams":0.0,"sugar_per_100g":0.0,"added_sugar_per_100g":0.0,"fiber_per_100g":0.0,"saturated_fat_per_100g":0.0,"monounsaturated_fat_per_100g":0.0,"polyunsaturated_fat_per_100g":0.0,"cholesterol_per_100g":0.0,"sodium_per_100g":0.0,"potassium_per_100g":0.0,"trans_fat_per_100g":0.0,"calcium_per_100g":0.0,"iron_per_100g":0.0,"magnesium_per_100g":0.0,"zinc_per_100g":0.0,"vitamin_a_per_100g":0.0,"vitamin_c_per_100g":0.0,"vitamin_d_per_100g":0.0,"vitamin_b12_per_100g":0.0,"vitamin_e_per_100g":0.0,"vitamin_k_per_100g":0.0,"folate_per_100g":0.0,"omega_3_per_100g":0.0,"unit_options":[]}
            The [] in unit_options above is only a JSON shape placeholder; replace it with options when a non-gram unit is visible.
            All values should be numbers. If serving size or any nutrient is not available, use null. unit_options is required when a non-gram label serving unit is visible, such as slice, piece, tbsp, cup, ml, fl oz, can, or packet. Do not copy any sample number; use the quantity shown on the label. Use [] only when no non-gram unit is visible. Do not include g/grams in unit_options.
        """.trimIndent()
        val raw = callAi(prompt, imageBytes, op = "analyzeLabel")
        val analysis = PerfLog.measure("analyzeLabel", "parse", "chars=${raw.length}") { FoodJsonParser.parseLabel(raw) }
        return addingFallbackServingUnits(analysis, imageBytes).scaled(servingGrams)
    }

    // -- Diet mode ---------------------------------------------------------

    /** One-line diet-mode summary for profile blocks in prompts. */
    private fun dietModeLine(profile: UserProfile): String =
        if (profile.dietMode == DietMode.KETO) {
            "- Diet mode: keto (net carbs target ${profile.ketoActiveCarbTarget} g/day)"
        } else {
            "- Diet mode: standard"
        }

    /**
     * Keto override for the goal-calculation FORMULAS block. Mirrors the keto
     * macro math in [UserProfile] (carbsGoal/proteinGoal/fatGoal) so the
     * model's targets can't drift from what the app computes deterministically.
     */
    private fun ketoGoalRulesSection(profile: UserProfile): String {
        if (profile.dietMode != DietMode.KETO) return ""
        return "\nDIET MODE OVERRIDE: the user follows a KETO diet. Ignore the standard fat/carb formulas above and use these rules instead (they match the app's own keto math):" +
            "\n- Carbs: fixed at the keto net-carb target of ${profile.ketoActiveCarbTarget} g/day. Do not raise it to fill remaining calories." +
            "\n- Protein: at least the formula protein below (it already includes the keto floor of 1.6 g/kg lean mass, minimum 60 g)." +
            "\n- Fat: fills the calories remaining after carbs and protein, never below 45 g/day. Fat is the primary energy source." +
            "\n- Keep 4*protein + 4*carbs + 9*fat approximately equal to calories."
    }

    // -- Internal dispatch ------------------------------------------------

    private suspend fun callAi(
        prompt: String,
        imageBytes: ByteArray?,
        op: String = "callAi",
        onProgress: (FoodAnalysisProgress) -> Unit = {},
        reportPhases: Boolean = true,
    ): String {
        return callAi(prompt, imageBytes?.let { listOf(it) }.orEmpty(), op, onProgress, reportPhases)
    }

    private suspend fun callAi(
        prompt: String,
        imageBytesList: List<ByteArray>,
        op: String = "callAi",
        onProgress: (FoodAnalysisProgress) -> Unit = {},
        reportPhases: Boolean = true,
    ): String {
        callAiDelegate?.let { delegate ->
            if (reportPhases) {
                onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Preparing))
                onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.CallingAi))
            }
            return delegate(prompt, imageBytesList, op)
        }

        if (reportPhases) onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Preparing))
        // Debug-only: replay a scripted response when the demo_ai extra is set
        // (usage-video capture). Phases/partials/final parse all use the real
        // pipeline; only the provider reply is fake. Never active in release.
        if (BuildConfig.DEBUG && prefs?.debugDemoAnalysis?.first() == true && op in DemoFoodAnalysis.ENTRY_OPS) {
            return DemoFoodAnalysis.run(onProgress)
        }
        // Time input/prompt assembly (includes the suspending userContext read) as
        // the "promptBuild" phase; the network round-trip itself is captured by the
        // OkHttp PerfEventListener, and JSON parse is timed at the call site.
        val finalPrompt = PerfLog.measure(op, "promptBuild") {
            val context = prefs!!.userContext.first()
            // Non-English UI locales get localized prose (food names, reasons, advice)
            // while the machine-read parts of the JSON stay English for the parser.
            val languageLine = nonEnglishResponseLanguage()?.let {
                "Write all human-readable text (food name, reason, advice prose) in $it. Keep JSON keys, numbers, and unit_options unit words in English.\n\n"
            } ?: ""
            val contextLine = if (context.isNotBlank()) "User context (apply to every analysis): $context\n\n" else ""
            languageLine + contextLine + prompt
        }

        val primary = prefs!!.selectedAIProvider.first()
        val primaryModel = primary.supportedModelOrDefault(prefs.selectedAIModel.first())
        val primaryBaseUrl = prefs.customBaseUrl(primary).first()?.takeIf { it.isNotEmpty() }?.let(AiHttp::normalizeCustomBaseUrl) ?: primary.baseUrl
        val primaryKey = AiHttp.sanitizeApiKey(keyStore!!.apiKey(primary))
        if (primary.requiresApiKey && primaryKey.isNullOrEmpty()) throw AiError.NoApiKey
        val maxTokens = prefs.maxResponseTokens.first()
        val readTimeoutSeconds = prefs.aiReadTimeoutSeconds.first()
        val geminiGoogleSearch = prefs.geminiGoogleSearchEnabled.first()
        val aiImages = if (imageBytesList.isEmpty()) {
            imageBytesList
        } else {
            withContext(Dispatchers.Default) {
                imageBytesList.map { AiImageBytes.jpegForUpload(it) }
            }
        }

        if (reportPhases) onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.CallingAi))
        val streamProgress: (FoodAnalysisProgress) -> Unit =
            if (reportPhases) onProgress else ({})
        return try {
            dispatch(
                primary, primaryModel, primaryBaseUrl, primaryKey, finalPrompt, aiImages,
                maxTokens, geminiGoogleSearch, readTimeoutSeconds,
                onProgress = streamProgress,
                preferStreaming = reportPhases,
            )
        } catch (primaryError: Throwable) {
            val fallback = currentFallbackConfig(primary, primaryModel) ?: throw primaryError
            dispatch(
                fallback.provider, fallback.model, fallback.baseUrl, fallback.apiKey, finalPrompt, aiImages,
                maxTokens, geminiGoogleSearch, readTimeoutSeconds,
                onProgress = streamProgress,
                preferStreaming = reportPhases,
            )
        }
    }

    private suspend fun servingUnitInferenceMode(): ServingUnitInferenceMode {
        val mode = inferenceModeForTest ?: prefs!!.servingUnitInferenceMode.first()
        if (mode != ServingUnitInferenceMode.AI_CALL) return mode
        val provider = prefs?.selectedAIProvider?.first()
        // Second AI call for units is too unreliable on local Gemma — use heuristics.
        return if (provider == AIProvider.ON_DEVICE) ServingUnitInferenceMode.HEURISTIC else mode
    }

    private suspend fun finalizeAnalysis(
        analysis: FoodAnalysis,
        imageBytes: ByteArray?,
        description: String?,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ): FoodAnalysis {
        val unitsPending = analysis.servingUnitOptions.isEmpty() &&
            servingUnitInferenceMode() == ServingUnitInferenceMode.AI_CALL
        onProgress(FoodAnalysisProgress.Parsed(analysis, unitsPending))
        val final = addingFallbackServingUnits(analysis, imageBytes, description, onProgress)
        onProgress(FoodAnalysisProgress.Complete(final))
        return final
    }

    private suspend fun addingFallbackServingUnits(
        analysis: FoodAnalysis,
        imageBytes: ByteArray?,
        description: String?,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): FoodAnalysis {
        if (analysis.servingUnitOptions.isNotEmpty()) return analysis
        val options = servingUnitFallbackOptions(analysis.name, analysis.servingSizeGrams, imageBytes, description)
        if (options.isEmpty()) return analysis
        val selected = options.first()
        return analysis.copy(
            servingUnitOptions = options,
            selectedServingUnit = selected.unit,
            selectedServingQuantity = selected.quantityFor(analysis.servingSizeGrams)
        )
    }

    private suspend fun addingFallbackServingUnits(
        analysis: NutritionLabelAnalysis,
        imageBytes: ByteArray
    ): NutritionLabelAnalysis {
        if (analysis.servingUnitOptions.isNotEmpty()) return analysis
        val servingSizeGrams = analysis.servingSizeGrams ?: return analysis
        val options = servingUnitFallbackOptions(analysis.name, servingSizeGrams, imageBytes, description = null)
        if (options.isEmpty()) return analysis
        return analysis.copy(servingUnitOptions = options)
    }

    /**
     * Dispatches to whichever serving-unit strategy the user picked in Settings
     * ([ServingUnitInferenceMode]) — these are mutually exclusive, not chained:
     * grams-only never guesses a unit, heuristic never calls the network, and
     * AI-call always uses [inferServingUnitOptions] (today's original behavior).
     */
    private suspend fun servingUnitFallbackOptions(
        name: String,
        servingSizeGrams: Double,
        imageBytes: ByteArray?,
        description: String?
    ): List<ServingUnitOption> = when (servingUnitInferenceMode()) {
        ServingUnitInferenceMode.GRAMS_ONLY -> emptyList()
        ServingUnitInferenceMode.HEURISTIC ->
            heuristicServingUnitOptions(name, servingSizeGrams, prefs!!.heuristicServingUnitSettings.first()).orEmpty()
        ServingUnitInferenceMode.AI_CALL -> runCatching {
            inferServingUnitOptions(name, servingSizeGrams, imageBytes, description)
        }.getOrDefault(emptyList())
    }

    /**
     * Zero-network guess at a non-gram serving unit from the food name alone,
     * using [ServingUnitHeuristics.RULES] adjusted by the user's [settings]
     * (disabled rules are skipped; a custom gramsPerUnit overrides the
     * built-in default). Returns null when no keyword matches or the
     * matching rule is disabled.
     */
    private fun heuristicServingUnitOptions(
        name: String,
        servingSizeGrams: Double,
        settings: HeuristicServingUnitSettings
    ): List<ServingUnitOption>? {
        if (servingSizeGrams <= 0) return null
        val rule = ServingUnitHeuristics.matchingRule(name) ?: return null
        val override = settings.overrides[rule.id]
        if (override?.enabled == false) return null
        val gramsPerUnit = override?.gramsPerUnit?.takeIf { it > 0 } ?: rule.defaultGramsPerUnit
        PerfLog.event("op=servingUnits phase=heuristic unit=${rule.unit}")
        return listOf(
            ServingUnitOption(
                unit = rule.unit,
                gramsPerUnit = gramsPerUnit,
                quantity = servingSizeGrams / gramsPerUnit
            )
        )
    }

    private suspend fun inferServingUnitOptions(
        name: String,
        servingSizeGrams: Double,
        imageBytes: ByteArray?,
        description: String?
    ): List<app.chompass.models.ServingUnitOption> {
        val context = description?.trim()?.takeIf { it.isNotEmpty() }
        val contextLine = context?.let { "\nUser context: $it" }.orEmpty()
        val prompt = """
            The previous food analysis returned grams only. Infer non-gram serving unit options for the same food and amount.

            Food: $name
            Total grams for the analyzed amount: ${String.format(java.util.Locale.US, "%.1f", servingSizeGrams)}$contextLine

            Return ONLY JSON:
            {"unit_options":[{"unit":"slice","quantity":8.0,"grams_per_unit":45.0}]}

            Rules:
            - Replace the sample numbers with the actual best estimate. Do not copy 8 or 45 unless they fit the food.
            - If the image shows countable portions, count visible pieces/slices. For pizza, cake, pie, bread, cookies, fruit pieces, nuggets, or sweets, use slice or piece.
            - For liquids or pourable foods like milk, juice, soup, smoothies, dal, sauces, or yogurt, use ml when the volume is clearer than a count.
            - For spooned foods like peanut butter, honey, oil, chutney, or ghee, use tbsp or tsp.
            - For packaged foods/drinks, use can, packet, bar, scoop, or bowl only when that unit is visible or strongly implied.
            - grams_per_unit is grams for one unit. For countable units, use total grams / visible quantity. For ml, use grams per ml.
            - Return [] only if no non-gram unit is apparent.

            Good outputs:
            {"unit_options":[{"unit":"slice","quantity":8.0,"grams_per_unit":45.0}]}
            {"unit_options":[{"unit":"ml","quantity":250.0,"grams_per_unit":1.03},{"unit":"cup","quantity":1.0,"grams_per_unit":250.0}]}
            {"unit_options":[{"unit":"tbsp","quantity":2.0,"grams_per_unit":16.0}]}
            {"unit_options":[{"unit":"can","quantity":1.0,"grams_per_unit":330.0}]}
            {"unit_options":[{"unit":"piece","quantity":5.0,"grams_per_unit":18.0}]}
        """.trimIndent()
        val raw = callAi(prompt, imageBytes, op = "inferServing", reportPhases = false)
        return PerfLog.measure("inferServing", "parse", "chars=${raw.length}") {
            FoodJsonParser.parseServingUnitOptions(raw, servingSizeGrams)
        }
    }

    private suspend fun dispatch(
        provider: AIProvider,
        model: String,
        baseUrl: String,
        apiKey: String?,
        prompt: String,
        imageBytesList: List<ByteArray>,
        maxTokens: Int,
        geminiGoogleSearch: Boolean,
        readTimeoutSeconds: Int,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
        preferStreaming: Boolean = false,
    ): String {
        if (provider.apiFormat == AIProvider.ApiFormat.ON_DEVICE) {
            val gateway = onDeviceGateway ?: throw AiError.OnDeviceModelNotDownloaded
            return OnDeviceLlmDispatchClient.analyze(gateway, prompt, imageBytesList)
        }
        if (baseUrl.isEmpty()) throw AiError.InvalidUrl(baseUrl)
        val sanitizedKey = AiHttp.sanitizeApiKey(apiKey)
        if (provider.requiresApiKey && sanitizedKey.isNullOrEmpty()) throw AiError.NoApiKey
        val httpClient = AiHttp.clientForProvider(okHttp, provider, readTimeoutSeconds)
        val enableGoogleSearch = provider.apiFormat == AIProvider.ApiFormat.GEMINI && geminiGoogleSearch
        if (!preferStreaming) {
            return when (provider.apiFormat) {
                AIProvider.ApiFormat.GEMINI ->
                    GeminiClient.analyze(httpClient, baseUrl, model, sanitizedKey!!, prompt, imageBytesList, enableGoogleSearch)
                AIProvider.ApiFormat.ANTHROPIC ->
                    AnthropicClient.analyze(httpClient, baseUrl, model, sanitizedKey!!, prompt, imageBytesList, maxTokens)
                AIProvider.ApiFormat.OPENAI_COMPATIBLE ->
                    OpenAICompatibleClient.analyze(httpClient, baseUrl, model, sanitizedKey, prompt, imageBytesList, provider, maxTokens)
                AIProvider.ApiFormat.ON_DEVICE -> error("unreachable")
            }
        }

        val assembler = FoodPartialJsonAssembler()
        val onDelta: (String) -> Unit = { piece ->
            val partial = assembler.push(piece)
            if (partial != null) {
                onProgress(
                    FoodAnalysisProgress.Partial(
                        partial = partial,
                        source = AnalysisPreviewSource.Streaming,
                    )
                )
            }
        }
        return when (provider.apiFormat) {
            AIProvider.ApiFormat.GEMINI ->
                GeminiClient.analyzeStreaming(
                    httpClient, baseUrl, model, sanitizedKey!!, prompt, imageBytesList,
                    enableGoogleSearch, onDelta,
                )
            AIProvider.ApiFormat.ANTHROPIC ->
                AnthropicClient.analyzeStreaming(
                    httpClient, baseUrl, model, sanitizedKey!!, prompt, imageBytesList, maxTokens, onDelta,
                )
            AIProvider.ApiFormat.OPENAI_COMPATIBLE ->
                OpenAICompatibleClient.analyzeStreaming(
                    httpClient, baseUrl, model, sanitizedKey, prompt, imageBytesList, provider, maxTokens, onDelta,
                )
            AIProvider.ApiFormat.ON_DEVICE -> error("unreachable")
        }
    }

    private suspend fun currentFallbackConfig(
        primary: AIProvider,
        primaryModel: String
    ): FallbackConfig? {
        if (!prefs!!.fallbackEnabled.first()) return null
        val provider = prefs.selectedFallbackProvider.first()
        val model = provider.supportedFallbackModelOrDefault(prefs.selectedFallbackModel.first())
        // Fallback identical to primary would be a pointless retry of the same call.
        if (provider == primary && model == primaryModel) return null
        val key = AiHttp.sanitizeApiKey(keyStore!!.apiKey(provider))
        if (provider.requiresApiKey && key.isNullOrEmpty()) return null
        val baseUrl = prefs.customBaseUrl(provider).first()?.takeIf { it.isNotEmpty() }?.let(AiHttp::normalizeCustomBaseUrl) ?: provider.baseUrl
        if (baseUrl.isEmpty()) return null
        return FallbackConfig(provider, model, baseUrl, key)
    }

    private data class FallbackConfig(
        val provider: AIProvider,
        val model: String,
        val baseUrl: String,
        val apiKey: String?
    )

    companion object {
        internal val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                // Debug-only: capture per-call network latency phases (DNS/connect/
                // TLS/TTFB/total + byte counts) for every AI/STT/OpenFoodFacts call.
                .apply { if (BuildConfig.DEBUG) eventListenerFactory(PerfEventListener.Factory) }
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
}
