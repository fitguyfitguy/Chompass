package app.chompass.services.mealie

import app.chompass.models.FoodSource
import app.chompass.models.Recipe
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MealieRecipeMapperTest {
    @Test
    fun fromDetail_camelCaseNutritionAndDisplay() {
        val recipe = MealieRecipeMapper.fromDetailJson(
            """
            {
              "slug": "evening-salad",
              "name": "Evening Salad",
              "recipeIngredient": [
                {"display": "2 cups lettuce"},
                {"display": "1 tbsp olive oil"}
              ],
              "nutrition": {
                "calories": "350",
                "proteinContent": "12",
                "carbohydrateContent": "20.5",
                "fatContent": "22",
                "fiberContent": "8 g",
                "sugarContent": "4",
                "sodiumContent": "180mg"
              }
            }
            """.trimIndent(),
        )!!

        assertEquals("Evening Salad", recipe.name)
        assertEquals("mealie:evening-salad", recipe.source)
        assertEquals(MealieRecipeMapper.recipeIdForSlug("evening-salad"), recipe.id)
        assertEquals(listOf("2 cups lettuce", "1 tbsp olive oil"), recipe.ingredients.map { it.name })
        assertTrue(recipe.ingredients.all { it.baseCalories == 0 })
        assertEquals(350, recipe.nutritionCalories)
        assertEquals(12.0, recipe.nutritionProtein!!, 0.001)
        assertEquals(20.5, recipe.nutritionCarbs!!, 0.001)
        assertEquals(22.0, recipe.nutritionFat!!, 0.001)
        assertEquals(8.0, recipe.nutritionFiber!!, 0.001)
        assertEquals(4.0, recipe.nutritionSugar!!, 0.001)
        assertEquals(180.0, recipe.nutritionSodium!!, 0.001)
        assertEquals(350, recipe.totalCalories)
        assertTrue(recipe.logsAsNamedMeal)
    }

    @Test
    fun fromDetail_snakeCaseAndComposedIngredient() {
        val recipe = MealieRecipeMapper.fromDetailJson(
            """
            {
              "slug": "oats",
              "name": "Overnight oats",
              "recipe_ingredient": [
                {
                  "quantity": 50,
                  "unit": {"name": "g"},
                  "food": {"name": "rolled oats"},
                  "note": "toasted"
                }
              ],
              "nutrition": {
                "calories": "200 kcal",
                "protein_content": "8",
                "carbohydrate_content": "30",
                "fat_content": "4"
              }
            }
            """.trimIndent(),
        )!!

        assertEquals("50 g rolled oats toasted", recipe.ingredients.single().name)
        assertEquals(200, recipe.totalCalories)
        assertEquals(8.0, recipe.totalProtein, 0.001)
    }

    @Test
    fun fromDetail_missingNutritionStaysZero() {
        val recipe = MealieRecipeMapper.fromDetailJson(
            """
            {
              "slug": "plain-soup",
              "name": "Soup",
              "recipeIngredient": [{"display": "1 onion"}]
            }
            """.trimIndent(),
        )!!

        assertNull(recipe.nutritionCalories)
        assertEquals(0, recipe.totalCalories)
        assertEquals(0.0, recipe.totalProtein, 0.001)
        assertEquals("1 onion", recipe.ingredients.single().name)
    }

    @Test
    fun recipeIdForSlug_isStable() {
        val a = MealieRecipeMapper.recipeIdForSlug("evening-salad")
        val b = MealieRecipeMapper.recipeIdForSlug("evening-salad")
        val c = MealieRecipeMapper.recipeIdForSlug("other")
        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test
    fun parseSummaryList_readsItems() {
        val list = MealieRecipeMapper.parseSummaryList(
            """
            {"items":[{"slug":"a","name":"A"},{"slug":"b","name":"B"},{"slug":"","name":"skip"}]}
            """.trimIndent(),
        )
        assertEquals(listOf("a" to "A", "b" to "B"), list.map { it.slug to it.name })
    }

    @Test
    fun toNamedMealEntry_usesRecipeNutritionNotIngredientZeros() {
        val recipe = MealieRecipeMapper.fromDetailJson(
            """
            {
              "slug": "bowl",
              "name": "Rice bowl",
              "recipeIngredient": [{"display": "rice"}, {"display": "chicken"}],
              "nutrition": {"calories": "600", "proteinContent": "40", "carbohydrateContent": "70", "fatContent": "12"}
            }
            """.trimIndent(),
        )!!
        val entry = recipe.toNamedMealEntry(Instant.parse("2026-09-08T12:00:00Z"))
        assertEquals("Rice bowl", entry.name)
        assertEquals(600, entry.calories)
        assertEquals(40.0, entry.protein, 0.001)
        assertEquals(2, entry.constituents.size)
        assertEquals(listOf("rice", "chicken"), entry.constituents.map { it.name })
        assertTrue(entry.constituents.all { it.calories == 0 })
        assertNull(entry.recipeLogId)
        assertEquals(FoodSource.MANUAL, entry.source)
    }

    @Test
    fun handBuiltRecipe_doesNotLogAsNamedMeal() {
        val recipe = Recipe(name = "Hand", ingredients = emptyList())
        assertFalse(recipe.logsAsNamedMeal)
        assertNull(recipe.source)
        assertEquals(0, recipe.totalCalories)
    }

    @Test
    fun ingredientDisplay_prefersDisplayOverParts() {
        val obj = JSONObject(
            """{"display":"2 cups flour","quantity":2,"unit":{"name":"cups"},"food":{"name":"flour"}}""",
        )
        assertEquals("2 cups flour", MealieRecipeMapper.ingredientDisplay(obj))
    }

    @Test
    fun parseNutritionNumber_stripsUnits() {
        assertEquals(350.0, MealieRecipeMapper.parseNutritionNumber("350 kcal")!!, 0.001)
        assertEquals(1.5, MealieRecipeMapper.parseNutritionNumber("1,5 g")!!, 0.001)
        assertNull(MealieRecipeMapper.parseNutritionNumber(""))
        assertNull(MealieRecipeMapper.parseNutritionNumber(null))
    }
}
