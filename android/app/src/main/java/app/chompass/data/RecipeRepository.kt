package app.chompass.data

import app.chompass.models.Recipe
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * CRUD + logging for [Recipe] (composable multi-ingredient saved meals).
 * A parallel entity to single-item Favorites in [FoodRepository] — recipes
 * are never favorited/duplicated through that path, and logging a recipe
 * writes one [app.chompass.models.FoodEntry] per ingredient via
 * [FoodRepository.addEntry] so bucket persistence, Health Connect sync, and
 * the first-log review prompt all keep working unchanged.
 */
class RecipeRepository(
    private val prefs: PreferencesStore,
    private val foodRepository: FoodRepository,
    private val sync: app.chompass.sync.SyncRepository? = null,
) {
    val recipes: Flow<List<Recipe>> = prefs.recipes

    suspend fun saveRecipe(recipe: Recipe) {
        prefs.editListPref(Keys.RECIPES, Recipe.serializer()) { current ->
            val next = current.toMutableList()
            val idx = next.indexOfFirst { it.id == recipe.id }
            if (idx >= 0) next[idx] = recipe else next.add(recipe)
            next
        }
        sync?.touch(recipe.id, "recipe")
    }

    /** Upsert by id. Used by Mealie re-import so slugs replace instead of duplicating. */
    suspend fun upsertRecipes(incoming: List<Recipe>) {
        if (incoming.isEmpty()) return
        prefs.editListPref(Keys.RECIPES, Recipe.serializer()) { current ->
            val next = current.toMutableList()
            for (recipe in incoming) {
                val idx = next.indexOfFirst { it.id == recipe.id }
                if (idx >= 0) next[idx] = recipe else next.add(recipe)
            }
            next
        }
        for (recipe in incoming) sync?.touch(recipe.id, "recipe")
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        prefs.editListPref(Keys.RECIPES, Recipe.serializer()) { current ->
            current.filterNot { it.id == recipe.id }
        }
        sync?.tombstone(recipe.id, "recipe")
    }

    suspend fun moveRecipe(from: Int, to: Int) {
        prefs.editListPref(Keys.RECIPES, Recipe.serializer()) { current ->
            val list = current.toMutableList()
            if (from !in list.indices) return@editListPref list
            val item = list.removeAt(from)
            list.add(to.coerceIn(0, list.size), item)
            list
        }
    }

    /**
     * Logs a recipe. Mealie imports (`source` starts with `mealie:`) write one
     * named [app.chompass.models.FoodEntry] with constituents. Hand-built
     * recipes still explode to one diary row per ingredient.
     */
    suspend fun logRecipe(
        recipe: Recipe,
        logDate: Instant,
        mealType: String = recipe.mealType,
        planned: Boolean = false,
    ): List<UUID> {
        if (recipe.logsAsNamedMeal) {
            val entry = recipe.toNamedMealEntry(logDate, mealType).copy(planned = planned)
            foodRepository.addEntries(listOf(entry))
            return listOf(entry.id)
        }
        val recipeLogId = UUID.randomUUID()
        val entries = recipe.ingredients.map { it.toFoodEntry(logDate, mealType, recipeLogId).copy(planned = planned) }
        // One batched DataStore edit instead of one full-file write per ingredient.
        foodRepository.addEntries(entries)
        return entries.map { it.id }
    }
}
