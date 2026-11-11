package app.chompass.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * R7 (UX friction audit) typed-mode date field: digits fill the locale's
 * wheel column order positionally — MMDDYYYY for en, DDMMYYYY for de,
 * YYYYMMDD for ja — mirroring the clock field's HHMM entry. Guards the parse
 * rules: complete 8 digits only, month/day validated, year clamped into the
 * wheel's year range before the day is checked.
 */
class DateDigitsTest {
    private val mdy = listOf(DateWheelColumn.MONTH, DateWheelColumn.DAY, DateWheelColumn.YEAR)
    private val dmy = listOf(DateWheelColumn.DAY, DateWheelColumn.MONTH, DateWheelColumn.YEAR)
    private val ymd = listOf(DateWheelColumn.YEAR, DateWheelColumn.MONTH, DateWheelColumn.DAY)

    @Test
    fun eightDigits_parseInColumnOrder() {
        assertEquals(LocalDate.of(2026, 11, 5), parseDateDigits("11052026", mdy, 1920, 2026))
        assertEquals(LocalDate.of(2026, 11, 5), parseDateDigits("05112026", dmy, 1920, 2026))
        assertEquals(LocalDate.of(2026, 11, 5), parseDateDigits("20261105", ymd, 1920, 2026))
    }

    @Test
    fun invalidMonth_returnsNull() {
        assertNull(parseDateDigits("13252026", mdy, 1920, 2026))
        assertNull(parseDateDigits("00252026", mdy, 1920, 2026))
    }

    @Test
    fun dayBeyondMonthLength_returnsNull() {
        assertNull(parseDateDigits("02312026", mdy, 1920, 2026))
        // 2025 is not a leap year; 2024 is.
        assertNull(parseDateDigits("02292025", mdy, 1920, 2026))
        assertEquals(LocalDate.of(2024, 2, 29), parseDateDigits("02292024", mdy, 1920, 2026))
    }

    @Test
    fun partialOrExtraDigits_returnNull() {
        assertNull(parseDateDigits("1105202", mdy, 1920, 2026))
        assertNull(parseDateDigits("110520261", mdy, 1920, 2026))
        assertNull(parseDateDigits("", mdy, 1920, 2026))
    }

    @Test
    fun nonDigitCharacters_areIgnored() {
        assertEquals(LocalDate.of(2026, 11, 5), parseDateDigits("11/05/2026", mdy, 1920, 2026))
        assertEquals(LocalDate.of(2026, 11, 5), parseDateDigits("05.11.2026", dmy, 1920, 2026))
    }

    @Test
    fun outOfRangeYear_clampedIntoWheelRangeBeforeDayCheck() {
        assertEquals(LocalDate.of(2026, 2, 15), parseDateDigits("02152030", mdy, 1920, 2026))
        assertEquals(LocalDate.of(1920, 1, 11), parseDateDigits("01111919", mdy, 1920, 2026))
        // Clamp lands on a non-leap year: the day no longer exists → rejected.
        assertNull(parseDateDigits("02292030", mdy, 1920, 2026))
    }

    @Test
    fun parseTypedDate_commitsOnlyCompleteDatesInTheGivenOrder() {
        // Device order lookup is injected: getBestDateTimePattern is
        // unimplemented on the plain JVM test stack.
        assertEquals(LocalDate.of(2026, 11, 5), parseTypedDate("11052026", 1920, 2026, mdy))
        assertEquals(LocalDate.of(2026, 11, 5), parseTypedDate("05112026", 1920, 2026, dmy))
        // Partials never commit at OK time.
        assertNull(parseTypedDate("1105", 1920, 2026, mdy))
        assertNull(parseTypedDate(null, 1920, 2026, mdy))
    }
}
