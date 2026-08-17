package app.chompass.services.grounding

import app.chompass.data.PreferencesStore
import android.os.SystemClock
import android.util.Log
import app.chompass.models.NutrientSourceKind
import app.chompass.services.OpenFoodFactsService
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.round

/**
 * One food-database search hit shown in the Add Food search sheet, normalized so
 * the UI can render a name, source badge, and per-serving macros uniformly.
 */
data class DatabaseSearchResult(
    val sourceKind: NutrientSourceKind,
    val sourceId: String,
    val name: String,
    val brand: String? = null,
    /** Swiss rows are tagged with the bundled language (en/de/fr/it). */
    val lang: String? = null,
    /** Portion used for the per-serving macros below; 100g when unknown. */
    val servingGrams: Double? = null,
    val caloriesPerServing: Double? = null,
    val proteinPerServing: Double? = null,
    val carbsPerServing: Double? = null,
    val fatPerServing: Double? = null,
    val incompleteEnergy: Boolean = false,
    val datasetVersion: String? = null,
    val matchScore: Double = 0.0,
) {
    val displayCalories: Int get() = (caloriesPerServing ?: 0.0).let { round(it).toInt() }

    companion object {
        fun fromOff(hit: OpenFoodFactsService.SearchHit): DatabaseSearchResult {
            val grams = hit.servingGrams ?: 100.0
            val scale = grams / 100.0
            fun p100(v: Double?) = v?.let { it * scale }
            return DatabaseSearchResult(
                sourceKind = NutrientSourceKind.OPEN_FOOD_FACTS,
                sourceId = hit.barcode,
                // hit.name is the plain product name; the brand rides in the
                // subtitle badge — joining them here double-prints "Aldi Aldi…".
                name = hit.name,
                brand = hit.brand,
                servingGrams = grams,
                caloriesPerServing = p100(hit.caloriesPer100g),
                proteinPerServing = p100(hit.proteinPer100g),
                carbsPerServing = p100(hit.carbsPer100g),
                fatPerServing = p100(hit.fatPer100g),
                incompleteEnergy = hit.incompleteEnergy,
                datasetVersion = "openfoodfacts-live",
                matchScore = hit.score,
            )
        }

        fun fromUsda(candidate: app.chompass.models.GroundingCandidate): DatabaseSearchResult {
            val grams = candidate.servingSizeGrams ?: 100.0
            val scale = grams / 100.0
            fun p100(v: Double?) = v?.let { it * scale }
            return DatabaseSearchResult(
                sourceKind = NutrientSourceKind.USDA,
                sourceId = candidate.sourceId,
                name = candidate.displayName,
                servingGrams = grams,
                caloriesPerServing = p100(candidate.caloriesPer100g),
                proteinPerServing = p100(candidate.proteinPer100g),
                carbsPerServing = p100(candidate.carbsPer100g),
                fatPerServing = p100(candidate.fatPer100g),
                incompleteEnergy = candidate.incompleteEnergy,
                datasetVersion = candidate.datasetVersion,
                matchScore = candidate.score,
            )
        }

        fun fromSwiss(record: SwissFoodRecord, matchScore: Double): DatabaseSearchResult {
            val grams = 100.0
            return DatabaseSearchResult(
                sourceKind = NutrientSourceKind.SWISS,
                sourceId = record.id.toString(),
                name = record.name,
                lang = record.lang,
                servingGrams = grams,
                caloriesPerServing = record.calories,
                proteinPerServing = record.protein,
                carbsPerServing = record.carbs,
                fatPerServing = record.fat,
                incompleteEnergy = record.calories == null,
                matchScore = matchScore,
            )
        }
    }
}

/**
 * Multi-source food database search used by the Add Food "Search food" sheet.
 * Open Food Facts is a live API call (ODbL, query string only); USDA and Swiss
 * are read from the bundled offline SQLite indexes. Results are merged and
 * ranked by their source match score.
 */
