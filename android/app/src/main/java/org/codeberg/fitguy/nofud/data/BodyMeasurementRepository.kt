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
