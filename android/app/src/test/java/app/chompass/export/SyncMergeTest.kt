package app.chompass.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    data class Row(val id: String, val updatedAt: String, val deletedAt: String? = null, val v: Int = 0)

    @Test
    fun pickNewerPrefersHigherUpdatedAt() {
        val a = Row("1", "2026-01-01T00:00:00Z", v = 1)
        val b = Row("1", "2026-01-02T00:00:00Z", v = 2)
        val winner = SyncMerge.pickNewer(a, b, { it.updatedAt }, { it.deletedAt })
        assertEquals(2, winner?.v)
    }

    @Test
    fun pickNewerPrefersDeleteOnTie() {
        val live = Row("1", "2026-01-01T00:00:00Z", deletedAt = null)
        val dead = Row("1", "2026-01-01T00:00:00Z", deletedAt = "2026-01-01T00:00:00Z")
        val winner = SyncMerge.pickNewer(live, dead, { it.updatedAt }, { it.deletedAt })
        assertEquals("2026-01-01T00:00:00Z", winner?.deletedAt)
    }

    @Test
    fun mergeRecordListsUnionsById() {
        val local = listOf(
            Row("a", "2026-01-01T00:00:00Z", v = 1),
            Row("b", "2026-01-01T00:00:00Z", v = 1),
        )
        val remote = listOf(
            Row("b", "2026-01-03T00:00:00Z", v = 2),
            Row("c", "2026-01-02T00:00:00Z", v = 1),
        )
        val merged = SyncMerge.mergeRecordLists(
            local, remote,
            idOf = { it.id },
            updatedAt = { it.updatedAt },
            deletedAt = { it.deletedAt },
        )
        assertEquals(3, merged.size)
        assertEquals(2, merged.first { it.id == "b" }.v)
        assertTrue(merged.any { it.id == "c" })
    }

    @Test
    fun partitionLiveAndDeleted() {
        val part = SyncMerge.partitionLiveAndDeleted(
            listOf(
                Row("a", "1", deletedAt = null),
                Row("b", "2", deletedAt = "2"),
            ),
            idOf = { it.id },
            deletedAt = { it.deletedAt },
        )
        assertEquals(1, part.live.size)
        assertEquals(listOf("b"), part.deletedIds)
        assertNull(part.live.first().deletedAt)
    }
}
