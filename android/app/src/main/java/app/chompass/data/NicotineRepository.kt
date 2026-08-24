package app.chompass.data

import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
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
 * Nicotine log: standalone count-based entries (cigarettes, vapes, pouches).
 * Mirrors [WaterRepository] minus Health Connect — HC has no count-based
 * nicotine record type, so nicotine rides buckets + WebDAV sync only.
 */
class NicotineRepository(
    private val prefs: PreferencesStore,
    private val sync: SyncRepository? = null,
) {
    val entries: Flow<List<NicotineEntry>> = prefs.nicotineEntries.map { list -> list.sortedBy { it.date } }

    suspend fun countForDate(date: LocalDate): Int =
        prefs.nicotineEntries.first()
            .filter { it.date.atZone(ZoneId.systemDefault()).toLocalDate() == date }
            .sumOf { it.count }

    suspend fun add(entry: NicotineEntry) {
        if (entry.count <= 0) return
        PerfLog.measure("nicotineLog", "dataStore", "kind=${entry.kind.storageKey}") {
            prefs.applyNicotineBucketChanges(
                upsertsByMonth = mapOf(entry.month() to listOf(entry)),
            )
        }
        sync?.touch(entry.id, "nicotine")
    }

    /** Edits count/mg/kind of an existing log in place (same id, same timestamp). */
    suspend fun update(id: UUID, kind: NicotineKind, count: Int, mg: Double?) {
        if (count <= 0) return
        val existing = prefs.nicotineEntries.first().firstOrNull { it.id == id } ?: return
        val updated = existing.copy(kind = kind, count = count, mg = mg?.takeIf { it > 0 })
        prefs.applyNicotineBucketChanges(upsertsByMonth = mapOf(updated.month() to listOf(updated)))
        sync?.touch(id, "nicotine")
    }

    suspend fun delete(id: UUID) {
        val existing = prefs.nicotineEntries.first().firstOrNull { it.id == id } ?: return
        prefs.applyNicotineBucketChanges(removalIdsByMonth = mapOf(existing.month() to setOf(id)))
        sync?.tombstone(id, "nicotine")
    }

    private fun NicotineEntry.month(): YearMonth = YearMonth.from(date.atZone(ZoneId.systemDefault()))
}
