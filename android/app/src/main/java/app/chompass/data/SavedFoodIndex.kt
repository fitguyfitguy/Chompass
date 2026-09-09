package app.chompass.data

import app.chompass.models.FoodEntry
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
): List<SavedFoodIndexEntry> {
    val favoriteKeys = favorites.mapNotNull { it.favoriteKey.takeIf(String::isNotEmpty) }.toSet()
    val counts = mutableMapOf<String, Int>()
    val templates = LinkedHashMap<String, FoodEntry>()
    for (entry in entries.sortedByDescending { it.timestamp }) {
        val key = entry.favoriteKey
        if (key.isEmpty()) continue
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
