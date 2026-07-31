package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class FoodLogMealGroupsTest {
    @Test
    fun standard_ordersMealSlotsThenOldestFirstWithinMeal() {
        // Same arrival shape as FoodRepository.entriesForDate: latest-first.
        val dinnerLate = entry("Dinner late", MealType.DINNER, "2024-06-01T19:30:00Z")
        val lunch = entry("Lunch", MealType.LUNCH, "2024-06-01T12:00:00Z")
        val breakfastLate = entry("Breakfast late", MealType.BREAKFAST, "2024-06-01T09:00:00Z")
        val breakfastEarly = entry("Breakfast early", MealType.BREAKFAST, "2024-06-01T07:30:00Z")
        val snack = entry("Snack", MealType.SNACK, "2024-06-01T15:00:00Z")

        val groups = foodLogMealGroups(
            listOf(dinnerLate, snack, lunch, breakfastLate, breakfastEarly),
            FoodLogSortOrder.STANDARD,
        )

        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK),
            groups.map { it.meal },
        )
        assertEquals(
            listOf("Breakfast early", "Breakfast late"),
            groups[0].entries.map { it.name },
        )
        assertEquals(listOf("Lunch"), groups[1].entries.map { it.name })
        assertEquals(listOf("Dinner late"), groups[2].entries.map { it.name })
        assertEquals(listOf("Snack"), groups[3].entries.map { it.name })
    }

    @Test
    fun latestMealsFirst_ordersByTimestampDescendingInRuns() {
        val breakfastEarly = entry("Breakfast early", MealType.BREAKFAST, "2024-06-01T07:30:00Z")
        val breakfastLate = entry("Breakfast late", MealType.BREAKFAST, "2024-06-01T09:00:00Z")
        val lunch = entry("Lunch", MealType.LUNCH, "2024-06-01T12:00:00Z")
        val snack = entry("Snack", MealType.SNACK, "2024-06-01T15:00:00Z")
        val dinner = entry("Dinner", MealType.DINNER, "2024-06-01T19:00:00Z")

        val groups = foodLogMealGroups(
            listOf(breakfastEarly, lunch, dinner, snack, breakfastLate),
            FoodLogSortOrder.LATEST_MEALS_FIRST,
        )

        assertEquals(
            listOf(MealType.DINNER, MealType.SNACK, MealType.LUNCH, MealType.BREAKFAST),
            groups.map { it.meal },
        )
        assertEquals(
            listOf("Breakfast late", "Breakfast early"),
            groups[3].entries.map { it.name },
        )
    }

    private fun entry(name: String, meal: MealType, iso: String): FoodEntry = FoodEntry(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        name = name,
        calories = 100,
        protein = 1.0,
        carbs = 1.0,
        fat = 1.0,
        timestamp = Instant.parse(iso),
        source = FoodSource.MANUAL,
        mealType = meal,
    )
}
