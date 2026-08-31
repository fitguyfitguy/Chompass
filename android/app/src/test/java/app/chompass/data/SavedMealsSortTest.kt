package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SavedMealsSortTest {
    private fun entry(
        name: String,
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
        mealType = MealType.LUNCH.id,
    )

    private val oats = entry("Oats", Instant.parse("2024-06-01T08:00:00Z"), calories = 150)
    private val oatmeal = entry("Oatmeal", Instant.parse("2024-06-01T10:00:00Z"), calories = 400)
    private val apple = entry("apple", Instant.parse("2024-06-01T14:00:00Z"), calories = 200)
    private val avocado = entry("Avocado", Instant.parse("2024-06-01T09:00:00Z"), calories = 200)
    private val banana = entry("Banana", Instant.parse("2024-06-01T16:00:00Z"), calories = 90)
    private val unique = listOf(oats, oatmeal, apple, avocado, banana)

    @Test
    fun recentKeepsNewestFirst() {
        val out = sortHistoryTemplates(unique, SavedMealsSort.RECENT)
        assertEquals(listOf("Banana", "apple", "Oatmeal", "Avocado", "Oats"), out.map { it.name })
    }

    @Test
    fun nameIsCaseInsensitive() {
        val out = sortHistoryTemplates(unique, SavedMealsSort.NAME)
        assertEquals(listOf("apple", "Avocado", "Banana", "Oatmeal", "Oats"), out.map { it.name })
    }

    @Test
    fun sizeIsKcalDescThenName() {
        val out = sortHistoryTemplates(unique, SavedMealsSort.SIZE)
        assertEquals(listOf("Oatmeal", "apple", "Avocado", "Oats", "Banana"), out.map { it.name })
    }

    @Test
    fun searchFilterThenSortIsIndependent() {
        val filtered = filterHistoryTemplates(unique, "oat")
        assertEquals(setOf("Oats", "Oatmeal"), filtered.map { it.name }.toSet())
        assertEquals(
            listOf("Oatmeal", "Oats"),
            sortHistoryTemplates(filtered, SavedMealsSort.SIZE).map { it.name },
        )
        assertEquals(
            listOf("Oatmeal", "Oats"),
            sortHistoryTemplates(filtered, SavedMealsSort.NAME).map { it.name },
        )
        assertEquals(
            listOf("Oatmeal", "Oats"),
            sortHistoryTemplates(filtered, SavedMealsSort.RECENT).map { it.name },
        )
    }

    @Test
    fun blankSearchReturnsInput() {
        assertEquals(unique, filterHistoryTemplates(unique, "  "))
    }

    @Test
    fun fromPrefFallsBackToRecent() {
        assertEquals(SavedMealsSort.NAME, SavedMealsSort.fromPref("name"))
        assertEquals(SavedMealsSort.SIZE, SavedMealsSort.fromPref("SIZE"))
        assertEquals(SavedMealsSort.RECENT, SavedMealsSort.fromPref("nope"))
    }
}
