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

    /**
     * Merge rows by id (LWW, same as [mergeRecordLists]) and then collapse live
     * rows that share a dedupe key — for weights, identical (date, weight_kg)
     * written under different ids. Keeps the newest [updatedAt]; on a tie
     * prefers the remote row, mirroring [pickNewer]. Tombstoned rows carry no
     * date/value and pass through untouched so deletes still propagate.
     * Replaces [mergeRecordLists] for the weights key.
     */
    fun <T> dedupeRecordLists(
        local: List<T>,
        remote: List<T>,
        idOf: (T) -> String,
        keyOf: (T) -> String,
        updatedAt: (T) -> String,
        deletedAt: (T) -> String?,
    ): List<T> {
        val merged = mergeRecordLists(local, remote, idOf, updatedAt, deletedAt)
        // A merged row came from remote when the remote side holds the same id
        // with the same updated_at (pickNewer breaks equal-timestamp ties toward
        // remote, so the winner can only be the remote copy in that case).
        val remoteUpdatedAt = HashMap<String, String>()
        for (row in remote) {
            val id = idOf(row)
            if (id.isNotBlank()) remoteUpdatedAt[id] = updatedAt(row)
        }
        val byKey = LinkedHashMap<String, T>()
        val winnerRemote = HashMap<String, Boolean>()
        for (row in merged) {
            if (!deletedAt(row).isNullOrBlank()) continue // tombstones pass through
            val key = keyOf(row)
            if (key.isBlank()) continue
            val existing = byKey[key]
            val rowIsRemote = remoteUpdatedAt[idOf(row)] == updatedAt(row)
            if (existing == null) {
                byKey[key] = row
                winnerRemote[key] = rowIsRemote
            } else {
                val cmp = compareUpdatedAt(updatedAt(existing), updatedAt(row))
                if (cmp < 0 || (cmp == 0 && rowIsRemote && winnerRemote[key] == false)) {
                    byKey[key] = row
                    winnerRemote[key] = rowIsRemote
                }
            }
        }
        val tombstones = merged.filter { !deletedAt(it).isNullOrBlank() }
        return (tombstones + byKey.values).sortedBy { idOf(it) }
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
