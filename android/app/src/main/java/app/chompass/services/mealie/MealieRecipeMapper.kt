package app.chompass.services.mealie

import app.chompass.models.MealType
import app.chompass.models.Recipe
import app.chompass.models.RecipeIngredient
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Mealie REST recipe JSON → Chompass [Recipe]. Pure; no network.
 *
 * Ingredients keep culinary display text and zero macros. Recipe-level
 * nutrition is copied only when Mealie sends parseable numbers.
 */
object MealieRecipeMapper {
    fun recipeIdForSlug(slug: String): UUID =
        UUID.nameUUIDFromBytes("${Recipe.MEALIE_SOURCE_PREFIX}$slug".toByteArray(StandardCharsets.UTF_8))

    fun sourceForSlug(slug: String): String = "${Recipe.MEALIE_SOURCE_PREFIX}$slug"

    fun fromDetailJson(body: String): Recipe? {
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return fromDetail(obj)
    }

    fun fromDetail(obj: JSONObject): Recipe? {
        val slug = obj.str("slug")?.trim().orEmpty()
        val name = obj.str("name")?.trim().orEmpty()
        if (slug.isEmpty() || name.isEmpty()) return null
        val ingredients = parseIngredients(obj.optJSONArray("recipeIngredient") ?: obj.optJSONArray("recipe_ingredient"))
        val nutrition = obj.optJSONObject("nutrition")
        return Recipe(
            id = recipeIdForSlug(slug),
            name = name,
            mealType = MealType.OTHER.id,
            ingredients = ingredients,
            source = sourceForSlug(slug),
            nutritionCalories = nutrition.number("calories")?.toInt(),
            nutritionProtein = nutrition.number("proteinContent", "protein_content"),
            nutritionCarbs = nutrition.number("carbohydrateContent", "carbohydrate_content"),
            nutritionFat = nutrition.number("fatContent", "fat_content"),
            nutritionFiber = nutrition.number("fiberContent", "fiber_content"),
            nutritionSugar = nutrition.number("sugarContent", "sugar_content"),
            nutritionSodium = nutrition.number("sodiumContent", "sodium_content"),
        )
    }

    fun parseSummaryList(body: String): List<MealieRecipeSummary> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("items") ?: root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<MealieRecipeSummary>(items.length())
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val slug = obj.str("slug")?.trim().orEmpty()
            val name = obj.str("name")?.trim().orEmpty()
            if (slug.isEmpty() || name.isEmpty()) continue
            out += MealieRecipeSummary(slug = slug, name = name)
        }
        return out
    }

    private fun parseIngredients(arr: JSONArray?): List<RecipeIngredient> {
        if (arr == null) return emptyList()
        val out = ArrayList<RecipeIngredient>(arr.length())
        for (i in 0 until arr.length()) {
            val display = when (val el = arr.opt(i)) {
                is String -> el.trim()
                is JSONObject -> ingredientDisplay(el)
                else -> ""
            }
            if (display.isEmpty()) continue
            out += RecipeIngredient(
                name = display,
                baseCalories = 0,
                baseProtein = 0.0,
                baseCarbs = 0.0,
                baseFat = 0.0,
            )
        }
        return out
    }

    internal fun ingredientDisplay(obj: JSONObject): String {
        obj.str("display")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.str("originalText", "original_text")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val quantity = obj.optDouble("quantity", Double.NaN).takeIf { !it.isNaN() && it != 0.0 }
            ?.let { formatQuantity(it) }
        val unit = obj.optJSONObject("unit")?.str("name", "abbreviation")?.trim()
        val food = obj.optJSONObject("food")?.str("name")?.trim()
            ?: obj.optJSONObject("referencedRecipe")?.str("name")?.trim()
            ?: obj.optJSONObject("referenced_recipe")?.str("name")?.trim()
        val note = obj.str("note")?.trim()
        return listOfNotNull(quantity, unit, food, note)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .trim()
    }

    internal fun parseNutritionNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = NUTRITION_NUMBER.find(raw.trim()) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }

    private fun formatQuantity(value: Double): String {
        val asInt = value.toInt()
        return if (value == asInt.toDouble()) asInt.toString() else value.toString()
    }

    private fun JSONObject?.number(vararg keys: String): Double? {
        if (this == null) return null
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            when (value) {
                is Number -> {
                    val d = value.toDouble()
                    if (d.isFinite()) return d
                }
                is String -> parseNutritionNumber(value)?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.str(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = optString(key).trim()
            if (value.isNotEmpty() && value != "null") return value
        }
        return null
    }

    private val NUTRITION_NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")
}

data class MealieRecipeSummary(
    val slug: String,
    val name: String,
)
