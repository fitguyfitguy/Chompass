package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class FoodBucketMonthsTest {
    @Test
    fun overlappingMonthsAreInclusiveAndOrdered() {
        val months = yearMonthsOverlapping(
            LocalDate.of(2026, 5, 21),
            LocalDate.of(2026, 8, 19),
        )
        assertEquals(
            listOf(
                YearMonth.of(2026, 5),
                YearMonth.of(2026, 6),
                YearMonth.of(2026, 7),
                YearMonth.of(2026, 8),
            ),
            months,
        )
    }

    @Test
    fun oneWeekStaysInsideTheCurrentMonth() {
        val today = LocalDate.of(2026, 8, 19)
        val months = yearMonthsOverlapping(today.minusDays(6), today)
        assertEquals(listOf(YearMonth.of(2026, 8)), months)
    }

    @Test
    fun oneYearDoesNotIncludeThePreviousCalendarYearPlusOne() {
        val today = LocalDate.of(2026, 8, 19)
        val months = yearMonthsOverlapping(today.minusDays(364), today)
        assertFalse(months.contains(YearMonth.of(2024, 8)))
        assertTrue(months.contains(YearMonth.of(2025, 8)))
        assertTrue(months.contains(YearMonth.of(2026, 8)))
        assertEquals(13, months.size)
    }

    @Test
    fun quickRelogWindowIsThreeOrFourMonthsNotTheYear() {
        val now = Instant.parse("2026-08-19T12:00:00Z")
        val months = yearMonthsForQuickRelog(now, ZoneOffset.UTC)
        assertTrue(months.size in 3..4)
        assertFalse(months.contains(YearMonth.of(2025, 8)))
        assertEquals(YearMonth.of(2026, 8), months.last())
    }

    @Test
    fun thirteenMonthFixtureMatchesFullMergeInsideTheWindow() {
        val now = Instant.parse("2026-08-19T12:00:00Z")
        val all = (0..12).flatMap { monthsAgo ->
            val ts = Instant.parse("2026-08-19T12:00:00Z").minusSeconds(monthsAgo * 30L * 86_400)
            listOf(
                entry("Old $monthsAgo", ts),
                entry("Old $monthsAgo", ts.minusSeconds(86_400)),
            )
        } + listOf(
            entry("Recent A", Instant.parse("2026-08-18T12:00:00Z")),
            entry("Recent B", Instant.parse("2026-08-10T12:00:00Z")),
            entry("Freq X", Instant.parse("2026-06-01T12:00:00Z")),
            entry("Freq X", Instant.parse("2026-06-15T12:00:00Z")),
            entry("Freq X", Instant.parse("2026-07-01T12:00:00Z")),
            entry("Freq Y", Instant.parse("2026-06-20T12:00:00Z")),
            entry("Freq Y", Instant.parse("2026-07-20T12:00:00Z")),
        )
        val recentStart = now.minusSeconds(30L * 86_400)
        val frequentStart = now.minusSeconds(90L * 86_400)
        val fromAll = quickRelogRows(
            recentWindow = all.filter { !it.timestamp.isBefore(recentStart) },
            frequentWindow = all.filter { !it.timestamp.isBefore(frequentStart) },
        )
        val months = yearMonthsForQuickRelog(now, ZoneOffset.UTC).toSet()
        val windowed = all.filter { entry ->
            val month = YearMonth.from(entry.timestamp.atZone(ZoneOffset.UTC))
            month in months
        }
        val fromWindow = quickRelogRows(
            recentWindow = windowed.filter { !it.timestamp.isBefore(recentStart) },
            frequentWindow = windowed.filter { !it.timestamp.isBefore(frequentStart) },
        )
        assertEquals(fromAll.recents.map { it.name }, fromWindow.recents.map { it.name })
        assertEquals(fromAll.frequents.map { it.name }, fromWindow.frequents.map { it.name })
        assertTrue(fromAll.recents.isNotEmpty())
        assertTrue(fromAll.frequents.isNotEmpty())
    }

    private fun entry(name: String, timestamp: Instant) = FoodEntry(
        name = name,
        calories = 100,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH,
    )
}