class FoodDatabaseSearch(
    private val prefs: PreferencesStore,
    private val usda: UsdaFoodIndex,
    private val swiss: SwissFoodIndex,
) {
    /**
     * Serializes the offline SQLite queries (USDA + Swiss). They run in
     * parallel `async` blocks today; a shared mutex bounds concurrent
     * CursorWindow allocations and keeps the offline phase deterministic
     * under fast typing (Codeberg #26). `withLock` is cancellation-safe:
     * a cancelled search releases the lock and propagates.
     */
    private val offlineMutex = Mutex()

    enum class Source {
        OPEN_FOOD_FACTS,
        USDA,
        SWISS,
    }

    /** Max results per source; the merged list is capped at [limit]. */
    suspend fun search(
        query: String,
        sources: Set<Source>,
        limit: Int = 12,
    ): List<DatabaseSearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        // Phase logs ride logcat so a post-crash `adb logcat -d` shows which
        // source was in flight when the process died (Codeberg #26; logcat
        // survives process death). Same tag the per-source failure log uses.
        Log.i("FoodSearch", "search start '$q' sources=$sources")
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<DatabaseSearchResult>>>()
                // Each source is isolated: a failing source (backend outage,
                // DB hiccup) must never hide the other sources' results, and
                // cancellation always propagates (never swallowed).
                fun launch(label: String, block: suspend () -> List<DatabaseSearchResult>) {
                    jobs += async {
                        val t0 = SystemClock.elapsedRealtime()
                        try {
                            val results = block()
                            Log.d(
                                "FoodSearch",
                                "$label: ${results.size} hits for '$q' in " +
                                    "${SystemClock.elapsedRealtime() - t0} ms",
                            )
                            results
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("FoodSearch", "$label search failed for '$q'", e)
                            emptyList()
                        }
                    }
                }
                if (Source.OPEN_FOOD_FACTS in sources) {
                    launch("off") { offSearch(q) }
                }
                if (Source.USDA in sources) {
                    launch("usda") {
                        val rows = offlineMutex.withLock { usda.search(q, limit = 6) }
                        rows.map(DatabaseSearchResult::fromUsda)
                    }
                }
                if (Source.SWISS in sources) {
                    launch("swiss") {
                        val rows = offlineMutex.withLock { swiss.searchScored(q, limit = 6) }
                        rows.map { (rec, score) -> DatabaseSearchResult.fromSwiss(rec, score) }
                    }
                }
                val merged = jobs.flatMap { it.await() }
                    .map { it.withNormalizedScore() }
                    .sortedByDescending { it.matchScore }
                    .take(limit)
                Log.d("FoodSearch", "search end '$q': ${merged.size} results")
                merged
            }
        }
    }

    /**
     * Resolve a selected hit into a reviewable [FoodAnalysis] (full micronutrients):
     * OFF does a cached barcode lookup, USDA/Swiss scale the offline per-100g row.
     */
    suspend fun toAnalysis(result: DatabaseSearchResult): FoodAnalysis = when (result.sourceKind) {
        NutrientSourceKind.OPEN_FOOD_FACTS ->
            offToAnalysis(result.sourceId) { OpenFoodFactsService.lookupByCode(it, prefs) }
        NutrientSourceKind.USDA -> {
            val record = result.sourceId.toLongOrNull()
                ?.let { usda.getByFdcId(it) }
                ?: return error("USDA food ${result.sourceId} not found in offline index")
            record.toFoodAnalysis(
                grams = result.servingGrams ?: 100.0,
                datasetVersion = usda.version(),
            )
        }
        NutrientSourceKind.SWISS -> {
            val record = result.sourceId.toLongOrNull()
                ?.let { swiss.getById(it) }
                ?: return error("Swiss food ${result.sourceId} not found in offline index")
            record.toFoodAnalysis(
                grams = result.servingGrams ?: 100.0,
                datasetVersion = swiss.version(),
            )
        }
        NutrientSourceKind.HISTORY,
        NutrientSourceKind.NUTRITION_LABEL,
        NutrientSourceKind.MODEL_ESTIMATE,
        -> error("Not a searchable database source: ${result.sourceKind}")
    }

    private suspend fun offSearch(query: String): List<DatabaseSearchResult> =
        withContext(Dispatchers.IO) {
            OpenFoodFactsService.search(query, limit = 6).map(DatabaseSearchResult::fromOff)
        }

    companion object {
        /**
         * Per-source ceilings used to normalize raw match scores onto a shared
         * 0..1 scale before merging. The three sources use different score
         * formulas (OFF: overlap*2 + length/energy bonus; USDA: base + overlap
         * + category/form adjustments; Swiss: base + overlap + language bonus),
         * so raw scores are not directly comparable. Values are the approximate
         * top raw score each source can produce for a typical multi-token query.
         */
        private val SCORE_CEILINGS = mapOf(
            NutrientSourceKind.OPEN_FOOD_FACTS to 20.0,
            NutrientSourceKind.USDA to 28.0,
            NutrientSourceKind.SWISS to 20.0,
        )

        /** Normalize one source's raw match score onto the shared 0..1 scale. */
        internal fun normalizedMatchScore(sourceKind: NutrientSourceKind, rawScore: Double): Double {
            val ceil = SCORE_CEILINGS[sourceKind] ?: return rawScore
            return (rawScore / ceil).coerceIn(0.0, 1.0)
        }
    }
}

private fun DatabaseSearchResult.withNormalizedScore(): DatabaseSearchResult =
    copy(matchScore = FoodDatabaseSearch.normalizedMatchScore(sourceKind, matchScore))

/**
 * OFF branch of [FoodDatabaseSearch.toAnalysis], extracted so the lookup can be
 * injected in unit tests (the Robolectric suite has no network seam). The
 * source id is an OFF product code straight from OFF's search API and must not
 * be re-validated through the scanner normalizer — OFF codes are not guaranteed
 * to satisfy the GTIN check-digit / digit-shape rules — so the branch uses
 * [OpenFoodFactsService.lookupByCode] (no normalizer gate).
 */
internal suspend fun offToAnalysis(
    sourceId: String,
    lookup: suspend (String) -> FoodAnalysis,
): FoodAnalysis = lookup(sourceId)
