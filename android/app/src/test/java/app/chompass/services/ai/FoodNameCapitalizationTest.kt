package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AI food-name auto-capitalization (Codeberg #7): LLM names render polished
 * and consistent with manually entered foods — first letter of the name and
 * of every non-connector word, EN + DE minor words kept low, acronyms kept.
 */
class FoodNameCapitalizationTest {
    @Test
    fun capitalizesEnglishFoodName_withMinorWordsLow() {
        assertEquals(
            "Grilled Chicken Breast with Rice",
            capitalizeAiFoodName("grilled chicken breast with rice"),
        )
        assertEquals(
            "Scrambled Eggs and Bacon",
            capitalizeAiFoodName("scrambled eggs and bacon"),
        )
        assertEquals(
            "Oatmeal with Banana and Honey",
            capitalizeAiFoodName("oatmeal with banana and honey"),
        )
    }

    @Test
    fun capitalizesGermanName_nounsUp_connectorsLow() {
        assertEquals(
            "Hähnchen mit Reis und Gemüse",
            capitalizeAiFoodName("hähnchen mit reis und gemüse"),
        )
        assertEquals(
            "Spaghetti Carbonara",
            capitalizeAiFoodName("spaghetti carbonara"),
        )
    }

    @Test
    fun keepsShortForms_andAcronyms() {
        assertEquals("BBQ Ribs", capitalizeAiFoodName("bbq ribs"))
        assertEquals("BBQ Pulled Pork Sandwich", capitalizeAiFoodName("BBQ pulled pork sandwich"))
        assertEquals("V8 Juice", capitalizeAiFoodName("v8 juice"))
    }

    @Test
    fun keepsAlreadyCapitalizedBrands() {
        assertEquals("Coca-Cola", capitalizeAiFoodName("Coca-Cola"))
        assertEquals("McDonald's Fries", capitalizeAiFoodName("McDonald's fries"))
    }

    @Test
    fun handlesPunctuationAndNumbers() {
        // Hyphenated words capitalize their first letter ("3-Cheese").
        assertEquals("3-Cheese Pizza", capitalizeAiFoodName("3-cheese pizza"))
        assertEquals("Orange Juice (Fresh)", capitalizeAiFoodName("orange juice (fresh)"))
        assertEquals("Protein Shake 2.0", capitalizeAiFoodName("protein shake 2.0"))
        assertEquals("Extra-virgin Olive Oil", capitalizeAiFoodName("extra-virgin olive oil"))
    }

    @Test
    fun trimsAndHandlesEmpty() {
        assertEquals("", capitalizeAiFoodName(""))
        assertEquals("", capitalizeAiFoodName("   "))
        assertEquals("Soup", capitalizeAiFoodName("  soup  "))
    }

    @Test
    fun parseFood_appliesCapitalization() {
        val food = FoodJsonParser.parseFood(
            """{"name":"greek yogurt with berries","calories":150,"protein":9,"carbs":18,"fat":4}""",
        )
        assertEquals("Greek Yogurt with Berries", food.name)
    }

    @Test
    fun parseRecognition_capitalizesMealNameAndComponents() {
        val result = FoodJsonParser.parseRecognition(
            """
            {"meal_name":"bacon and egg breakfast","components":[
              {"name":"fried egg","estimated_grams":50},
              {"name":"whole grain toast","estimated_grams":28}
            ]}
            """.trimIndent(),
        )
        assertEquals("Bacon and Egg Breakfast", result.mealName)
        assertEquals("Fried Egg", result.components[0].name)
        assertEquals("Whole Grain Toast", result.components[1].name)
    }

    @Test
    fun parseLabel_capitalizesName() {
        val label = FoodJsonParser.parseLabel(
            """{"name":"low fat vanilla yogurt","calories_per_100g":63,"protein_per_100g":5,
                "carbs_per_100g":8,"fat_per_100g":1,"serving_size_grams":150}""",
        )
        assertEquals("Low Fat Vanilla Yogurt", label.name)
    }
}
