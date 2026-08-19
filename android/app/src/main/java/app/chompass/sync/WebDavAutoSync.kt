package app.chompass.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides whether opt-in WebDAV auto-sync should run on this app open.
 * At most once per local calendar day; skipped if a successful sync already happened today.
 */
/**
 * True when a WebDAV URL is set, so touch/tombstone should record revisions.
 * Auto-sync is a separate toggle: Sync Now still runs without it, and deletes
 * must tombstone or the next pull re-injects the row (#39).
 */
fun shouldTrackSyncRevisions(webDavUrl: String): Boolean =
    normalizeWebDavUrl(webDavUrl).isNotEmpty()

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
