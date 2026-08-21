package app.chompass.services

import app.chompass.models.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeightForecastMathTest {
    private val zone = ZoneId.of("UTC")

  @Test
  fun averageDailyIntake_usesLoggedDaysWhenConsistent() {
    val result = WeightForecastMath.averageDailyIntake(
      totalCalories = 14_000,
      loggedDays = 7,
      calendarDaysInWindow = 10,
    )
    assertEquals(2000, result.avgDailyCalories)
    assertFalse(result.usesCalendarDayAverage)
  }

  @Test
  fun averageDailyIntake_usesCalendarDaysWhenSparse() {
    val result = WeightForecastMath.averageDailyIntake(
      totalCalories = 10_000,
      loggedDays = 2,
      calendarDaysInWindow = 30,
    )
    assertEquals(333, result.avgDailyCalories)
    assertTrue(result.usesCalendarDayAverage)
  }

  @Test
  fun theilSen_resistsSingleOutlier() {
    val base = Instant.parse("2026-01-01T12:00:00Z")
    val entries = listOf(
      WeightEntry(date = base, weightKg = 80.0),
      WeightEntry(date = base.plusSeconds(7 * 86_400L), weightKg = 79.5),
      WeightEntry(date = base.plusSeconds(14 * 86_400L), weightKg = 79.0),
      WeightEntry(date = base.plusSeconds(21 * 86_400L), weightKg = 78.5),
      // Outlier spike — OLS would pull slope upward; Theil–Sen should stay near −0.5 kg/week.
      WeightEntry(date = base.plusSeconds(28 * 86_400L), weightKg = 82.0),
    )
    val slopePerDay = WeightForecastMath.theilSenSlopePerDay(entries, zone)
    assertNotNull(slopePerDay)
    assertEquals(-0.071, slopePerDay!!, 0.02)
  }

  @Test
  fun theilSen_returnsNullForSingleEntry() {
    assertNull(
      WeightForecastMath.theilSenSlopePerDay(
        listOf(WeightEntry(weightKg = 80.0)),
        zone,
      )
    )
  }

  @Test
  fun trendsDisagree_whenDeltaExceedsThreshold() {
    assertTrue(
      WeightForecastMath.trendsDisagree(
        predictedWeeklyKg = -0.2,
        observedWeeklyKg = -0.7,
        hasEnoughData = true,
      )
    )
    assertFalse(
      WeightForecastMath.trendsDisagree(
        predictedWeeklyKg = -0.5,
        observedWeeklyKg = -0.55,
        hasEnoughData = true,
      )
    )
  }

  @Test
  fun completeDayWindow_startsAtFirstLog_excludesToday() {
    val today = LocalDate.of(2026, 8, 21)
    val logged = (0..8).map { today.minusDays(it.toLong()) } // includes today
    val window = WeightForecastMath.completeDayWindow(logged, today, maxLookbackDays = 90)
    assertEquals(LocalDate.of(2026, 8, 13), window.firstLogged)
    assertEquals(8, window.loggedDates.size)
    assertFalse(today in window.loggedDates)
    assertEquals(8, window.calendarDays)
  }

  @Test
  fun completeDayWindow_emptyBeforeFirstLogDoesNotPad() {
    val today = LocalDate.of(2026, 8, 21)
    val logged = listOf(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 19))
    val window = WeightForecastMath.completeDayWindow(logged, today, maxLookbackDays = 90)
    assertEquals(2, window.calendarDays)
    assertEquals(2, window.loggedDates.size)
  }
}
