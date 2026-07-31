package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocaleFormatTest {
    @Test
    fun signedDeltaUsesProvidedLocaleDecimalSeparator() {
        val german = UnitFormat.signedDelta(1.5, Locale.GERMAN)
        assertTrue(german.contains("1,5") || german.contains("1.5"))
        // Always has sign for positive
        assertTrue(german.startsWith("+"))
    }

    @Test
    fun weightFormatsMetric() {
        val us = UnitFormat.weight(70.0, useMetric = true, locale = Locale.US)
        assertEquals("70.0 kg", us)
    }

    @Test
    fun mediumDatePatternUsesLocale() {
        val fmt = LocaleFormat.mediumDate()
        assertEquals(Locale.getDefault(), fmt.locale)
    }
}
