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
import okhttp3.Response
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

    /** Max attempts per candidate query before trying a shorter one. */
    private const val MAX_QUERY_ATTEMPTS = 3

    /**
     * One empty-but-valid response is retried once (OFF intermittently answers
     * empty and then non-empty for the same query within seconds); a second
     * empty moves on to the next shorter candidate.
     */
    private const val EMPTY_RETRY_ATTEMPTS = 1

    /** Backoff between retries (multiplied by 2^attempt: 400, 800 ms). */
    private const val QUERY_RETRY_BASE_DELAY_MS = 200L

    /** Max attempts for a barcode lookup (OFF intermittently 503s, see #24). */
    private const val LOOKUP_MAX_ATTEMPTS = 3

    /** Backoff between barcode-lookup retries (multiplied by 2^attempt: 200, 400, 800 ms). */
    private const val LOOKUP_RETRY_BASE_DELAY_MS = 200L

    private const val NOT_FOUND_MESSAGE =
        "Product not found in Open Food Facts. Scan the nutrition label instead."
    private const val TROUBLE_MESSAGE =
        "Open Food Facts is having trouble right now. Try again in a moment."
    private const val UNEXPECTED_RESPONSE_MESSAGE =
        "Open Food Facts returned an unexpected response."

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
        client: OkHttpClient = FoodAnalysisService.defaultClient,
        baseUrl: String = OFF_BASE_URL,
    ): FoodAnalysis = withContext(Dispatchers.IO) {
        // 2D matrix codes (QR / DataMatrix) decode to GS1 / URL text, not a bare
        // EAN: normalize before the cache check so all callers (camera, photo
        // entry, grounded-entry tools) resolve 2D codes the same way. null = not
        // a product code (internal factory codes, brand URLs) — no network call.
        val code = BarcodeCodeNormalizer.normalize(barcode)
            ?: throw LookupException("That barcode could not be read. Try scanning it again.")

        prefs.barcodeCache.first()[code]?.let { return@withContext it.analysis }

        val result = lookupNetwork(code, client, baseUrl)
        prefs.cacheBarcodeLookup(code, result)
        result
    }

    /**
     * Live text/brand search against Open Food Facts (ODbL). Sends only the
     * search query — never diary history. Results are not merged into USDA SQLite.
     *
     * OFF's search has no typo tolerance, ANDs every query token, and
     * intermittently 503s or returns empty for queries that work seconds
     * later (which the UI would otherwise show as "no foods found").
     * [search] rides out transient failures with retries, then walks a
     * candidate chain — full query, without the separately-passed brand,
     * then shorter token drops (brand-ish first token first) — so "Aldi
     * Laugen" still surfaces Laugen products when the AND query misses.
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

        // Candidate queries in order of preference: the full AND query, the
        // query without the separately-passed brand, then progressively
        // shorter token drops (brand-ish first token first, then the last
        // token). Backend failures (null) and successful-but-empty responses
        // both advance to the next candidate — OFF often answers a shorter
        // query while the longer one 503s or AND-misses.
        val candidates = buildList {
            add(terms)
            if (brandToken != null && terms != q) add(q)
            val tokens = q.split(' ').filter { it.isNotBlank() }
            if (tokens.size > 1) {
                add(tokens.drop(1).joinToString(" "))
                add(tokens.dropLast(1).joinToString(" "))
            }
        }.distinct()

        var hits: List<SearchHit>? = null
        for (candidate in candidates) {
            var attempt = 0
            while (attempt < MAX_QUERY_ATTEMPTS) {
                hits = searchOnce(candidate, capped, client, baseUrl)
                if (!hits.isNullOrEmpty()) break
                if (hits != null && attempt >= EMPTY_RETRY_ATTEMPTS) break
                attempt++
                if (attempt < MAX_QUERY_ATTEMPTS) {
                    delay(QUERY_RETRY_BASE_DELAY_MS * (1 shl attempt))
                }
            }
            if (!hits.isNullOrEmpty()) break
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

    /**
     * Live barcode lookup against Open Food Facts (ODbL). Retries transient
     * failures (429/5xx, network) with backoff; a 404 (product not in the
     * database) and other 4xx are definitive and fail immediately with a
     * distinct message, so "not found" no longer reads as a generic error
     * (Codeberg #24).
     */
    internal suspend fun lookupNetwork(
        code: String,
        client: OkHttpClient,
        baseUrl: String = OFF_BASE_URL,
    ): FoodAnalysis = run {
        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val url = "$baseUrl/api/v2/product/$encodedCode.json?fields=$FIELDS"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()

        var lastNetworkError: String? = null
        var attempt = 0
        while (attempt < LOOKUP_MAX_ATTEMPTS) {
            val outcome = runCatching { client.newCall(request).execute() }
            val error = outcome.exceptionOrNull()
            if (error == null) {
                val parsed = outcome.getOrThrow().use { response -> parseLookupResponse(response, code) }
                if (parsed != null) return@run parsed
            } else {
                lastNetworkError = error.localizedMessage ?: "network error"
            }
            attempt++
            if (attempt < LOOKUP_MAX_ATTEMPTS) {
                delay(LOOKUP_RETRY_BASE_DELAY_MS * (1 shl attempt))
            }
        }
        throw LookupException(
            if (lastNetworkError != null) "Barcode lookup failed: $lastNetworkError"
            else TROUBLE_MESSAGE
        )
    }

    /**
     * One lookup response. Returns the analysis on success, null when the
     * response was transiently unusable (429/5xx, retry). 404 and other 4xx
     * are definitive errors.
     */
    private fun parseLookupResponse(response: Response, code: String): FoodAnalysis? {
        when {
            response.code == 404 -> throw LookupException(NOT_FOUND_MESSAGE)
            response.code == 429 || response.code >= 500 -> return null
            !response.isSuccessful -> throw LookupException(UNEXPECTED_RESPONSE_MESSAGE)
        }
        val raw = response.body?.string().orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw LookupException(UNEXPECTED_RESPONSE_MESSAGE)
        val product = json.optJSONObject("product")
        if (json.optInt("status", 0) == 0 || product == null) {
            throw LookupException(NOT_FOUND_MESSAGE)
        }
        return analysis(product, code)
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
