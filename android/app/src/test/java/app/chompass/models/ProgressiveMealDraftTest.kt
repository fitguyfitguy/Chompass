package app.chompass.models

import app.chompass.services.ai.FoodAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ProgressiveMealDraftTest {
    @Test
    fun toFoodEntries_unnamedSharesRecipeLogId() {
        val draft = ProgressiveMealDraft(
            mealType = MealType.LUNCH.id,
            items = listOf(
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Buckwheat",
                        calories = 200,
                        protein = 7.0,
                        carbs = 40.0,
                        fat = 2.0,
                        servingSizeGrams = 180.0,
                    ),
                    source = FoodSource.SNAP_FOOD,
                ),
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Chicken",
                        calories = 250,
                        protein = 40.0,
                        carbs = 0.0,
                        fat = 8.0,
                        servingSizeGrams = 120.0,
                    ),
                    source = FoodSource.SNAP_FOOD,
                ),
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Salad",
                        calories = 50,
                        protein = 2.0,
                        carbs = 5.0,
                        fat = 3.0,
                        servingSizeGrams = 90.0,
                    ),
                    source = FoodSource.SNAP_FOOD,
                ),
            ),
        )

        val recipeLogId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val timestamp = Instant.parse("2026-07-29T12:00:00Z")
        val entries = draft.toFoodEntries(
            recipeLogId = recipeLogId,
            timestamp = timestamp,
            imageFilenameFor = { _, _ -> null },
            resolveName = { "$it!" },
        )

        assertEquals(3, entries.size)
        assertTrue(entries.all { it.recipeLogId == recipeLogId })
        assertTrue(entries.all { it.mealType == MealType.LUNCH.id })
        assertTrue(entries.all { it.timestamp == timestamp })
        assertEquals("Buckwheat!", entries[0].name)
        assertEquals("Chicken!", entries[1].name)
        assertEquals("Salad!", entries[2].name)
        assertEquals(200, entries[0].calories)
        assertEquals(250, entries[1].calories)
        assertEquals(50, entries[2].calories)
        assertEquals(500, entries.sumOf { it.calories })
    }

    @Test
    fun toFoodEntries_namedMealIsOneEntryWithConstituents() {
        val draft = ProgressiveMealDraft(
            name = "Plate",
            mealType = MealType.LUNCH.id,
            items = listOf(
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Buckwheat",
                        calories = 200,
                        protein = 7.0,
                        carbs = 40.0,
                        fat = 2.0,
                        servingSizeGrams = 180.0,
                        fiber = 4.0,
                    ),
                    source = FoodSource.SNAP_FOOD,
                ),
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Chicken",
                        calories = 250,
                        protein = 40.0,
                        carbs = 0.0,
                        fat = 8.0,
                        servingSizeGrams = 120.0,
                    ),
                    source = FoodSource.SNAP_FOOD,
                ),
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Salad",
                        calories = 50,
                        protein = 2.0,
                        carbs = 5.0,
                        fat = 3.0,
                        servingSizeGrams = 90.0,
                    ),
                    source = FoodSource.SNAP_FOOD,
                ),
            ),
        )

        val recipeLogId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val timestamp = Instant.parse("2026-07-29T12:00:00Z")
        val entries = draft.toFoodEntries(
            recipeLogId = recipeLogId,
            timestamp = timestamp,
            imageFilenameFor = { _, _ -> null },
            resolveName = { "$it!" },
        )

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("Plate!", entry.name)
        assertEquals(null, entry.recipeLogId)
        assertEquals(MealType.LUNCH.id, entry.mealType)
        assertEquals(timestamp, entry.timestamp)
        assertEquals(500, entry.calories)
        assertEquals(49.0, entry.protein, 0.001)
        assertEquals(45.0, entry.carbs, 0.001)
        assertEquals(13.0, entry.fat, 0.001)
        assertEquals(390.0, entry.servingSizeGrams!!, 0.001)
        assertEquals(FoodSource.SNAP_FOOD, entry.source)
        assertEquals(3, entry.constituents.size)
        assertEquals("Buckwheat", entry.constituents[0].name)
        assertEquals("Chicken", entry.constituents[1].name)
        assertEquals("Salad", entry.constituents[2].name)
        assertEquals(4.0, entry.fiber!!, 0.001)
        assertEquals(null, entry.productMetadata)
        assertEquals(null, entry.grounding)
    }

    @Test
    fun toFoodEntries_assignsDistinctIdsAndPreservesMicros() {
        val draft = ProgressiveMealDraft(
            items = listOf(
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Oats",
                        calories = 150,
                        protein = 5.0,
                        carbs = 27.0,
                        fat = 3.0,
                        fiber = 4.0,
                        servingSizeGrams = 40.0,
                    ),
                ),
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "Milk",
                        calories = 60,
                        protein = 3.0,
                        carbs = 5.0,
                        fat = 3.0,
                        servingSizeGrams = 100.0,
                    ),
                ),
            ),
        )

        val entries = draft.toFoodEntriesForTest()
        assertEquals(2, entries.size)
        assertTrue(entries[0].id != entries[1].id)
        assertNotNull(entries[0].recipeLogId)
        assertEquals(entries[0].recipeLogId, entries[1].recipeLogId)
        assertEquals(4.0, entries[0].fiber!!, 0.001)
    }

    @Test
    fun draft_totalsMatchIngredients() {
        val draft = ProgressiveMealDraft(
            items = listOf(
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "A",
                        calories = 100,
                        protein = 10.0,
                        carbs = 5.0,
                        fat = 2.0,
                        servingSizeGrams = 100.0,
                    ),
                ),
                ProgressiveMealItem(
                    analysis = FoodAnalysis(
                        name = "B",
                        calories = 50,
                        protein = 1.0,
                        carbs = 8.0,
                        fat = 1.0,
                        servingSizeGrams = 50.0,
                    ),
                ),
            ),
        )
        assertEquals(150, draft.totalCalories)
        assertEquals(11.0, draft.totalProtein, 0.001)
        assertEquals(13.0, draft.totalCarbs, 0.001)
        assertEquals(3.0, draft.totalFat, 0.001)
    }
}
