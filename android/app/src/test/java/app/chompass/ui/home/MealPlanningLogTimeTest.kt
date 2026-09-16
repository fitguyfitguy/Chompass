package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealCatalog
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MealPlanningLogTimeTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 16)
    private val now = today.atTime(19, 30, 45).atZone(zone).toInstant()
    private val catalog = MealCatalog.Default

    // plannedFor truth table

    @Test
    fun plannedFor_modeOff_alwaysFalse() {
        assertFalse(plannedFor(false, planAction = true, tomorrow(), today))
        assertFalse(plannedFor(false, planAction = false, tomorrow(), today))
    }

    @Test
    fun plannedFor_modeOn_planActionTrueEvenToday() {
        assertTrue(plannedFor(true, planAction = true, today, today))
        assertTrue(plannedFor(true, planAction = true, tomorrow(), today))
    }

    @Test
    fun plannedFor_modeOn_futureTargetTrue() {
        assertTrue(plannedFor(true, planAction = false, tomorrow(), today))
    }

    @Test
    fun plannedFor_modeOn_todayWithoutPlanActionFalse() {
        assertFalse(plannedFor(true, planAction = false, today, today))
        assertFalse(plannedFor(true, planAction = false, today.minusDays(1), today))
    }

    // confirmPlannedEntry

    @Test
    fun confirm_futurePlanned_movesToTodayNowWithSlotAndClearsFlag() {
        val source = entry("Curry", tomorrow().atTime(12, 0).atZone(zone).toInstant(), MealType.LUNCH.id)
            .copy(planned = true)
        val confirmed = confirmPlannedEntry(
            source, today, now, zone,
            timesEnabled = true, catalog = catalog,
        )
        val zoned = confirmed.timestamp.atZone(zone)
        assertEquals(today, zoned.toLocalDate())
        assertEquals(java.time.LocalTime.of(19, 30), zoned.toLocalTime())
        assertEquals("dinner", confirmed.mealType)
        assertFalse(confirmed.planned)
        assertEquals(source.id, confirmed.id)
        assertEquals(source.name, confirmed.name)
    }

    @Test
    fun confirm_futurePlanned_timesOff_keepsTemplateSlot() {
        val source = entry("Curry", tomorrow().atTime(12, 0).atZone(zone).toInstant(), MealType.LUNCH.id)
            .copy(planned = true)
        val confirmed = confirmPlannedEntry(
            source, today, now, zone,
            timesEnabled = false, catalog = catalog,
        )
        assertEquals(MealType.LUNCH.id, confirmed.mealType)
        assertFalse(confirmed.planned)
    }

    @Test
    fun confirm_todayPlanned_clearsFlagOnly() {
        val stamp = today.atTime(12, 0).atZone(zone).toInstant()
        val source = entry("Curry", stamp, MealType.LUNCH.id).copy(planned = true)
        val confirmed = confirmPlannedEntry(
            source, today, now, zone,
            timesEnabled = true, catalog = catalog,
        )
        assertEquals(stamp, confirmed.timestamp)
        assertEquals(MealType.LUNCH.id, confirmed.mealType)
        assertFalse(confirmed.planned)
    }

    @Test
    fun confirm_pastPlanned_clearsFlagOnly() {
        val stamp = today.minusDays(1).atTime(9, 0).atZone(zone).toInstant()
        val source = entry("Curry", stamp, MealType.BREAKFAST.id).copy(planned = true)
        val confirmed = confirmPlannedEntry(
            source, today, now, zone,
            timesEnabled = true, catalog = catalog,
        )
        assertEquals(stamp, confirmed.timestamp)
        assertEquals(MealType.BREAKFAST.id, confirmed.mealType)
        assertFalse(confirmed.planned)
    }

    @Test
    fun confirm_unplannedEntry_unchanged() {
        val source = entry("Curry", tomorrow().atTime(12, 0).atZone(zone).toInstant(), MealType.LUNCH.id)
        assertEquals(
            source,
            confirmPlannedEntry(
                source, today, now, zone,
                timesEnabled = true, catalog = catalog,
            ),
        )
    }

    private fun tomorrow(): LocalDate = today.plusDays(1)

    private fun entry(name: String, timestamp: Instant, mealType: String) = FoodEntry(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        name = name,
        calories = 100,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = mealType,
    )
}
