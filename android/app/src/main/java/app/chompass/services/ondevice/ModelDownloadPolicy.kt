package app.chompass.services.ondevice

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import java.io.IOException
import java.io.InputStream

/**
 * Pure decisions for the on-device model download (Codeberg #51).
 *
 * Kept off [ModelDownloadWorker] / [ModelDownloadManager] so the resume,
 * unique-work, and progress-mapping rules can be unit-tested without
 * WorkManager or a network.
 */
internal object ModelDownloadPolicy {
    const val HTTP_PARTIAL_CONTENT = 206
    const val HTTP_RANGE_NOT_SATISFIABLE = 416

    /** How to treat the HTTP body relative to any existing `.part` bytes. */
    enum class StreamDisposition {
        /** 206 (or a 200 that is only the remainder): append to `.part`. */
        APPEND,
        /** Fresh download: write from byte 0. Never used when `.part` has bytes. */
        WRITE_FROM_START,
        /** Server ignored Range and sent the full file: skip prefix, then append. */
        SKIP_PREFIX,
        /** 416: `.part` is already at/past the server file. Verify, don't stream. */
        ALREADY_COMPLETE,
        /** Ambiguous 200 with existing bytes: keep `.part`, retry later. */
        RETRY,
    }

    /**
     * Decide what to do with a response. A non-empty `.part` is never truncated:
     * 200 + full body skips the prefix; anything we can't interpret retries.
     */
    fun streamDisposition(
        responseCode: Int,
        resumedBytes: Long,
        contentLength: Long,
        catalogSize: Long,
    ): StreamDisposition = when {
        responseCode == HTTP_RANGE_NOT_SATISFIABLE -> StreamDisposition.ALREADY_COMPLETE
        responseCode == HTTP_PARTIAL_CONTENT -> StreamDisposition.APPEND
        resumedBytes <= 0L -> StreamDisposition.WRITE_FROM_START
        contentLength > 0L && catalogSize > 0L &&
            contentLength == catalogSize - resumedBytes -> StreamDisposition.APPEND
        contentLength > 0L && catalogSize > 0L &&
            contentLength == catalogSize && resumedBytes < catalogSize -> StreamDisposition.SKIP_PREFIX
        else -> StreamDisposition.RETRY
    }

    fun isComplete(partLength: Long, catalogSize: Long): Boolean =
        catalogSize > 0L && partLength >= catalogSize

    /** Percent implied by on-disk `.part` length. Caps at 99 so 100 is reserved for verify. */
    fun partProgress(partLength: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((partLength * 100) / total).toInt().coerceIn(0, 99)
    }

    /**
     * Re-tapping Download while the same model is already running must not
     * cancel it ([ExistingWorkPolicy.REPLACE] was the #51 loop). A different
     * model, or no unfinished work, replaces.
     */
    fun uniqueWorkPolicy(
        activeUnfinished: Boolean,
        activeVersion: String?,
        requestedVersion: String,
    ): ExistingWorkPolicy =
        if (activeUnfinished && activeVersion == requestedVersion) {
            ExistingWorkPolicy.KEEP
        } else {
            ExistingWorkPolicy.REPLACE
        }

    fun mapState(
        workState: WorkInfo.State?,
        workMatchesEntry: Boolean,
        workProgressPercent: Int,
        partProgress: Int,
        isDownloaded: Boolean,
        failureReason: String?,
        defaultFailure: String,
    ): OnDeviceDownloadState {
        if (!workMatchesEntry) {
            return if (isDownloaded) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
        }
        val shown = when {
            workProgressPercent >= 100 -> workProgressPercent
            else -> maxOf(workProgressPercent.coerceAtLeast(0), partProgress)
        }
        return when (workState) {
            null -> if (isDownloaded) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
            WorkInfo.State.RUNNING -> {
                if (shown >= 100) OnDeviceDownloadState.Verifying
                else OnDeviceDownloadState.Downloading(shown.coerceIn(0, 99))
            }
            // BLOCKED (Wi-Fi-only + radio asleep) used to fall through to
            // NotDownloaded and flash 0% even with a gigabyte already on disk.
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                OnDeviceDownloadState.Downloading(partProgress)
            WorkInfo.State.SUCCEEDED -> OnDeviceDownloadState.Downloaded
            WorkInfo.State.FAILED ->
                OnDeviceDownloadState.Failed(failureReason ?: defaultFailure)
            WorkInfo.State.CANCELLED ->
                if (isDownloaded) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
        }
    }

    /**
     * [InputStream.skip] is allowed to no-op on a network stream. Read-and-discard
     * until [bytes] have actually been consumed.
     */
    fun skipFully(input: InputStream, bytes: Long) {
        var left = bytes
        val buf = ByteArray(64 * 1024)
        while (left > 0L) {
            val toRead = minOf(buf.size.toLong(), left).toInt()
            val n = input.read(buf, 0, toRead)
            if (n < 0) throw IOException("EOF after skipping ${bytes - left} of $bytes bytes")
            left -= n
        }
    }
}
