package app.chompass.data

import app.chompass.models.Recipe
import kotlinx.coroutines.flow.Flow

/**
 * Recipes (composable multi-ingredient saved meals) — a single JSON blob,
 * not month-bucketed like [foodEntriesImpl], since the number of saved
 * recipes is expected to stay small (mirrors the favorites storage shape).
 */
internal val PreferencesStore.recipesImpl: Flow<List<Recipe>>
    get() = listPref(Keys.RECIPES, Recipe.serializer())

internal suspend fun PreferencesStore.setRecipesImpl(recipes: List<Recipe>) =
    setListPref(Keys.RECIPES, Recipe.serializer(), recipes)
