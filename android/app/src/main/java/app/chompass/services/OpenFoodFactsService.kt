package app.chompass.services

import app.chompass.data.PreferencesStore
import app.chompass.models.FoodGroundingProvenance
import app.chompass.models.FoodProductMetadata
import app.chompass.models.NutrientBasis
import app.chompass.models.NutrientSourceKind
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.FoodAnalysisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

object OpenFoodFactsService {
    private const val FIELDS = "product_name,generic_name,brands,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,nutriments,ingredients_text,allergens_tags,traces_tags,nutriscore_grade,nova_group,ecoscore_grade,labels_tags,categories_tags,image_front_url"
    private const val SEARCH_FIELDS =
        "code,product_name,generic_name,brands,serving_size,serving_quantity,nutriments"
    private const val USER_AGENT = "Chompass/Android (https://chompass.app)"
    private const val OFF_BASE_URL = "https://world.openfoodfacts.org"

    /** search-a-licious (Sal) full-text search endpoint. */
    private const val SAL_BASE_URL = "https://search.openfoodfacts.org"

    private const val SAL_SEARCH_FIELDS =
        "code,product_name,generic_name,brands,serving_quantity,nutriments"

    /** Search backend selector; the cgi path stays for rollback. */
    private enum class SearchBackend { SAL, CGI }

    private val SEARCH_BACKEND = SearchBackend.SAL

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

    /** Hard cap on total network attempts per search() call, across candidates and retries. */
    private const val MAX_SEARCH_ATTEMPTS = 4

    /** Rate-limit breaker cooldown when a 429 carries no Retry-After. */
    private const val RATE_LIMIT_DEFAULT_COOLDOWN_SECONDS = 60L

    /** Rate-limit breaker: longest cooldown honored from a Retry-After header. */
    private const val RATE_LIMIT_MAX_COOLDOWN_SECONDS = 120L

    /** Max bytes accepted for a downloaded OFF product photo. */
    private const val MAX_PRODUCT_IMAGE_BYTES = 5_000_000L

    private const val NOT_FOUND_MESSAGE =
        "Product not found in Open Food Facts. Scan the nutrition label instead."
    private const val TROUBLE_MESSAGE =
        "Open Food Facts is having trouble right now. Try again in a moment."
    private const val UNEXPECTED_RESPONSE_MESSAGE =
        "Open Food Facts returned an unexpected response."

    class LookupException(message: String) : Exception(message)

    /**
     * Rate-limit circuit breaker, tripped by any 429 from search or lookup:
     * while the cooldown lasts, [search] returns empty without touching the
     * network and barcode lookup fails fast with [TROUBLE_MESSAGE].
     * Module-level on purpose — OFF rate-limits the app as a whole, not per
     * call site, so every path shares one breaker.
     */
    @Volatile
    private var rateLimitCooldownUntilMs = 0L

    /** Wall clock for the breaker cooldown; swappable in tests. */
    internal var rateLimitNowMs: () -> Long = { System.currentTimeMillis() }

    private fun rateLimitActive(): Boolean = rateLimitNowMs() < rateLimitCooldownUntilMs

    private fun tripRateLimit(retryAfterSeconds: Long?) {
        val cooldownSeconds = (retryAfterSeconds ?: RATE_LIMIT_DEFAULT_COOLDOWN_SECONDS)
            .coerceIn(0L, RATE_LIMIT_MAX_COOLDOWN_SECONDS)
        rateLimitCooldownUntilMs = rateLimitNowMs() + cooldownSeconds * 1000L
    }

    /** Test hook: clears the breaker and restores the real clock. */
    internal fun resetRateLimitForTest() {
        rateLimitCooldownUntilMs = 0L
        rateLimitNowMs = { System.currentTimeMillis() }
    }

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

