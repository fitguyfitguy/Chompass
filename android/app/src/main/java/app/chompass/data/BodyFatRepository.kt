package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.services.health.ExternalBodyFat
import app.chompass.services.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * Local-only store for body-fat history. Mirrors WeightRepository — Codable
 * persistence via PreferencesStore, no goal-crossing notification yet, and
 * (until Health Connect is wired up) no external sync.
 *
 * The latest entry's value is treated as the user's "current" body fat % and
 * pushed back to UserProfile.bodyFatPercentage on every add via
 * syncProfileBodyFatToLatest, so Katch-McArdle BMR + Settings → Body Fat row
 * never drift apart.
 */
class BodyFatRepository(
    private val prefs: PreferencesStore,
    private val profileRepository: ProfileRepository,
    private val health: HealthConnectManager? = null
) {
    val entries: Flow<List<BodyFatEntry>> = prefs.bodyFatEntries.map { it.sortedBy { e -> e.date } }

    val latest: Flow<BodyFatEntry?> = prefs.bodyFatEntries.map { list ->
        list.maxByOrNull { it.date }
    }

    /** Safe to call repeatedly — no-ops once any entries exist. Mirrors
     *  WeightRepository.seedInitialWeightIfEmpty. */
    suspend fun seedInitialBodyFatIfEmpty(fraction: Double) {
        if (prefs.bodyFatEntries.first().isNotEmpty()) return
        addEntry(BodyFatEntry(bodyFatFraction = fraction))
    }

    suspend fun addEntry(entry: BodyFatEntry) {
        val current = prefs.bodyFatEntries.first()
        prefs.setBodyFatEntries(current + entry)
        syncProfileBodyFatToLatest()
        if (shouldSyncHealth()) {
            health?.writeBodyFat(entry)
        }
    }

    suspend fun deleteEntry(id: UUID) {
        val current = prefs.bodyFatEntries.first()
        prefs.setBodyFatEntries(current.filter { it.id != id })
        syncProfileBodyFatToLatest()
        // Delete the HC record even when sync is off (iOS parity, best-effort) —
        // a surviving fudai-tagged record would resurrect through the own-record
        // restore path on the next full backfill.
        health?.deleteBodyFat(id)
    }

    suspend fun replaceAll(entries: List<BodyFatEntry>) {
        prefs.setBodyFatEntries(entries)
        syncProfileBodyFatToLatest()
    }

    suspend fun clear() {
        prefs.setBodyFatEntries(emptyList())
    }

    suspend fun entriesInRange(from: Instant, to: Instant): List<BodyFatEntry> =
        prefs.bodyFatEntries.first()
            .filter { it.date in from..to }
            .sortedBy { it.date }

    /**
     * Merge externally-sourced body-fat readings (e.g. a smart scale via Health
     * Connect) into local history. Idempotent: each external record maps to a
     * deterministic id so repeated imports upsert in place instead of duplicating,
     * and the user's own manual entries are preserved. Fud AI's own records restore
     * under their original UUID (reinstall recovery); the same-id upsert is a no-op
     * when the entry still exists locally. The change-token path filters own records
     * at the manager level, so live echo-imports stay suppressed — see
     * WeightRepository.importExternalWeights for the full rationale.
     */
    suspend fun importExternalBodyFats(external: List<ExternalBodyFat>) {
        val manager = health ?: return
        val incoming = external
            .map {
                BodyFatEntry(
                    id = manager.ownRecordId(it.clientRecordId)
                        ?: externalId(it.clientRecordId, it.recordId, it.time),
                    date = it.time,
                    bodyFatFraction = it.bodyFatFraction
                )
            }
        if (incoming.isEmpty()) return
        val byId = prefs.bodyFatEntries.first().associateBy { it.id }.toMutableMap()
        var changed = false
        for (entry in incoming) {
            val existing = byId[entry.id]
            if (existing == null || abs(existing.bodyFatFraction - entry.bodyFatFraction) > 0.0001 || existing.date != entry.date) {
                byId[entry.id] = entry
                changed = true
            }
        }
        if (!changed) return
        prefs.setBodyFatEntries(byId.values.sortedBy { it.date })
        syncProfileBodyFatToLatest()
    }

    /**
     * Merge file-imported body-fat readings into local history. Same by-id upsert
     * semantics as [importExternalBodyFats]; the importer assigns deterministic ids,
     * so re-importing the same file is a no-op. Does NOT push to Health Connect —
     * see WeightRepository.importFromFile. Returns entries added or updated.
     */
    suspend fun importFromFile(entries: List<BodyFatEntry>): Int {
        if (entries.isEmpty()) return 0
        val (merged, changed) = mergeBodyFatsById(prefs.bodyFatEntries.first(), entries)
        if (changed == 0) return 0
        prefs.setBodyFatEntries(merged)
        syncProfileBodyFatToLatest()
        return changed
    }

    /** Stable id for an external record: prefer the source's clientRecordId, then the
     *  Health Connect record id, then the timestamp. The VALUE is never part of the seed,
     *  so an in-place correction upserts in place instead of duplicating. */
    private fun externalId(clientRecordId: String?, recordId: String, time: Instant): UUID {
        val seed = clientRecordId?.takeIf { it.isNotBlank() }
            ?: recordId.takeIf { it.isNotBlank() }
            ?: "hc-bodyfat:${time.toEpochMilli()}"
        return UUID.nameUUIDFromBytes(seed.toByteArray())
    }

    /** Keep UserProfile.bodyFatPercentage aligned with the latest reading so
     *  Katch-McArdle BMR + Settings → Body Fat row never drift apart. If the
     *  store is empty after a delete, leave the profile value alone — silently
     *  dropping someone's BMR formula because they cleared one row would
     *  surprise them; they can clear it explicitly via Settings. */
    private suspend fun syncProfileBodyFatToLatest() {
        val profile = profileRepository.current() ?: return
        val newest = prefs.bodyFatEntries.first().maxByOrNull { it.date } ?: return
        if (abs((profile.bodyFatPercentage ?: -1.0) - newest.bodyFatFraction) > 0.0001) {
            profileRepository.save(profile.copy(bodyFatPercentage = newest.bodyFatFraction))
        }
    }

    /** Write gate — only push to Health Connect when the user granted body-fat WRITE. */
    private suspend fun shouldSyncHealth(): Boolean {
        val manager = health ?: return false
        return prefs.healthConnectEnabled.first() && manager.hasBodyFatWrite()
    }
}

/**
 * Pure merge for file imports: by-id upsert plus a near-duplicate guard (same value
 * within the same minute of an existing entry is skipped). Mirrors [mergeWeightsById].
 * Returns the merged list and the number of entries added or updated.
 */
internal fun mergeBodyFatsById(
    existing: List<BodyFatEntry>,
    incoming: List<BodyFatEntry>,
): Pair<List<BodyFatEntry>, Int> {
    val byId = existing.associateBy { it.id }.toMutableMap()
    var changed = 0
    for (entry in incoming) {
        val current = byId[entry.id]
        if (current != null) {
            if (abs(current.bodyFatFraction - entry.bodyFatFraction) > 0.0001 || current.date != entry.date) {
                byId[entry.id] = entry
                changed++
            }
            continue
        }
        val nearDuplicate = existing.any {
            abs(it.bodyFatFraction - entry.bodyFatFraction) < 0.0005 &&
                abs(it.date.toEpochMilli() - entry.date.toEpochMilli()) < 60_000
        }
        if (nearDuplicate) continue
        byId[entry.id] = entry
        changed++
    }
    return byId.values.sortedBy { it.date } to changed
}
