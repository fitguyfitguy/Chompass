package app.chompass.models

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealCatalogTest {
    @Test
    fun defaultMatchesLegacyFourWindows() {
        val catalog = MealCatalog.Default
        assertEquals(MealType.SNACK.id, catalog.mealIdAt(LocalTime.of(4, 59)))
        assertEquals(MealType.BREAKFAST.id, catalog.mealIdAt(LocalTime.of(5, 0)))
        assertEquals(MealType.LUNCH.id, catalog.mealIdAt(LocalTime.of(11, 0)))
        assertEquals(MealType.DINNER.id, catalog.mealIdAt(LocalTime.of(15, 0)))
        assertEquals(MealType.SNACK.id, catalog.mealIdAt(LocalTime.of(21, 0)))
        assertEquals(MealSchedule.Default, catalog.toLegacySchedule())
    }

    @Test
    fun fiveWindowsIncludingComida() {
        val catalog = MealCatalog.Default
            .withStart(MealType.DINNER.id, 20 * 60)
            .addCustom("Comida", 15 * 60)
        assertTrue(catalog.isValid)
        assertEquals(MealType.LUNCH.id, catalog.mealIdAt(LocalTime.of(14, 0)))
        val comida = catalog.meals.first { it.label == "Comida" }
        assertEquals(comida.id, catalog.mealIdAt(LocalTime.of(16, 0)))
        assertEquals(MealType.DINNER.id, catalog.mealIdAt(LocalTime.of(20, 0)))
    }

    @Test
    fun threeWindowsWrapOvernight() {
        val catalog = MealCatalog(
            meals = listOf(
                MealDef(MealType.BREAKFAST.id, startMinutes = 7 * 60),
                MealDef(MealType.DINNER.id, startMinutes = 18 * 60),
                MealDef(MealType.SNACK.id, startMinutes = 22 * 60),
            ),
        )
        assertTrue(catalog.isValid)
        assertEquals(MealType.SNACK.id, catalog.mealIdAt(LocalTime.of(6, 59)))
        assertEquals(MealType.BREAKFAST.id, catalog.mealIdAt(LocalTime.of(7, 0)))
        assertEquals(MealType.DINNER.id, catalog.mealIdAt(LocalTime.of(18, 0)))
        assertEquals(MealType.SNACK.id, catalog.mealIdAt(LocalTime.of(23, 0)))
    }

    @Test
    fun invalidCatalogFallsBack() {
        val invalid = MealCatalog(meals = emptyList())
        assertFalse(invalid.isValid)
        assertEquals(MealCatalog.Default, invalid.validatedOrDefault())
    }

    @Test
    fun fromLegacySchedulePreservesStarts() {
        val schedule = MealSchedule(
            breakfastStartMinutes = 7 * 60,
            lunchStartMinutes = 13 * 60,
            dinnerStartMinutes = 20 * 60,
            snackStartMinutes = 23 * 60 + 30,
        )
        val catalog = MealCatalog.fromLegacySchedule(schedule)
        assertEquals(schedule, catalog.toLegacySchedule())
        assertFalse(catalog.def(MealType.OTHER.id)!!.enabled)
    }

    @Test
    fun tailWrapAfterMidnightIsValidAndClassifies() {
        val catalog = MealCatalog.Default.withStart(MealType.SNACK.id, 60)

        assertTrue(catalog.isValid)
        assertEquals(catalog, catalog.validatedOrDefault())
        assertEquals(MealType.DINNER.id, catalog.mealIdAt(LocalTime.of(0, 30)))
        assertEquals(MealType.SNACK.id, catalog.mealIdAt(LocalTime.of(1, 0)))
    }

    @Test
    fun rotatedNightShiftSchedulePersistsAndClassifies() {
        val catalog = MealCatalog(
            meals = listOf(
                MealDef(MealType.BREAKFAST.id, startMinutes = 20 * 60),
                MealDef(MealType.LUNCH.id, startMinutes = 60),
                MealDef(MealType.DINNER.id, startMinutes = 5 * 60),
                MealDef(MealType.SNACK.id, startMinutes = 9 * 60),
            ),
        )

        assertTrue(catalog.isValid)
        assertEquals(catalog, catalog.validatedOrDefault())
        assertEquals(MealType.LUNCH.id, catalog.mealIdAt(LocalTime.of(2, 0)))
        assertEquals(MealType.BREAKFAST.id, catalog.mealIdAt(LocalTime.of(23, 0)))
        val legacy = catalog.toLegacySchedule()
        assertTrue(legacy.isValid)
        assertEquals(20 * 60, legacy.breakfastStartMinutes)
        assertEquals(60, legacy.lunchStartMinutes)
    }

    @Test
    fun duplicateStartsAcrossWrapStayInvalid() {
        val catalog = MealCatalog(
            meals = listOf(
                MealDef(MealType.BREAKFAST.id, startMinutes = 7 * 60),
                MealDef(MealType.LUNCH.id, startMinutes = 19 * 60),
                MealDef(MealType.DINNER.id, startMinutes = 7 * 60),
            ),
        )

        assertFalse(catalog.isValid)
    }
}
