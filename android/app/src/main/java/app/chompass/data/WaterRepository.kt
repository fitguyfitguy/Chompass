package app.chompass.data

import app.chompass.models.WaterEntry
import app.chompass.services.health.HealthConnectManager
import app.chompass.services.health.ExternalHydration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class WaterRepository(
    private val prefs: PreferencesStore,
    private val health: HealthConnectManager? = null,
    private val sync: app.chompass.sync.SyncRepository? = null,
) {
    val entries: Flow<List<WaterEntry>> = prefs.waterEntries.map { list -> list.sortedBy { it.date } }

    /**
     * Invoked after every add/delete so the adaptive reminder chain can re-arm
     * with the new pace (wired in ChompassApp; the repository stays
     * notification-agnostic).
     */
    var onEntriesChanged: (suspend () -> Unit)? = null

    suspend fun add(entry: WaterEntry) {
        if (entry.milliliters <= 0) return
        prefs.setWaterEntries(prefs.waterEntries.first() + entry)
        sync?.touch(entry.id, "water")
        if (shouldSyncHealth()) health?.writeHydration(entry)
        onEntriesChanged?.invoke()
    }

    suspend fun delete(id: UUID) {
        prefs.setWaterEntries(prefs.waterEntries.first().filter { it.id != id })
        sync?.tombstone(id, "water")
        // Delete even when sync is off (food-log parity, best-effort) — a surviving
        // fudai-tagged record would resurrect through restoreFromHealthConnect.
        health?.deleteHydration(id)
        onEntriesChanged?.invoke()
    }

    private suspend fun shouldSyncHealth(): Boolean {
        val manager = health ?: return false
        return prefs.healthConnectEnabled.first() && manager.hasHydrationWrite()
    }

    // -- Restore from Health Connect --------------------------------------

    /**
     * Rebuilds the water log from the HydrationRecords Chompass itself wrote to
     * Health Connect — the restore path after a reinstall or new phone, where
     * Health Connect data survives but app storage doesn't. Only records
     * carrying our fudai_(uuid) clientRecordId are considered; the original
     * entry UUID is recovered from the tag so future deletes still target the
     * matching HC record. Ids already in the log are skipped, and nothing is
     * written back to Health Connect. Restored entries are indistinguishable
     * from manual ones (WaterEntry has no source field) — they are the same data.
     */
    suspend fun restoreFromHealthConnect(external: List<ExternalHydration>) {
        val manager = health ?: return
        val existingIds = prefs.waterEntries.first().map { it.id }.toSet()
        val restored = external.mapNotNull { record ->
            val id = manager.ownRecordId(record.clientRecordId) ?: return@mapNotNull null
            if (id in existingIds) return@mapNotNull null
            if (record.milliliters <= 0) return@mapNotNull null
            WaterEntry(
                id = id,
                date = record.time,
                milliliters = record.milliliters,
            )
        }
        if (restored.isEmpty()) return
        prefs.setWaterEntries(prefs.waterEntries.first() + restored)
    }
}
