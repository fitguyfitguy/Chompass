package app.chompass.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-value regression tests for the shared external-input sanitizer. */
class InputSanitizerTest {
    @Test
    fun clamp_boundsIntsAndDoubles() {
        assertEquals(100, InputSanitizer.clamp(9_999, 0, 100, 0))
        assertEquals(0, InputSanitizer.clamp(-5, 0, 100, 0))
        assertEquals(7, InputSanitizer.clamp(7, 0, 100, 0))
        assertEquals(0.0, InputSanitizer.clamp(-1.0, 0.0, 100.0, 0.0), 0.0)
        assertEquals(0.0, InputSanitizer.clamp(Double.NaN, 0.0, 100.0, 0.0), 0.0)
        assertEquals(0.0, InputSanitizer.clamp(null, 0.0, 100.0, 0.0), 0.0)
    }

    @Test
    fun nutrient_dropsNonFiniteAndBindsToMacroRange() {
        assertNull(InputSanitizer.nutrient(Double.NaN))
        assertNull(InputSanitizer.nutrient(Double.POSITIVE_INFINITY))
        assertNull(InputSanitizer.nutrient(null))
        assertEquals(0.0, InputSanitizer.nutrient(-3.0)!!, 0.0)
        assertEquals(2_000.0, InputSanitizer.nutrient(9_999.0)!!, 0.0)
        assertEquals(12.5, InputSanitizer.nutrient(12.5)!!, 0.0)
    }

    @Test
    fun micro_allowsLargeMgValues_butBoundsAbsurdity() {
        assertEquals(2_500.0, InputSanitizer.micro(2_500.0)!!, 0.0)
        assertEquals(InputSanitizer.MAX_MICRO_UNITS, InputSanitizer.micro(9_999_999.0)!!, 0.0)
        assertNull(InputSanitizer.micro(Double.NaN))
        assertEquals(0.0, InputSanitizer.micro(-1.0)!!, 0.0)
    }

    @Test
    fun text_stripsControlAndBidiOverrides_keepsZwj() {
        assertNull(InputSanitizer.text("\u0000\u0001   ", InputSanitizer.MAX_NAME_LENGTH))
        assertEquals(
            "evilname",
            InputSanitizer.text("evil\u0000na\u0008me", InputSanitizer.MAX_NAME_LENGTH),
        )
        assertEquals(
            "notewithoverride",
            InputSanitizer.text("note\u0000with\u202Eoverride", InputSanitizer.MAX_NOTE_LENGTH),
        )
        // ZWJ emoji sequences survive (they are Cf but not spoofing-relevant).
        assertEquals("ok \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", InputSanitizer.text("ok \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", 20))
    }

    @Test
    fun text_capsLength_andTrims() {
        val long = "n".repeat(500)
        assertEquals(InputSanitizer.MAX_NAME_LENGTH, InputSanitizer.text(long, InputSanitizer.MAX_NAME_LENGTH)!!.length)
        assertEquals("hi", InputSanitizer.text("  hi  ", 4))
    }

    @Test
    fun servingGrams_andQuantity_nullWhenInvalid() {
        assertNull(InputSanitizer.servingGrams(0.0))
        assertNull(InputSanitizer.servingGrams(-100.0))
        assertNull(InputSanitizer.servingGrams(Double.NaN))
        assertNotNull(InputSanitizer.servingGrams(250.0))
        assertNull(InputSanitizer.quantity(Double.POSITIVE_INFINITY))
    }
}
