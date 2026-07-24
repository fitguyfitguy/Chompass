package app.chompass.models

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MealScheduleTest {
    @Test
    fun defaultsMatchLegacyNofudMealWindows() {
        val schedule = MealSchedule.Default

        assertEquals(MealType.SNACK, schedule.mealTypeAt(LocalTime.of(4, 59)))
        assertEquals(MealType.BREAKFAST, schedule.mealTypeAt(LocalTime.of(5, 0)))
        assertEquals(MealType.BREAKFAST, schedule.mealTypeAt(LocalTime.of(10, 59)))
        assertEquals(MealType.LUNCH, schedule.mealTypeAt(LocalTime.of(11, 0)))
        assertEquals(MealType.LUNCH, schedule.mealTypeAt(LocalTime.of(14, 59)))
        assertEquals(MealType.DINNER, schedule.mealTypeAt(LocalTime.of(15, 0)))
        assertEquals(MealType.DINNER, schedule.mealTypeAt(LocalTime.of(20, 59)))
        assertEquals(MealType.SNACK, schedule.mealTypeAt(LocalTime.of(21, 0)))
    }

    @Test
    fun customBoundariesDriveClassification() {
        val schedule = MealSchedule(
            breakfastStartMinutes = 7 * 60,
            lunchStartMinutes = 13 * 60,
            dinnerStartMinutes = 20 * 60,
            snackStartMinutes = 23 * 60 + 30,
        )

        assertEquals(MealType.SNACK, schedule.mealTypeAt(LocalTime.of(6, 59)))
        assertEquals(MealType.BREAKFAST, schedule.mealTypeAt(LocalTime.of(7, 0)))
        assertEquals(MealType.LUNCH, schedule.mealTypeAt(LocalTime.of(17, 0)))
        assertEquals(MealType.DINNER, schedule.mealTypeAt(LocalTime.of(20, 0)))
        assertEquals(MealType.SNACK, schedule.mealTypeAt(LocalTime.of(23, 30)))
    }

    @Test
    fun invalidScheduleFallsBackToDefaults() {
        val invalid = MealSchedule(lunchStartMinutes = 19 * 60, dinnerStartMinutes = 18 * 60)

        assertFalse(invalid.isValid)
        assertEquals(MealSchedule.Default, invalid.validatedOrDefault())
    }
}
