package app.chompass.ui.util

import android.content.Context
import android.text.format.DateFormat

/**
 * The hour:minute pattern for the device's clock setting — "H:mm" (24-hour) or "h:mm a" (12-hour).
 * Honors Settings → System → Date & time → Use 24-hour format, so a phone set to 24-hour shows
 * "20:30" instead of "8:30 PM". Build a [java.time.format.DateTimeFormatter] from this at the call
 * site (adding `.withZone(...)` when formatting an Instant).
 */
fun clockTimePattern(context: Context): String =
    if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
