package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            listOf(MealType.BREAKFAST.id, MealType.LUNCH.id, MealType.DINNER.id, MealType.SNACK.id),
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
            listOf(MealType.DINNER.id, MealType.SNACK.id, MealType.LUNCH.id, MealType.BREAKFAST.id),
            groups.map { it.meal },
        )
        assertEquals(
            listOf("Breakfast late", "Breakfast early"),
            groups[3].entries.map { it.name },
        )
    }

    @Test
    fun latestMealsFirst_unaffectedRunKeepsIdAcrossMealEdit() {
        // Codeberg #56: a meal-type edit must not rename unrelated runs, or the
        // LazyColumn tears down headers that did not change (the old id scheme
        // embedded the run index, so every edit after the change point churned).
        val lunch = entry("Lunch", MealType.LUNCH, "2024-06-01T14:30:00Z")
        val dinner = entry("Dinner", MealType.DINNER, "2024-06-01T13:00:00Z")
        val snack = entry("Snack", MealType.SNACK, "2024-06-01T12:00:00Z")

        val before = foodLogMealGroups(
            listOf(lunch, dinner, snack),
            FoodLogSortOrder.LATEST_MEALS_FIRST,
        )
        // Edit: dinner -> lunch. Snack is untouched and stays the last run; its
        // id must be identical before and after.
        val edited = dinner.copy(mealType = MealType.LUNCH.id)
        val after = foodLogMealGroups(
            listOf(lunch, edited, snack),
            FoodLogSortOrder.LATEST_MEALS_FIRST,
        )

        val snackBefore = before.first { it.meal == MealType.SNACK.id }
        val snackAfter = after.first { it.meal == MealType.SNACK.id }
        assertEquals(snackBefore.id, snackAfter.id)
        assertEquals("latest-snack-${snack.id}", snackAfter.id)
        // The lunch run absorbs the moved entry and keeps its anchor (lunch is
        // still its newest entry).
        val lunchAfter = after.first { it.meal == MealType.LUNCH.id }
        assertEquals("latest-lunch-${lunch.id}", lunchAfter.id)
        assertEquals(listOf("Lunch", "Dinner"), lunchAfter.entries.map { it.name })
        // The emptied dinner run is gone (correct section removal, not a bug).
        assertTrue(after.none { it.meal == MealType.DINNER.id })
    }

    @Test
    fun latestMealsFirst_destinationRunKeepsIdWhenMovedEntryIsNotNewest() {
        // The moved entry joins the destination run without becoming its newest
        // entry, so even the destination header key stays put.
        val lunchLate = entry("Lunch late", MealType.LUNCH, "2024-06-01T14:30:00Z")
        val lunchEarly = entry("Lunch early", MealType.LUNCH, "2024-06-01T12:30:00Z")
        val dinner = entry("Dinner", MealType.DINNER, "2024-06-01T13:00:00Z")

        val before = foodLogMealGroups(
            listOf(lunchLate, lunchEarly, dinner),
            FoodLogSortOrder.LATEST_MEALS_FIRST,
        )
        val edited = dinner.copy(mealType = MealType.LUNCH.id)
        val after = foodLogMealGroups(
            listOf(lunchLate, lunchEarly, edited),
            FoodLogSortOrder.LATEST_MEALS_FIRST,
        )

        assertEquals("latest-lunch-${lunchLate.id}", before.first { it.meal == MealType.LUNCH.id }.id)
        val lunchAfter = after.first { it.meal == MealType.LUNCH.id }
        assertEquals("latest-lunch-${lunchLate.id}", lunchAfter.id)
        assertEquals(listOf("Lunch late", "Dinner", "Lunch early"), lunchAfter.entries.map { it.name })
    }

    @Test
    fun latestMealsFirst_idsUniqueWithinComputation() {
        // Every group must carry a distinct key regardless of meal/entry mix
        // (duplicate LazyColumn keys drop items silently).
        val a = entry("A", MealType.BREAKFAST, "2024-06-01T08:00:00Z")
        val b = entry("B", MealType.LUNCH, "2024-06-01T12:00:00Z")
        val c = entry("C", MealType.DINNER, "2024-06-01T19:00:00Z")
        val d = entry("D", MealType.SNACK, "2024-06-01T22:00:00Z")

        val groups = foodLogMealGroups(
            listOf(a, b, c, d),
            FoodLogSortOrder.LATEST_MEALS_FIRST,
        )
        assertEquals(groups.size, groups.map { it.id }.toSet().size)
    }

    @Test
    fun mealTotals_sumFiberAndSugarAcrossEntries() {
        val oats = FoodEntry(
            id = UUID.nameUUIDFromBytes("oats".toByteArray()),
            name = "Oats",
            calories = 150,
            protein = 5.0,
            carbs = 27.0,
            fat = 3.0,
            fiber = 4.2,
            sugar = 0.8,
            timestamp = Instant.parse("2024-06-01T08:00:00Z"),
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST.id,
        )
        val berries = FoodEntry(
            id = UUID.nameUUIDFromBytes("berries".toByteArray()),
            name = "Berries",
            calories = 50,
            protein = 1.0,
            carbs = 12.0,
            fat = 0.3,
            fiber = 2.5,
            sugar = 7.0,
            timestamp = Instant.parse("2024-06-01T08:10:00Z"),
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST.id,
        )

        val group = foodLogMealGroups(
            listOf(oats, berries),
            FoodLogSortOrder.STANDARD,
        ).first()

        assertEquals(6.7, group.totalFiber, 1e-9)
        assertEquals(7.8, group.totalSugar, 1e-9)
        assertEquals(200, group.totalCalories)
    }

    @Test
    fun chipValues_fromMealTotalsMatchSelectedChips() {
        assertEquals(10.0, FoodLogMacroChip.PROTEIN.valueFromMeal(10.0, 0.0, 0.0, 0.0, 0.0), 1e-9)
        assertEquals(2.5, FoodLogMacroChip.CARBS.valueFromMeal(0.0, 2.5, 0.0, 0.0, 0.0), 1e-9)
        assertEquals(4.0, FoodLogMacroChip.FAT.valueFromMeal(0.0, 0.0, 4.0, 0.0, 0.0), 1e-9)
        assertEquals(6.7, FoodLogMacroChip.FIBER.valueFromMeal(0.0, 0.0, 0.0, 6.7, 0.0), 1e-9)
        assertEquals(7.8, FoodLogMacroChip.SUGAR.valueFromMeal(0.0, 0.0, 0.0, 0.0, 7.8), 1e-9)
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
        mealType = meal.id,
    )
}
