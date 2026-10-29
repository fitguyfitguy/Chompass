package app.chompass.data

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.NutritionConstants
import app.chompass.services.health.ExternalBodyComposition
import app.chompass.services.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/**
 * A measured lean-mass value only when finite and inside the plausible window
 * (20–250 kg). Both the store and [app.chompass.models.UserProfile] gate on this, so a
 * typo cannot reach Katch-McArdle from either side.
 */
internal fun sanitizeLeanMassKg(value: Double?): Double? = value?.takeIf {
    it.isFinite() && it in NutritionConstants.LEAN_MASS_MIN_KG..NutritionConstants.LEAN_MASS_MAX_KG
}

/**
 * Boundary validation for a stored row: every mass must be finite and positive, the
 * visceral rating must sit inside the vendor 1–59 window. The repository is the
 * boundary — [BodyCompositionEntry] stays a dumb record.
 */
internal fun BodyCompositionEntry.sanitizedComposition(): BodyCompositionEntry = copy(
    boneMassKg = boneMassKg?.takeIf { it.isFinite() && it > 0 },
    totalBodyWaterKg = totalBodyWaterKg?.takeIf { it.isFinite() && it > 0 },
    muscleMassKg = muscleMassKg?.takeIf { it.isFinite() && it > 0 },
    leanMassKg = leanMassKg?.takeIf { it.isFinite() && it > 0 },
    visceralFatRating = visceralFatRating?.takeIf { it.isFinite() && it in 1.0..59.0 },
)

/**
 * Store for bioimpedance-scale composition rows (bone / water / muscle / lean / visceral).
 * Mirrors BodyFatRepository minus the spike guard: composition fields have no
 * profile-level accuracy model to spike against.
 *
 * The only calculation side effect: the newest valid lean-mass reading is stamped onto
 * [app.chompass.models.UserProfile.measuredLeanMassKg] (+ its timestamp), which takes
 * priority over body-fat-derived lean mass for Katch-McArdle BMR and the protein cap.
 * Imports without a lean mass (e.g. bone-only rows) leave the stamp alone.
 */
