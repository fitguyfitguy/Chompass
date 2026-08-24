package app.chompass.ui.util

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats [epochMillis] as the device's clock time ("20:30" or "8:30 PM").
 * The one safe way to format an epoch millis with the user's clock pattern:
 * [clockTimePattern] carries no zone, so formatting a raw [Instant] throws
 * [java.time.temporal.UnsupportedTemporalTypeException] — this applies the
 * system zone first (the crash fixed in 46accde4, still inlined at three
 * call sites). Honors the device 24-hour setting via [clockTimePattern].
 */
fun formatClockMillis(context: Context, epochMillis: Long): String =
    DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
