package app.chompass.services.ondevice

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import app.chompass.AppContainer
import app.chompass.ChompassApp
import app.chompass.services.ai.FoodAnalysisService

/**
 * Streams the catalog model file to `filesDir/models/<file>.part`, verifies
 * its SHA-256, then atomically renames it into place. Runs as a WorkManager
 * job (not a plain coroutine) so a killed process resumes cleanly instead of
 * leaving a half-written file mistaken for a real model.
 *
 * Retries resume from the partial file via an HTTP Range request instead of
 * restarting from zero (Codeberg #51: a mid-download interruption used to
 * delete the `.part` file and loop "100% → 0%" forever on a fresh install).
 * Only permanent problems (integrity failure, full disk) surface an error.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? ChompassApp)?.container
        // Codeberg #20 phase 2: with the master AI switch off, downloading a
        // model (Gemma is on-device but still an AI feature) is pointless.
        if (container?.prefs?.aiFeaturesEnabled?.first() == false) return Result.success()
        val entry = ModelCatalog.forVersion(inputData.getString(MODEL_VERSION)) ?: ModelCatalog.default
        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, entry.filename)
        val partFile = File(modelsDir, "${entry.filename}.part")

        if (target.exists()) return Result.success()

        if (!hasEnoughFreeSpace(modelsDir, entry, partFile)) {
            return failure("Not enough free storage. Need at least ${entry.sizeBytes / (1024 * 1024)} MB free.")
        }

        // A previous run may have finished streaming but died before
        // verify/rename (e.g. process kill during SHA-256). Verify the complete
        // file instead of re-downloading it.
        if (partFile.length() >= entry.sizeBytes) {
            return finalizeDownload(entry, partFile, target, container)
        }

        return try {
            downloadWithProgress(entry, partFile)
            setProgress(workDataOf(PROGRESS_PERCENT to 100))
            finalizeDownload(entry, partFile, target, container)
        } catch (e: IOException) {
            if (partFile.length() >= entry.sizeBytes) {
                // Stream finished; the failure is in verify/rename. Keep the
                // complete file — the next retry re-verifies it, and re-running
                // the download couldn't fix a verify/rename I/O error anyway.
                return failure("Downloaded file failed integrity check. Please retry.")
            }
            // Keep the partial file: the retry resumes it via Range instead of
            // starting over (the old behavior deleted it, so any mid-download
            // drop looped "100% → 0%" forever). Only a full disk is permanent —
            // re-downloading 2.6 GB would just fail again.
            if (!hasEnoughFreeSpace(modelsDir, entry, partFile)) {
                partFile.delete()
                return failure("Not enough free storage to complete the download. Free up space and retry.")
            }
            Result.retry()
        }
    }

    /**
     * Streams the model file, resuming from any existing `.part` bytes via an
     * HTTP Range request. The `.part` file is only ever deleted on permanent
     * failures (integrity check, disk full), so retries pick up where the last
     * run stopped instead of restarting from zero.
     */
    private fun downloadWithProgress(entry: OnDeviceModelEntry, dest: File) {
        val resumedBytes = dest.length()
        val request = Request.Builder()
            .url(entry.downloadUrl)
            .apply { if (resumedBytes > 0) header("Range", "bytes=$resumedBytes-") }
            .build()
        downloadClient.newCall(request).execute().use { response ->
            // 416: the requested range is past the end of the server's file.
            // Treat as "already complete" and let doWork()'s verify step judge.
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE) return@use
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading model")
            val body = response.body ?: throw IOException("Empty response body downloading model")
            var downloaded = 0L
            when (response.code) {
                HTTP_PARTIAL_CONTENT -> downloaded = resumedBytes // resume appends below
                else -> if (resumedBytes > 0) {
                    // Server ignored the Range header (200): restart from scratch.
                    dest.writeBytes(ByteArray(0)) // truncates via FileOutputStream
                }
            }
            // Catalog size is the authority for progress; a 206 body's
            // contentLength() is only the remaining bytes.
            val total = entry.sizeBytes
            FileOutputStream(dest, downloaded > 0).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReportedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 99)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            setProgressAsync(workDataOf(PROGRESS_PERCENT to percent))
                        }
                    }
                }
            }
        }
    }

    private suspend fun finalizeDownload(
        entry: OnDeviceModelEntry,
        partFile: File,
        target: File,
        container: AppContainer?,
    ): Result {
        if (!verifySha256(partFile, entry.sha256)) {
            partFile.delete()
            return failure("Downloaded file failed integrity check. Please retry.")
        }
        if (!partFile.renameTo(target)) {
            partFile.delete()
            return failure("Could not finalize the downloaded model file.")
        }
        container?.prefs?.setOnDeviceModelDownloadedVersion(entry.version)
        return Result.success()
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    /**
     * Free-space check that accounts for bytes already written to the partial
     * file: a resumed retry only needs the *remaining* bytes plus headroom, not
     * the full model size again.
     */
    private fun hasEnoughFreeSpace(dir: File, entry: OnDeviceModelEntry, partFile: File): Boolean {
        val stat = android.os.StatFs(dir.path)
        val remaining = (entry.sizeBytes - partFile.length()).coerceAtLeast(0L)
        val margin = 200L * 1024 * 1024
        return stat.availableBytes >= remaining + margin
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(FAILURE_REASON to message))

    companion object {
        const val UNIQUE_NAME = "ondevice_model_download"
        const val MODEL_VERSION = "modelVersion"
        const val PROGRESS_PERCENT = "progressPercent"
        const val FAILURE_REASON = "failureReason"

        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /**
         * A 2.6–3.6 GB stream can legitimately stall for minutes on slow
         * Wi-Fi; the shared client's 60 s read timeout would abort mid-file.
         */
        private val downloadClient: OkHttpClient by lazy {
            FoodAnalysisService.defaultClient.newBuilder()
                .readTimeout(15, TimeUnit.MINUTES)
                .build()
        }

        fun enqueue(context: Context, entry: OnDeviceModelEntry, overWifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(MODEL_VERSION to entry.version))
                .addTag(modelTag(entry.version))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (overWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun modelTag(version: String): String = "model:$version"
    }
}
