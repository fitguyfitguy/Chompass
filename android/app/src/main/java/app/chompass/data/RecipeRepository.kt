package app.chompass.data

import app.chompass.models.MealType
import app.chompass.models.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val current = prefs.recipes.first().toMutableList()
        val idx = current.indexOfFirst { it.id == recipe.id }
        if (idx >= 0) current[idx] = recipe else current.add(recipe)
        prefs.setRecipes(current)
        sync?.touch(recipe.id, "recipe")
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        prefs.setRecipes(prefs.recipes.first().filterNot { it.id == recipe.id })
        sync?.tombstone(recipe.id, "recipe")
    }

    suspend fun moveRecipe(from: Int, to: Int) {
        val list = prefs.recipes.first().toMutableList()
        if (from !in list.indices) return
        val item = list.removeAt(from)
        val safeTo = to.coerceIn(0, list.size)
        list.add(safeTo, item)
        prefs.setRecipes(list)
    }

    /** Logs every ingredient as its own diary row, sharing a fresh [Recipe.recipeLogId]. */
    suspend fun logRecipe(recipe: Recipe, logDate: Instant, mealType: MealType = recipe.mealType): List<UUID> {
        val recipeLogId = UUID.randomUUID()
        val entries = recipe.ingredients.map { it.toFoodEntry(logDate, mealType, recipeLogId) }
        entries.forEach { foodRepository.addEntry(it) }
        return entries.map { it.id }
    }
}
