package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.models.NutritionConstants
import app.chompass.services.health.ExternalBodyFat
import app.chompass.services.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/** Clamp a body-fat fraction into the valid 3–65% window; null when non-finite.
 *  The repository is the boundary validation — [BodyFatEntry] stays a dumb record. */
internal fun sanitizeBodyFatFraction(value: Double): Double? =
    value.takeIf { it.isFinite() }
        ?.coerceIn(NutritionConstants.BODY_FAT_MIN_FRACTION, NutritionConstants.BODY_FAT_MAX_FRACTION)

/** A jump beyond the profile's body-fat accuracy (in fraction terms) is treated
 *  as a one-off spike (scale misread, water shift) until a second reading
 *  confirms it. */
private fun BodyFatEntry.spikeJumpFraction(accuracyPoints: Double): Double = accuracyPoints / 100.0

/** The previous reading only counts as spike baseline when it is this recent. */
private const val BODY_FAT_SPIKE_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

/**
 * The reading that represents the user's current body fat: the newest valid
 * entry, unless it jumps beyond the profile's measurement accuracy
 * ([accuracyPoints], percentage points) over the immediately preceding reading
 * within 14 days — then the previous entry stands (a guarded spike is ignored
 * for the profile but stays in history; a second confirming reading makes the
 * new level representative).
 */
