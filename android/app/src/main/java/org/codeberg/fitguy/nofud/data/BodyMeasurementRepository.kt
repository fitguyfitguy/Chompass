package org.codeberg.fitguy.nofud.data

import org.codeberg.fitguy.nofud.models.BodyMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Local-only store for body-circumference history. Mirrors WeightRepository / BodyFatRepository but
 * does NOT sync anything back to UserProfile — circumferences are extra signal for the AI, not a
 * profile field. Entirely optional: an empty store means the feature is invisible to the goal calc
 * and the Coach.
 */
class BodyMeasurementRepository(private val prefs: PreferencesStore) {
    val entries: Flow<List<BodyMeasurement>> =
        prefs.bodyMeasurements.map { it.sortedBy { e -> e.date } }

    val latest: Flow<BodyMeasurement?> =
        prefs.bodyMeasurements.map { list -> list.maxByOrNull { it.date } }

    suspend fun addEntry(entry: BodyMeasurement) {
        if (!entry.hasAnyValue) return
        val current = prefs.bodyMeasurements.first()
        prefs.setBodyMeasurements(current + entry)
    }

    suspend fun deleteEntry(id: UUID) {
        val current = prefs.bodyMeasurements.first()
        prefs.setBodyMeasurements(current.filter { it.id != id })
    }

    /**
     * Set one site's value. Editing several sites the same day updates today's single snapshot;
     * the first edit on a new day starts a fresh dated snapshot carrying the previous values
     * forward (so the latest entry always holds the user's current full set). `null` clears a site.
     */
    suspend fun setValue(site: BodyMeasurement.Site, cm: Double?) {
        val current = prefs.bodyMeasurements.first()
        val latest = current.maxByOrNull { it.date }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        if (latest != null && latest.date.atZone(zone).toLocalDate() == today) {
            val updated = latest.setting(site, cm)
            val rest = current.filter { it.id != latest.id }
            prefs.setBodyMeasurements(if (updated.hasAnyValue) rest + updated else rest)
        } else {
            var fresh = BodyMeasurement()
            if (latest != null) {
                BodyMeasurement.Site.values().forEach { s -> fresh = fresh.setting(s, latest.value(s)) }
            }
            fresh = fresh.setting(site, cm)
            if (fresh.hasAnyValue) prefs.setBodyMeasurements(current + fresh)
        }
    }

    /**
     * Merge file-imported circumference snapshots into local history. By-id upsert —
     * the importer assigns deterministic ids keyed on the timestamp, so re-importing
     * the same file is a no-op. Empty snapshots are skipped. Returns entries added or
     * updated.
     */
    suspend fun importFromFile(entries: List<BodyMeasurement>): Int {
        val incoming = entries.filter { it.hasAnyValue }
        if (incoming.isEmpty()) return 0
        val (merged, changed) = mergeMeasurementsById(prefs.bodyMeasurements.first(), incoming)
        if (changed == 0) return 0
        prefs.setBodyMeasurements(merged)
        return changed
    }

    suspend fun replaceAll(entries: List<BodyMeasurement>) {
        prefs.setBodyMeasurements(entries)
    }

    suspend fun clear() {
        prefs.setBodyMeasurements(emptyList())
    }

    /** Current latest snapshot — used by the goal calc + Coach call sites. */
    suspend fun latestSnapshot(): BodyMeasurement? =
        prefs.bodyMeasurements.first().maxByOrNull { it.date }
}

/**
 * Pure merge for file imports: by-id upsert. An incoming snapshot replaces the
 * existing one with the same id only when its site values actually differ, so
 * re-importing the same file reports no changes. Returns the merged list and the
 * number of entries added or updated.
 */
internal fun mergeMeasurementsById(
    existing: List<BodyMeasurement>,
    incoming: List<BodyMeasurement>,
): Pair<List<BodyMeasurement>, Int> {
    val byId = existing.associateBy { it.id }.toMutableMap()
    var changed = 0
    for (entry in incoming) {
        val current = byId[entry.id]
        val sameValues = current != null &&
            BodyMeasurement.Site.values().all { current.value(it) == entry.value(it) } &&
            current.date == entry.date
        if (sameValues) continue
        byId[entry.id] = entry
        changed++
    }
    return byId.values.sortedBy { it.date } to changed
}
