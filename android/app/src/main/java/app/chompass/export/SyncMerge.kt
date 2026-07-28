package app.chompass.export

/**
 * Last-write-wins merge for sync-1.0 documents. Pure logic — mirrors
 * web/app/src/lib/nofud-core/sync-merge.js.
 */
object SyncMerge {

    fun compareUpdatedAt(a: String, b: String): Int = a.compareTo(b)

    /**
     * Prefer the record with the higher [updatedAt]. On a tie, prefer a tombstone
     * (deleted) over a live row; if still tied, prefer [remote].
     */
    fun <T> pickNewer(
        local: T?,
        remote: T?,
        updatedAt: (T) -> String,
        deletedAt: (T) -> String?,
    ): T? {
        if (local == null) return remote
        if (remote == null) return local
        val cmp = compareUpdatedAt(updatedAt(local), updatedAt(remote))
        if (cmp < 0) return remote
        if (cmp > 0) return local
        val localDeleted = !deletedAt(local).isNullOrBlank()
        val remoteDeleted = !deletedAt(remote).isNullOrBlank()
        if (localDeleted != remoteDeleted) return if (remoteDeleted) remote else local
        return remote
    }

    fun <T> mergeRecordLists(
        local: List<T>,
        remote: List<T>,
        idOf: (T) -> String,
        updatedAt: (T) -> String,
        deletedAt: (T) -> String?,
    ): List<T> {
        val byId = LinkedHashMap<String, T>()
        for (row in local) {
            val id = idOf(row)
            if (id.isBlank()) continue
            byId[id] = row
        }
        for (row in remote) {
            val id = idOf(row)
            if (id.isBlank()) continue
            val winner = pickNewer(byId[id], row, updatedAt, deletedAt) ?: continue
            byId[id] = winner
        }
        return byId.values.sortedBy { idOf(it) }
    }

    data class Partition<T>(val live: List<T>, val deletedIds: List<String>)

    fun <T> partitionLiveAndDeleted(
        rows: List<T>,
        idOf: (T) -> String,
        deletedAt: (T) -> String?,
    ): Partition<T> {
        val live = mutableListOf<T>()
        val deleted = mutableListOf<String>()
        for (row in rows) {
            if (!deletedAt(row).isNullOrBlank()) deleted += idOf(row)
            else live += row
        }
        return Partition(live, deleted)
    }
}
