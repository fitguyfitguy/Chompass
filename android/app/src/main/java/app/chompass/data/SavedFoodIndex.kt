package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.NutrientSourceKind
import app.chompass.services.grounding.FoodSuggestion
import app.chompass.services.grounding.QueryNormalizer
import app.chompass.services.grounding.SuggestionKind
import java.time.Instant

/**
 * One food in the Add Food suggestion index: a diary/favorite template plus the
 * signals the ranker needs, with the expensive text work done once at build
 * time. The whole index is rescored on every keystroke, so re-tokenizing each
 * name there would dominate the cost.
 */
data class SavedFoodIndexEntry(
    val template: FoodEntry,
    val kind: SuggestionKind,
    val logCount: Int,
    val lastLogged: Instant,
    val normalizedName: String,
    val nameTokens: Set<String>,
)

/**
 * Render this index entry as a suggestion row. Score is left at zero: with no
 * query there is nothing to rank against, and the caller supplies the ordering
 * (newest, most-logged, favorites) instead.
 */
fun SavedFoodIndexEntry.asSuggestion(): FoodSuggestion.SavedFood = FoodSuggestion.SavedFood(
    template = template,
    kind = kind,
    logCount = logCount,
    daysSince = 0,
    score = 0.0,
)

/**
 * Collapse diary rows and favorites into one suggestion index, newest template
 * per [FoodEntry.favoriteKey] — the same identity every other saved-food
 * surface uses (see [recentFoodTemplates] / [frequentFoodGroups]).
 *
 * A food that is favorited is marked [SuggestionKind.FAVORITE] regardless of
 * how often it was logged; the rest split on log count so the row can say
 * "Recent" vs "Frequent" without a second pass.
 *
 * Pure so it can be tested without Android, mirroring the pure helpers beside
 * [quickRelogRows].
 */
internal fun buildSavedFoodIndex(
    entries: List<FoodEntry>,
    favorites: List<FoodEntry>,
    frequentThreshold: Int = 3,
    hiddenKeys: Set<String> = emptySet(),
): List<SavedFoodIndexEntry> {
    val favoriteKeys = favorites.mapNotNull { it.favoriteKey.takeIf(String::isNotEmpty) }.toSet()
    val counts = mutableMapOf<String, Int>()
    val templates = LinkedHashMap<String, FoodEntry>()
    for (entry in entries.sortedByDescending { it.timestamp }) {
        val key = entry.favoriteKey
        if (key.isEmpty()) continue
        if (key in hiddenKeys) continue
        counts[key] = (counts[key] ?: 0) + 1
        // Sorted newest-first, so the first template seen for a key is the
        // newest serving snapshot — what a re-log should offer.
        templates.putIfAbsent(key, entry)
    }
    // Favorites the user has never logged (or not inside the window) still
    // belong in the index; they are the whole point of favoriting.
    for (favorite in favorites) {
        val key = favorite.favoriteKey
        if (key.isEmpty()) continue
        if (key in hiddenKeys) continue
        templates.putIfAbsent(key, favorite)
    }
    return templates.map { (key, template) ->
        val count = counts[key] ?: 0
        val kind = when {
            key in favoriteKeys -> SuggestionKind.FAVORITE
            count >= frequentThreshold -> SuggestionKind.FREQUENT
            else -> SuggestionKind.RECENT
        }
        SavedFoodIndexEntry(
            template = template,
            kind = kind,
            logCount = count,
            lastLogged = template.timestamp,
            normalizedName = QueryNormalizer.normalizeQuery(template.name),
            nameTokens = QueryNormalizer.normalizeTokens(template.name).toSet(),
        )
    }
}

/**
 * Barcode identity behind this entry's template, when it has one: the OFF
 * product card's barcode, or an Open Food Facts grounding id (both carry the
 * same product namespace — see [matchesSavedBarcode]). USDA / Swiss grounding
 * ids are database row ids, deliberately not read as barcodes.
 */
internal val SavedFoodIndexEntry.barcodeIdentity: String?
    get() = template.productMetadata?.barcode
        ?: template.grounding?.takeIf { it.sourceKind == NutrientSourceKind.OPEN_FOOD_FACTS }?.sourceId

/**
 * Merge/dedupe candidates for the library browser (Codeberg #142 WP4):
 * entries that plausibly describe the same food under two identities,
 * grouped by equal normalized name — which catches "Oatmeal" / "Oatmeal (2)"
 * forks (the disambiguation suffix is quantity-like noise to the tokenizer)
 * and spelling variants the normalizer folds — or identical barcode (same
 * product logged under different names). Hidden entries participate: the
 * browser loads the full index anyway, and a hidden duplicate is exactly
 * what a merge cleans up.
 *
 * Pure and deterministic. Groups carry ≥2 members, ordered newest-template
 * first (equal timestamps fall back to alphabetical key order) so the
 * dialog's default "keep" is the freshest identity; groups are ordered by
 * total log count (noisiest first), then first member's normalized name.
 */
internal fun mergeCandidateGroups(index: List<SavedFoodIndexEntry>): List<List<SavedFoodIndexEntry>> {
    // Union-find over positions, joined by the two relations. Roots keep the
    // smallest index so component discovery order is input-order stable.
    val parent = IntArray(index.size) { it }
    fun find(i: Int): Int {
        var root = i
        while (parent[root] != root) root = parent[root]
        var current = i
        while (parent[current] != root) {
            val next = parent[current]
            parent[current] = root
            current = next
        }
        return root
    }
    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
    }
    val byName = HashMap<String, Int>()
    val byBarcode = HashMap<String, Int>()
    index.forEachIndexed { i, entry ->
        if (entry.normalizedName.isNotEmpty()) {
            val sameName = byName[entry.normalizedName]
            if (sameName == null) byName[entry.normalizedName] = i else union(sameName, i)
        }
        entry.barcodeIdentity?.let { barcode ->
            val sameBarcode = byBarcode[barcode]
            if (sameBarcode == null) byBarcode[barcode] = i else union(sameBarcode, i)
        }
    }
    val components = LinkedHashMap<Int, MutableList<SavedFoodIndexEntry>>()
    index.forEachIndexed { i, entry ->
        components.getOrPut(find(i)) { mutableListOf() }.add(entry)
    }
    return components.values
        .filter { it.size >= 2 }
        .map { group ->
            group.sortedWith(
                compareByDescending<SavedFoodIndexEntry> { it.lastLogged }.thenBy { it.template.favoriteKey },
            )
        }
        .sortedWith(
            compareByDescending<List<SavedFoodIndexEntry>> { group -> group.sumOf { it.logCount } }
                .thenBy { group -> group.first().normalizedName },
        )
}
