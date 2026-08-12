package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun expressions_areAbsoluteArithmetic() {
        assertEquals(100.0, delta("50×2", 50.0), 0.001)
        assertEquals(100.0, delta("50*2", 50.0), 0.001)
        assertEquals(170.0, delta("200−30", 50.0), 0.001)
        assertEquals(170.0, delta("200-30", 50.0), 0.001)
        assertEquals(25.0, delta("100÷4", 50.0), 0.001)
        assertEquals(25.0, delta("100/4", 50.0), 0.001)
        assertEquals(40.0, delta("20+20", 50.0), 0.001)
        // Expressions ignore the current value — they are absolute.
        assertEquals(100.0, delta("50×2", null), 0.001)
    }

    @Test
    fun expressions_applyOperatorPrecedence() {
        assertEquals(14.0, delta("2+3×4", 50.0), 0.001)
        assertEquals(14.0, delta("2+3*4", 50.0), 0.001)
        assertEquals(5.5, delta("1+9÷2", 50.0), 0.001)
        assertEquals(0.0, delta("10−5×2", 50.0), 0.001)
    }

    @Test
    fun expressions_acceptWhitespaceAndCommaDecimals() {
        assertEquals(100.0, delta(" 50 × 2 ", 50.0), 0.001)
        assertEquals(125.0, delta("50×2,5", 50.0, Locale.GERMANY), 0.001)
        assertEquals(125.0, delta("50×2.5", 50.0), 0.001)
    }

    @Test
    fun expressions_malformed_returnsNull() {
        assertNull(ServingUnitOption.applyDeltaInput("50×", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("×2", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("50××2", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("50×2×", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("50÷0", 50.0))
        assertNull(ServingUnitOption.applyDeltaInput("1+×2", 50.0))
    }

    @Test
    fun isQuantityExpression_distinguishesDeltas() {
        assertTrue(ServingUnitOption.isQuantityExpression("50×2"))
        assertTrue(ServingUnitOption.isQuantityExpression("200−30"))
        assertFalse(ServingUnitOption.isQuantityExpression("+20"))
        assertFalse(ServingUnitOption.isQuantityExpression("-20"))
        assertFalse(ServingUnitOption.isQuantityExpression("50"))
        assertFalse(ServingUnitOption.isQuantityExpression(""))
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
    fun innerSigns_nowEvaluateAsExpressions() {
        // "20+20" used to be rejected; it is now a valid expression.
        assertEquals(40.0, delta("20+20", 50.0), 0.001)
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
