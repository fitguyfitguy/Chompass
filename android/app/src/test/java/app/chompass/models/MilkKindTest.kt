package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MilkKindTest {
    @Test
    fun scaledWhole200MatchesBallpark() {
        val macros = MilkKind.WHOLE.scaled(200)
        assertEquals(124, macros.kcal)
        assertEquals(6.6, macros.protein, 0.001)
        assertEquals(9.6, macros.carbs, 0.001)
        assertEquals(6.6, macros.fat, 0.001)
        assertEquals(240.0, macros.calcium, 0.001)
        assertEquals(200 * 1.03, macros.grams, 0.001)
    }

    @Test
    fun toFoodEntryIsManualMilkWithoutCaffeine() {
        val at = Instant.parse("2026-09-14T08:00:00Z")
        val food = MilkKind.WHOLE.toFoodEntry(50, at, "breakfast", "Whole")
        assertEquals("Whole", food.name)
        assertEquals("🥛", food.emoji)
        assertEquals(FoodSource.MANUAL, food.source)
        assertEquals("breakfast", food.mealType)
        assertEquals(31, food.calories)
        assertEquals(1.7, food.protein, 0.001)
        assertEquals(2.4, food.carbs, 0.001)
        assertEquals(1.7, food.fat, 0.001)
        assertEquals(60.0, food.calcium!!, 0.001)
        assertNull(food.caffeine)
        assertEquals(50 * 1.03, food.servingSizeGrams!!, 0.001)
        assertEquals("ml", food.selectedServingUnit)
        assertEquals(50.0, food.selectedServingQuantity!!, 0.001)
        assertEquals(1.03, food.servingUnitOptions.single().gramsPerUnit, 0.001)
        assertEquals(at, food.timestamp)
    }

    @Test
    fun sidecarPairSetsLinkedFoodIdAndKeepsCaffeineOnTracker() {
        val at = Instant.parse("2026-09-14T08:00:00Z")
        val food = MilkKind.WHOLE.toFoodEntry(50, at, "breakfast", "Whole")
        val caffeine = CaffeineEntry(
            kind = "coffee",
            mg = 80.0,
            date = at,
            linkedFoodEntryId = food.id,
        )
        assertEquals(food.id, caffeine.linkedFoodEntryId)
        assertNull(food.caffeine)
        assertEquals(FoodSource.MANUAL, food.source)

        val afterFoodDelete = caffeine.copy(linkedFoodEntryId = null)
        assertNull(afterFoodDelete.linkedFoodEntryId)
        assertEquals(80.0, afterFoodDelete.mg, 0.001)
        assertEquals(caffeine.id, afterFoodDelete.id)
    }
}
