package app.chompass.ui.home

import androidx.annotation.StringRes
import app.chompass.R
import app.chompass.services.grounding.FoodSuggestion
import app.chompass.services.grounding.SuggestionKind

/**
 * The labelled groups the Add Food list is divided into. One group per source
 * the rows can come from, so the same list can carry the user's own foods and
 * the food databases without tabs deciding which of them the user gets to see.
 */
enum class AddFoodGroup(@StringRes val labelRes: Int) {
    RECENTS(R.string.saved_meals_tab_recents),
    FREQUENT(R.string.saved_meals_tab_frequent),
    FAVORITES(R.string.saved_meals_tab_favorites),
    RECIPES(R.string.saved_meals_tab_recipes),
    DATABASES(R.string.food_search_sheet_title),
}

/** One labelled run of rows in the Add Food list. Never empty. */
data class AddFoodSection(val group: AddFoodGroup, val rows: List<FoodSuggestion>)

/**
 * [SuggestionKind] is already mutually exclusive — a favorited food is
 * FAVORITE however often it was logged, and the rest split on log count — so
 * grouping on it gives sections that never repeat the same food.
 */
private fun SuggestionKind.group(): AddFoodGroup = when (this) {
    SuggestionKind.RECENT -> AddFoodGroup.RECENTS
    SuggestionKind.FREQUENT -> AddFoodGroup.FREQUENT
    SuggestionKind.FAVORITE -> AddFoodGroup.FAVORITES
    SuggestionKind.RECIPE -> AddFoodGroup.RECIPES
    SuggestionKind.DATABASE -> AddFoodGroup.DATABASES
}

/**
 * Split a flat suggestion list into its labelled sections, preserving the
 * incoming order inside each one.
 *
 * Sections are ordered by their best row's score so the ranker's promise
 * survives the grouping: whichever source the top match came from, its section
 * is the first one under the field. With no query every score is zero and the
 * tie-break puts them in declaration order (recents, frequent, favorites,
 * recipes, databases). The score bands in FoodSuggestionRanker keep every
 * local section above the database one either way.
 */
internal fun groupSuggestions(suggestions: List<FoodSuggestion>): List<AddFoodSection> =
    suggestions.groupBy { it.kind.group() }
        .map { (group, rows) -> AddFoodSection(group, rows) }
        .sortedWith(
            compareByDescending<AddFoodSection> { section -> section.rows.maxOf { it.score } }
                .thenBy { it.group.ordinal },
        )
