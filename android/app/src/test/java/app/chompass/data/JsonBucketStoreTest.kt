package app.chompass.data

import app.chompass.models.WaterEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * JsonBucketStore: per-month file buckets with atomic rename, on-disk
 * reconstruction, no-op write suppression, and reactive re-emission only for
 * the month that changed.
 */
class JsonBucketStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private fun store(root: File = tmp.newFolder("buckets")) = JsonBucketStore(
        root = root,
        json = json,
        serializer = WaterEntry.serializer(),
        idOf = { it.id },
        order = compareBy(WaterEntry::date),
    )

    private fun entry(id: String, ts: String, ml: Int = 250) =
        WaterEntry(id = UUID.nameUUIDFromBytes(id.toByteArray()), date = Instant.parse(ts), milliliters = ml)

    @Test
    fun `applyChanges writes only the touched month file and nothing else`() = runBlocking {
        val root = tmp.newFolder("buckets")
        val s = store(root)
        val aug = entry("a", "2026-08-01T10:00:00Z")
        val jul = entry("j", "2026-07-05T10:00:00Z")
        s.applyChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug)))

        val files = root.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("2026-08.json"), files)

        s.applyChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 7) to listOf(jul)))
        assertEquals(listOf("2026-07.json", "2026-08.json"), root.listFiles()!!.map { it.name }.sorted())
        assertEquals(listOf(jul), s.readMonth(YearMonth.of(2026, 7)))
        assertEquals(listOf(aug), s.readMonth(YearMonth.of(2026, 8)))
    }

    @Test
    fun `month content is reconstructed from disk on a fresh store instance`() = runBlocking {
        val root = tmp.newFolder("buckets")
        store(root).applyChanges(
            upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(entry("a", "2026-08-01T10:00:00Z"))),
        )
        // A new store over the same root must see what the previous one wrote.
        val fresh = store(root)
        assertEquals(1, fresh.readMonth(YearMonth.of(2026, 8)).size)
        assertEquals(listOf("2026-08"), fresh.monthsOnDisk().map { it.toString() })
    }

    @Test
    fun `upsert by id replaces in place and removals drop the row`() = runBlocking {
        val s = store(tmp.newFolder("buckets"))
        val month = YearMonth.of(2026, 8)
        val a = entry("a", "2026-08-01T10:00:00Z", ml = 250)
        val b = entry("b", "2026-08-02T10:00:00Z", ml = 300)
        s.applyChanges(upsertsByMonth = mapOf(month to listOf(a, b)))

        val edited = entry("a", "2026-08-01T10:00:00Z", ml = 500)
        s.applyChanges(upsertsByMonth = mapOf(month to listOf(edited)))
        assertEquals(listOf(500, 300), s.readMonth(month).map { it.milliliters })

        s.applyChanges(removalIdsByMonth = mapOf(month to setOf(b.id)))
        assertEquals(listOf(edited), s.readMonth(month))
    }

    @Test
    fun `no-op upsert does not rewrite the file`() = runBlocking {
        val root = tmp.newFolder("buckets")
        val s = store(root)
        val month = YearMonth.of(2026, 8)
        val a = entry("a", "2026-08-01T10:00:00Z")
        s.applyChanges(upsertsByMonth = mapOf(month to listOf(a)))
        val mtime = File(root, "2026-08.json").lastModified()

        // Same id, same content -> nothing to write (import re-run / idempotent retry).
        s.applyChanges(upsertsByMonth = mapOf(month to listOf(a)))
        assertEquals(mtime, File(root, "2026-08.json").lastModified())
    }

    @Test
    fun `empty month after removal deletes the file`() = runBlocking {
        val root = tmp.newFolder("buckets")
        val s = store(root)
        val month = YearMonth.of(2026, 8)
        val a = entry("a", "2026-08-01T10:00:00Z")
        s.applyChanges(upsertsByMonth = mapOf(month to listOf(a)))
        s.applyChanges(removalIdsByMonth = mapOf(month to setOf(a.id)))

        assertFalse(File(root, "2026-08.json").exists())
        assertTrue(s.readMonth(month).isEmpty())
        assertTrue(s.monthsOnDisk().isEmpty())
    }

    @Test
    fun `replaceAll wipes months absent from the new dataset`() = runBlocking {
        val s = store(tmp.newFolder("buckets"))
        s.applyChanges(
            upsertsByMonth = mapOf(
                YearMonth.of(2026, 7) to listOf(entry("j", "2026-07-05T10:00:00Z")),
                YearMonth.of(2026, 8) to listOf(entry("a", "2026-08-01T10:00:00Z")),
            ),
        )
        s.replaceAll(mapOf(YearMonth.of(2026, 8) to listOf(entry("a2", "2026-08-02T10:00:00Z"))))

        assertEquals(listOf("2026-08"), s.monthsOnDisk().map { it.toString() })
        assertEquals(1, s.readAll().size)
        assertEquals("2026-08-02T10:00:00Z", s.readAll().single().date.toString())
    }

    @Test
    fun `clear deletes every month file`() = runBlocking {
        val s = store(tmp.newFolder("buckets"))
        s.applyChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(entry("a", "2026-08-01T10:00:00Z"))))
        s.clear()
        assertTrue(s.monthsOnDisk().isEmpty())
        assertTrue(s.readAll().isEmpty())
    }

    @Test
    fun `corrupt month file decodes leniently to empty`() = runBlocking {
        val root = tmp.newFolder("buckets")
        File(root, "2026-08.json").writeText("{not json")
        val s = store(root)
        assertTrue(s.readMonth(YearMonth.of(2026, 8)).isEmpty())
        // And the month can be overwritten again.
        val a = entry("a", "2026-08-01T10:00:00Z")
        s.applyChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(a)))
        assertEquals(listOf(a), s.readMonth(YearMonth.of(2026, 8)))
    }

    @Test
    fun `stale tmp file from a crashed write is ignored`() = runBlocking {
        val root = tmp.newFolder("buckets")
        File(root, "2026-08.json.tmp").writeText("garbage")
        val s = store(root)
        assertTrue(s.monthsOnDisk().isEmpty())
        assertTrue(s.readAll().isEmpty())
    }

    @Test
    fun `month flow re-emits only when that month changes`() = runBlocking {
        val s = store(tmp.newFolder("buckets"))
        val august = YearMonth.of(2026, 8)
        val july = YearMonth.of(2026, 7)
        val a = entry("a", "2026-08-01T10:00:00Z")

        s.applyChanges(upsertsByMonth = mapOf(august to listOf(a)))
        val emissions = mutableListOf<List<WaterEntry>>()
        val job = launch {
            s.monthFlow(august).collect { emissions.add(it) }
        }
        // Let the collector start and see the initial value.
        delay(50)
        s.applyChanges(upsertsByMonth = mapOf(july to listOf(entry("j", "2026-07-05T10:00:00Z"))))
        delay(50)
        val b = entry("b", "2026-08-02T10:00:00Z")
        s.applyChanges(upsertsByMonth = mapOf(august to listOf(b)))
        delay(50)
        job.cancel()

        assertEquals(2, emissions.size)
        assertEquals(listOf(a), emissions[0])
        assertEquals(listOf(a, b), emissions[1])
    }

    @Test
    fun `allFlow merges months oldest first`() = runBlocking {
        val s = store(tmp.newFolder("buckets"))
        s.applyChanges(
            upsertsByMonth = mapOf(
                YearMonth.of(2026, 8) to listOf(entry("a", "2026-08-01T10:00:00Z")),
                YearMonth.of(2026, 7) to listOf(entry("j", "2026-07-05T10:00:00Z")),
            ),
        )
        val all = s.allFlow().take(1).toList().single()
        assertEquals(listOf("2026-07-05T10:00:00Z", "2026-08-01T10:00:00Z"), all.map { it.date.toString() })
    }

    @Test
    fun `readMonth on a never-written month is empty`() = runBlocking {
        val s = store(tmp.newFolder("buckets"))
        assertTrue(s.readMonth(YearMonth.of(2025, 1)).isEmpty())
        assertEquals(emptyList<WaterEntry>(), s.allFlow().first())
    }
}
