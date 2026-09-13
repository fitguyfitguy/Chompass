package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.services.health.NutritionWriteGate
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deferred retry for nutrition writes Health Connect never confirmed (port of
 * upstream fud-ai 51d9c2e1 + c7f09602, #204/#312). Every case runs against
 * fakes — no live Health Connect service.
 */
class NutritionHealthRetryTest {

    @Test
    fun `failed write is queued and retried as an idempotent update`() = runBlocking {
        val entry = foodEntry("Spaghetti Bolognese")
        val store = FakeNutritionSyncStore(entries = listOf(entry))
        val health = FakeNutritionHealthSync(writeSucceeds = false)
        val retry = NutritionHealthRetry(store, health)

        retry.sync(entry, isUpdate = false)
        assertEquals(setOf(entry.id.toString()), store.pending.value)

        health.writeSucceeds = true
        retry.retryPending()

        // Retries go through update (delete-then-write on the entry's own clientRecordId),
        // so a record that did land after all cannot end up duplicated.
        assertEquals(listOf(entry.id), health.updated)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `pending id is registered before the Health Connect write`() = runBlocking {
        val entry = foodEntry("Oats")
        val store = FakeNutritionSyncStore(entries = listOf(entry))
        val health = FakeNutritionHealthSync(onWrite = {
            assertEquals(setOf(entry.id.toString()), store.pending.value)
        })
        val retry = NutritionHealthRetry(store, health)

        retry.sync(entry, isUpdate = false)

        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `probe failure does not discard the write`() = runBlocking {
        val entry = foodEntry("Svinemorbrad")
        val store = FakeNutritionSyncStore(entries = listOf(entry))
        // The regression: an unreachable Health Connect used to read as "no permission".
        val health = FakeNutritionHealthSync(gate = NutritionWriteGate.UNKNOWN, writeSucceeds = false)
        val retry = NutritionHealthRetry(store, health)

        retry.sync(entry, isUpdate = false)

        assertEquals(1, health.writes.size) // attempted rather than skipped
        assertEquals(setOf(entry.id.toString()), store.pending.value)
    }

    @Test
    fun `probe still failing keeps the queue intact`() = runBlocking {
        val entry = foodEntry("Wasa Sport")
        val store = FakeNutritionSyncStore(
            entries = listOf(entry),
            pending = setOf(entry.id.toString()),
        )
        val health = FakeNutritionHealthSync(gate = NutritionWriteGate.UNKNOWN)
        val retry = NutritionHealthRetry(store, health)

        retry.retryPending()

        assertTrue(health.updated.isEmpty())
        assertEquals(setOf(entry.id.toString()), store.pending.value)
    }

    @Test
    fun `revoked permission clears the queue instead of retrying forever`() = runBlocking {
        val entry = foodEntry("Kaffe")
        val store = FakeNutritionSyncStore(
            entries = listOf(entry),
            pending = setOf(entry.id.toString()),
        )
        val health = FakeNutritionHealthSync(gate = NutritionWriteGate.DENIED)
        val retry = NutritionHealthRetry(store, health)

        retry.retryPending()

        assertTrue(health.updated.isEmpty())
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `deleted entry is dropped from the queue rather than recreated`() = runBlocking {
        val gone = UUID.randomUUID()
        val kept = foodEntry("Skyr")
        val store = FakeNutritionSyncStore(
            entries = listOf(kept),
            pending = setOf(gone.toString(), kept.id.toString()),
        )
        val health = FakeNutritionHealthSync()
        val retry = NutritionHealthRetry(store, health)

        retry.retryPending()

        assertEquals(listOf(kept.id), health.updated)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `write completing after forget rolls back Health Connect`() = runBlocking {
        val entry = foodEntry("Toast")
        val store = FakeNutritionSyncStore(
            entries = listOf(entry),
            pending = setOf(entry.id.toString()),
        )
        val health = FakeNutritionHealthSync(onUpdate = {
            store.pending.value = emptySet()
        })
        val retry = NutritionHealthRetry(store, health)

        retry.retryPending()

        assertEquals(listOf(entry.id), health.deleted)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `write completing after the entry is gone rolls back Health Connect`() = runBlocking {
        // The reachable half of the rollback. `forget` takes the mutex the pass is
        // holding, so the queue cannot empty mid-write; the local row can, because
        // combining, replacing or clearing the log rewrites it without the lock.
        val entry = foodEntry("Rugbrod")
        val store = FakeNutritionSyncStore(
            entries = listOf(entry),
            pending = setOf(entry.id.toString()),
        )
        val health = FakeNutritionHealthSync(onUpdate = {
            store.entries.value = emptyList()
        })
        val retry = NutritionHealthRetry(store, health)

        retry.retryPending()

        assertEquals(listOf(entry.id), health.deleted)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `sync is a no-op while Health Connect is switched off`() = runBlocking {
        val entry = foodEntry("Havregryn")
        val store = FakeNutritionSyncStore(entries = listOf(entry), enabled = false)
        val health = FakeNutritionHealthSync(writeSucceeds = false)
        val retry = NutritionHealthRetry(store, health)

        retry.sync(entry, isUpdate = false)

        assertTrue(health.writes.isEmpty())
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `successful write leaves nothing queued`() = runBlocking {
        val entry = foodEntry("Chia")
        val store = FakeNutritionSyncStore(entries = listOf(entry), pending = setOf(entry.id.toString()))
        val health = FakeNutritionHealthSync()
        val retry = NutritionHealthRetry(store, health)

        retry.sync(entry, isUpdate = false)

        assertEquals(listOf(entry.id), health.writes)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test
    fun `batch resolves the gate once per call, not once per entry`() = runBlocking {
        // A bulk log (Log meal / Copy From Day) can carry dozens of entries.
        // writeGate() is a Health Connect IPC round-trip, so probing per entry
        // would put the save on the wire dozens of times.
        val entries = (1..50).map { foodEntry("Meal $it") }
        val store = FakeNutritionSyncStore(entries = entries)
        val health = FakeNutritionHealthSync()
        val retry = NutritionHealthRetry(store, health)

        retry.syncAll(entries, isUpdate = true)

        assertEquals(1, health.gateProbes)
        assertEquals(50, health.updated.size)
    }

    @Test
    fun `batch queues only the entries that failed`() = runBlocking {
        val ok = foodEntry("Skyr")
        val bad = foodEntry("Spaghetti Bolognese")
        val store = FakeNutritionSyncStore(entries = listOf(ok, bad))
        val health = FakeNutritionHealthSync(failFor = setOf(bad.id))
        val retry = NutritionHealthRetry(store, health)

        retry.syncAll(listOf(ok, bad), isUpdate = false)

        assertEquals(setOf(bad.id.toString()), store.pending.value)
    }

    @Test
    fun `sync uses the latest entry snapshot from the store`() = runBlocking {
        val entry = foodEntry("Pasta")
        val updated = entry.copy(calories = 480)
        val store = FakeNutritionSyncStore(entries = listOf(updated))
        val health = FakeNutritionHealthSync()
        val retry = NutritionHealthRetry(store, health)

        retry.sync(entry, isUpdate = true)

        assertEquals(480, health.lastWrittenCalories)
    }

    @Test
    fun `retainAll keeps only surviving rows when the log is replaced`() = runBlocking {
        // replaceAll / clear ordering rule: queue keys for rows that no longer
        // exist are dropped before the local rows disappear.
        val kept = foodEntry("Skyr")
        val gone = foodEntry("Toast")
        val store = FakeNutritionSyncStore(
            entries = listOf(kept),
            pending = setOf(kept.id.toString(), gone.id.toString()),
        )
        val retry = NutritionHealthRetry(store, null)

        retry.retainAll(listOf(kept.id))

        assertEquals(setOf(kept.id.toString()), store.pending.value)
    }

    @Test
    fun `malformed queue key is dropped, not crashed on`() = runBlocking {
        val entry = foodEntry("Müsli")
        val store = FakeNutritionSyncStore(
            entries = listOf(entry),
            pending = setOf("not-a-uuid", entry.id.toString()),
        )
        val health = FakeNutritionHealthSync()
        val retry = NutritionHealthRetry(store, health)

        retry.retryPending()

        assertEquals(listOf(entry.id), health.updated)
        assertTrue(store.pending.value.isEmpty())
    }

    private fun foodEntry(name: String) = FoodEntry(
        name = name,
        calories = 315,
        protein = 18.0,
        carbs = 40.0,
        fat = 9.0,
        timestamp = Instant.parse("2026-08-27T17:54:00Z"),
        source = FoodSource.MANUAL,
    )
}

private class FakeNutritionSyncStore(
    entries: List<FoodEntry> = emptyList(),
    pending: Set<String> = emptySet(),
    enabled: Boolean = true,
) : NutritionSyncStore {
    val pending = MutableStateFlow(pending)
    val entries = MutableStateFlow(entries)
    override val healthConnectEnabled: Flow<Boolean> = MutableStateFlow(enabled)
    override val pendingNutritionHealthWrites: Flow<Set<String>> = this.pending
    override suspend fun setPendingNutritionHealthWrites(ids: Set<String>) { pending.value = ids }
    override suspend fun foodEntryById(id: UUID): FoodEntry? =
        entries.value.firstOrNull { it.id == id }
}

private class FakeNutritionHealthSync(
    private val gate: NutritionWriteGate = NutritionWriteGate.ALLOWED,
    var writeSucceeds: Boolean = true,
    private val failFor: Set<UUID> = emptySet(),
    private val onWrite: suspend () -> Unit = {},
    private val onUpdate: suspend () -> Unit = {},
) : NutritionHealthSync {
    val writes = mutableListOf<UUID>()
    val updated = mutableListOf<UUID>()
    val deleted = mutableListOf<UUID>()
    var gateProbes = 0
        private set
    var lastWrittenCalories: Int? = null
        private set

    override suspend fun writeGate(): NutritionWriteGate {
        gateProbes++
        return gate
    }

    override suspend fun write(entry: FoodEntry): Boolean {
        onWrite()
        writes += entry.id
        lastWrittenCalories = entry.calories
        return succeeds(entry)
    }

    override suspend fun update(entry: FoodEntry): Boolean {
        onUpdate()
        updated += entry.id
        lastWrittenCalories = entry.calories
        return succeeds(entry)
    }

    override suspend fun delete(entryId: UUID): Boolean {
        deleted += entryId
        return true
    }

    private fun succeeds(entry: FoodEntry) = writeSucceeds && entry.id !in failFor
}
