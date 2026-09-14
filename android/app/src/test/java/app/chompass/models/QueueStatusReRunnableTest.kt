package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #99: the analysis-queue history tab renders Re-run on FAILED and DONE
 * entries, so every persisted status must count as runnable — a new status
 * only joins by opting in here.
 */
class QueueStatusReRunnableTest {
    @Test
    fun everyPersistedStatusIsRunnable() {
        assertTrue(QueueStatus.PENDING.reRunnable)
        assertTrue(QueueStatus.FAILED.reRunnable)
        assertTrue(QueueStatus.DONE.reRunnable)
        // Exhaustiveness guard: a new status must deliberately opt in, so
        // this count is part of the contract, not incidental.
        assertEquals(3, QueueStatus.entries.size)
    }
}
