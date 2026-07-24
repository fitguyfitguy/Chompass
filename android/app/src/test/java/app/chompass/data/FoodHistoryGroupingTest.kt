package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FoodHistoryGroupingTest {

    @Test
    fun favoriteKey_ignoresCaloriesAndServing() {
        val a = entry("Chicken Breast", calories = 250, grams = 150.0, at = 1)
        val b = entry("Chicken Breast", calories = 333, grams = 200.0, at = 2)
        val c = entry("  Chicken Breast ", calories = 100, grams = 60.0, at = 3)
        assertEquals("chicken breast", a.favoriteKey)
        assertEquals(a.favoriteKey, b.favoriteKey)
        assertEquals(a.favoriteKey, c.favoriteKey)
    }

    @Test
    fun favoriteKey_differentNamesStayDistinct() {
        val grilled = entry("Grilled Chicken", calories = 250, grams = 150.0, at = 1)
        val fried = entry("Fried Chicken", calories = 250, grams = 150.0, at = 2)
        assertTrue(grilled.favoriteKey != fried.favoriteKey)
    }

    @Test
    fun recent_collapsesSameFoodKeepingNewestServing() {
        val older = entry("Oats", calories = 150, grams = 40.0, at = 1)
        val newer = entry("Oats", calories = 300, grams = 80.0, at = 2)
        val other = entry("Banana", calories = 90, grams = 120.0, at = 3)
        val result = recentFoodTemplates(listOf(older, newer, other), limit = 50)
        assertEquals(listOf(other.id, newer.id), result.map { it.id })
        assertEquals(80.0, result.first { it.name == "Oats" }.servingSizeGrams)
        assertEquals(300, result.first { it.name == "Oats" }.calories)
    }

    @Test
    fun frequent_groupsByNameAndUsesNewestTemplate() {
        val first = entry("Yogurt", calories = 100, grams = 100.0, at = 1)
        val second = entry("Yogurt", calories = 150, grams = 150.0, at = 2)
        val third = entry("Yogurt", calories = 200, grams = 200.0, at = 3)
        val banana = entry("Banana", calories = 90, grams = 120.0, at = 4)
        val groups = frequentFoodGroups(listOf(first, second, third, banana))
        assertEquals(2, groups.size)
        val yogurt = groups.first { it.name == "Yogurt" }
        assertEquals(3, yogurt.count)
        assertEquals(third.id, yogurt.template.id)
        assertEquals(200, yogurt.calories)
        assertEquals(200.0, yogurt.template.servingSizeGrams)
    }

    @Test
    fun recent_respectsLimitAfterDedupe() {
        val entries = (1..10).map { i ->
            entry("Food $i", calories = i * 10, grams = i * 10.0, at = i.toLong())
        } + entry("Food 1", calories = 999, grams = 999.0, at = 100)
        val result = recentFoodTemplates(entries, limit = 5)
        assertEquals(5, result.size)
        assertEquals(999, result.first().calories) // newest Food 1 wins
        assertEquals("Food 1", result.first().name)
    }

    @Test
    fun disambiguateFoodName_unchangedWhenFree() {
        assertEquals("Chicken", disambiguateFoodName("Chicken", setOf("banana")))
        assertEquals("Chicken", disambiguateFoodName("  Chicken  ", emptySet()))
    }

    @Test
    fun disambiguateFoodName_appendsNumberOnCollision() {
        val keys = setOf("chicken", "chicken (2)")
        assertEquals("Chicken (3)", disambiguateFoodName("Chicken", keys))
        // Casing of the requested name is preserved on the suffix form.
        assertEquals("chicken (3)", disambiguateFoodName("chicken", keys))
    }

    @Test
    fun disambiguateFoodName_usesStemWhenDesiredAlreadyNumbered() {
        val keys = setOf("yogurt", "yogurt (2)")
        assertEquals("Yogurt (3)", disambiguateFoodName("Yogurt (2)", keys))
    }

    @Test
    fun disambiguateFoodName_keepsNonNumericParenSuffix() {
        // "Vitamin (B12)" must not be treated as "Vitamin" + number.
        assertEquals(
            "Vitamin (B12) (2)",
            disambiguateFoodName("Vitamin (B12)", setOf("vitamin (b12)")),
        )
    }

    @Test
    fun recent_respectsRollingWindow() {
        val now = Instant.parse("2026-07-20T12:00:00Z")
        val recent = entry("Recent Oats", calories = 150, grams = 40.0, at = now.epochSecond)
        val old = entry(
            "Old Oats",
            calories = 100,
            grams = 30.0,
            at = now.minus(31, java.time.temporal.ChronoUnit.DAYS).epochSecond,
        )
        val result = recentFoodTemplates(
            listOf(recent, old).filter {
                !it.timestamp.isBefore(now.minus(30, java.time.temporal.ChronoUnit.DAYS))
            },
        )
        assertEquals(listOf(recent.id), result.map { it.id })
    }

    private fun entry(
        name: String,
        calories: Int,
        grams: Double,
        at: Long,
    ): FoodEntry = FoodEntry(
        name = name,
        calories = calories,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        timestamp = Instant.ofEpochSecond(at),
        source = FoodSource.MANUAL,
        mealType = MealType.OTHER,
        servingSizeGrams = grams,
    )
}
