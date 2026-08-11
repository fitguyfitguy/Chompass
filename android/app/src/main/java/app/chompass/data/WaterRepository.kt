package app.chompass.data

import app.chompass.models.WaterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class WaterRepository(
    private val prefs: PreferencesStore,
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
        onEntriesChanged?.invoke()
    }

    suspend fun delete(id: UUID) {
        prefs.setWaterEntries(prefs.waterEntries.first().filter { it.id != id })
        sync?.tombstone(id, "water")
        onEntriesChanged?.invoke()
    }
}
