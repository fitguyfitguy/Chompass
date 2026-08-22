package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class WeekStartDayTest {
    @Test
    fun fromStoragePrefersExplicitDay() {
        assertEquals(WeekStartDay.SATURDAY, WeekStartDay.fromStorage("saturday", true))
        assertEquals(WeekStartDay.SUNDAY, WeekStartDay.fromStorage("sunday", true))
        assertEquals(WeekStartDay.MONDAY, WeekStartDay.fromStorage("monday", false))
    }

    @Test
    fun fromStorageFallsBackToLegacyBoolean() {
        assertEquals(WeekStartDay.SUNDAY, WeekStartDay.fromStorage(null, false))
        assertEquals(WeekStartDay.MONDAY, WeekStartDay.fromStorage(null, true))
        assertEquals(WeekStartDay.MONDAY, WeekStartDay.fromStorage(null, null))
    }

    @Test
    fun javaDayMatchesCalendar() {
        assertEquals(DayOfWeek.SATURDAY, WeekStartDay.SATURDAY.javaDay)
        assertEquals(DayOfWeek.SUNDAY, WeekStartDay.SUNDAY.javaDay)
        assertEquals(DayOfWeek.MONDAY, WeekStartDay.MONDAY.javaDay)
    }
}
