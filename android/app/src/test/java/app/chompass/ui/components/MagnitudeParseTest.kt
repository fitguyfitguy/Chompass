package app.chompass.ui.components

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
}
