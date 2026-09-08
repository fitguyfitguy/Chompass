package app.chompass.debug

import android.util.Log
import app.chompass.AppContainer
import app.chompass.services.mealie.MealieClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * Debug-only Mealie import device pass. Hits a real HTTP host (mock or LAN),
 * upserts three recipes, logs one named meal, re-imports, then deletes the
 * fixtures so the diary is left as it was.
 */
object MealieImportDebugLauncher {
    const val TAG = "FudMealieTest"
    const val DEFAULT_URL = "http://127.0.0.1:9876"
    const val DEFAULT_TOKEN = "chompass-mealie-test"

    fun launchIfRequested(
        scope: CoroutineScope,
        container: AppContainer,
        url: String,
        token: String,
    ) {
        scope.launch {
            runCatching { run(container, url, token) }
                .onFailure { Log.e(TAG, "FAIL ${it.message}", it) }
        }
    }

    private suspend fun run(container: AppContainer, rawUrl: String, rawToken: String) {
        val url = rawUrl.ifBlank { DEFAULT_URL }
        val token = rawToken.ifBlank { DEFAULT_TOKEN }
        val allowInsecure = container.prefs.allowInsecureHttp.first()
        Log.i(TAG, "START url=$url")

        val listed = withContext(Dispatchers.IO) {
            MealieClient.listRecipes(url, token, allowInsecure)
        }
        Log.i(TAG, "listed n=${listed.size} slugs=${listed.map { it.slug }}")
        check(listed.size >= 3) { "need ≥3 recipes, got ${listed.size}" }
        val slugs = listed.take(3).map { it.slug }

        val mapped = withContext(Dispatchers.IO) {
            slugs.map { slug ->
                MealieClient.getRecipe(url, token, slug, allowInsecure)
                    ?: error("detail null for $slug")
            }
        }
        mapped.forEach { r ->
            Log.i(
                TAG,
                "mapped ${r.name} source=${r.source} kcal=${r.totalCalories} " +
                    "ings=${r.ingredients.size} named=${r.logsAsNamedMeal}",
            )
            check(r.logsAsNamedMeal) { "${r.name} is not a named-meal import" }
        }

        val beforeIds = container.recipeRepository.recipes.first().map { it.id }.toSet()
        container.recipeRepository.upsertRecipes(mapped)
        val afterFirst = container.recipeRepository.recipes.first()
        val firstIds = afterFirst.map { it.id }.toSet()
        check(mapped.all { it.id in firstIds }) { "imported ids missing after upsert" }
        Log.i(TAG, "imported ${mapped.size} recipes=${mapped.map { it.name }}")

        val recipe = mapped.first()
        val ids = container.recipeRepository.logRecipe(recipe, Instant.now())
        check(ids.size == 1) { "named meal must log one row, got ${ids.size}" }
        val today = LocalDate.now()
        val logged = container.foodRepository.entriesForDate(today).first()
            .filter { it.id in ids.toSet() }
        check(logged.size == 1) { "today diary missing logged row" }
        val entry = logged.single()
        check(entry.name == recipe.name) { "logged name ${entry.name}" }
        check(entry.recipeLogId == null) { "named meal must not set recipeLogId" }
        check(entry.constituents.size == recipe.ingredients.size) {
            "constituents ${entry.constituents.size}"
        }
        check(entry.calories == recipe.totalCalories) {
            "kcal ${entry.calories} vs ${recipe.totalCalories}"
        }
        Log.i(
            TAG,
            "logged id=${entry.id} name=${entry.name} kcal=${entry.calories} " +
                "constituents=${entry.constituents.map { it.name }}",
        )

        container.recipeRepository.upsertRecipes(mapped)
        val afterSecond = container.recipeRepository.recipes.first().map { it.id }.toSet()
        check(afterSecond == firstIds) {
            "re-import duplicated recipes first=${firstIds.size} second=${afterSecond.size}"
        }
        Log.i(TAG, "reimport count_unchanged n=${afterSecond.size}")

        container.foodRepository.deleteEntry(entry)
        val leftover = mapped.filter { it.id !in beforeIds }
        leftover.forEach { container.recipeRepository.deleteRecipe(it) }
        Log.i(TAG, "PASS cleaned recipes=${leftover.size} log=1")
    }
}
