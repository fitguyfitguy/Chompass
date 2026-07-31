package app.chompass.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides whether opt-in WebDAV auto-sync should run on this app open.
 * At most once per local calendar day; skipped if a successful sync already happened today.
 */
fun shouldAutoSyncWebDav(
    enabled: Boolean,
    configured: Boolean,
    today: LocalDate,
    lastSyncAtIso: String?,
    lastAutoSyncDayIso: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (!enabled || !configured) return false
    if (lastAutoSyncDayIso == today.toString()) return false
    val lastSyncDay = lastSyncAtIso
        ?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull() }
    if (lastSyncDay == today) return false
    return true
}
