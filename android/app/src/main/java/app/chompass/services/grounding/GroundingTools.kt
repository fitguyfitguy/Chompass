package app.chompass.services.grounding

import app.chompass.data.PreferencesStore
import app.chompass.models.FoodEntry
import app.chompass.models.GroundingCandidate
import app.chompass.models.NutrientSourceKind
import app.chompass.services.OpenFoodFactsService
import app.chompass.services.ai.FoodAnalysis
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tool kit for grounded food entry. The model may search local sources and must
 * finish with [finalize_grounding]; nutrient totals are never invented here —
 * only looked up / serialized for the orchestrator to scale.
 */
class GroundingTools(
    private val usdaIndex: UsdaFoodIndex?,
    private val historyPool: List<FoodEntry>,
    private val prefs: PreferencesStore?,
    private val barcodeLookup: (suspend (String) -> FoodAnalysis)? = null,
    private val onToolUsed: (String) -> Unit = {},
) {
    /** Set by the most recent successful [finalize_grounding] in this turn. */
    var lastFinalize: FinalizePayload? = null
        private set

    var searchUsdaCount: Int = 0
        private set
    var searchHistoryCount: Int = 0
        private set
    var barcodeLookupCount: Int = 0
        private set

    /** Source IDs returned by search/lookup tools in this turn (finalize allow-list). */
    private val _seenSourceIds = linkedSetOf<String>()
    val seenSourceIds: Set<String> get() = _seenSourceIds.toSet()

    fun rememberSourceId(sourceId: String?) {
        val id = sourceId?.trim().orEmpty()
        if (id.isNotEmpty()) _seenSourceIds += id
    }

    data class FinalizeComponent(
        val name: String,
        val brand: String? = null,
        val preparation: String? = null,
        val sourceId: String? = null,
        val sourceKind: String? = null,
        val grams: Double? = null,
        val portionHint: String? = null,
        val barcode: String? = null,
        val quantity: Double? = null,
        val unit: String? = null,
        val rejectToEstimate: Boolean = false,
        val needsUserChoice: Boolean = false,
    )

    data class FinalizePayload(
        val mealName: String,
        val emoji: String? = null,
        val notes: String? = null,
        val components: List<FinalizeComponent>,
    )

    suspend fun execute(name: String, args: JSONObject): String {
        onToolUsed(name)
        return when (name) {
            "search_usda" -> searchUsda(args)
            "search_history" -> searchHistory(args)
            "lookup_barcode" -> lookupBarcode(args)
            "finalize_grounding" -> finalizeGrounding(args)
            else -> jsonError("Unknown tool: $name. Available: ${TOOL_NAMES.joinToString()}")
        }
    }

    private fun searchUsda(args: JSONObject): String {
        searchUsdaCount++
        val index = usdaIndex ?: return jsonError("usda index unavailable")
        val rawQuery = args.optString("query").trim()
        if (rawQuery.isEmpty()) return jsonError("query is required")
        val query = QueryNormalizer.normalizeQuery(rawQuery).ifEmpty { rawQuery }
        val limit = args.optInt("limit", 6).coerceIn(1, 8)
        val hits = index.search(query, limit = limit, includeIncompleteEnergy = false)
        hits.forEach { rememberSourceId(it.sourceId) }
        return JSONObject().apply {
            put("query", query)
            put("results", candidatesToJson(hits))
            put(
                "hint",
                "Prefer survey_fndds_food for cooked/generic meals; avoid flour/powder/dry/pie " +
                    "unless the query says so. Pick source_id from results only.",
            )
        }.toString()
    }

    private fun searchHistory(args: JSONObject): String {
        searchHistoryCount++
        val rawQuery = args.optString("query").trim()
        if (rawQuery.isEmpty()) return jsonError("query is required")
        val query = QueryNormalizer.normalizeQuery(rawQuery).ifEmpty { rawQuery }
        val limit = args.optInt("limit", 5).coerceIn(1, 8)
        val hits = ConfirmedHistorySearch.search(historyPool, query, limit = limit)
        val candidates = hits.map { ConfirmedHistorySearch.toCandidate(it) }
        candidates.forEach { rememberSourceId(it.sourceId) }
        return JSONObject().apply {
            put("query", query)
            put("results", candidatesToJson(candidates))
            put(
                "hint",
                "History matches identity only — do not copy prior portion unless the user said so.",
            )
        }.toString()
    }

    private suspend fun lookupBarcode(args: JSONObject): String {
        barcodeLookupCount++
        val raw = args.optString("barcode").filter { it.isDigit() }
        if (raw.length < 8) return jsonError("barcode must be at least 8 digits")
        val off = runCatching {
            barcodeLookup?.invoke(raw)
                ?: prefs?.let { OpenFoodFactsService.lookup(raw, it) }
        }.getOrNull()
        if (off == null) {
            return JSONObject().apply {
                put("found", false)
                put("barcode", raw)
            }.toString()
        }
        rememberSourceId(raw)
        val grams = off.servingSizeGrams.takeIf { it > 0 } ?: 100.0
        val scale = 100.0 / grams
        return JSONObject().apply {
            put("found", true)
            put("barcode", raw)
            put(
                "result",
                JSONObject().apply {
                    put("source_kind", "openFoodFacts")
                    put("source_id", raw)
                    put("description", off.name)
                    put("calories_per_100g", round1(off.calories * scale))
                    put("protein_per_100g", round1(off.protein * scale))
                    put("carbs_per_100g", round1(off.carbs * scale))
                    put("fat_per_100g", round1(off.fat * scale))
                    put("serving_grams", off.servingSizeGrams)
                },
            )
        }.toString()
    }

    private fun finalizeGrounding(args: JSONObject): String {
        val mealName = args.optString("meal_name").trim()
            .ifEmpty { args.optString("mealName").trim() }
        if (mealName.isEmpty()) return jsonError("meal_name is required")
        val emoji = args.optString("emoji").takeIf { it.isNotBlank() && it != "null" }
        val notes = args.optString("notes").takeIf { it.isNotBlank() && it != "null" }
        val arr = args.optJSONArray("components") ?: JSONArray()
        if (arr.length() == 0) return jsonError("components must be a non-empty array")
        val components = mutableListOf<FinalizeComponent>()
        val invalidIds = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val raw = arr.optJSONObject(i) ?: continue
            val name = raw.optString("name").trim()
            if (name.isEmpty()) continue
            val grams = raw.optDouble("grams", Double.NaN).takeIf { !it.isNaN() && it > 0 }
                ?: raw.optDouble("estimated_grams", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            val quantity = raw.optDouble("quantity", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            val sourceId = raw.optString("source_id").takeIf { it.isNotBlank() && it != "null" }
                ?: raw.optString("sourceId").takeIf { it.isNotBlank() && it != "null" }
            var reject = raw.optBoolean("reject_to_estimate", false) ||
                raw.optBoolean("rejectToEstimate", false)
            var needsChoice = raw.optBoolean("needs_user_choice", false) ||
                raw.optBoolean("needsUserChoice", false)
            // Harden: unknown source_id cannot be finalized as a DB hit.
            if (!sourceId.isNullOrBlank() &&
                _seenSourceIds.isNotEmpty() &&
                sourceId !in _seenSourceIds &&
                !reject
            ) {
                invalidIds += sourceId
                needsChoice = true
            }
            components += FinalizeComponent(
                name = name,
                brand = raw.optString("brand").takeIf { it.isNotBlank() && it != "null" },
                preparation = raw.optString("preparation").takeIf { it.isNotBlank() && it != "null" },
                sourceId = sourceId,
                sourceKind = raw.optString("source_kind").takeIf { it.isNotBlank() && it != "null" }
                    ?: raw.optString("sourceKind").takeIf { it.isNotBlank() && it != "null" },
                grams = grams,
                portionHint = raw.optString("portion_hint").takeIf { it.isNotBlank() && it != "null" },
                barcode = raw.optString("barcode").filter { it.isDigit() }.takeIf { it.length >= 8 },
                quantity = quantity,
                unit = raw.optString("unit").takeIf { it.isNotBlank() && it != "null" },
                rejectToEstimate = reject,
                needsUserChoice = needsChoice,
            )
        }
        if (components.isEmpty()) return jsonError("components must include at least one named food")
        // If every component rejected/needs choice with no viable source, still accept finalize
        // so the orchestrator can estimate — never leave a silent empty path.
        lastFinalize = FinalizePayload(
            mealName = mealName,
            emoji = emoji,
            notes = notes,
            components = components,
        )
        return JSONObject().apply {
            put("ok", true)
            put("component_count", components.size)
            if (invalidIds.isNotEmpty()) {
                put("invalid_source_ids", JSONArray(invalidIds))
                put(
                    "warning",
                    "Some source_id values were not returned by tools; marked needs_user_choice.",
                )
            }
            put("message", "Grounding finalized. Do not invent nutrients; the app will scale from selected sources.")
        }.toString()
    }

    private fun candidatesToJson(candidates: List<GroundingCandidate>): JSONArray =
        JSONArray().apply {
            for (c in candidates) {
                put(
                    JSONObject().apply {
                        put("source_kind", when (c.sourceKind) {
                            NutrientSourceKind.USDA -> "usda"
                            NutrientSourceKind.HISTORY -> "history"
                            NutrientSourceKind.OPEN_FOOD_FACTS -> "openFoodFacts"
                            NutrientSourceKind.NUTRITION_LABEL -> "nutritionLabel"
                            NutrientSourceKind.MODEL_ESTIMATE -> "modelEstimate"
                        })
                        put("source_id", c.sourceId)
                        put("description", c.displayName)
                        put("score", round1(c.score))
                        c.dataType?.let { put("data_type", it) }
                        c.foodCategory?.let { put("food_category", it) }
                        if (c.incompleteEnergy) put("incomplete_energy", true)
                        c.caloriesPer100g?.let { put("calories_per_100g", round1(it)) }
                        c.proteinPer100g?.let { put("protein_per_100g", round1(it)) }
                        c.carbsPer100g?.let { put("carbs_per_100g", round1(it)) }
                        c.fatPer100g?.let { put("fat_per_100g", round1(it)) }
                        c.servingSizeGrams?.let { put("serving_grams", round1(it)) }
                    },
                )
            }
        }

    private fun jsonError(message: String): String =
        JSONObject().put("error", message).toString()

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0

    companion object {
        val TOOL_NAMES = listOf(
            "search_usda",
            "search_history",
            "lookup_barcode",
            "finalize_grounding",
        )

        val TOOL_DESCRIPTIONS = mapOf(
            "search_usda" to
                "Search the offline USDA Foundation + FNDDS food index. Returns ranked candidates with macros per 100g. Call before picking a USDA source_id.",
            "search_history" to
                "Search the user's confirmed diary and favorites for identity matches. Portion is not auto-copied.",
            "lookup_barcode" to
                "Look up a packaged product by barcode digits via Open Food Facts.",
            "finalize_grounding" to
                "Required terminal tool. Submit meal_name, emoji, and components with source_id (from a prior search/lookup), grams, or reject_to_estimate / needs_user_choice.",
        )

        fun parametersSchema(toolName: String): JSONObject = when (toolName) {
            "search_usda", "search_history" -> JSONObject().apply {
                put("type", "object")
                put(
                    "properties",
                    JSONObject().apply {
                        put("query", JSONObject().put("type", "string").put("description", "Food name / preparation to search"))
                        put("limit", JSONObject().put("type", "integer").put("description", "Max results 1–8 (default 6)"))
                    },
                )
                put("required", JSONArray().put("query"))
            }
            "lookup_barcode" -> JSONObject().apply {
                put("type", "object")
                put(
                    "properties",
                    JSONObject().apply {
                        put("barcode", JSONObject().put("type", "string").put("description", "Digits-only barcode"))
                    },
                )
                put("required", JSONArray().put("barcode"))
            }
            "finalize_grounding" -> JSONObject().apply {
                put("type", "object")
                put(
                    "properties",
                    JSONObject().apply {
                        put("meal_name", JSONObject().put("type", "string"))
                        put("emoji", JSONObject().put("type", "string"))
                        put("notes", JSONObject().put("type", "string"))
                        put(
                            "components",
                            JSONObject().apply {
                                put("type", "array")
                                put(
                                    "items",
                                    JSONObject().apply {
                                        put("type", "object")
                                        put(
                                            "properties",
                                            JSONObject().apply {
                                                put("name", JSONObject().put("type", "string"))
                                                put("brand", JSONObject().put("type", "string"))
                                                put("preparation", JSONObject().put("type", "string"))
                                                put("source_id", JSONObject().put("type", "string").put("description", "fdc_id, history key, or barcode from a tool result"))
                                                put("source_kind", JSONObject().put("type", "string").put("description", "usda | history | openFoodFacts"))
                                                put("grams", JSONObject().put("type", "number"))
                                                put("quantity", JSONObject().put("type", "number"))
                                                put("unit", JSONObject().put("type", "string"))
                                                put("portion_hint", JSONObject().put("type", "string"))
                                                put("barcode", JSONObject().put("type", "string"))
                                                put("reject_to_estimate", JSONObject().put("type", "boolean"))
                                                put("needs_user_choice", JSONObject().put("type", "boolean"))
                                            },
                                        )
                                        put("required", JSONArray().put("name"))
                                    },
                                )
                            },
                        )
                    },
                )
                put("required", JSONArray().put("meal_name").put("components"))
            }
            else -> JSONObject().put("type", "object").put("properties", JSONObject())
        }

        /** Cap how many tool results characters we keep in prompts/logs. */
        fun truncateToolResult(raw: String, maxChars: Int = 6000): String =
            if (raw.length <= maxChars) raw else raw.take(min(maxChars, raw.length)) + "…"
    }
}
