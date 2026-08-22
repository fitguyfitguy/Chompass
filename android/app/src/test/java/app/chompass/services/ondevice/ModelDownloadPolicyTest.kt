package app.chompass.services.ondevice

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Codeberg #51: resume / unique-work / progress-mapping rules that used to
 * loop a multi-GB Gemma download back to 0%.
 */
class ModelDownloadPolicyTest {
    private val catalog = 2_500_000_000L
    private val fortyPercent = 1_000_000_000L

    @Test
    fun range206_appends() {
        assertEquals(
            ModelDownloadPolicy.StreamDisposition.APPEND,
            ModelDownloadPolicy.streamDisposition(206, fortyPercent, catalog - fortyPercent, catalog),
        )
    }

    @Test
    fun fresh200_writesFromStart() {
        assertEquals(
            ModelDownloadPolicy.StreamDisposition.WRITE_FROM_START,
            ModelDownloadPolicy.streamDisposition(200, 0L, catalog, catalog),
        )
    }

    @Test
    fun ignoredRangeFullBody_skipsPrefix_neverTruncates() {
        assertEquals(
            ModelDownloadPolicy.StreamDisposition.SKIP_PREFIX,
            ModelDownloadPolicy.streamDisposition(200, fortyPercent, catalog, catalog),
        )
    }

    @Test
    fun ignoredRangeRemainderOnly_appends() {
        assertEquals(
            ModelDownloadPolicy.StreamDisposition.APPEND,
            ModelDownloadPolicy.streamDisposition(200, fortyPercent, catalog - fortyPercent, catalog),
        )
    }

    @Test
    fun ambiguous200WithPart_retriesWithoutTruncating() {
        assertEquals(
            ModelDownloadPolicy.StreamDisposition.RETRY,
            ModelDownloadPolicy.streamDisposition(200, fortyPercent, -1L, catalog),
        )
    }

    @Test
    fun range416_alreadyComplete() {
        assertEquals(
            ModelDownloadPolicy.StreamDisposition.ALREADY_COMPLETE,
            ModelDownloadPolicy.streamDisposition(416, catalog, 0L, catalog),
        )
    }

    @Test
    fun incompleteFile_isNotComplete() {
        assertFalse(ModelDownloadPolicy.isComplete(fortyPercent, catalog))
        assertTrue(ModelDownloadPolicy.isComplete(catalog, catalog))
        assertTrue(ModelDownloadPolicy.isComplete(catalog + 1, catalog))
        assertFalse(ModelDownloadPolicy.isComplete(catalog, 0L))
    }

    @Test
    fun partProgress_capsAt99() {
        assertEquals(0, ModelDownloadPolicy.partProgress(0L, catalog))
        assertEquals(40, ModelDownloadPolicy.partProgress(fortyPercent, catalog))
        assertEquals(99, ModelDownloadPolicy.partProgress(catalog, catalog))
        assertEquals(0, ModelDownloadPolicy.partProgress(100L, 0L))
    }

    @Test
    fun uniqueWork_keepSameVersion_replaceOtherwise() {
        assertEquals(
            ExistingWorkPolicy.KEEP,
            ModelDownloadPolicy.uniqueWorkPolicy(true, "gemma-4-e2b-it-1", "gemma-4-e2b-it-1"),
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            ModelDownloadPolicy.uniqueWorkPolicy(true, "gemma-4-e2b-it-1", "gemma-4-e4b-it-1"),
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            ModelDownloadPolicy.uniqueWorkPolicy(false, "gemma-4-e2b-it-1", "gemma-4-e2b-it-1"),
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            ModelDownloadPolicy.uniqueWorkPolicy(false, null, "gemma-4-e2b-it-1"),
        )
    }

    private fun map(
        workState: WorkInfo.State?,
        workProgressPercent: Int = 0,
        partProgress: Int = 0,
        partComplete: Boolean = false,
        isDownloaded: Boolean = false,
        workMatchesEntry: Boolean = true,
        failureReason: String? = null,
    ) = ModelDownloadPolicy.mapState(
        workState = workState,
        workMatchesEntry = workMatchesEntry,
        workProgressPercent = workProgressPercent,
        partProgress = partProgress,
        partComplete = partComplete,
        isDownloaded = isDownloaded,
        failureReason = failureReason,
        defaultFailure = "fail",
    )

    @Test
    fun mapState_blockedShowsPartProgress_notZero() {
        assertEquals(
            OnDeviceDownloadState.Downloading(40),
            map(WorkInfo.State.BLOCKED, partProgress = 40),
        )
    }

    @Test
    fun mapState_enqueuedShowsPartProgress() {
        assertEquals(
            OnDeviceDownloadState.Downloading(40),
            map(WorkInfo.State.ENQUEUED, partProgress = 40),
        )
    }

    @Test
    fun mapState_runningPrefersOnDiskWhenWorkerProgressReset() {
        assertEquals(
            OnDeviceDownloadState.Downloading(40),
            map(WorkInfo.State.RUNNING, workProgressPercent = 0, partProgress = 40),
        )
    }

    @Test
    fun mapState_running100IsVerifying() {
        assertEquals(
            OnDeviceDownloadState.Verifying,
            map(WorkInfo.State.RUNNING, workProgressPercent = 100, partProgress = 99),
        )
    }

    @Test
    fun mapState_cancelledWithoutFileIsNotDownloaded() {
        assertEquals(
            OnDeviceDownloadState.NotDownloaded,
            map(WorkInfo.State.CANCELLED, workProgressPercent = 40, partProgress = 40),
        )
    }

    @Test
    fun mapState_fileOnDiskWinsOverFailedWork() {
        assertEquals(
            OnDeviceDownloadState.Downloaded,
            map(WorkInfo.State.FAILED, isDownloaded = true),
        )
    }

    @Test
    fun mapState_succeededWithoutFileIsNotDownloaded() {
        assertEquals(
            OnDeviceDownloadState.NotDownloaded,
            map(WorkInfo.State.SUCCEEDED),
        )
    }

    @Test
    fun mapState_completePartWhileEnqueuedIsVerifying() {
        assertEquals(
            OnDeviceDownloadState.Verifying,
            map(WorkInfo.State.ENQUEUED, partProgress = 99, partComplete = true),
        )
    }

    @Test
    fun mapState_completePartAfterCancelSurfacesRetry() {
        assertEquals(
            OnDeviceDownloadState.Failed("fail"),
            map(WorkInfo.State.CANCELLED, partProgress = 99, partComplete = true),
        )
    }

    @Test
    fun skipFully_consumesExactPrefix() {
        val data = ByteArray(100) { it.toByte() }
        val input = ByteArrayInputStream(data)
        ModelDownloadPolicy.skipFully(input, 40)
        assertEquals(40, input.read())
    }

    @Test(expected = IOException::class)
    fun skipFully_eofThrows() {
        ModelDownloadPolicy.skipFully(ByteArrayInputStream(ByteArray(4)), 10)
    }
}
