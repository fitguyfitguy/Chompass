package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class FoodEntryDuplicationTest {
    @Test
    fun duplicatedForLogging_usesNewTimestampAndCurrentMealDefault() {
        val source = FoodEntry(
            name = "Oats",
            calories = 150,
            protein = 5.0,
            carbs = 27.0,
            fat = 3.0,
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST,
            timestamp = Instant.parse("2026-01-15T08:30:00Z"),
        )
        val logAt = Instant.parse("2026-07-22T18:05:00Z")
        val copy = source.duplicatedForLogging(logAt)

        assertNotEquals(source.id, copy.id)
        assertEquals(logAt, copy.timestamp)
        assertEquals(MealType.currentMeal, copy.mealType)
        assertEquals(source.name, copy.name)
        assertEquals(source.calories, copy.calories)
    }

    @Test
    fun duplicatedForLogging_canStillOverrideMealType() {
        val source = FoodEntry(
            name = "Salad",
            calories = 200,
            protein = 10.0,
            carbs = 15.0,
            fat = 8.0,
            source = FoodSource.MANUAL,
            mealType = MealType.LUNCH,
        )
        val copy = source.duplicatedForLogging(
            Instant.parse("2026-07-22T12:00:00Z"),
            mealType = MealType.DINNER,
        )
        assertEquals(MealType.DINNER, copy.mealType)
    }

    @Test
    fun duplicatedForLogging_carriesImageFilenameForRelog() {
        // #12: the duplicated entry must keep the source's image so the
        // repository can copy the JPEG to the new entry's own filename on save.
        val source = FoodEntry(
            name = "Oats",
            calories = 150,
            protein = 5.0,
            carbs = 27.0,
            fat = 3.0,
            source = FoodSource.SNAP_FOOD,
            imageFilename = "11111111-2222-3333-4444-555555555555.jpg",
            emoji = "🥣",
        )
        val copy = source.duplicatedForLogging(Instant.parse("2026-07-22T18:05:00Z"))

        assertEquals(source.imageFilename, copy.imageFilename)
        assertEquals(source.emoji, copy.emoji)
        assertNotEquals(source.id, copy.id)
    }
}
