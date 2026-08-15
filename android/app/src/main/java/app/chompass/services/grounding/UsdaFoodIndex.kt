package app.chompass.services.grounding

import android.content.Context
import app.chompass.models.GroundingCandidate
import app.chompass.models.NutrientBasis
import app.chompass.models.NutrientSourceKind
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.FoodAnalysis
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Read-only lookup over the compact USDA Foundation + FNDDS SQLite asset
 * (`assets/usda/usda_foods.sqlite`). Ships in all build types and powers the
 * Add Food "Search food" sheet plus the gated grounded-entry pipeline. No Room —
 * opens a copied file with [android.database.sqlite.SQLiteDatabase.openDatabase].
 */
class UsdaFoodIndex(
    context: Context,
    assetPath: String = ASSET_PATH,
) : OfflineFoodIndex(
    context = context,
    assetPath = assetPath,
    manifestAssetPath = MANIFEST_ASSET_PATH,
    dbFileName = DB_FILE_NAME,
) {
    fun getByFdcId(fdcId: Long): UsdaFoodRecord? {
        db.rawQuery(
            "SELECT * FROM foods WHERE fdc_id = ? LIMIT 1",
            arrayOf(fdcId.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return readRecord(c)
        }
    }

    /**
     * Exact / prefix / token search. Scores exact description matches highest,
     * then token overlap. Cap [limit] results.
     *
     * @param includeIncompleteEnergy when false (default), rows with null calories
     *   are omitted so callers never silently scale 0 kcal from Foundation gaps.
     */
    fun search(
        query: String,
        limit: Int = 8,
        includeIncompleteEnergy: Boolean = false,
    ): List<GroundingCandidate> {
        val tokens = QueryNormalizer.normalizeTokens(query)
        if (tokens.isEmpty()) return emptyList()

        return searchRows(
            query = query,
            idColumn = "fdc_id",
            nameColumn = "description",
            tokensColumn = "tokens",
            rowReader = ::readRecord,
        )
            .asSequence()
            .filter { includeIncompleteEnergy || it.calories != null }
            .map { it to score(tokens, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (food, score) -> food.toCandidate(score, datasetVersion) }
            .toList()
    }

    private fun readRecord(c: android.database.Cursor): UsdaFoodRecord {
        fun d(col: String): Double? {
            val idx = c.getColumnIndex(col)
            if (idx < 0 || c.isNull(idx)) return null
            return c.getDouble(idx)
        }
        fun s(col: String): String? {
            val idx = c.getColumnIndex(col)
            if (idx < 0 || c.isNull(idx)) return null
            return c.getString(idx)
        }
        return UsdaFoodRecord(
            fdcId = c.getLong(c.getColumnIndexOrThrow("fdc_id")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            dataType = s("data_type") ?: "",
            foodCategory = s("food_category"),
            tokens = s("tokens") ?: "",
            servingUnit = s("serving_unit"),
            servingGrams = d("serving_grams"),
            calories = d("calories"),
            protein = d("protein"),
            carbs = d("carbs"),
            fat = d("fat"),
            fiber = d("fiber"),
            sugar = d("sugar"),
            addedSugar = d("added_sugar"),
            saturatedFat = d("saturated_fat"),
            monounsaturatedFat = d("monounsaturated_fat"),
            polyunsaturatedFat = d("polyunsaturated_fat"),
            cholesterol = d("cholesterol"),
            sodium = d("sodium"),
            potassium = d("potassium"),
            transFat = d("trans_fat"),
            calcium = d("calcium"),
            iron = d("iron"),
            magnesium = d("magnesium"),
            zinc = d("zinc"),
            vitaminA = d("vitamin_a"),
            vitaminC = d("vitamin_c"),
            vitaminD = d("vitamin_d"),
            vitaminB12 = d("vitamin_b12"),
            vitaminE = d("vitamin_e"),
            vitaminK = d("vitamin_k"),
            folate = d("folate"),
            omega3 = d("omega_3"),
        )
    }

    companion object {
        const val ASSET_PATH = "usda/usda_foods.sqlite"
        const val MANIFEST_ASSET_PATH = "usda/usda_foods.manifest.json"
        private const val DB_FILE_NAME = "usda_foods.sqlite"
        /** Score margin below which top-2 candidates are treated as ambiguous. */
        const val AMBIGUITY_SCORE_DELTA = 1.5

        /** True when the APK includes the offline index (all build types today). */
        fun assetAvailable(context: Context, assetPath: String = ASSET_PATH): Boolean =
            OfflineFoodIndex.assetAvailable(context, assetPath)

        internal fun tokenize(text: String): List<String> = QueryNormalizer.normalizeTokens(text)

        private val COOKED_OR_GENERIC = setOf(
            "cooked", "grilled", "steamed", "boiled", "baked", "roasted", "fried",
            "sauteed", "braised", "meal", "plate", "bowl", "lunch", "dinner",
            "breakfast", "snack",
        )
        private val DRY_FORM = setOf("flour", "powder", "dry", "dried", "mix")
        private val DESSERT_FORM = setOf("pie", "cake", "cookie", "candy")
        private val BEVERAGE_QUERY = setOf(
            "beer", "wine", "milk", "juice", "soda", "coffee", "tea", "water",
            "shake", "smoothie", "drink", "beverage", "cola",
        )
        private val BEVERAGE_DESC = setOf(
            "beer", "wine", "milk", "juice", "soda", "coffee", "tea", "drink",
            "beverage", "cola", "ale", "lager",
        )

        internal fun score(queryTokens: List<String>, food: UsdaFoodRecord): Double {
            if (queryTokens.isEmpty()) return 0.0
            val desc = food.description.lowercase(Locale.US)
            val foodTokens = food.tokens.split(" ").filter { it.isNotEmpty() }.toSet()
            val joined = queryTokens.joinToString(" ")
            var score = 0.0
            if (desc == joined) score += 10.0
            else if (desc.startsWith(joined)) score += 6.0
            else if (desc.contains(joined)) score += 3.5
            val overlap = queryTokens.count { it in foodTokens }
            score += overlap * 1.5
            // Prefer shorter / more specific names slightly.
            score += max(0.0, 2.0 - food.description.length / 80.0)

            val isFndds = food.dataType.contains("fndds", ignoreCase = true) ||
                food.dataType.contains("survey", ignoreCase = true)
            val queryImpliesCookedOrGeneric =
                queryTokens.any { it in COOKED_OR_GENERIC } ||
                    queryTokens.none { it in setOf("raw", "dry", "dried", "flour", "powder") }
            if (isFndds) {
                score += if (queryImpliesCookedOrGeneric) 1.0 else 0.35
            }

            // Category token overlap when WWEIA / food_category is present.
            food.foodCategory?.let { cat ->
                val catTokens = QueryNormalizer.tokenize(cat).toSet()
                val catOverlap = queryTokens.count { it in catTokens }
                score += catOverlap * 0.4
            }

            score += formAdjustment(queryTokens, desc, foodTokens)
            if (food.calories == null) score -= 2.0
            return score
        }

        /**
         * Soft penalties for form mismatches (cooked rice vs rice flour, beer vs dip, etc.).
         */
        internal fun formAdjustment(
            queryTokens: List<String>,
            descriptionLower: String,
            foodTokens: Set<String> = emptySet(),
        ): Double {
            var adj = 0.0
            val q = queryTokens.toSet()
            val impliesCookedSolid = q.any { it in COOKED_OR_GENERIC } ||
                (q.intersect(BEVERAGE_QUERY).isEmpty() && q.none { it in DRY_FORM })
            val descDry = DRY_FORM.any { it in foodTokens || descriptionLower.contains(it) }
            val descDessert = DESSERT_FORM.any { it in foodTokens || descriptionLower.contains(" $it") || descriptionLower.startsWith("$it,") || descriptionLower.contains(", $it") }
            val queryBeverage = q.any { it in BEVERAGE_QUERY }
            val descBeverage = BEVERAGE_DESC.any { it in foodTokens || descriptionLower.contains(it) }

            if (impliesCookedSolid && descDry && q.none { it in DRY_FORM }) adj -= 2.5
            if (q.none { it in DESSERT_FORM } && descDessert && impliesCookedSolid) adj -= 2.0
            if (queryBeverage && !descBeverage) adj -= 3.0
            if (!queryBeverage && descBeverage && q.intersect(BEVERAGE_QUERY).isEmpty()) {
                // Don't penalize milk/yogurt-adjacent solids hard; only clear drinks.
                if (listOf("beer", "wine", "soda", "cola", "ale", "lager").any { descriptionLower.contains(it) }) {
                    adj -= 2.0
                }
            }
            if (q.any { it in setOf("raw", "fresh") } && descriptionLower.contains("cooked")) adj -= 1.0
            if (q.any { it == "cooked" } && (descriptionLower.contains("raw") || descDry)) adj -= 1.5
            return adj
        }

        /** Calibrated source-aware ranking used by the orchestrator. */
        fun sourceAwareScore(c: GroundingCandidate, query: String = ""): Double {
            val base = c.score
            val bonus = when (c.sourceKind) {
                NutrientSourceKind.OPEN_FOOD_FACTS -> 12.0
                NutrientSourceKind.NUTRITION_LABEL -> 10.0
                NutrientSourceKind.USDA -> 4.0
                NutrientSourceKind.SWISS -> 4.0
                NutrientSourceKind.HISTORY -> 2.5
                NutrientSourceKind.MODEL_ESTIMATE -> 0.0
            }
            val correction = if (query.isNotBlank()) {
                GroundingCorrectionStore.boostFor(query, c.sourceId)
            } else {
                0.0
            }
            val incompletePenalty = if (c.incompleteEnergy) 5.0 else 0.0
            return base + bonus + correction - incompletePenalty
        }

        fun isAmbiguous(top: GroundingCandidate, second: GroundingCandidate?, query: String = ""): Boolean {
            if (second == null) return false
            return sourceAwareScore(top, query) - sourceAwareScore(second, query) < AMBIGUITY_SCORE_DELTA
        }
    }
}

data class UsdaFoodRecord(
    val fdcId: Long,
    val description: String,
    val dataType: String,
    val foodCategory: String?,
    val tokens: String,
    val servingUnit: String?,
    val servingGrams: Double?,
    val calories: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val addedSugar: Double?,
    val saturatedFat: Double?,
    val monounsaturatedFat: Double?,
    val polyunsaturatedFat: Double?,
    val cholesterol: Double?,
    val sodium: Double?,
    val potassium: Double?,
    val transFat: Double?,
    val calcium: Double?,
    val iron: Double?,
    val magnesium: Double?,
    val zinc: Double?,
    val vitaminA: Double?,
    val vitaminC: Double?,
    val vitaminD: Double?,
    val vitaminB12: Double?,
    val vitaminE: Double?,
    val vitaminK: Double?,
    val folate: Double?,
    val omega3: Double?,
) {
    fun toCandidate(score: Double, datasetVersion: String): GroundingCandidate =
        GroundingCandidate(
            sourceKind = NutrientSourceKind.USDA,
            sourceId = fdcId.toString(),
            displayName = description,
            score = score,
            foodCategory = foodCategory,
            dataType = dataType,
            incompleteEnergy = calories == null,
            caloriesPer100g = calories,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            fatPer100g = fat,
            servingSizeGrams = servingGrams,
            matchedBy = "usda_search",
            datasetVersion = datasetVersion,
        )

    /** Scale per-100g nutrients to [grams]. */
    fun toFoodAnalysis(grams: Double, datasetVersion: String): FoodAnalysis {
        val unitOptions = if (!servingUnit.isNullOrBlank() && servingGrams != null && servingGrams > 0) {
            listOf(
                ServingUnitOption(
                    unit = servingUnit,
                    gramsPerUnit = servingGrams,
                    quantity = grams / servingGrams,
                )
            )
        } else {
            emptyList()
        }
        val selected = unitOptions.firstOrNull()
        val per100 = FoodAnalysis(
            name = description,
            calories = (calories ?: 0.0).roundToInt(),
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fat = fat ?: 0.0,
            servingSizeGrams = 100.0,
            sugar = sugar,
            addedSugar = addedSugar,
            fiber = fiber,
            saturatedFat = saturatedFat,
            monounsaturatedFat = monounsaturatedFat,
            polyunsaturatedFat = polyunsaturatedFat,
            cholesterol = cholesterol,
            sodium = sodium,
            potassium = potassium,
            transFat = transFat,
            calcium = calcium,
            iron = iron,
            magnesium = magnesium,
            zinc = zinc,
            vitaminA = vitaminA,
            vitaminC = vitaminC,
            vitaminD = vitaminD,
            vitaminB12 = vitaminB12,
            vitaminE = vitaminE,
            vitaminK = vitaminK,
            folate = folate,
            omega3 = omega3,
            grounding = app.chompass.models.FoodGroundingProvenance(
                sourceKind = NutrientSourceKind.USDA,
                sourceId = fdcId.toString(),
                sourceName = description,
                nutrientBasis = NutrientBasis.PER_100G,
                datasetVersion = datasetVersion,
                retrievedAtEpochMs = System.currentTimeMillis(),
                identityEvidence = "USDA FDC $fdcId",
            ),
        )
        val scaled = NutrientScaling.scaleAnalysis(per100, grams)
        return if (unitOptions.isEmpty()) {
            scaled
        } else {
            scaled.copy(
                servingUnitOptions = unitOptions,
                selectedServingUnit = selected?.unit,
                selectedServingQuantity = selected?.quantityFor(grams),
            )
        }
    }
}

/**
 * Pure helpers for combining / scaling grounded nutrient snapshots.
 * Kept separate from Android DB code for JVM unit tests.
 */
object NutrientScaling {
    fun scaleAnalysis(basePer100g: FoodAnalysis, grams: Double): FoodAnalysis {
        val scale = grams / 100.0
        fun s(v: Double?) = v?.let { round(it * scale * 10.0) / 10.0 }
        return basePer100g.copy(
            calories = (basePer100g.calories * scale).roundToInt(),
            protein = basePer100g.protein * scale,
            carbs = basePer100g.carbs * scale,
            fat = basePer100g.fat * scale,
            servingSizeGrams = grams,
            sugar = s(basePer100g.sugar),
            addedSugar = s(basePer100g.addedSugar),
            fiber = s(basePer100g.fiber),
            saturatedFat = s(basePer100g.saturatedFat),
            monounsaturatedFat = s(basePer100g.monounsaturatedFat),
            polyunsaturatedFat = s(basePer100g.polyunsaturatedFat),
            cholesterol = s(basePer100g.cholesterol),
            sodium = s(basePer100g.sodium),
            potassium = s(basePer100g.potassium),
            transFat = s(basePer100g.transFat),
            calcium = s(basePer100g.calcium),
            iron = s(basePer100g.iron),
            magnesium = s(basePer100g.magnesium),
            zinc = s(basePer100g.zinc),
            vitaminA = s(basePer100g.vitaminA),
            vitaminC = s(basePer100g.vitaminC),
            vitaminD = s(basePer100g.vitaminD),
            vitaminB12 = s(basePer100g.vitaminB12),
            vitaminE = s(basePer100g.vitaminE),
            vitaminK = s(basePer100g.vitaminK),
            folate = s(basePer100g.folate),
            omega3 = s(basePer100g.omega3),
        )
    }

    /** Sum absolute-amount analyses into one meal total. */
    fun sumAnalyses(name: String, emoji: String?, parts: List<FoodAnalysis>): FoodAnalysis {
        require(parts.isNotEmpty())
        fun sumD(sel: (FoodAnalysis) -> Double?) =
            parts.mapNotNull(sel).takeIf { it.isNotEmpty() }?.sum()?.let { round(it * 10.0) / 10.0 }
        return FoodAnalysis(
            name = name,
            calories = parts.sumOf { it.calories },
            protein = parts.sumOf { it.protein },
            carbs = parts.sumOf { it.carbs },
            fat = parts.sumOf { it.fat },
            servingSizeGrams = parts.mapNotNull { it.servingSizeGrams }
                .takeIf { it.size == parts.size }?.sum() ?: 100.0,
            emoji = emoji,
            sugar = sumD { it.sugar },
            addedSugar = sumD { it.addedSugar },
            fiber = sumD { it.fiber },
            saturatedFat = sumD { it.saturatedFat },
            monounsaturatedFat = sumD { it.monounsaturatedFat },
            polyunsaturatedFat = sumD { it.polyunsaturatedFat },
            cholesterol = sumD { it.cholesterol },
            sodium = sumD { it.sodium },
            potassium = sumD { it.potassium },
            transFat = sumD { it.transFat },
            calcium = sumD { it.calcium },
            iron = sumD { it.iron },
            magnesium = sumD { it.magnesium },
            zinc = sumD { it.zinc },
            vitaminA = sumD { it.vitaminA },
            vitaminC = sumD { it.vitaminC },
            vitaminD = sumD { it.vitaminD },
            vitaminB12 = sumD { it.vitaminB12 },
            vitaminE = sumD { it.vitaminE },
            vitaminK = sumD { it.vitaminK },
            folate = sumD { it.folate },
            omega3 = sumD { it.omega3 },
        )
    }

    /** Cap a history prior boost so it cannot dominate contradictory evidence. */
    fun cappedHistoryBoost(rawBoost: Double, maxBoost: Double = 1.5): Double =
        min(max(rawBoost, 0.0), maxBoost)
}
