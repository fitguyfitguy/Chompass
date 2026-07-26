package app.chompass.services.ai

import app.chompass.models.BodyFatEntry
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import app.chompass.models.UnitFormat

/**
 * A write action the coach LLM proposed via a `propose_log_*` tool call, held
 * for user confirmation — [CoachTools] never persists anything itself.
 */
sealed class CoachProposal {
    data class Food(val entry: FoodEntry) : CoachProposal()
    data class Weight(val entry: WeightEntry) : CoachProposal()
    data class Water(val entry: WaterEntry) : CoachProposal()
}

/**
 * On-demand data accessor for Coach. Replaces the old "dump everything into the
 * system prompt" pattern: instead of stuffing the prompt with the last N weights
 * + N body fats + N days of food, we expose a small tool kit that the LLM can
 * call when it actually needs older / specific data.
 *
 * Three provider formats (Gemini / Anthropic Messages / OpenAI-compatible) each
 * have a slightly different tool schema shape — built per-format inside
 * ChatService — but the executor side (turning a name + args into a JSON result)
 * lives here.
 *
 * Date format on the API: ISO yyyy-MM-dd. Each list-returning tool caps results
 * at 365 entries to bound any single tool result's size.
 */
class CoachTools(
    private val weights: List<WeightEntry>,
    private val bodyFats: List<BodyFatEntry>,
    private val foods: List<FoodEntry>,
    private val foodAnalysisService: FoodAnalysisService
) {
    /** Set by the most recent `propose_log_*` call in the current turn, if any.
     *  Capped at one pending proposal per turn — a later call simply replaces it. */
    var lastProposal: CoachProposal? = null
        private set

    suspend fun execute(name: String, args: JSONObject): String = when (name) {
        "get_data_summary" -> getDataSummary()
        "get_weight_history" -> getWeightHistory(args)
        "get_body_fat_history" -> getBodyFatHistory(args)
        "get_calorie_totals" -> getCalorieTotals(args)
        "get_food_entries" -> getFoodEntries(args)
        "propose_log_food" -> proposeLogFood(args)
        "propose_log_weight" -> proposeLogWeight(args)
        "propose_log_water" -> proposeLogWater(args)
        else -> jsonError("Unknown tool: $name. Available tools: ${TOOL_NAMES.joinToString(", ")}")
    }

    // MARK: - Tool implementations

    private fun getDataSummary(): String {
        val weightDates = weights.map { it.date }.sorted()
        val bodyFatDates = bodyFats.map { it.date }.sorted()
        val foodDates = foods.map { it.timestamp }.sorted()
        val payload = JSONObject().apply {
            put("weights", JSONObject().apply {
                put("count", weights.size)
                put("first_date", weightDates.firstOrNull()?.let { iso(it) } ?: JSONObject.NULL)
                put("last_date", weightDates.lastOrNull()?.let { iso(it) } ?: JSONObject.NULL)
            })
            put("body_fats", JSONObject().apply {
                put("count", bodyFats.size)
                put("first_date", bodyFatDates.firstOrNull()?.let { iso(it) } ?: JSONObject.NULL)
                put("last_date", bodyFatDates.lastOrNull()?.let { iso(it) } ?: JSONObject.NULL)
            })
            put("foods", JSONObject().apply {
                put("count", foods.size)
                put("first_date", foodDates.firstOrNull()?.let { iso(it) } ?: JSONObject.NULL)
                put("last_date", foodDates.lastOrNull()?.let { iso(it) } ?: JSONObject.NULL)
            })
        }
        return payload.toString()
    }

    private fun getWeightHistory(args: JSONObject): String {
        val (from, to) = parseRange(args)
        val limit = args.optInt("limit", 0).takeIf { it > 0 }?.coerceAtMost(365) ?: 365
        val filtered = weights
            .filter { it.date in from..to }
            .sortedBy { it.date }
            .take(limit)
        val arr = JSONArray()
        for (e in filtered) {
            arr.put(JSONObject().apply {
                put("date", iso(e.date))
                put("kg", round1(e.weightKg))
                put("lbs", round1(UnitFormat.kgToLbs(e.weightKg)))
            })
        }
        return JSONObject().apply {
            put("from", iso(from))
            put("to", iso(to))
            put("count", filtered.size)
            put("weights", arr)
        }.toString()
    }

    private fun getBodyFatHistory(args: JSONObject): String {
        val (from, to) = parseRange(args)
        val limit = args.optInt("limit", 0).takeIf { it > 0 }?.coerceAtMost(365) ?: 365
        val filtered = bodyFats
            .filter { it.date in from..to }
            .sortedBy { it.date }
            .take(limit)
        val arr = JSONArray()
        for (e in filtered) {
            arr.put(JSONObject().apply {
                put("date", iso(e.date))
                put("percent", (e.bodyFatFraction * 100).toInt())
            })
        }
        return JSONObject().apply {
            put("from", iso(from))
            put("to", iso(to))
            put("count", filtered.size)
            put("body_fats", arr)
        }.toString()
    }

    private fun getCalorieTotals(args: JSONObject): String {
        val (from, to) = parseRange(args)
        val zone = ZoneId.systemDefault()
        val daily = sortedMapOf<String, Int>()
        for (food in foods) {
            if (food.timestamp !in from..to) continue
            val day = ISO_FMT.format(food.timestamp.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant())
            daily[day] = (daily[day] ?: 0) + food.calories
        }
        val arr = JSONArray()
        for ((day, kcal) in daily) {
            arr.put(JSONObject().apply { put("date", day); put("kcal", kcal) })
        }
        return JSONObject().apply {
            put("from", iso(from))
            put("to", iso(to))
            put("days_with_data", daily.size)
            put("totals", arr)
        }.toString()
    }

    private fun getFoodEntries(args: JSONObject): String {
        val (from, to) = parseRange(args)
        val limit = args.optInt("limit", 0).takeIf { it > 0 }?.coerceAtMost(365) ?: 200
        val filtered = foods
            .filter { it.timestamp in from..to }
            .sortedBy { it.timestamp }
            .take(limit)
        val arr = JSONArray()
        for (f in filtered) {
            arr.put(JSONObject().apply {
                put("date", iso(f.timestamp))
                put("name", f.name)
                put("kcal", f.calories)
                put("protein_g", f.protein)
                put("carbs_g", f.carbs)
                put("fat_g", f.fat)
                put("meal_type", mealTypeName(f.mealType))
                put("source", sourceName(f.source))
                putDoubleIfPresent("serving_size_g", f.servingSizeGrams)
                putDoubleIfPresent("sugar_g", f.sugar)
                putDoubleIfPresent("added_sugar_g", f.addedSugar)
                putDoubleIfPresent("fiber_g", f.fiber)
                putDoubleIfPresent("saturated_fat_g", f.saturatedFat)
                putDoubleIfPresent("monounsaturated_fat_g", f.monounsaturatedFat)
                putDoubleIfPresent("polyunsaturated_fat_g", f.polyunsaturatedFat)
                putDoubleIfPresent("cholesterol_mg", f.cholesterol)
                putDoubleIfPresent("sodium_mg", f.sodium)
                putDoubleIfPresent("potassium_mg", f.potassium)
                putDoubleIfPresent("trans_fat_g", f.transFat)
                putDoubleIfPresent("calcium_mg", f.calcium)
                putDoubleIfPresent("iron_mg", f.iron)
                putDoubleIfPresent("magnesium_mg", f.magnesium)
                putDoubleIfPresent("zinc_mg", f.zinc)
                putDoubleIfPresent("vitamin_a_mcg", f.vitaminA)
                putDoubleIfPresent("vitamin_c_mg", f.vitaminC)
                putDoubleIfPresent("vitamin_d_mcg", f.vitaminD)
                putDoubleIfPresent("vitamin_b12_mcg", f.vitaminB12)
                putDoubleIfPresent("vitamin_e_mg", f.vitaminE)
                putDoubleIfPresent("vitamin_k_mcg", f.vitaminK)
                putDoubleIfPresent("folate_mcg", f.folate)
                putDoubleIfPresent("omega_3_g", f.omega3)
            })
        }
        return JSONObject().apply {
            put("from", iso(from))
            put("to", iso(to))
            put("count", filtered.size)
            put("foods", arr)
        }.toString()
    }

    // MARK: - Write-proposal tools (never persist directly — see [CoachProposal])

    /** Runs the same nutrition-estimation prompt used by the manual "describe your food"
     *  flow, so chat-logged food gets the same numbers as every other text-entry path. */
    private suspend fun proposeLogFood(args: JSONObject): String {
        val description = args.optString("description").takeIf { it.isNotBlank() }
            ?: return jsonError("propose_log_food requires a non-empty 'description'.")
        val mealType = args.optString("meal_type").takeIf { it.isNotBlank() }?.let(::parseMealType)
            ?: MealType.currentMeal
        val analysis = try {
            foodAnalysisService.analyzeText(description)
        } catch (e: Throwable) {
            return jsonError("Could not estimate nutrition for '$description': ${e.localizedMessage ?: "analysis failed"}")
        }
        val entry = analysis.toFoodEntry(source = FoodSource.TEXT_INPUT, mealType = mealType)
        lastProposal = CoachProposal.Food(entry)
        return JSONObject().apply {
            put("proposed", true)
            put("kind", "food")
            put("name", entry.name)
            put("calories", entry.calories)
            put("protein_g", entry.protein)
            put("carbs_g", entry.carbs)
            put("fat_g", entry.fat)
            put("meal_type", mealTypeName(entry.mealType))
        }.toString()
    }

    private fun proposeLogWeight(args: JSONObject): String {
        val kg = optDouble(args, "weight_kg")?.takeIf { it > 0 }
            ?: return jsonError("propose_log_weight requires a positive 'weight_kg'.")
        val entry = WeightEntry(weightKg = kg)
        lastProposal = CoachProposal.Weight(entry)
        return JSONObject().apply {
            put("proposed", true)
            put("kind", "weight")
            put("weight_kg", round1(kg))
        }.toString()
    }

    private fun proposeLogWater(args: JSONObject): String {
        val ml = args.optInt("milliliters", -1).takeIf { it > 0 }
            ?: return jsonError("propose_log_water requires a positive integer 'milliliters'.")
        val entry = WaterEntry(milliliters = ml)
        lastProposal = CoachProposal.Water(entry)
        return JSONObject().apply {
            put("proposed", true)
            put("kind", "water")
            put("milliliters", ml)
        }.toString()
    }

    private fun parseMealType(raw: String): MealType = when (raw.trim().lowercase()) {
        "breakfast" -> MealType.BREAKFAST
        "lunch" -> MealType.LUNCH
        "dinner" -> MealType.DINNER
        "snack" -> MealType.SNACK
        else -> MealType.OTHER
    }

    private fun optDouble(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        return when (val value = json.opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }?.takeUnless { it.isNaN() || it.isInfinite() }
    }

    // MARK: - Helpers

    /** Generous defaults: missing `from` falls back to 30 days before `to`,
     *  missing `to` falls back to now. End-of-day inclusive on `to`. */
    private fun parseRange(args: JSONObject): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val to = args.optString("to").takeIf { it.isNotBlank() }?.let { parseDate(it) } ?: Instant.now()
        val toEnd = LocalDate.ofInstant(to, zone).atTime(23, 59, 59).atZone(zone).toInstant()
        val from = args.optString("from").takeIf { it.isNotBlank() }?.let { parseDate(it) }
            ?: to.minusSeconds(30 * 86_400L)
        val fromStart = LocalDate.ofInstant(from, zone).atStartOfDay(zone).toInstant()
        return fromStart to toEnd
    }

    private fun round1(v: Double): Double = Math.round(v * 10) / 10.0

    private fun jsonError(message: String): String =
        JSONObject().apply { put("error", message) }.toString()

    private fun JSONObject.putDoubleIfPresent(key: String, value: Double?) {
        if (value != null) put(key, value)
    }

    private fun sourceName(source: FoodSource): String = when (source) {
        FoodSource.SNAP_FOOD -> "snapFood"
        FoodSource.NUTRITION_LABEL -> "nutritionLabel"
        FoodSource.BARCODE -> "barcode"
        FoodSource.TEXT_INPUT -> "textInput"
        FoodSource.MANUAL -> "manual"
        FoodSource.GROUNDED -> "grounded"
    }

    private fun mealTypeName(mealType: MealType): String = when (mealType) {
        MealType.BREAKFAST -> "breakfast"
        MealType.LUNCH -> "lunch"
        MealType.DINNER -> "dinner"
        MealType.SNACK -> "snack"
        MealType.OTHER -> "other"
    }

    private fun iso(instant: Instant): String =
        ISO_FMT.format(instant.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant())

    private fun parseDate(s: String): Instant? = runCatching {
        LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
    }.getOrNull()

    companion object {
        val TOOL_NAMES = listOf(
            "get_data_summary",
            "get_weight_history",
            "get_body_fat_history",
            "get_calorie_totals",
            "get_food_entries",
            "propose_log_food",
            "propose_log_weight",
            "propose_log_water"
        )

        val TOOL_DESCRIPTIONS: Map<String, String> = mapOf(
            "get_data_summary" to "Get a quick summary of the user's available data: total counts and earliest/latest dates for weights, body-fat readings, and food entries. Call this first when the user asks anything about their history range or data spanning more than 14 days.",
            "get_weight_history" to "Fetch weight entries between two dates (inclusive). Returns date + weight (kg + lbs). Use this when the user asks about specific past dates or weight trends older than the last 10 entries.",
            "get_body_fat_history" to "Fetch body-fat readings between two dates (inclusive). Returns date + percent. Use when the user asks about body composition trends older than the last 10 readings.",
            "get_calorie_totals" to "Daily calorie totals (sum of all logged foods per day) between two dates. Returns date + kcal. Use when the user asks about intake patterns older than the last 14 days.",
            "get_food_entries" to "Individual logged food items (name + calories + macros) between two dates. Use when the user asks about specific meals, what they ate on a given date, or wants macro breakdowns rather than just kcal totals.",
            "propose_log_food" to "Propose logging a food entry from a natural-language description (e.g. \"2 eggs and toast\"). This does NOT save it — it only estimates nutrition and shows the user a confirmation before anything is logged. Call this when the user asks you to log, add, or track something they ate. After calling it, tell the user what you propose to log and that they need to confirm it in the app.",
            "propose_log_weight" to "Propose logging a body weight entry in kilograms. This does NOT save it — the user must confirm in the app. Call this when the user tells you their current weight and asks you to log it (e.g. \"log my weight, I'm 82.3kg today\"). Convert to kilograms first if the user gave pounds.",
            "propose_log_water" to "Propose logging a water intake entry in milliliters. This does NOT save it — the user must confirm in the app. Call this when the user asks you to log water/hydration (e.g. \"log 500ml of water\"). Convert to milliliters first if the user gave another unit."
        )

        private val ISO_FMT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
    }
}
