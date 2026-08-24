package app.chompass.data

import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.services.PerfLog
import app.chompass.sync.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Caffeine log: standalone mg-based entries (coffee, tea, energy drinks).
 * Mirrors [NicotineRepository]: buckets + WebDAV sync only, no Health Connect
 * record type (caffeine rides NutritionRecord.caffeine from food entries, and
 * the tracker total also sums those — see HomeViewModel).
 */
class CaffeineRepository(
    private val prefs: PreferencesStore,
    private val sync: SyncRepository? = null,
) {
    val entries: Flow<List<CaffeineEntry>> = prefs.caffeineEntries.map { list -> list.sortedBy { it.date } }

    suspend fun mgForDate(date: LocalDate): Double =
        prefs.caffeineEntries.first()
            .filter { it.date.atZone(ZoneId.systemDefault()).toLocalDate() == date }
            .sumOf { it.mg }

    suspend fun add(entry: CaffeineEntry) {
        if (entry.mg <= 0) return
        PerfLog.measure("caffeineLog", "dataStore", "kind=${entry.kind.storageKey}") {
            prefs.applyCaffeineBucketChanges(
                upsertsByMonth = mapOf(entry.month() to listOf(entry)),
            )
        }
        sync?.touch(entry.id, "caffeine")
    }

    /** Edits kind/mg of an existing log in place (same id, same timestamp). */
    suspend fun update(id: UUID, kind: CaffeineKind, mg: Double) {
        if (mg <= 0) return
        val existing = prefs.caffeineEntries.first().firstOrNull { it.id == id } ?: return
        val updated = existing.copy(kind = kind, mg = mg)
        prefs.applyCaffeineBucketChanges(upsertsByMonth = mapOf(updated.month() to listOf(updated)))
        sync?.touch(id, "caffeine")
    }

    suspend fun delete(id: UUID) {
        val existing = prefs.caffeineEntries.first().firstOrNull { it.id == id } ?: return
        prefs.applyCaffeineBucketChanges(removalIdsByMonth = mapOf(existing.month() to setOf(id)))
        sync?.tombstone(id, "caffeine")
    }

    private fun CaffeineEntry.month(): YearMonth = YearMonth.from(date.atZone(ZoneId.systemDefault()))
}
