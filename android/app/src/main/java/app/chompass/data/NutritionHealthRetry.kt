package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.services.health.NutritionWriteGate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Health Connect as the food log needs it. Kept as an interface so the retry
 * path can be exercised without a live Health Connect service.
 */
interface NutritionHealthSync {
    suspend fun writeGate(): NutritionWriteGate
    suspend fun write(entry: FoodEntry): Boolean

    /**
     * Delete-then-write on the entry's own clientRecordId. Used for every retry, so a
     * second attempt cannot duplicate a record that did land after all — the delete
     * targets that one entry, never the day or the whole log.
     */
    suspend fun update(entry: FoodEntry): Boolean

    /** Best-effort undo when a write completes after the local entry was removed. */
    suspend fun delete(entryId: UUID): Boolean
}

/** The slice of persistence the retry needs. Implemented by [PreferencesStore]. */
interface NutritionSyncStore {
    val healthConnectEnabled: Flow<Boolean>
    val pendingNutritionHealthWrites: Flow<Set<String>>
    suspend fun setPendingNutritionHealthWrites(ids: Set<String>)

    /** Latest persisted snapshot of [id], or null once the row is gone. */
    suspend fun foodEntryById(id: UUID): FoodEntry?
}

/**
 * Keeps food entries whose Health Connect write was never confirmed, and
 * re-attempts them on the next foreground sync (port of upstream fud-ai
 * 51d9c2e1 + c7f09602, #204/#312).
 *
 * Before this existed, [FoodRepository.addEntry] called `writeNutrition` and
 * discarded the result, and the permission probe in front of it turned an
 * unreachable Health Connect service into "no permission". Either path dropped
 * the mirror write with no exception, no log and no retry: the food log kept
 * the entry, Health Connect never heard about it, and nothing in the app knew
 * the two had diverged — until a reinstall rebuilt the diary from the
 * incomplete mirror.
 *
 * Ordering rules (upstream c7f09602): every path that deliberately removes a
 * row or its HC record must drop the queue key under [mutex] FIRST — an
 * in-flight retry must not be able to recreate what the user just deleted.
 * A write that completes after its row vanished is rolled back with a targeted
 * delete instead, so the restore path can never resurrect it.
 */
