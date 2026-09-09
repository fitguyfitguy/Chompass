package app.chompass.services.grounding

import app.chompass.models.FoodEntry
import app.chompass.models.NutrientSourceKind
import app.chompass.models.Recipe

/** Where a suggestion came from. Also drives the row's badge and tie-break rank. */
enum class SuggestionKind {
    RECENT,
    FREQUENT,
    FAVORITE,
    RECIPE,
    DATABASE,
}

/**
 * One row in the unified Add Food suggestion list: a food the user already
 * logged or favorited, one of their recipes, or a hit from the bundled /
 * remote food databases.
 *
 * The "Analyze this text with AI" row is deliberately *not* a member. The
 * ranker returns matches only; the sheet appends the analyze affordance. That
 * keeps ranking tests about ranking, and keeps the AI-disabled build from
 * needing a branch inside the ranker.
 */
sealed interface FoodSuggestion {
    /** Stable identity: dedup key and LazyColumn key. */
    val key: String
    val name: String
    val score: Double
    val kind: SuggestionKind

    data class SavedFood(
        val template: FoodEntry,
        override val kind: SuggestionKind,
        val logCount: Int,
        val daysSince: Long,
        override val score: Double,
    ) : FoodSuggestion {
        override val key: String get() = "saved:${template.favoriteKey}"
        override val name: String get() = template.name
    }

    data class SavedRecipe(
        val recipe: Recipe,
        override val score: Double,
    ) : FoodSuggestion {
        override val key: String get() = "recipe:${recipe.id}"
        override val name: String get() = recipe.name
        override val kind: SuggestionKind get() = SuggestionKind.RECIPE
    }

    data class DatabaseHit(
        val result: DatabaseSearchResult,
        override val score: Double,
    ) : FoodSuggestion {
        override val key: String get() = "db:${result.sourceKind}:${result.sourceId}"
        override val name: String get() = result.name
        override val kind: SuggestionKind get() = SuggestionKind.DATABASE
    }
}

/**
 * Tie-break order when two suggestions score identically. Local sources first
 * (the user's own foods carry their real portions), then offline databases,
 * then the network one. Purely for determinism — the score already separates
 * the common cases.
 */
internal fun FoodSuggestion.sourceRank(): Int = when (this) {
    is FoodSuggestion.SavedFood -> 0
    is FoodSuggestion.SavedRecipe -> 1
    is FoodSuggestion.DatabaseHit -> when (result.sourceKind) {
        NutrientSourceKind.USDA -> 2
        NutrientSourceKind.SWISS -> 3
        else -> 4
    }
}
