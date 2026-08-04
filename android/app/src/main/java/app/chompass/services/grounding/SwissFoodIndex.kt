package app.chompass.services.grounding

import android.content.Context
import app.chompass.models.NutrientBasis
import app.chompass.models.NutrientSourceKind
import app.chompass.services.ai.FoodAnalysis
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Read-only lookup over the compact Swiss Food Composition Database SQLite asset
 * (`assets/swiss/swiss_foods.sqlite`, naehrwertdaten.ch — Swiss federal open data).
 * Ships in all build types (main assets). No Room — opens a copied file with
 * [android.database.sqlite.SQLiteDatabase.openDatabase].
 *
 * The source publishes the same foods as four independent language files (en/de/
 * fr/it) that are not row-aligned, so each row carries its own [SwissFoodRecord.lang].
 * Search runs across every language and ranks hits for the device [locale] first.
 */
class SwissFoodIndex(
    context: Context,
    private val locale: Locale = Locale.getDefault(),
    assetPath: String = ASSET_PATH,
) : OfflineFoodIndex(
    context = context,
    assetPath = assetPath,
    manifestAssetPath = MANIFEST_ASSET_PATH,
    dbFileName = DB_FILE_NAME,
) {
    fun getById(id: Long): SwissFoodRecord? {
        db.rawQuery(
            "SELECT * FROM foods WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return readRecord(c)
        }
    }

    /**
     * Full-text search across all four bundled languages, preferring rows in the
     * device language. Falls back to multi-token LIKE when FTS has no match.
     */
    fun search(
        query: String,
        limit: Int = 8,
    ): List<SwissFoodRecord> = searchScored(query, limit).map { it.first }

    /**
     * Like [search] but keeps the per-row relevance score so callers can rank
     * Swiss hits against other sources on a shared scale (see [FoodDatabaseSearch]).
     */
    internal fun searchScored(
        query: String,
        limit: Int = 8,
    ): List<Pair<SwissFoodRecord, Double>> {
        val tokens = QueryNormalizer.normalizeTokens(query)
        if (tokens.isEmpty()) return emptyList()
        val preferredLang = preferredLanguage()

        return searchRows(
            query = query,
            idColumn = "id",
            nameColumn = "name",
            tokensColumn = "tokens",
            rowReader = ::readRecord,
        )
            .asSequence()
            .filter { it.calories != null }
            .map { it to score(tokens, it, preferredLang) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    private fun preferredLanguage(): String? {
        val code = locale.language.lowercase(Locale.US)
        return if (code in setOf("de", "fr", "it")) code else "en"
    }

    private fun readRecord(c: android.database.Cursor): SwissFoodRecord {
        fun d(col: String): Double? {
            val idx = c.getColumnIndex(col)
            if (idx < 0 || c.isNull(idx)) return null
            return c.getDouble(idx)
        }
        return SwissFoodRecord(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            lang = c.getString(c.getColumnIndexOrThrow("lang")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            calories = d("calories"),
            protein = d("protein"),
            carbs = d("carbs"),
            fat = d("fat"),
            fiber = d("fiber"),
            sugar = d("sugar"),
            saturatedFat = d("saturated_fat"),
            monounsaturatedFat = d("monounsaturated_fat"),
            polyunsaturatedFat = d("polyunsaturated_fat"),
            cholesterol = d("cholesterol"),
            omega3 = d("omega3"),
            sodium = d("sodium"),
            potassium = d("potassium"),
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
        )
    }

    companion object {
        const val ASSET_PATH = "swiss/swiss_foods.sqlite"
        const val MANIFEST_ASSET_PATH = "swiss/swiss_foods.manifest.json"
        private const val DB_FILE_NAME = "swiss_foods.sqlite"

        /** True when the APK includes the offline index (main assets — all builds). */
        fun assetAvailable(context: Context, assetPath: String = ASSET_PATH): Boolean =
            OfflineFoodIndex.assetAvailable(context, assetPath)

        internal fun tokenize(text: String): List<String> = QueryNormalizer.normalizeTokens(text)

        internal fun score(
            queryTokens: List<String>,
            food: SwissFoodRecord,
            preferredLang: String?,
        ): Double {
            if (queryTokens.isEmpty()) return 0.0
            val desc = food.name.lowercase(Locale.US)
            val foodTokens = QueryNormalizer.tokenize(food.name).toSet()
            val joined = queryTokens.joinToString(" ")
            var score = 0.0
            if (desc == joined) score += 10.0
            else if (desc.startsWith(joined)) score += 6.0
            else if (desc.contains(joined)) score += 3.5
            val overlap = queryTokens.count { it in foodTokens }
            score += overlap * 1.5
            score += max(0.0, 2.0 - food.name.length / 80.0)
            if (preferredLang != null && food.lang == preferredLang) score += 2.0
            return score
        }
    }
}

/**
 * One Swiss Food Composition Database row (per-100g values, normalized to Chompass
 * units: macros g, cholesterol/sodium/minerals mg, vitamins µg where noted).
 */
data class SwissFoodRecord(
    val id: Long,
    val lang: String,
    val name: String,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val saturatedFat: Double? = null,
    val monounsaturatedFat: Double? = null,
    val polyunsaturatedFat: Double? = null,
    val cholesterol: Double? = null,
    val omega3: Double? = null,
    val sodium: Double? = null,
    val potassium: Double? = null,
    val calcium: Double? = null,
    val iron: Double? = null,
    val magnesium: Double? = null,
    val zinc: Double? = null,
    val vitaminA: Double? = null,
    val vitaminC: Double? = null,
    val vitaminD: Double? = null,
    val vitaminB12: Double? = null,
    val vitaminE: Double? = null,
    val vitaminK: Double? = null,
    val folate: Double? = null,
) {
    /** Scale per-100g nutrients to [grams] as a reviewable [FoodAnalysis]. */
    fun toFoodAnalysis(grams: Double, datasetVersion: String): FoodAnalysis {
        val per100 = FoodAnalysis(
            name = name,
            calories = (calories ?: 0.0).roundToInt(),
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fat = fat ?: 0.0,
            servingSizeGrams = 100.0,
            sugar = sugar,
            fiber = fiber,
            saturatedFat = saturatedFat,
            monounsaturatedFat = monounsaturatedFat,
            polyunsaturatedFat = polyunsaturatedFat,
            cholesterol = cholesterol,
            omega3 = omega3,
            sodium = sodium,
            potassium = potassium,
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
            grounding = app.chompass.models.FoodGroundingProvenance(
                sourceKind = NutrientSourceKind.SWISS,
                sourceId = id.toString(),
                sourceName = name,
                nutrientBasis = NutrientBasis.PER_100G,
                datasetVersion = datasetVersion,
                retrievedAtEpochMs = System.currentTimeMillis(),
                identityEvidence = "swiss-food-composition-db:$id",
            ),
        )
        return NutrientScaling.scaleAnalysis(per100, grams)
    }
}