class NutritionHealthRetry(
    private val store: NutritionSyncStore,
    private val health: NutritionHealthSync?,
) {
    private val mutex = Mutex()

    /**
     * Push [entry] to Health Connect, queueing it when the write is not confirmed.
     *
     * On [NutritionWriteGate.UNKNOWN] the write is attempted anyway. Health Connect
     * enforces its own permissions, so the worst case is a rejection we then queue
     * and resolve on the next pass — strictly better than assuming the answer and
     * dropping the entry.
     *
     * @return true when the write was confirmed (or nothing was owed: Health
     *   Connect absent / sync switched off); false when the entry stayed queued
     *   for the next retry pass.
     */
    suspend fun sync(entry: FoodEntry, isUpdate: Boolean): Boolean =
        syncAll(listOf(entry), isUpdate)

    /**
     * Push several entries in one pass. A bulk log (Log meal / Copy From Day)
     * can carry dozens of entries, and resolving the gate per entry would mean
     * one Health Connect IPC round-trip each, so the gate and the queue are
     * resolved once for the batch.
     *
     * @return true when every entry was confirmed or nothing was owed; false
     *   when at least one entry stayed queued.
     */
    suspend fun syncAll(entries: List<FoodEntry>, isUpdate: Boolean): Boolean {
        val adapter = health ?: return true
        if (entries.isEmpty()) return true
        val queue = mutex.withLock { syncAllLocked(adapter, entries, isUpdate) }
        return entries.none { it.id.toString() in queue }
    }

    /** Drop [id] from the queue — the write landed, or the entry no longer exists. */
    suspend fun forget(id: UUID) = forgetAll(listOf(id))

    /** Batch form of [forget], so a bulk delete does not rewrite the queue once per entry. */
    suspend fun forgetAll(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        val keys = ids.mapTo(mutableSetOf()) { it.toString() }
        mutex.withLock { updateQueueLocked { it - keys } }
    }

    /**
     * Keep queue keys only for [ids] — used when the whole log is replaced
     * (clear / seed / backup restore), so the queue never describes rows that
     * no longer exist. Called BEFORE the local rows disappear.
     */
    suspend fun retainAll(ids: Collection<UUID>) {
        val keys = ids.mapTo(mutableSetOf()) { it.toString() }
        mutex.withLock { updateQueueLocked { it intersect keys } }
    }

    /**
     * Re-attempt every queued write. Called from the Health Connect read-sync
     * coordinator on app foreground and (opt-in) periodic background sync.
     */
    suspend fun retryPending() {
        val adapter = health ?: return
        mutex.withLock { retryPendingLocked(adapter) }
    }

    private suspend fun syncAllLocked(
        adapter: NutritionHealthSync,
        entries: List<FoodEntry>,
        isUpdate: Boolean,
    ): Set<String> {
        if (!store.healthConnectEnabled.first()) return emptySet()
        if (adapter.writeGate() == NutritionWriteGate.DENIED) return emptySet()

        val keys = entries.map { it.id.toString() }
        updateQueueLocked { it + keys }

        val written = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        for (entry in entries) {
            val key = entry.id.toString()
            if (!isStillPending(key)) continue
            // Re-read the persisted row: the user may have edited it since this
            // call captured its snapshot, and a row that already disappeared is
            // not worth writing at all.
            val currentEntry = store.foodEntryById(entry.id) ?: continue

            val ok = if (isUpdate) adapter.update(currentEntry) else adapter.write(currentEntry)
            when {
                !isStillPending(key) -> undoWriteIfNeeded(adapter, entry.id, ok)
                store.foodEntryById(entry.id) == null -> undoWriteIfNeeded(adapter, entry.id, ok)
                ok -> written += key
                else -> failed += key
            }
        }
        return updateQueueLocked { (it - written) + failed }
    }

    private suspend fun retryPendingLocked(adapter: NutritionHealthSync) {
        val pending = store.pendingNutritionHealthWrites.first()
        if (pending.isEmpty()) return
        if (!store.healthConnectEnabled.first()) return
        when (adapter.writeGate()) {
            // Still cannot reach the service. Keep the queue and try again on the next
            // pass — discarding it here is the exact bug this retry exists to fix.
            NutritionWriteGate.UNKNOWN -> return
            // Nutrition write was revoked. Nothing is retryable any more, and an
            // unbounded queue would otherwise outlive the permission forever.
            NutritionWriteGate.DENIED -> {
                store.setPendingNutritionHealthWrites(emptySet())
                return
            }
            NutritionWriteGate.ALLOWED -> Unit
        }

        val remaining = mutableSetOf<String>()
        for (key in pending) {
            // Lenient decode discipline: a malformed key is dropped, never allowed
            // to crash the foreground sync (it can only come from a corrupt pref).
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            if (!isStillPending(key)) continue
            val entry = store.foodEntryById(id) ?: continue

            val ok = adapter.update(entry)
            when {
                !isStillPending(key) -> undoWriteIfNeeded(adapter, id, ok)
                store.foodEntryById(id) == null -> undoWriteIfNeeded(adapter, id, ok)
                ok -> Unit
                else -> remaining += key
            }
        }
        if (remaining != pending) store.setPendingNutritionHealthWrites(remaining)
    }

    private suspend fun isStillPending(key: String): Boolean =
        key in store.pendingNutritionHealthWrites.first()

    private suspend fun undoWriteIfNeeded(adapter: NutritionHealthSync, entryId: UUID, writeSucceeded: Boolean) {
        if (writeSucceeded) adapter.delete(entryId)
    }

    /** Read-modify-write of the queue. Caller must hold [mutex]. Returns the resulting queue. */
    private suspend fun updateQueueLocked(transform: (Set<String>) -> Set<String>): Set<String> {
        val current = store.pendingNutritionHealthWrites.first()
        val next = transform(current)
        if (next != current) store.setPendingNutritionHealthWrites(next)
        return next
    }
}
