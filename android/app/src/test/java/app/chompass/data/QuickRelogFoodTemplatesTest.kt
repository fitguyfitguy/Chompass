package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class QuickRelogFoodTemplatesTest {
    private fun entry(
        name: String,
        mealType: MealType = MealType.LUNCH,
        timestamp: Instant = Instant.parse("2024-06-01T12:00:00Z"),
        calories: Int = 100,
    ) = FoodEntry(
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = mealType,
    )

    @Test
    fun atLunch_mealMatchedBeforeDinnerFavorite() {
        val favorites = listOf(entry("Dinner Fav", MealType.DINNER, Instant.parse("2024-06-01T19:00:00Z")))
        val recents = listOf(
            entry("Lunch Bowl", MealType.LUNCH, Instant.parse("2024-06-01T12:00:00Z")),
            entry("Yogurt", MealType.SNACK, Instant.parse("2024-06-01T15:00:00Z")),
        )
        val out = quickRelogFoodTemplates(
            favorites, recents, emptyList(), currentMeal = MealType.LUNCH, limit = 6,
        )
        assertEquals(listOf("Lunch Bowl", "Yogurt", "Dinner Fav"), out.map { it.name })
    }

    @Test
    fun atLunch_favoriteLunchBeforeNonFavoriteLunch() {
        val favorites = listOf(entry("Fav Salad", MealType.LUNCH, Instant.parse("2024-06-01T11:00:00Z")))
        val recents = listOf(
            entry("Newer Sandwich", MealType.LUNCH, Instant.parse("2024-06-01T13:00:00Z")),
            entry("Fav Salad", MealType.LUNCH, Instant.parse("2024-06-01T12:30:00Z")),
        )
        val out = quickRelogFoodTemplates(
            favorites, recents, emptyList(), currentMeal = MealType.LUNCH, limit = 6,
        )
        // Favorite wins within meal-matched even when recent copy is newer;
        // merge keeps favorite payload first for shared key.
        assertEquals(listOf("Fav Salad", "Newer Sandwich"), out.map { it.name })
    }

    @Test
    fun atSnack_snacksThenRecentOthers() {
        val favorites = listOf(entry("Steak", MealType.DINNER, Instant.parse("2024-06-01T19:00:00Z")))
        val recents = listOf(
            entry("Apple", MealType.SNACK, Instant.parse("2024-06-01T16:00:00Z")),
            entry("Coffee", MealType.SNACK, Instant.parse("2024-06-01T10:00:00Z")),
            entry("Oatmeal", MealType.BREAKFAST, Instant.parse("2024-06-01T08:00:00Z")),
        )
        val out = quickRelogFoodTemplates(
            favorites, recents, emptyList(), currentMeal = MealType.SNACK, limit = 6,
        )
        assertEquals(listOf("Apple", "Coffee", "Steak", "Oatmeal"), out.map { it.name })
    }

    @Test
    fun dedupesByFavoriteKey_prefersFavoritePayload() {
        val favorites = listOf(entry("Shared", MealType.LUNCH, calories = 200))
        val recents = listOf(entry("Shared", MealType.LUNCH, calories = 150))
        val frequents = listOf(entry("Shared", MealType.LUNCH, calories = 100))
        val out = quickRelogFoodTemplates(
            favorites, recents, frequents, currentMeal = MealType.LUNCH, limit = 6,
        )
        assertEquals(1, out.size)
        assertEquals(200, out.single().calories)
    }

    @Test
    fun respectsLimit() {
        val favorites = listOf(
            entry("A", MealType.LUNCH),
            entry("B", MealType.LUNCH),
            entry("C", MealType.LUNCH),
        )
        val out = quickRelogFoodTemplates(
            favorites, emptyList(), emptyList(), currentMeal = MealType.LUNCH, limit = 2,
        )
        assertEquals(2, out.size)
    }

    @Test
    fun frequentFillsAfterRecents() {
        val recents = listOf(entry("Recent", MealType.LUNCH, Instant.parse("2024-06-01T12:00:00Z")))
        val frequents = listOf(entry("Freq", MealType.LUNCH, Instant.parse("2024-05-01T12:00:00Z")))
        val out = quickRelogFoodTemplates(
            emptyList(), recents, frequents, currentMeal = MealType.LUNCH, limit = 6,
        )
        assertEquals(listOf("Recent", "Freq"), out.map { it.name })
    }
}
