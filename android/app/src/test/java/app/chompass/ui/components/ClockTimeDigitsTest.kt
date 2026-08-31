package app.chompass.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * Codeberg #77 typed-mode clock field: digits fill HH then MM positionally
 * (1200 -> 12:00, 930 -> 09:30). Guards the deletion/re-entry freeze fix —
 * partial entries must parse without ever re-padding the caller's draft.
 */
class ClockTimeDigitsTest {
    @Test
    fun fourDigits_parseAsHHMM() {
        assertEquals(LocalTime.of(12, 0), parseClockDigits("1200"))
        assertEquals(LocalTime.of(9, 5), parseClockDigits("0905"))
        assertEquals(LocalTime.of(23, 59), parseClockDigits("2359"))
    }
    @Test
    fun threeDigits_parseAsHM() {
        assertEquals(LocalTime.of(9, 30), parseClockDigits("930"))
        assertEquals(LocalTime.of(1, 23), parseClockDigits("123"))
    }
    @Test
    fun oneOrTwoDigits_parseAsHourOnly() {
        assertEquals(LocalTime.of(9, 0), parseClockDigits("9"))
        assertEquals(LocalTime.of(12, 0), parseClockDigits("12"))
    }

    @Test
    fun outOfRange_returnsNull() {
        assertNull(parseClockDigits("2400"))
        assertNull(parseClockDigits("1260"))
        assertNull(parseClockDigits(""))
    }

    @Test
    fun nonDigitCharacters_areIgnored() {
        assertEquals(LocalTime.of(12, 0), parseClockDigits("12:00"))
        assertEquals(LocalTime.of(12, 0), parseClockDigits("1.200"))
    }
}
