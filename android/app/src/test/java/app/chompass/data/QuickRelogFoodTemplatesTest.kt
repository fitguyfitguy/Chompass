package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class QuickRelogFoodTemplatesTest {
    private fun entry(name: String, calories: Int = 100) = FoodEntry(
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = Instant.now(),
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH,
    )

    @Test
    fun prefersFavoritesThenRecentsThenFrequent() {
        val favorites = listOf(entry("Fav A"), entry("Shared"))
        val recents = listOf(entry("Recent B"), entry("Shared"))
        val frequents = listOf(entry("Freq C"), entry("Fav A"))
        val out = quickRelogFoodTemplates(favorites, recents, frequents, limit = 6)
        assertEquals(listOf("Fav A", "Shared", "Recent B", "Freq C"), out.map { it.name })
    }

    @Test
    fun respectsLimit() {
        val favorites = listOf(entry("A"), entry("B"), entry("C"))
        val out = quickRelogFoodTemplates(favorites, emptyList(), emptyList(), limit = 2)
        assertEquals(2, out.size)
    }
}