internal fun representativeBodyFat(entries: List<BodyFatEntry>, accuracyPoints: Double): BodyFatEntry? {
    val valid = entries.filter { sanitizeBodyFatFraction(it.bodyFatFraction) != null }.sortedBy { it.date }
    val newest = valid.lastOrNull() ?: return null
    val previous = valid.dropLast(1).lastOrNull()
    val recent = previous != null &&
        abs(newest.date.toEpochMilli() - previous.date.toEpochMilli()) <= BODY_FAT_SPIKE_WINDOW_MS
    val jump = newest.spikeJumpFraction(accuracyPoints)
    return if (recent && abs(newest.bodyFatFraction - previous.bodyFatFraction) > jump) {
        previous
    } else {
        newest
    }
}

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
    private val health: HealthConnectManager? = null,
    private val sync: app.chompass.sync.SyncRepository? = null,
) {
    val entries: Flow<List<BodyFatEntry>> = prefs.bodyFatEntries.map { it.sortedBy { e -> e.date } }

    val latest: Flow<BodyFatEntry?> = prefs.bodyFatEntries.map { list ->
        list.maxByOrNull { it.date }
    }

    /** Safe to call repeatedly — no-ops once any entries exist. Mirrors
     *  WeightRepository.seedInitialWeightIfEmpty. [at] aligns the entry date with
     *  the profile's bodyFatUpdatedAt stamp so the recency gate sees one truth. */
    suspend fun seedInitialBodyFatIfEmpty(fraction: Double, at: Instant = Instant.now()) {
        if (prefs.bodyFatEntries.first().isNotEmpty()) return
        addEntry(BodyFatEntry(date = at, bodyFatFraction = fraction), forceProfile = true)
    }

    /**
     * Store one reading. The fraction is sanitized (clamped into 3–65%;
     * non-finite readings are refused). [forceProfile] marks an explicit user
     * entry (Settings sheet, Progress manual entry, tape confirmation, seed):
     * it drives the profile update directly instead of running the spike
     * guard; automatic imports use the guard.
     */
    suspend fun addEntry(entry: BodyFatEntry, forceProfile: Boolean = false) {
        val fraction = sanitizeBodyFatFraction(entry.bodyFatFraction) ?: return
        val safe = entry.copy(bodyFatFraction = fraction)
        prefs.applyBodyFatBucketChanges(upsertsByMonth = mapOf(safe.month() to listOf(safe)))
        sync?.touch(safe.id, "bodyfat")
        syncProfileBodyFatToLatest(prefer = if (forceProfile) safe else null)
        if (shouldSyncHealth()) {
            health?.writeBodyFat(safe)
        }
    }

    suspend fun deleteEntry(id: UUID) {
        val existing = prefs.bodyFatEntries.first().firstOrNull { it.id == id } ?: return
        // Tombstone BEFORE the bucket removal (upstream d03be0ea): the id lands in
        // healthDeletedBodyFatIds so a later Health Connect import mapping onto the
        // same deterministic id is filtered out instead of resurrecting the record.
        prefs.setHealthDeletedBodyFatIds(appendTombstoneId(prefs.healthDeletedBodyFatIds.first(), id))
        prefs.applyBodyFatBucketChanges(removalIdsByMonth = mapOf(existing.month() to setOf(id)))
        sync?.tombstone(id, "bodyfat")
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
        val tombstoned = prefs.healthDeletedBodyFatIds.first()
        val incoming = external
            .asSequence()
            .map {
                BodyFatEntry(
                    id = manager.ownRecordId(it.clientRecordId)
                        ?: externalId(it.clientRecordId, it.recordId, it.time),
                    date = it.time,
                    bodyFatFraction = it.bodyFatFraction
                )
            }
            // Deleted stays deleted (upstream d03be0ea), fudai_ tag or not — a
            // surviving own-record HC delete must not resurrect the reading.
            .filter { it.id !in tombstoned }
            // Out-of-range readings (3–65%) never enter history or the profile.
            .filter { sanitizeBodyFatFraction(it.bodyFatFraction) != null }
            .toList()
        if (incoming.isEmpty()) return
        // Same #63 contract as WeightRepository.importExternalWeights: upsert
        // into current month files, never replaceAll from a stale snapshot.
        prefs.applyBodyFatBucketChanges(upsertsByMonth = incoming.groupBy { it.month() })
        syncProfileBodyFatToLatest()
    }

    /**
     * Merge file-imported body-fat readings into local history. Same by-id upsert
     * semantics as [importExternalBodyFats]; the importer assigns deterministic ids,
     * so re-importing the same file is a no-op. Does NOT push to Health Connect —
     * see WeightRepository.importFromFile. Returns entries added or updated.
     */
    suspend fun importFromFile(entries: List<BodyFatEntry>): Int {
        // Out-of-range readings (3–65%) are dropped instead of stored.
        val valid = entries.filter { sanitizeBodyFatFraction(it.bodyFatFraction) != null }
        if (valid.isEmpty()) return 0
        val existing = prefs.bodyFatEntries.first()
        val (merged, changed) = mergeBodyFatsById(existing, valid)
        // Same duplicate-collapse persistence as WeightRepository (upstream
        // fud-ai 736f25fc).
        if (changed == 0 && merged.size == existing.size) return 0
        // Upsert into month files. Do not replaceAll from a snapshot taken
        // around a slow read: that wipe is #63 (a reading logged while the
        // import is in flight would disappear with the stale list) — and a
        // month that failed to decode would be deleted instead of preserved.
        val existingById = existing.associateBy { it.id }
        val duplicateIds = existing.groupBy { it.id }.filterValues { it.size > 1 }.keys
        val upserts = merged.filter { row ->
            val prev = existingById[row.id]
            prev == null || abs(prev.bodyFatFraction - row.bodyFatFraction) > 0.0001 || prev.date != row.date ||
                row.id in duplicateIds
        }
        prefs.applyBodyFatBucketChanges(upsertsByMonth = upserts.groupBy { it.month() })
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

    private fun BodyFatEntry.month(): YearMonth = YearMonth.from(date.atZone(ZoneId.systemDefault()))

    /** Keep UserProfile.bodyFatPercentage aligned with the representative reading so
     *  Katch-McArdle BMR + Settings → Body Fat row never drift apart. [prefer] (an
     *  explicit user entry) wins only while no valid stored entry is newer — a
     *  past-dated entry cannot pull the profile backward. Falls back to
     *  [representativeBodyFat] (spike-guarded). If the store is empty, leave the
     *  profile value alone — silently dropping someone's BMR formula because they
     *  cleared one row would surprise them; they can clear it explicitly via
     *  Settings. Auto-sourced goals re-clamp when the new reading moves BMR. */
    private suspend fun syncProfileBodyFatToLatest(prefer: BodyFatEntry? = null) {
        val profile = profileRepository.current() ?: return
        val stored = prefs.bodyFatEntries.first()
        val preferValid = prefer?.takeIf { candidate ->
            stored.none { it.date > candidate.date && sanitizeBodyFatFraction(it.bodyFatFraction) != null }
        }
        val chosen = preferValid
            ?: representativeBodyFat(stored, profile.effectiveBodyFatRepeatabilityPoints)
            ?: return
        if (abs((profile.bodyFatPercentage ?: -1.0) - chosen.bodyFatFraction) > 0.0001 ||
            profile.bodyFatUpdatedAt != chosen.date
        ) {
            profileRepository.save(
                profile.copy(
                    bodyFatPercentage = chosen.bodyFatFraction,
                    bodyFatUpdatedAt = chosen.date,
                ).reclampedAutoGoal()
            )
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
