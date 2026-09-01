package app.chompass.ui.components

import app.chompass.models.UnitFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MagnitudeParseTest {
    @Test
    fun typedValueKeepsExactNumber() {
        assertEquals(187.0, parseMagnitude("187", 0.0, 400.0, 5.0, '.')!!, 0.0)
    }

    @Test
    fun localeCommaDecimal() {
        assertEquals(72.4, parseMagnitude("72,4", 30.0, 250.0, 0.1, ',')!!, 1e-9)
    }

    @Test
    fun emptyKeepsPrevious() {
        assertNull(parseMagnitude("", 0.0, 100.0, 1.0, '.'))
        assertNull(parseMagnitude("  ", 0.0, 100.0, 1.0, '.'))
        assertNull(parseMagnitude("-", 0.0, 100.0, 1.0, '.'))
    }

    @Test
    fun unparsable() {
        assertNull(parseMagnitude("abc", 0.0, 100.0, 1.0, '.'))
    }

    @Test
    fun clampBelowMin() {
        assertEquals(50.0, parseMagnitude("10", 50.0, 200.0, 1.0, '.')!!, 0.0)
    }

    @Test
    fun clampAboveMax() {
        assertEquals(200.0, parseMagnitude("999", 50.0, 200.0, 1.0, '.')!!, 0.0)
    }

    @Test
    fun intHelper() {
        assertEquals(187, parseMagnitudeInt("187", 0, 400, 5))
        assertNull(parseMagnitudeInt("", 0, 400, 5))
    }

    @Test
    fun intHelperRoundsTypedDecimal() {
        // Truncation committed 72.9 as 72 on integer wheels (#64 leftover).
        assertEquals(73, parseMagnitudeInt("72.9", 0, 400, 1))
        assertEquals(72, parseMagnitudeInt("72.1", 0, 400, 1))
        assertEquals(73, parseMagnitudeInt("72,9", 0, 400, 1, ','))
    }

    @Test
    fun splitDecimalDoesNotPaint803As802() {
        // Codeberg #63: truncate-toward-zero painted 80.3 as 80.2 so Save kept 80.3.
        assertEquals(80 to 1, splitDecimalParts(80.1, 30, 250))
        assertEquals(80 to 2, splitDecimalParts(80.2, 30, 250))
        assertEquals(80 to 3, splitDecimalParts(80.3, 30, 250))
        assertEquals(80 to 4, splitDecimalParts(80.4, 30, 250))
        assertEquals(70 to 9, splitDecimalParts(70.9, 30, 250))
        assertEquals(80 to 0, splitDecimalParts(80.0, 30, 250))
        assertEquals(81 to 0, splitDecimalParts(80.96, 30, 250))
    }

    @Test
    fun storageGridKeepsKgWheelValues() {
        // #63 invariant: a kg wheel pick must survive storage quantization unchanged.
        assertEquals(80.3, UnitFormat.roundKgToHundredths(80.3), 0.0)
        assertEquals(80.2, UnitFormat.roundKgToHundredths(80.2), 0.0)
        assertEquals(80.1, UnitFormat.roundKgToHundredths(80.1), 0.0)
    }
}
