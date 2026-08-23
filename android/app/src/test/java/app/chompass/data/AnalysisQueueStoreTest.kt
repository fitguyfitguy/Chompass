package app.chompass.data

import app.chompass.models.FoodSource
import app.chompass.models.QueuedAnalysis
import app.chompass.models.QueueStatus
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Analysis queue + prompt history store (Codeberg #53): upsert semantics,
 * status transitions, retention pruning (history only — PENDING never pruned),
 * atomic writes, corrupt-file leniency, and entry+photo cleanup on delete.
 */
class AnalysisQueueStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): AnalysisQueueStore =
        AnalysisQueueStore(tmp.newFolder("queue"))

    /** Two stores over the SAME folder — simulates app restart. */
    private fun storePair(): Pair<AnalysisQueueStore, AnalysisQueueStore> {
        val root = tmp.newFolder("queue-pair")
        return AnalysisQueueStore(root) to AnalysisQueueStore(root)
    }

    private fun entry(
        id: UUID = UUID.randomUUID(),
        createdAt: Instant = Instant.now(),
        status: QueueStatus = QueueStatus.PENDING,
        filenames: List<String> = emptyList(),
    ) = QueuedAnalysis(
        id = id,
        createdAt = createdAt,
        targetDate = LocalDate.now(),
        imageFilenames = filenames,
        note = "test",
        source = FoodSource.SNAP_FOOD,
        status = status,
    )

    @Test
    fun upsert_insertsAndReplacesById() = runBlocking {
        val s = store()
        val a = entry()
        val b = entry()
        s.upsert(a)
        s.upsert(b)
        s.upsert(a.copy(note = "updated"))
        assertEquals(2, s.entries.value.size)
        assertEquals("updated", s.item(a.id)?.note)
    }

    @Test
    fun entriesAreNewestFirst() = runBlocking {
        val s = store()
        val old = entry(createdAt = Instant.now().minusSeconds(3600))
        val fresh = entry(createdAt = Instant.now())
        s.upsert(old)
        s.upsert(fresh)
        assertEquals(listOf(fresh.id, old.id), s.entries.value.map { it.id })
    }

    @Test
    fun markDoneSetsResultAndClearsError() = runBlocking {
        val s = store()
        val a = entry(status = QueueStatus.PENDING, filenames = listOf("a.jpg"))
        s.upsert(a.copy(error = "boom"))
        s.markDone(a.id, result())
        val done = s.item(a.id)!!
        assertEquals(QueueStatus.DONE, done.status)
        assertEquals(123, done.result?.calories)
        assertNull(done.error)
    }

    @Test
    fun markFailedKeepsEntryRunnable() = runBlocking {
        val s = store()
        val a = entry()
        s.upsert(a)
        s.markFailed(a.id, "network")
        val failed = s.item(a.id)!!
        assertEquals(QueueStatus.PENDING, failed.status)
        assertEquals("network", failed.error)
    }

    @Test
    fun deleteRemovesEntryAndPhotos() = runBlocking {
        val s = store()
        val dir = File(tmp.root, "queue/fudai-queue-images").apply { mkdirs() }
        File(dir, "a.jpg").writeText("img")
        val a = entry(filenames = listOf("a.jpg"))
        s.upsert(a)
        s.delete(a.id)
        assertNull(s.item(a.id))
        assertTrue(!File(dir, "a.jpg").exists())
    }

    @Test
    fun clearHistoryKeepsPendingAndRemovesPhotos() = runBlocking {
        val s = store()
        val dir = File(tmp.root, "queue/fudai-queue-images").apply { mkdirs() }
        File(dir, "done.jpg").writeText("img")
        File(dir, "pending.jpg").writeText("img")
        val done = entry(status = QueueStatus.DONE, filenames = listOf("done.jpg"))
        val pending = entry(status = QueueStatus.PENDING, filenames = listOf("pending.jpg"))
        s.upsert(done)
        s.upsert(pending)
        s.clearHistory()
        assertEquals(listOf(pending.id), s.entries.value.map { it.id })
        assertTrue(!File(dir, "done.jpg").exists())
        assertTrue(File(dir, "pending.jpg").exists())
    }

    @Test
    fun pruneDropsOldHistoryOnly() = runBlocking {
        val s = store()
        val oldDone = entry(
            createdAt = Instant.now().minus(10, ChronoUnit.DAYS),
            status = QueueStatus.DONE,
        )
        val oldPending = entry(
            createdAt = Instant.now().minus(10, ChronoUnit.DAYS),
            status = QueueStatus.PENDING,
        )
        val freshDone = entry(status = QueueStatus.DONE)
        s.upsert(oldDone)
        s.upsert(oldPending)
        s.upsert(freshDone)
        s.prune(retentionDays = 7)
        val ids = s.entries.value.map { it.id }
        assertTrue(oldDone.id !in ids)
        assertTrue(oldPending.id in ids)
        assertTrue(freshDone.id in ids)
    }

    @Test
    fun corruptFileDecodesToEmpty() = runBlocking {
        val (s, s2) = storePair()
        val a = entry()
        s.upsert(a)
        // Corrupt the on-disk JSON, then verify a fresh store stays alive.
        File(tmp.root, "queue-pair/chompass-analysis-queue.json")
            .writeText("{not json!!")
        s2.ensureLoaded()
        assertTrue(s2.entries.value.isEmpty())
        // And writes still work after the corruption.
        s2.upsert(entry())
        assertEquals(1, s2.entries.value.size)
    }

    @Test
    fun persistedAcrossStoreInstances() = runBlocking {
        val (s, s2) = storePair()
        val a = entry()
        s.upsert(a)
        s2.ensureLoaded()
        assertEquals(a.id, s2.item(a.id)?.id)
        assertEquals("test", s2.item(a.id)?.note)
    }

    private fun result(): FoodAnalysis = FoodAnalysis(
        name = "Test meal",
        calories = 123,
        protein = 5.0,
        carbs = 10.0,
        fat = 2.0,
        servingSizeGrams = 100.0,
    )
}
