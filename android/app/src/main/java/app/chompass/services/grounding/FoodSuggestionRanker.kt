package app.chompass.services.grounding

import app.chompass.data.SavedFoodIndexEntry
import app.chompass.models.NutrientSourceKind
import app.chompass.models.Recipe
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Ranks the unified Add Food suggestion list: the user's own foods and recipes
 * merged with food-database hits under one score, deduped across sources.
 *
 * Pure and clock-injected so the whole thing is testable on the JVM.
 */
object FoodSuggestionRanker {

    /**
     * Score floor every saved food starts from. It is what guarantees a
     * lexically matching saved food outranks *any* database hit, which is both
     * the behavior we want (the user's own entry carries their real portion and
     * merges identity on log) and the anti-flicker invariant: the local rows are
     * published first and the later database legs can never reorder them.
     *
     * The weights are chosen so the score bands cannot overlap:
     *
     *  - saved     0.55 + 0.60*lex + 0.15*recency + 0.10*freq, lex >= 0.375 -> [0.775, 1.40]
     *  - recipe    0.50 + 0.60*lex,                            lex >= 0.375 -> [0.725, 1.10]
     *  - database  0.40*lex + 0.30*matchScore                               -> [0.00,  0.70]
     *
     * Every local row therefore outranks every database row. If these are ever
     * retuned, keep the database ceiling below the recipe floor or the list will
     * reshuffle when the database legs land.
     */
    private const val SAVED_PRIOR = 0.55
    private const val RECIPE_PRIOR = 0.50

    /** Below this the name barely relates to the query; drop rather than pad the list. */
    internal const val MIN_LEXICAL = 0.375 // the "contains" tier, 3.0 / LEXICAL_MAX

    fun rank(
        query: String,
        saved: List<SavedFoodIndexEntry>,
        recipes: List<Recipe>,
        database: List<DatabaseSearchResult>,
        now: Instant = Instant.now(),
        limit: Int = 20,
        maxDatabase: Int = 8,
        minLexical: Double = MIN_LEXICAL,
    ): List<FoodSuggestion> {
        val q = QueryNormalizer.normalizeQuery(query)
        val tokens = QueryNormalizer.normalizeTokens(query)
        if (q.isEmpty() || tokens.isEmpty()) return emptyList()

        val savedHits = saved.mapNotNull { entry ->
            val lex = lexNorm(q, tokens, entry.normalizedName, entry.nameTokens)
            if (lex < minLexical) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(entry.lastLogged, now).coerceAtLeast(0)
            // Half-life ~30 days, matching ConfirmedHistorySearch's decay.
            val recency = exp(-ln(2.0) * days / 30.0)
            val frequency = if (entry.logCount > 1) min(1.0, 0.25 * ln(entry.logCount.toDouble())) else 0.0
            FoodSuggestion.SavedFood(
                template = entry.template,
                kind = entry.kind,
                logCount = entry.logCount,
                daysSince = days,
                // Lexical quality dominates deliberately: an exact match the
                // user logged four months ago must still beat a fresh prefix
                // match. Recency and frequency separate foods that match the
                // query equally well, they do not overturn a better match.
                score = SAVED_PRIOR + 0.60 * lex + 0.15 * recency + 0.10 * frequency,
            )
        }

        val recipeHits = recipes.mapNotNull { recipe ->
            val normalized = QueryNormalizer.normalizeQuery(recipe.name)
            val lex = lexNorm(q, tokens, normalized, QueryNormalizer.normalizeTokens(recipe.name).toSet())
            if (lex < minLexical) return@mapNotNull null
            FoodSuggestion.SavedRecipe(recipe = recipe, score = RECIPE_PRIOR + 0.60 * lex)
        }

        val databaseHits = database.mapNotNull { result ->
            val normalized = QueryNormalizer.normalizeQuery(result.name)
            val lex = lexNorm(q, tokens, normalized, QueryNormalizer.normalizeTokens(result.name).toSet())
            if (lex < minLexical) return@mapNotNull null
            // Re-score the name with the same lexical function so cross-source
            // ordering sits on one scale; the provider's own normalized score
            // rides along only as a confidence term (its per-source ceilings in
            // FoodDatabaseSearch.SCORE_CEILINGS are hand-tuned, not calibrated).
            FoodSuggestion.DatabaseHit(
                result = result,
                score = 0.40 * lex + 0.30 * result.matchScore.coerceIn(0.0, 1.0),
            )
        }

        // Score order before the cap, not after: dedupeDatabase returns the
        // offline rows ahead of the Open Food Facts ones regardless of score,
        // so truncating its output directly dropped the whole OFF leg whenever
        // the bundled databases alone filled maxDatabase.
        val bestDatabase = dedupeDatabase(databaseHits)
            .sortedByDescending { it.score }
            .take(maxDatabase)
        val ordered = (savedHits + recipeHits + bestDatabase)
            .sortedWith(
                compareByDescending<FoodSuggestion> { it.score }
                    .thenBy { it.sourceRank() }
                    .thenBy { it.name.lowercase() },
            )
        return dedupeByIdentity(ordered).take(limit)
    }

    private fun lexNorm(
        query: String,
        tokens: List<String>,
        normalizedName: String,
        nameTokens: Set<String>,
    ): Double =
        ConfirmedHistorySearch.lexicalScore(query, tokens, normalizedName, nameTokens) /
            ConfirmedHistorySearch.LEXICAL_MAX

    /**
     * Collapse the same real-world food appearing from several sources — the
     * search never deduped across providers, so "greek yogurt" arrived once from
     * USDA and again from Open Food Facts.
     *
     * The input is already in final order, so the first row for a normalized
     * name is the winner: a saved food beats a recipe beats a database row, and
     * among database rows the score (then [sourceRank]) decides.
     */
    private fun dedupeByIdentity(ordered: List<FoodSuggestion>): List<FoodSuggestion> {
        val seen = HashSet<String>()
        return ordered.filter { seen.add(QueryNormalizer.normalizeQuery(it.name)) }
    }

    /**
     * Open Food Facts routinely returns the same product under several
     * barcodes, so collapse brand+name before the cross-source pass. Prefer the
     * row that actually carries energy: an `incompleteEnergy` hit renders as
     * "— kcal" and is useless as a suggestion.
     */
    private fun dedupeDatabase(hits: List<FoodSuggestion.DatabaseHit>): List<FoodSuggestion.DatabaseHit> {
        val best = LinkedHashMap<String, FoodSuggestion.DatabaseHit>()
        val passthrough = mutableListOf<FoodSuggestion.DatabaseHit>()
        for (hit in hits) {
            // Barcode duplicates are an Open Food Facts problem. USDA and Swiss
            // rows that share a normalized brand+name are usually genuinely
            // different foods (raw vs cooked, dataset variants), so they skip
            // this pass — the cross-source pass still folds survivors together.
            if (hit.result.sourceKind != NutrientSourceKind.OPEN_FOOD_FACTS) {
                passthrough += hit
                continue
            }
            val key = QueryNormalizer.searchQuery(hit.result.brand, hit.result.name)
            val existing = best[key]
            if (existing == null || beats(hit, existing)) best[key] = hit
        }
        return passthrough + best.values
    }

    private fun beats(candidate: FoodSuggestion.DatabaseHit, incumbent: FoodSuggestion.DatabaseHit): Boolean {
        val candidateComplete = !candidate.result.incompleteEnergy
        val incumbentComplete = !incumbent.result.incompleteEnergy
        if (candidateComplete != incumbentComplete) return candidateComplete
        return candidate.score > incumbent.score
    }
}