class BodyCompositionRepository(
    private val prefs: PreferencesStore,
    private val profileRepository: ProfileRepository,
    private val health: HealthConnectManager? = null,
    private val sync: app.chompass.sync.SyncRepository? = null,
) {
    /** Serializes read-decide-write sequences (per-day upsert, delete, import). */
    private val writeMutex = Mutex()

    val entries: Flow<List<BodyCompositionEntry>> =
        prefs.bodyCompositions.map { it.sortedBy { e -> e.date } }

    val latest: Flow<BodyCompositionEntry?> =
        prefs.bodyCompositions.map { list -> list.maxByOrNull { it.date } }

    /**
     * Log a scale reading. Editing a metric twice on the same calendar day updates that
     * day's single row (the incoming non-null values win, the rest are carried forward),
     * mirroring [BodyMeasurementRepository.setValue]. An all-null entry is a no-op.
     */
    suspend fun upsert(entry: BodyCompositionEntry) = writeMutex.withLock {
        val safe = entry.sanitizedComposition()
        if (!safe.hasAnyValue) return@withLock
        val zone = ZoneId.systemDefault()
        val day = safe.date.atZone(zone).toLocalDate()
        val sameDay = prefs.bodyCompositions.first()
            .filter { it.date.atZone(zone).toLocalDate() == day }
            .sortedBy { it.date }
        if (sameDay.isEmpty()) {
            prefs.applyCompositionBucketChanges(upsertsByMonth = mapOf(safe.month() to listOf(safe)))
            sync?.touch(safe.id, "composition")
        } else {
            val anchor = sameDay.first()
            val merged = sameDay.drop(1).fold(anchor) { acc, row -> acc.fillingGapsFrom(row) }
                .overlayFrom(safe)
            prefs.applyCompositionBucketChanges(
                upsertsByMonth = mapOf(merged.month() to listOf(merged)),
                removalIdsByMonth = mapOf(
                    anchor.month() to (sameDay.map { it.id } - merged.id).toSet()
                ),
            )
            sync?.touch(merged.id, "composition")
        }
        syncProfileLeanMassToLatest(prefer = safe)
    }

    suspend fun deleteEntry(id: UUID) {
        writeMutex.withLock {
            val existing = prefs.bodyCompositions.first().firstOrNull { it.id == id } ?: return@withLock
            prefs.applyCompositionBucketChanges(
                removalIdsByMonth = mapOf(existing.month() to setOf(id)),
            )
        }
        sync?.tombstone(id, "composition")
        syncProfileLeanMassToLatest()
    }

    suspend fun replaceAll(entries: List<BodyCompositionEntry>) {
        prefs.setBodyCompositions(entries)
        syncProfileLeanMassToLatest()
    }

    suspend fun clear() {
        prefs.setBodyCompositions(emptyList())
    }

    suspend fun entriesInRange(from: Instant, to: Instant): List<BodyCompositionEntry> =
        prefs.bodyCompositions.first()
            .filter { it.date in from..to }
            .sortedBy { it.date }

    /**
     * Merge file-imported readings. By-id upsert with the same near-duplicate guard as
     * [mergeWeightsById]: an incoming row with a different id but an identical timestamp
     * and identical values is dropped, so importing a file that mirrors synced rows does
     * not double them. Returns the number of entries added or updated.
     */
    suspend fun importFromFile(entries: List<BodyCompositionEntry>): Int = writeMutex.withLock {
        val valid = entries.map { it.sanitizedComposition() }.filter { it.hasAnyValue }
        if (valid.isEmpty()) return@withLock 0
        val existing = prefs.bodyCompositions.first()
        val (merged, changed) = mergeCompositionsById(existing, valid)
        if (changed == 0) return@withLock 0
        val existingById = existing.associateBy { it.id }
        val duplicateIds = existing.groupBy { it.id }.filterValues { it.size > 1 }.keys
        val upserts = merged.filter { row ->
            val prev = existingById[row.id]
            prev == null || prev != row || row.id in duplicateIds
        }
        prefs.applyCompositionBucketChanges(upsertsByMonth = upserts.groupBy { it.month() })
        syncProfileLeanMassToLatest()
        changed
    }

    /**
     * Merge externally-sourced readings (a smart scale via Health Connect). Idempotent:
     * every external record maps to a deterministic id, so repeated imports upsert in
     * place and an in-place value correction never duplicates. Own records restore under
     * their original UUID (reinstall recovery); the change-token path filters own records
     * at the manager level.
     */
    suspend fun importExternal(external: List<ExternalBodyComposition>) {
        val manager = health ?: return
        val incoming = external
            .asSequence()
            .map { row ->
                BodyCompositionEntry(
                    id = manager.ownRecordId(row.clientRecordId)
                        ?: externalId(row.clientRecordId, row.recordId, row.time),
                    date = row.time,
                    boneMassKg = row.boneMassKg,
                    totalBodyWaterKg = row.totalBodyWaterKg,
                    muscleMassKg = row.muscleMassKg,
                    leanMassKg = row.leanMassKg,
                    visceralFatRating = row.visceralFatRating,
                ).sanitizedComposition()
            }
            .filter { it.hasAnyValue }
            .toList()
        if (incoming.isEmpty()) return
        writeMutex.withLock {
            prefs.applyCompositionBucketChanges(upsertsByMonth = incoming.groupBy { it.month() })
        }
        syncProfileLeanMassToLatest()
    }

    /** Stable id for an external record: same seeding order as the weight/body-fat stores. */
    private fun externalId(clientRecordId: String?, recordId: String, time: Instant): UUID {
        val seed = clientRecordId?.takeIf { it.isNotBlank() }
            ?: recordId.takeIf { it.isNotBlank() }
            ?: "hc-composition:${time.toEpochMilli()}"
        return UUID.nameUUIDFromBytes(seed.toByteArray())
    }

    private fun BodyCompositionEntry.month(): YearMonth =
        YearMonth.from(date.atZone(ZoneId.systemDefault()))

    /** Keep this row's values and pull in only what it is missing from [other]. */
    private fun BodyCompositionEntry.fillingGapsFrom(other: BodyCompositionEntry): BodyCompositionEntry =
        BodyCompositionEntry.CompositionMetric.entries.fold(this) { acc, metric ->
            if (acc.value(metric) != null) acc else acc.setting(metric, other.value(metric))
        }

    /** Incoming non-null values win; metrics absent from [other] keep this row's value. */
    private fun BodyCompositionEntry.overlayFrom(other: BodyCompositionEntry): BodyCompositionEntry =
        BodyCompositionEntry.CompositionMetric.entries.fold(this) { acc, metric ->
            other.value(metric)?.let { acc.setting(metric, it) } ?: acc
        }

    /**
     * Stamp the newest valid lean-mass reading onto the profile so Katch-McArdle BMR and
     * the protein cap use measured lean mass. [prefer] (an explicit user entry) wins only
     * while no valid stored reading is newer — a past-dated entry cannot pull the profile
     * backward. Rows without a lean mass never move the stamp. Auto-sourced goals
     * re-clamp when the new reading moves BMR.
     */
    private suspend fun syncProfileLeanMassToLatest(prefer: BodyCompositionEntry? = null) {
        val profile = profileRepository.current() ?: return
        val stored = prefs.bodyCompositions.first()
        val valid = stored.filter { sanitizeLeanMassKg(it.leanMassKg) != null }.sortedBy { it.date }
        val preferValid = prefer?.takeIf { candidate ->
            sanitizeLeanMassKg(candidate.leanMassKg) != null &&
                stored.none { it.date > candidate.date && sanitizeLeanMassKg(it.leanMassKg) != null }
        }
        val chosen = preferValid ?: valid.lastOrNull() ?: return
        val lean = sanitizeLeanMassKg(chosen.leanMassKg) ?: return
        if (abs((profile.measuredLeanMassKg ?: -1.0) - lean) > 0.0001 ||
            profile.leanMassUpdatedAt != chosen.date
        ) {
            profileRepository.save(
                profile.copy(
                    measuredLeanMassKg = lean,
                    leanMassUpdatedAt = chosen.date,
                ).reclampedAutoGoal()
            )
        }
    }
}

/**
 * Pure merge for file imports: by-id upsert plus a near-duplicate guard — an incoming row
 * with a different id but the same timestamp and identical values is skipped, so a file
 * that mirrors already-synced rows does not double them. Returns the merged list and the
 * number of entries added or updated.
 */
internal fun mergeCompositionsById(
    existing: List<BodyCompositionEntry>,
    incoming: List<BodyCompositionEntry>,
): Pair<List<BodyCompositionEntry>, Int> {
    val byId = existing.associateBy { it.id }.toMutableMap()
    var changed = 0
    for (entry in incoming) {
        val current = byId[entry.id]
        if (current != null) {
            if (current != entry) {
                byId[entry.id] = entry
                changed++
            }
            continue
        }
        val nearDuplicate = existing.any { it.date == entry.date && it.sameValues(entry) }
        if (nearDuplicate) continue
        byId[entry.id] = entry
        changed++
    }
    return byId.values.sortedBy { it.date } to changed
}

private fun BodyCompositionEntry.sameValues(other: BodyCompositionEntry): Boolean =
    BodyCompositionEntry.CompositionMetric.entries.all { value(it) == other.value(it) }
