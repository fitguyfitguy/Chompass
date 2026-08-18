package app.chompass.models

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * User-facing date/number formatters that follow the app locale
 * ([Locale.getDefault], including Android 13+ per-app language).
 *
 * Keep [Locale.US] only for export/protocol/parse paths — not display.
 */
object LocaleFormat {
    fun displayLocale(): Locale = Locale.getDefault()

    fun mediumDate(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", displayLocale())

    fun shortDate(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", displayLocale())

    fun monthYear(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", displayLocale())

    fun mediumDateZoned(zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        mediumDate().withZone(zone)

    fun shortDateZoned(zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        shortDate().withZone(zone)

    /** Medium date + local clock time (e.g. "Aug 18, 2026 · 9:03 AM"). */
    fun mediumDateTimeZoned(zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("MMM d, yyyy · h:mm a", displayLocale())
            .withZone(zone)

    fun monthOrDayZoned(showsYear: Boolean, zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        DateTimeFormatter
            .ofPattern(if (showsYear) "MMM yyyy" else "MMM d", displayLocale())
            .withZone(zone)

    fun decimal(value: Double, fractionDigits: Int = 1): String =
        String.format(displayLocale(), "%.${fractionDigits}f", value)

    fun integer(value: Int): String =
        String.format(displayLocale(), "%,d", value)

    fun integer(value: Long): String =
        String.format(displayLocale(), "%,d", value)

    /** Decimal-mark character for the display locale (e.g. '.' en, ',' de). */
    fun decimalSeparator(): Char =
        DecimalFormatSymbols.getInstance(displayLocale()).decimalSeparator
}
