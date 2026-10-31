package app.chompass.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * True when a WebDAV URL is set, so touch/tombstone should record revisions.
 * Auto-sync is a separate toggle: Sync Now still runs without it, and deletes
 * must tombstone or the next pull re-injects the row (#39).
 */
fun shouldTrackSyncRevisions(webDavUrl: String): Boolean =
    normalizeWebDavUrl(webDavUrl).isNotEmpty()

/**
 * Decides whether an opt-in auto sync or local backup should run on this app open.
 * At most once per local calendar day; skipped if a successful run already happened today.
 */
fun shouldAutoRunOnOpen(
    enabled: Boolean,
    configured: Boolean,
    today: LocalDate,
    lastRunAtIso: String?,
    lastAutoRunDayIso: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (!enabled || !configured) return false
    if (lastAutoRunDayIso == today.toString()) return false
    val lastRunDay = lastRunAtIso
        ?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull() }
    if (lastRunDay == today) return false
    return true
}