        lookupByCode(code, prefs, client, baseUrl)
    }

    /** Barcode lookup outcome plus the best-effort OFF front photo (null when unavailable). */
    data class LookupResult(
        val analysis: FoodAnalysis,
        val productImageBytes: ByteArray?,
    )

    /**
     * Barcode lookup plus a best-effort download of the OFF front product
     * photo. The photo download never fails the lookup: any problem (offline,
     * non-OFF host, non-image body, oversize) yields null bytes and the caller
     * falls back to the emoji hero.
     */
    suspend fun lookupWithImage(
        barcode: String,
        prefs: PreferencesStore,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
        baseUrl: String = OFF_BASE_URL,
    ): LookupResult = withContext(Dispatchers.IO) {
        val analysis = lookup(barcode, prefs, client, baseUrl)
        LookupResult(analysis, productImageBytes(analysis.productMetadata?.imageUrl, client))
    }

    /**
     * Looks up an OFF product code that is already authoritative — a search
     * hit's `code`/`_id` straight from OFF's search API — without re-validating
     * it through the scanner normalizer. OFF product codes are not guaranteed to
     * satisfy the GTIN check-digit / digit-shape rules (OFF indexes products
     * with codes that fail the check digit, carry letters, or use non-standard
     * lengths), so the normalizer gate must not apply here: the code is used
     * as-is for the cache key and the network call (OFF accepts any code
     * string; [URLEncoder] handles non-numeric shapes).
     */
    suspend fun lookupByCode(
        code: String,
        prefs: PreferencesStore,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
        baseUrl: String = OFF_BASE_URL,
    ): FoodAnalysis = withContext(Dispatchers.IO) {
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
     *
     * Queries run against OFF's search-a-licious (Sal) service, which ranks
     * multi-token and typo'd queries far better than the legacy cgi endpoint;
     * on a Sal transport failure a single automatic cgi attempt covers the
     * gap, and Sal's empty answers are authoritative.
     * Spending is bounded: at most [MAX_SEARCH_ATTEMPTS] network attempts per
     * call across all candidates and retries, a 503 (OFF's global request
     * limit) is retried once and then stops the whole walk, and any 429
     * trips the rate-limit breaker so subsequent searches and lookups back
     * off for the cooldown instead of hammering the shared endpoint.
     */
    suspend fun search(
        query: String,
        brand: String? = null,
        limit: Int = 6,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
        baseUrl: String = OFF_BASE_URL,
        searchBaseUrl: String = SAL_BASE_URL,
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        // Rate-limit breaker: while OFF has 429-ed us, answer from nothing
        // rather than adding more load (cached hits come from the callers'
        // local indexes; search has no cache of its own).
        if (rateLimitActive()) return@withContext emptyList()
        val capped = limit.coerceIn(1, 8)
        val brandToken = brand?.trim()?.takeIf { it.isNotEmpty() }
        val terms = listOfNotNull(brandToken, q).distinct().joinToString(" ")

        // Candidate queries in order of preference: the full AND query, the
        // query without the separately-passed brand, then progressively
        // shorter token drops (brand-ish first token first, then the last
        // token). Backend failures and successful-but-empty responses
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
        var attemptsLeft = MAX_SEARCH_ATTEMPTS
        var stopWalk = false
        var retriedGlobalLimit = false
        for (candidate in candidates) {
            var attempt = 0
            while (attempt < MAX_QUERY_ATTEMPTS && attemptsLeft > 0 && !stopWalk) {
                attemptsLeft--
                when (val outcome = backendSearch(candidate, capped, client, baseUrl, searchBaseUrl)) {
                    is SearchOutcome.Hits -> hits = outcome.hits
                    is SearchOutcome.RateLimited -> {
                        tripRateLimit(outcome.retryAfterSeconds)
                        stopWalk = true
                    }
                    is SearchOutcome.GlobalLimit -> if (retriedGlobalLimit) {
                        // A second 503 after the one allowed retry means OFF's
                        // global request limit: stop the walk entirely rather
                        // than pushing more candidates at a saturated backend.
                        stopWalk = true
                    } else {
                        retriedGlobalLimit = true
                    }
                    SearchOutcome.Unavailable -> Unit
                }
                if (stopWalk) break
                if (!hits.isNullOrEmpty()) break
                if (hits != null && attempt >= EMPTY_RETRY_ATTEMPTS) break
                attempt++
                if (attempt < MAX_QUERY_ATTEMPTS && attemptsLeft > 0) {
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

    /** One search attempt, classified so the walk can react per failure kind. */
    private sealed interface SearchOutcome {
        /** Usable response; empty means OFF authoritatively found nothing. */
        data class Hits(val hits: List<SearchHit>) : SearchOutcome

        /** HTTP 429 — rate limited; carries Retry-After when the server sent one. */
        data class RateLimited(val retryAfterSeconds: Long?) : SearchOutcome

        /** HTTP 503 — OFF's global request limit. */
        data object GlobalLimit : SearchOutcome

        /** Any other failure (network error, timeout, non-JSON, HTTP error). */
        data object Unavailable : SearchOutcome
    }

    /**
     * One search request. Returns [SearchOutcome.Unavailable] when the response
     * was not usable (HTTP error / non-JSON), [SearchOutcome.Hits] with an empty
     * list when OFF found nothing.
     */
    private suspend fun searchOnce(
        terms: String,
        capped: Int,
        client: OkHttpClient,
        baseUrl: String,
    ): SearchOutcome {
        val encoded = URLEncoder.encode(terms, "UTF-8")
        val url = "$baseUrl/cgi/search.pl" +
            "?search_terms=$encoded&search_simple=1&action=process&json=1" +
            "&page_size=$capped&fields=$SEARCH_FIELDS"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        // Cancellation-aware request: an abandoned search (new keystroke, sheet
        // closed) must die with its coroutine instead of blocking an IO thread
        // for the full 20 s connect / 60 s read timeout (Codeberg #26).
        // [cancellableExecute] cancels the call on cancellation, so the blocking
        // execute()/body read aborts promptly; cancellation then surfaces as
        // CancellationException from the caller's next suspension point (the
        // retry backoff `delay`), stopping the candidate/attempt loops.
        val call = client.newCall(request)
        val reply = try {
            cancellableExecute(call)
        } catch (e: IOException) {
            return SearchOutcome.Unavailable
        }
        return when {
            reply.code == 429 -> SearchOutcome.RateLimited(reply.retryAfterSeconds)
            reply.code == 503 -> SearchOutcome.GlobalLimit
            reply.body == null -> SearchOutcome.Unavailable
            else -> {
                val json = runCatching { JSONObject(reply.body) }.getOrNull()
                    ?: return SearchOutcome.Unavailable
                val products = json.optJSONArray("products")
                    ?: return SearchOutcome.Hits(emptyList())
                SearchOutcome.Hits(searchHitsFrom(products, capped))
            }
        }
    }

    /**
     * One walk step against the configured backend. With [SearchBackend.SAL]
     * (the default), a Sal failure that is not a rate/global-limit signal gets
     * exactly one automatic `cgi/search.pl` attempt before giving the step up
     * as [SearchOutcome.Unavailable]; Sal's successful-but-empty answer is
     * authoritative and never falls back to cgi.
     */
    private suspend fun backendSearch(
        terms: String,
        capped: Int,
        client: OkHttpClient,
        baseUrl: String,
        searchBaseUrl: String,
    ): SearchOutcome {
        if (SEARCH_BACKEND != SearchBackend.SAL) {
            return searchOnce(terms, capped, client, baseUrl)
        }
        return when (val sal = searchSalOnce(terms, capped, client, searchBaseUrl)) {
            is SearchOutcome.Hits -> sal
            SearchOutcome.Unavailable -> searchOnce(terms, capped, client, baseUrl)
            // 429/503 are OFF-side signals about the app as a whole, not Sal
            // being broken: pass them to the walk untouched.
            else -> sal
        }
    }

    /**
     * One search-a-licious request (`GET /search`). Envelope: `hits` array,
     * `count`/`page`/`page_size` metadata. Each hit carries `brands` as an
     * array of strings (or null) and flat per-100g nutriments named exactly
     * like the v2 API's; `serving_quantity` is requested but typically null,
     * leaving the caller's 100 g fallback in place.
     */
    private suspend fun searchSalOnce(
        terms: String,
        capped: Int,
        client: OkHttpClient,
        searchBaseUrl: String,
    ): SearchOutcome {
        val encoded = URLEncoder.encode(terms, "UTF-8")
        val url = "$searchBaseUrl/search?q=$encoded&fields=$SAL_SEARCH_FIELDS&page_size=$capped"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val reply = try {
            cancellableExecute(client.newCall(request))
        } catch (e: IOException) {
            return SearchOutcome.Unavailable
        }
        return when {
            reply.code == 429 -> SearchOutcome.RateLimited(reply.retryAfterSeconds)
            reply.code == 503 -> SearchOutcome.GlobalLimit
            reply.body == null -> SearchOutcome.Unavailable
            else -> {
                val json = runCatching { JSONObject(reply.body) }.getOrNull()
                    ?: return SearchOutcome.Unavailable
                val hits = json.optJSONArray("hits")
                    ?: return SearchOutcome.Hits(emptyList())
                SearchOutcome.Hits(salHitsFrom(hits, capped))
            }
        }
    }

    /** Maps a Sal `hits` array into [SearchHit]s, capped at [capped]. */
    private fun salHitsFrom(hits: org.json.JSONArray, capped: Int): List<SearchHit> {
        return buildList {
            for (i in 0 until hits.length()) {
                if (size >= capped) break
                val hit = hits.optJSONObject(i) ?: continue
                val code = hit.optString("code").trim()
                if (code.isEmpty()) continue
                val name = firstNonEmpty(
                    hit.optString("product_name"),
                    hit.optString("generic_name"),
                ) ?: "Barcode $code"
                // Sal's brands is an array of strings (or null/absent): the
                // first entry is the display brand, same as cgi's first term.
                val brandName = hit.optJSONArray("brands")
                    ?.optString(0)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val macros = macrosPer100g(hit.optJSONObject("nutriments"))
                val servingGrams = hit.flexibleDouble("serving_quantity")
                    ?.takeIf { it > 0 }
                add(searchHit(code, name, brandName, macros, servingGrams))
            }
        }
    }

    /** Per-100g macro values shared by every search backend's hit shape. */
    private class MacroPer100g(
        val calories: Double?,
        val protein: Double?,
        val carbs: Double?,
        val fat: Double?,
    )

    private fun macrosPer100g(nutriments: JSONObject?): MacroPer100g = MacroPer100g(
        calories = nutriments?.flexibleDouble("energy-kcal_100g")
            ?: nutriments?.flexibleDouble("energy_100g")?.let { it * 0.23900573614 },
        protein = nutriments?.flexibleDouble("proteins_100g"),
        carbs = nutriments?.flexibleDouble("carbohydrates_100g")
            ?: nutriments?.flexibleDouble("carbohydrates-total_100g"),
        fat = nutriments?.flexibleDouble("fat_100g"),
    )

    private fun searchHit(
        code: String,
        name: String,
        brandName: String?,
        macros: MacroPer100g,
        servingGrams: Double?,
    ) = SearchHit(
        barcode = code,
        name = name,
        brand = brandName,
        caloriesPer100g = macros.calories,
        proteinPer100g = macros.protein,
        carbsPer100g = macros.carbs,
        fatPer100g = macros.fat,
        servingGrams = servingGrams,
        incompleteEnergy = macros.calories == null,
        score = 0.0,
    )

    /** Maps a cgi `products` array into [SearchHit]s, capped at [capped]. */
    private fun searchHitsFrom(products: org.json.JSONArray, capped: Int): List<SearchHit> {
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
                val macros = macrosPer100g(nutriments)
                val servingGrams = maxOf(
                    product.flexibleDouble("serving_quantity")
                        ?: gramsFrom(product.optString("serving_size").takeIf { it.isNotBlank() })
                        ?: 0.0,
                    0.0,
                ).takeIf { it > 0 }
                add(searchHit(code, name, brandName, macros, servingGrams))
            }
        }
    }

    /** Cancellable response summary: status code, Retry-After hint, body of a 2xx. */
    private class HttpReply(val code: Int, val retryAfterSeconds: Long?, val body: String?)

    /**
     * Executes [call] and reads a successful body as a string; [HttpReply.body]
     * is null when the HTTP status was not successful (the status code itself
     * is still reported). Cancellation-aware (Codeberg #26): [Call.cancel]
     * is registered on the continuation, so an abandoned search (new keystroke,
     * sheet closed) closes the socket and the blocking read aborts promptly
     * instead of occupying an IO thread for the full 20 s connect / 60 s read
     * timeout. Runs on the caller's context — the search sheet's [search] already
     * executes on [Dispatchers.IO]. Network failures throw [IOException];
     * cancellation completes the caller with CancellationException.
     */
    private suspend fun cancellableExecute(call: Call): HttpReply =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            try {
                val response = call.execute()
                val reply = response.use {
                    HttpReply(
                        code = it.code,
                        retryAfterSeconds = it.header("Retry-After")?.trim()?.toLongOrNull(),
                        body = if (it.isSuccessful) it.body?.string().orEmpty() else null,
                    )
                }
                cont.resume(reply)
            } catch (e: IOException) {
                cont.resumeWithException(e)
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
     * failures (5xx, network) with backoff; a 404 (product not in the
     * database) and other 4xx are definitive and fail immediately with a
     * distinct message, so "not found" no longer reads as a generic error
     * (Codeberg #24). A 429 trips the rate-limit breaker and fails fast.
     */
    internal suspend fun lookupNetwork(
        code: String,
        client: OkHttpClient,
        baseUrl: String = OFF_BASE_URL,
    ): FoodAnalysis = run {
        if (rateLimitActive()) throw LookupException(TROUBLE_MESSAGE)
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
                outcome.getOrThrow().use { response ->
                    if (response.code == 429) {
                        // Rate limited: trip the shared breaker and stop instead
                        // of burning the retry budget against a 429 backend.
                        tripRateLimit(response.header("Retry-After")?.trim()?.toLongOrNull())
                        throw LookupException(TROUBLE_MESSAGE)
                    }
                    val parsed = parseLookupResponse(response, code)
                    if (parsed != null) return@run parsed
                }
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
     * response was transiently unusable (5xx, retry). 404 and other 4xx
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
        val carbs = servingValue("carbohydrates") ?: servingValue("carbohydrates-total")
        val fat = servingValue("fat")

        if (calories == null && protein == null && carbs == null && fat == null) {
            throw LookupException("This barcode was found, but nutrition data is incomplete. Scan the nutrition label instead.")
        }

        val servingOption = ServingUnitOption(unit = "serving", gramsPerUnit = servingGrams, quantity = 1.0)
        val packageGramsValue = packageGrams(product)
        val servingOptions = buildList {
            add(servingOption)
            if (packageGramsValue != null && abs(packageGramsValue - servingGrams) > 0.01) {
                add(ServingUnitOption(unit = "package", gramsPerUnit = packageGramsValue, quantity = 1.0))
            }
        }
        val metadata = FoodProductMetadata(
            barcode = barcode,
            packageQuantity = product.string("quantity"),
            ingredientsText = product.string("ingredients_text"),
            allergens = displayTags(product.stringList("allergens_tags"), 16),
            traces = displayTags(product.stringList("traces_tags"), 16),
            nutriScore = normalizedScore(product.string("nutriscore_grade")),
            novaGroup = product.flexibleInt("nova_group")?.takeIf { it in 1..4 },
            ecoScore = normalizedScore(product.string("ecoscore_grade")),
            labels = displayTags(product.stringList("labels_tags"), 12),
            categories = displayTags(product.stringList("categories_tags"), 8),
            imageUrl = product.string("image_front_url"),
        )
        val name = productName(product, barcode)
        val validation = app.chompass.models.GroundingValidator.validateServing(
            analysisName = name,
            calories = (calories ?: 0.0).roundToInt(),
            protein = protein,
            carbs = carbs,
            fat = fat,
            servingGrams = servingGrams,
            sodiumMg = milligrams(servingValue("sodium")),
            caloriesPer100g = nutriments.flexibleDouble("energy-kcal_100g")
                ?: nutriments.flexibleDouble("energy_100g")?.let { it * 0.23900573614 },
            proteinPer100g = nutriments.flexibleDouble("proteins_100g"),
            carbsPer100g = nutriments.flexibleDouble("carbohydrates_100g")
                ?: nutriments.flexibleDouble("carbohydrates-total_100g"),
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
            caffeine = milligrams(servingValue("caffeine")),
            servingUnitOptions = servingOptions,
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
            productMetadata = metadata,
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

    /**
     * Best-effort OFF product-photo download for the barcode review sheet:
     * single attempt, never throws, never retries. Only
     * `https://images.openfoodfacts.org` is allowed (matches the OFF API's
     * `image_front_url` host); anything else keeps the emoji fallback.
     */
    internal fun productImageBytes(urlString: String?, client: OkHttpClient): ByteArray? {
        val url = urlString?.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.host != "images.openfoodfacts.org") return null
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "image/*")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                if (response.body?.contentType()?.type != "image") return@use null
                val declared = response.body?.contentLength() ?: -1L
                if (declared > MAX_PRODUCT_IMAGE_BYTES) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.size > MAX_PRODUCT_IMAGE_BYTES) null else bytes
            }
        } catch (_: IOException) {
            null
        }
    }

    /** Package size in grams from structured quantity, else a strict display-string parse. */
    private fun packageGrams(product: JSONObject): Double? {
        val structured = product.flexibleDouble("product_quantity")?.takeIf { it > 0 }
        if (structured != null) {
            val unit = product.string("product_quantity_unit")?.lowercase(Locale.US)
            val grams = when (unit) {
                "kg" -> structured * 1000.0
                "mg" -> structured / 1000.0
                "g", null -> structured
                "l" -> structured * 1000.0
                "ml" -> structured
                else -> null
            }
            if (grams != null) return grams
        }
        val display = product.string("quantity") ?: return null
        if (!Regex("""^[0-9]+(?:[.,][0-9]+)?\s*(?:kg|mg|g|oz|ml|l)$""", RegexOption.IGNORE_CASE).matches(display)) {
            return null
        }
        return gramsFrom(display)
    }

    private fun JSONObject.string(key: String): String? =
        optString(key).trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    /** OFF tag ids (`en:milk`, `en:high-protein`) turned into display names. */
    private fun displayTags(tags: List<String>, limit: Int): List<String> {
        if (tags.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        val displayed = mutableListOf<String>()
        for (tag in tags) {
            val cleaned = tag
                .replaceFirst(Regex("^[a-z]{2}:"), "")
                .replace("-", " ")
                .replace("_", " ")
                .trim()
            if (cleaned.isEmpty() || !seen.add(cleaned.lowercase(Locale.US))) continue
            displayed.add(cleaned.replaceFirstChar { it.uppercase(Locale.US) })
            if (displayed.size >= limit) break
        }
        return displayed
    }

    /** Nutri-/Eco-Score grade letter, or null for `unknown` / `not-applicable`. */
    private fun normalizedScore(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("not-applicable", ignoreCase = true)
        ) {
            return null
        }
        return trimmed.uppercase(Locale.US)
    }

    private fun JSONObject.flexibleInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()?.toInt()
            else -> null
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
