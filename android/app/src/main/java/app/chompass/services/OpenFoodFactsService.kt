package app.chompass.services

import app.chompass.data.PreferencesStore
import app.chompass.models.FoodGroundingProvenance
import app.chompass.models.NutrientBasis
import app.chompass.models.NutrientSourceKind
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.FoodAnalysisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

object OpenFoodFactsService {
    private const val FIELDS = "product_name,generic_name,brands,quantity,serving_size,serving_quantity,nutriments"
    private const val SEARCH_FIELDS =
        "code,product_name,generic_name,brands,serving_size,serving_quantity,nutriments"
    private const val USER_AGENT = "Chompass/Android (https://chompass.app)"
    private const val OFF_BASE_URL = "https://world.openfoodfacts.org"

    /** Max attempts for the full query before falling back to shorter ones. */
    private const val MAX_QUERY_ATTEMPTS = 3

    /** Backoff between full-query retries (multiplied by the attempt number). */
    private const val QUERY_RETRY_BASE_DELAY_MS = 200L

    class LookupException(message: String) : Exception(message)

    /**
     * One Open Food Facts search hit with per-100g macros for grounding candidates.
     * [barcode] is the OFF product code (source id).
     */
    data class SearchHit(
        val barcode: String,
        val name: String,
        val brand: String?,
        val caloriesPer100g: Double?,
        val proteinPer100g: Double?,
        val carbsPer100g: Double?,
        val fatPer100g: Double?,
        val servingGrams: Double?,
        val incompleteEnergy: Boolean,
        val score: Double,
    )

    /**
     * Looks up [barcode], preferring a cached result from a previous lookup
     * (instant, works offline) and falling back to a live Open Food Facts
     * call on a cache miss. Successful network lookups are cached for next time.
     */
    suspend fun lookup(
        barcode: String,
        prefs: PreferencesStore,
        client: OkHttpClient = FoodAnalysisService.defaultClient
    ): FoodAnalysis = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        if (code.isEmpty()) throw LookupException("That barcode could not be read. Try scanning it again.")

        prefs.barcodeCache.first()[code]?.let { return@withContext it.analysis }

