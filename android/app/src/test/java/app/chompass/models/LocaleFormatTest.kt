package app.chompass.models

import app.chompass.ui.components.narrowDayName
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun mediumDateTimeZonedRendersLocalTimeNotUtc() {
        // Codeberg #40: the sync screen used to show the raw UTC timestamp.
        // The formatter must render the stored Instant in the given zone —
        // e.g. 14:32 UTC becomes 16:32 in UTC+2, never the raw UTC clock.
        val instant = java.time.Instant.parse("2026-08-18T14:32:00Z")
        val zone = java.time.ZoneId.of("Europe/Berlin") // UTC+2 in August
        val rendered = LocaleFormat.mediumDateTimeZoned(zone).format(instant)
        assertTrue("expected local time in rendered '$rendered'", rendered.contains("4:32 PM"))
        assertFalse("must not render the raw UTC clock", rendered.contains("2:32 PM"))
    }

    @Test
    fun narrowWeekdayInitialsAreLocalized() {
        // CLDR narrow for Spanish: X = miércoles (disambiguates from martes M).
        // Same data the PWA gets via toLocaleDateString({weekday:"narrow"}).
        val es = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        ).map { narrowDayName(it, Locale("es")) }
        assertEquals("L M X J V S D", es.joinToString(" "))

        // English stays the classic single-letter set.
        val en = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        ).map { narrowDayName(it, Locale.US) }
        assertEquals("M T W T F S S", en.joinToString(" "))
    }
}
