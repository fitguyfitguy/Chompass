package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealCatalog
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class FoodLogTimeTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 8, 31)
    private val now = today.atTime(19, 30, 45).atZone(zone).toInstant()

    @Test
    fun todayWithoutOverride_keepsNowIncludingSeconds() {
        assertEquals(now, timestampForLogging(today, now, zone, timeOverride = null))
    }

    @Test
    fun todayWithOverride_usesThatClockOnToday() {
        val stamped = timestampForLogging(today, now, zone, LocalTime.of(13, 0))
        val zoned = stamped.atZone(zone)
        assertEquals(today, zoned.toLocalDate())
        assertEquals(LocalTime.of(13, 0), zoned.toLocalTime())
    }

    @Test
    fun pastDayWithoutOverride_keepsWallClockTimeOfDay() {
        val yesterday = today.minusDays(1)
        val stamped = timestampForLogging(yesterday, now, zone, timeOverride = null)
        val zoned = stamped.atZone(zone)
        assertEquals(yesterday, zoned.toLocalDate())
        assertEquals(LocalTime.of(19, 30, 45), zoned.toLocalTime())
    }

    @Test
    fun pastDayWithOverride_usesOverrideClock() {
        val yesterday = today.minusDays(1)
        val stamped = timestampForLogging(yesterday, now, zone, LocalTime.of(13, 5, 59))
        val zoned = stamped.atZone(zone)
        assertEquals(yesterday, zoned.toLocalDate())
        assertEquals(LocalTime.of(13, 5), zoned.toLocalTime())
    }

    @Test
    fun mealId_followsCatalogAtOverride() {
        val catalog = MealCatalog.Default
        assertEquals("lunch", mealIdForLogging(catalog, LocalTime.of(13, 0), LocalTime.of(19, 30)))
        assertEquals("dinner", mealIdForLogging(catalog, timeOverride = null, nowTime = LocalTime.of(19, 30)))
        assertEquals("breakfast", mealIdForLogging(catalog, LocalTime.of(8, 0), LocalTime.of(19, 30)))
    }

    @Test
    fun loggingSlotFor_keepsTemplateWhenTimesOff() {
        val catalog = MealCatalog.Default
        assertEquals(
            "dinner",
            loggingSlotFor("dinner", timesEnabled = false, catalog, LocalTime.of(8, 0), LocalTime.of(19, 30)),
        )
        assertEquals(
            "breakfast",
            loggingSlotFor("dinner", timesEnabled = true, catalog, LocalTime.of(8, 0), LocalTime.of(19, 30)),
        )
    }

    @Test
    fun suggestedSlotFor_otherWhenTimesOff() {
        val catalog = MealCatalog.Default
        assertEquals(MealType.OTHER.id, suggestedSlotFor(timesEnabled = false, catalog, LocalTime.of(13, 0)))
        assertEquals("lunch", suggestedSlotFor(timesEnabled = true, catalog, LocalTime.of(13, 0)))
    }

    @Test
    fun siblings_sameMealSameDay_excludesSelfAndOtherSlots() {
        val lunch = Instant.parse("2026-08-31T13:00:00Z")
        val later = Instant.parse("2026-08-31T19:00:00Z")
        val a = entry("a", lunch, MealType.LUNCH.id)
        val b = entry("b", Instant.parse("2026-08-31T13:05:00Z"), MealType.LUNCH.id)
        val dinner = entry("c", later, MealType.DINNER.id)
        val otherDay = entry("d", Instant.parse("2026-08-30T13:00:00Z"), MealType.LUNCH.id)
        val siblings = siblingEntriesForTimeApply(listOf(a, b, dinner, otherDay), a, zone)
        assertEquals(listOf(b.id), siblings.map { it.id })
        assertTrue(siblingEntriesForTimeApply(listOf(a), a, zone).isEmpty())
    }

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
