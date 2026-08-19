package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class QuickRelogRowsTest {
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
        mealType = MealType.LUNCH,
    )

    @Test
    fun recentsAreNewestFirstAndUniqueByKey() {
        val olderApple = entry("Apple", Instant.parse("2024-06-01T08:00:00Z"), calories = 80)
        val banana = entry("Banana", Instant.parse("2024-06-01T10:00:00Z"))
        val newerApple = entry("Apple", Instant.parse("2024-06-01T14:00:00Z"), calories = 120)
        val cherry = entry("Cherry", Instant.parse("2024-06-01T16:00:00Z"))
        val out = quickRelogRows(
            recentWindow = listOf(olderApple, banana, newerApple, cherry),
            frequentWindow = emptyList(),
        )
        assertEquals(listOf("Cherry", "Apple", "Banana"), out.recents.map { it.name })
        assertEquals(120, out.recents.first { it.name == "Apple" }.calories)
        assertTrue(out.frequents.isEmpty())
    }

    @Test
    fun frequentsAreCountDescAndExcludeRecentKeys() {
        val apple = entry("Apple", Instant.parse("2024-06-01T12:00:00Z"))
        val banana = entry("Banana", Instant.parse("2024-05-01T12:00:00Z"))
        val cherry = entry("Cherry", Instant.parse("2024-05-02T12:00:00Z"))
        val frequentWindow = listOf(
            apple, apple, apple,
            banana, banana,
            cherry,
        )
        val out = quickRelogRows(
            recentWindow = listOf(apple),
            frequentWindow = frequentWindow,
        )
        assertEquals(listOf("Apple"), out.recents.map { it.name })
        assertEquals(listOf("Banana", "Cherry"), out.frequents.map { it.name })
    }

    @Test
    fun perRowIsHonoredOnEachSide() {
        val recents = (1..12).map { i ->
            entry("Recent $i", Instant.parse("2024-06-01T${"%02d".format(i)}:00:00Z"))
        }
        val frequents = (1..12).flatMap { i ->
            List(13 - i) { entry("Freq $i", Instant.parse("2024-04-01T12:00:00Z")) }
        }
        val out = quickRelogRows(
            recentWindow = recents,
            frequentWindow = frequents,
            perRow = 3,
        )
        assertEquals(3, out.recents.size)
        assertEquals(3, out.frequents.size)
        assertEquals(listOf("Recent 12", "Recent 11", "Recent 10"), out.recents.map { it.name })
        assertEquals(listOf("Freq 1", "Freq 2", "Freq 3"), out.frequents.map { it.name })
    }

    @Test
    fun emptyRecentsKeepsFrequents() {
        val yogurt = entry("Yogurt", Instant.parse("2024-04-01T12:00:00Z"))
        val oats = entry("Oats", Instant.parse("2024-04-02T12:00:00Z"))
        val out = quickRelogRows(
            recentWindow = emptyList(),
            frequentWindow = listOf(yogurt, yogurt, oats),
        )
        assertTrue(out.recents.isEmpty())
        assertEquals(listOf("Yogurt", "Oats"), out.frequents.map { it.name })
    }

    @Test
    fun emptyBothYieldsEmptyRows() {
        val out = quickRelogRows(emptyList(), emptyList())
        assertTrue(out.isEmpty)
        assertTrue(out.recents.isEmpty())
        assertTrue(out.frequents.isEmpty())
    }
}
