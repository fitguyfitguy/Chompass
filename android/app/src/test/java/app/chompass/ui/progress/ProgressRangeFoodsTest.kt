package app.chompass.ui.progress

import app.chompass.data.yearMonthsOverlapping
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth

class ProgressRangeFoodsTest {
    @Test
    fun yearTotalsMatchFullScanWhenOnlyOverlappingMonthsAreKept() {
        val today = LocalDate.of(2026, 8, 19)
        val foods = (0..729).map { i ->
            val day = today.minusDays(i.toLong())
            entry("Meal $i", day, calories = 100 + (i % 7))
        }
        val full = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = foods,
            timeRange = TimeRange.YEAR,
            anchorDate = today,
        )
        val (start, end) = TimeRange.YEAR.dateRange(today)
        val months = yearMonthsOverlapping(start, end).toSet()
        val zone = ZoneId.systemDefault()
        val scoped = foods.filter { entry ->
            val day = entry.timestamp.atZone(zone).toLocalDate()
            val month = YearMonth.from(day)
            month in months && !day.isBefore(start) && !day.isAfter(end)
        }
        val filtered = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = scoped,
            timeRange = TimeRange.YEAR,
            anchorDate = today,
        )
        assertEquals(full.dailyCalories, filtered.dailyCalories)
        assertEquals(full.macroAverages, filtered.macroAverages)
    }

    @Test
    fun calorieAverageExcludesToday() {
        val today = LocalDate.of(2026, 8, 21)
        val foods = listOf(
            entry("A", today.minusDays(2), calories = 2000),
            entry("B", today.minusDays(1), calories = 2200),
            entry("C", today, calories = 500),
        )
        val ui = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = foods,
            timeRange = TimeRange.ALL_TIME,
            anchorDate = today,
        )
        assertEquals(3, ui.dailyCalories.size)
        assertEquals(2100, ui.calorieAverage)
    }

    @Test
    fun oneWeekDoesNotTouchTwoYearOldBuckets() {
        val today = LocalDate.of(2026, 8, 19)
        val (start, end) = TimeRange.WEEK.dateRange(today)
        val months = yearMonthsOverlapping(start, end)
        assertFalse(months.contains(YearMonth.of(2024, 8)))
        assertTrue(months.size <= 2)
    }

    private fun entry(name: String, day: LocalDate, calories: Int) = FoodEntry(
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH,
    )
}
