package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import org.codeberg.fitguy.nofud.models.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

/**
 * Recipes (composable multi-ingredient saved meals) — a single JSON blob,
 * not month-bucketed like [foodEntriesImpl], since the number of saved
 * recipes is expected to stay small (mirrors the favorites storage shape).
 */
internal val PreferencesStore.recipesImpl: Flow<List<Recipe>> get() = dataStore.data.map { prefs ->
    prefs[Keys.RECIPES]?.let {
        runCatching { json.decodeFromString(ListSerializer(Recipe.serializer()), it) }.getOrNull()
    } ?: emptyList()
}

internal suspend fun PreferencesStore.setRecipesImpl(recipes: List<Recipe>) {
    dataStore.edit { it[Keys.RECIPES] = json.encodeToString(ListSerializer(Recipe.serializer()), recipes) }
}
