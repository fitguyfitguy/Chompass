package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ServingUnitDeltaTest {
    private fun delta(text: String, current: Double?, locale: Locale = Locale.getDefault()): Double =
        ServingUnitOption.applyDeltaInput(text, current, locale)!!

    @Test
    fun plainNumbers_parseAsBefore() {
        assertEquals(20.0, delta("20", 50.0), 0.001)
        assertEquals(1.5, delta("1.5", 50.0), 0.001)
    }

    @Test
    fun plusDelta_addsToCurrent() {
        assertEquals(70.0, delta("+20", 50.0), 0.001)
        assertEquals(52.5, delta("+2.5", 50.0), 0.001)
    }

    @Test
    fun minusDelta_subtractsFromCurrent() {
        assertEquals(40.0, delta("-10", 50.0), 0.001)
        assertEquals(49.0, delta("-1", 50.0), 0.001)
    }

    @Test
    fun deltaMayGoBelowZero_rawArithmetic() {
        // Callers ignore non-positive results; the helper stays arithmetic.
        assertEquals(-10.0, delta("-60", 50.0), 0.001)
    }

    @Test
    fun deltaOnNullCurrent_usesZero() {
        assertEquals(20.0, delta("+20", null), 0.001)
        assertEquals(-10.0, delta("-10", null), 0.001)
    }

    @Test
    fun deltaIgnoresInnerSigns() {
        // Only a leading sign is a delta; "20+20" is not an expression.
        assertNull(ServingUnitOption.applyDeltaInput("20+20", 50.0))
    }

    @Test
    fun loneSign_returnsNull() {
        assertNull(ServingUnitOption.applyDeltaInput("+", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("-", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("   ", 50.0))
    }

    @Test
    fun deltaWithSurroundingWhitespace_parses() {
        assertEquals(70.0, delta("+20 ", 50.0), 0.001)
        assertEquals(70.0, delta(" + 20", 50.0), 0.001)
    }

    @Test
    fun commaDecimalLocale_stillParses() {
        assertEquals(70.5, delta("+20,5", 50.0, Locale.GERMANY), 0.001)
        assertEquals(20.5, delta("20,5", 50.0, Locale.GERMANY), 0.001)
    }

    @Test
    fun formatQuantity_roundTripsDeltaResult() {
        assertEquals("70", ServingUnitOption.formatQuantity(delta("+20", 50.0)))
    }
}
