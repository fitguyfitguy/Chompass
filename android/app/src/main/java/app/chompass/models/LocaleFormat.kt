package app.chompass.models

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * User-facing date/number formatters that follow the app locale
 * ([Locale.getDefault], including Android 13+ per-app language).
 *
 * Keep [Locale.US] only for export/protocol/parse paths — not display.
 */
object LocaleFormat {
    /** Non-null locale for formatters. `Configuration.locales[0]` is null on some Android 10 builds (#43). */
    fun first(locale: Locale?): Locale = locale ?: Locale.getDefault() ?: Locale.US

    fun displayLocale(): Locale = first(Locale.getDefault())

    /**
     * Locale-natural medium date (e.g. "Aug 18, 2026" en-US, "18.08.2026" de) —
     * parity with the PWA's `Intl.DateTimeFormat` `dateStyle: "medium"` (UI audit
     * 2.6: word order deliberately follows the locale, see docs/LOCALIZATION.md).
     */
    fun mediumDate(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(displayLocale())

    /** Month-name short date ("Aug 18") — kept month-based for chart axes and day rows. */
    fun shortDate(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", displayLocale())

    fun monthYear(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", displayLocale())

    fun mediumDateZoned(zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        mediumDate().withZone(zone)

    fun shortDateZoned(zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        shortDate().withZone(zone)

    /** Medium date + local clock time (e.g. "Aug 18, 2026 · 4:32 PM" en-US, "18.08.2026 · 16:32" de). */
    fun mediumDateTimeZoned(zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            .appendLiteral(" · ")
            .append(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            .toFormatter(displayLocale())
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

    /** Whether the display locale uses 24-hour clock (honors Android 24-hour setting). */
    fun is24Hour(context: android.content.Context): Boolean =
        android.text.format.DateFormat.is24HourFormat(context)
}