        val result = lookupNetwork(code, client)
        prefs.cacheBarcodeLookup(code, result)
        result
    }

    /**
     * Live text/brand search against Open Food Facts (ODbL). Sends only the
     * search query — never diary history. Results are not merged into USDA SQLite.
     *
     * OFF's search has no typo tolerance, ANDs every query token, and
     * intermittently returns 503 (which the UI would otherwise show as "no
     * foods found"). [search] rides out transient failures with a few
     * short-retry attempts, then falls back to progressively shorter queries
     * (dropping the brand first) when the full query comes back empty.
     */
    suspend fun search(
        query: String,
        brand: String? = null,
        limit: Int = 6,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
        baseUrl: String = OFF_BASE_URL,
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val capped = limit.coerceIn(1, 8)
        val brandToken = brand?.trim()?.takeIf { it.isNotEmpty() }
        val terms = listOfNotNull(brandToken, q).distinct().joinToString(" ")

        var hits: List<SearchHit>? = null
        var attempt = 0
        while (attempt < MAX_QUERY_ATTEMPTS) {
            hits = searchOnce(terms, capped, client, baseUrl)
            // Non-null means OFF responded (empty or not): a successful-but-
            // empty result won't improve by retrying — fall back below instead.
            if (hits != null) break
            attempt++
            if (attempt < MAX_QUERY_ATTEMPTS) delay(QUERY_RETRY_BASE_DELAY_MS * attempt)
        }

        // Only fall back to shorter queries after a successful-but-empty
        // response — if the backend is failing (null), shorter queries won't
        // fare better. Drop the brand token first (it is the most likely
        // AND-miss), then the trailing food terms.
        var lastResponseOk = hits != null
        var shortened = terms
        var lastAttempt: String? = null
        while (hits.isNullOrEmpty() && lastResponseOk) {
            shortened = if (brandToken != null && shortened == terms) {
                q
            } else {
                shortened.substringBeforeLast(' ').trim()
            }
            if (shortened.isEmpty() || shortened == lastAttempt) break
            lastAttempt = shortened
            hits = searchOnce(shortened, capped, client, baseUrl)
            if (hits != null) lastResponseOk = true
        }

        val finalHits = hits.orEmpty()
        val queryTokens = terms.lowercase(Locale.US).split(Regex("\\s+")).filter { it.length > 1 }
        finalHits
            .map { it.withScoreAgainst(queryTokens) }
            .sortedByDescending { it.score }
    }

    /**
     * One search request. Returns null when the response was not usable (HTTP
     * error / non-JSON), empty when OFF found nothing.
     */
    private suspend fun searchOnce(
        terms: String,
        capped: Int,
        client: OkHttpClient,
        baseUrl: String,
    ): List<SearchHit>? {
        val encoded = URLEncoder.encode(terms, "UTF-8")
        val url = "$baseUrl/cgi/search.pl" +
            "?search_terms=$encoded&search_simple=1&action=process&json=1" +
            "&page_size=$capped&fields=$SEARCH_FIELDS"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val raw = runCatching { client.newCall(request).execute() }
            .getOrElse { return null }
            .use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val products = json.optJSONArray("products") ?: return emptyList()
        return buildList {
            for (i in 0 until products.length()) {
                if (size >= capped) break
                val product = products.optJSONObject(i) ?: continue
                val code = product.optString("code").trim().ifEmpty {
                    product.optString("_id").trim()
                }
                if (code.isEmpty()) continue
                // Brand stays in [SearchHit.brand]; keep [SearchHit.name] the
                // plain product name so consumers that render both don't show
                // "Aldi Aldi Laugen Brezen".
                val name = firstNonEmpty(
                    product.optString("product_name"),
                    product.optString("generic_name"),
                ) ?: "Barcode $code"
                val brandName = product.optString("brands")
                    .split(",")
                    .firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val nutriments = product.optJSONObject("nutriments")
                val cal100 = nutriments?.flexibleDouble("energy-kcal_100g")
                    ?: nutriments?.flexibleDouble("energy_100g")?.let { it * 0.23900573614 }
                val protein100 = nutriments?.flexibleDouble("proteins_100g")
                val carbs100 = nutriments?.flexibleDouble("carbohydrates_100g")
                val fat100 = nutriments?.flexibleDouble("fat_100g")
                val servingGrams = maxOf(
                    product.flexibleDouble("serving_quantity")
                        ?: gramsFrom(product.optString("serving_size").takeIf { it.isNotBlank() })
                        ?: 0.0,
                    0.0,
                ).takeIf { it > 0 }
                add(
                    SearchHit(
                        barcode = code,
                        name = name,
                        brand = brandName,
                        caloriesPer100g = cal100,
                        proteinPer100g = protein100,
                        carbsPer100g = carbs100,
                        fatPer100g = fat100,
                        servingGrams = servingGrams,
                        incompleteEnergy = cal100 == null,
                        score = 0.0,
                    ),
                )
            }
        }
    }

    private fun SearchHit.withScoreAgainst(queryTokens: List<String>): SearchHit {
        val hay = "$brand $name".lowercase(Locale.US)
        val overlap = queryTokens.count { hay.contains(it) }.toDouble()
        val score = overlap * 2.0 + maxOf(0.0, 3.0 - name.length / 40.0) +
            if (caloriesPer100g != null) 1.0 else 0.0
        return copy(score = score)
    }

    private suspend fun lookupNetwork(code: String, client: OkHttpClient): FoodAnalysis = run {
        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val url = "https://world.openfoodfacts.org/api/v2/product/$encodedCode.json?fields=$FIELDS"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()

        val raw = runCatching { client.newCall(request).execute() }
            .getOrElse { throw LookupException("Barcode lookup failed: ${it.localizedMessage ?: "network error"}") }
            .use { response ->
                if (!response.isSuccessful) {
                    throw LookupException("Open Food Facts returned an unexpected response.")
                }
                response.body?.string().orEmpty()
            }

        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw LookupException("Open Food Facts returned an unexpected response.")
        val product = json.optJSONObject("product")
        if (json.optInt("status", 0) == 0 || product == null) {
            throw LookupException("Product not found in Open Food Facts. Scan the nutrition label instead.")
        }
        analysis(product, code)
    }

    /** Maps an Open Food Facts `product` object to [FoodAnalysis] (serving-scaled). */
    internal fun analysis(product: JSONObject, barcode: String): FoodAnalysis {
        val nutriments = product.optJSONObject("nutriments")
            ?: throw LookupException("This barcode was found, but nutrition data is incomplete. Scan the nutrition label instead.")

        val servingGrams = maxOf(
            product.flexibleDouble("serving_quantity")
                ?: gramsFrom(product.optString("serving_size").takeIf { it.isNotBlank() })
                ?: 100.0,
            1.0
        )
        val scale = servingGrams / 100.0

        fun servingValue(key: String): Double? {
            nutriments.flexibleDouble("${key}_serving")?.let { return it }
            return nutriments.flexibleDouble("${key}_100g")?.let { it * scale }
        }

        val calories = servingValue("energy-kcal")
            ?: servingValue("energy")?.let { it * 0.23900573614 }
        val protein = servingValue("proteins")
        val carbs = servingValue("carbohydrates")
        val fat = servingValue("fat")

        if (calories == null && protein == null && carbs == null && fat == null) {
            throw LookupException("This barcode was found, but nutrition data is incomplete. Scan the nutrition label instead.")
        }

        val servingOption = ServingUnitOption(unit = "serving", gramsPerUnit = servingGrams, quantity = 1.0)
        val name = productName(product, barcode)
        val validation = app.chompass.models.GroundingValidator.validateServing(
            analysisName = name,
            calories = (calories ?: 0.0).roundToInt(),
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fat = fat ?: 0.0,
            servingGrams = servingGrams,
            sodiumMg = milligrams(servingValue("sodium")),
            caloriesPer100g = nutriments.flexibleDouble("energy-kcal_100g")
                ?: nutriments.flexibleDouble("energy_100g")?.let { it * 0.23900573614 },
            proteinPer100g = nutriments.flexibleDouble("proteins_100g"),
            carbsPer100g = nutriments.flexibleDouble("carbohydrates_100g"),
            fatPer100g = nutriments.flexibleDouble("fat_100g"),
        )
        return FoodAnalysis(
            name = name,
            calories = validation.correctedCalories ?: (calories ?: 0.0).roundToInt(),
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fat = fat ?: 0.0,
            servingSizeGrams = servingGrams,
            emoji = "🏷️",
            sugar = rounded(servingValue("sugars")),
            addedSugar = rounded(servingValue("added-sugars")),
            fiber = rounded(servingValue("fiber")),
            saturatedFat = rounded(servingValue("saturated-fat")),
            monounsaturatedFat = rounded(servingValue("monounsaturated-fat")),
            polyunsaturatedFat = rounded(servingValue("polyunsaturated-fat")),
            cholesterol = milligrams(servingValue("cholesterol")),
            sodium = validation.correctedSodiumMg ?: milligrams(servingValue("sodium")),
            potassium = milligrams(servingValue("potassium")),
            transFat = rounded(servingValue("trans-fat")),
            calcium = milligrams(servingValue("calcium")),
            iron = milligrams(servingValue("iron")),
            magnesium = milligrams(servingValue("magnesium")),
            zinc = milligrams(servingValue("zinc")),
            vitaminA = micrograms(servingValue("vitamin-a")),
            vitaminC = milligrams(servingValue("vitamin-c")),
            vitaminD = micrograms(servingValue("vitamin-d")),
            vitaminB12 = micrograms(servingValue("vitamin-b12")),
            vitaminE = milligrams(servingValue("vitamin-e")),
            vitaminK = micrograms(servingValue("vitamin-k")),
            folate = micrograms(servingValue("folates")),
            omega3 = rounded(servingValue("omega-3-fat")),
            servingUnitOptions = listOf(servingOption),
            selectedServingUnit = servingOption.unit,
            selectedServingQuantity = 1.0,
            grounding = FoodGroundingProvenance(
                sourceKind = NutrientSourceKind.OPEN_FOOD_FACTS,
                sourceId = barcode,
                sourceName = name,
                nutrientBasis = NutrientBasis.PER_SERVING,
                datasetVersion = "openfoodfacts-live",
                retrievedAtEpochMs = System.currentTimeMillis(),
                identityEvidence = "barcode:$barcode",
                portionEvidence = "serving_quantity=${servingGrams}g",
                identityConfirmed = true,
                validationNotes = validation.notes,
            ),
        )
    }

    private fun productName(product: JSONObject, barcode: String): String {
        val primary = firstNonEmpty(
            product.optString("product_name"),
            product.optString("generic_name")
        )
        val brand = product.optString("brands")
            .split(",")
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (primary != null && brand != null && !primary.lowercase(Locale.US).contains(brand.lowercase(Locale.US))) {
            return "$brand $primary"
        }
        return primary ?: brand ?: "Barcode $barcode"
    }

    private fun firstNonEmpty(vararg values: String?): String? =
        values.mapNotNull { it?.trim() }.firstOrNull { it.isNotEmpty() }

    private fun rounded(value: Double?): Double? =
        value?.let { round(it * 10.0) / 10.0 }

    private fun milligrams(grams: Double?): Double? =
        grams?.let { round(it * 1000.0 * 10.0) / 10.0 }

    private fun micrograms(grams: Double?): Double? =
        grams?.let { round(it * 1_000_000.0 * 10.0) / 10.0 }

    private fun gramsFrom(servingSize: String?): Double? {
        var text = servingSize?.lowercase(Locale.US) ?: return null
        text = text.replace(",", ".").replace("fl. oz", "fl oz")
        val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(fl oz|kg|mg|g|oz|ml|l)""")
            .find(text)
            ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2]) {
            "kg" -> value * 1000.0
            "mg" -> value / 1000.0
            "oz" -> value * 28.3495
            "fl oz" -> value * 29.5735
            "ml" -> value
            "l" -> value * 1000.0
            else -> value
        }
    }

    private fun JSONObject.flexibleDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }?.takeUnless { it.isNaN() || it.isInfinite() }
    }
}
