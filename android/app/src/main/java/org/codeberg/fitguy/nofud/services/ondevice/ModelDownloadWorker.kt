package org.codeberg.fitguy.nofud.services.ondevice

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
import java.io.IOException
import java.security.MessageDigest
import okhttp3.Request
import org.codeberg.fitguy.nofud.NoFUDApp
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysisService

/**
 * Streams the catalog model file to `filesDir/models/<file>.part`, verifies
 * its SHA-256, then atomically renames it into place. Runs as a WorkManager
 * job (not a plain coroutine) so a killed process resumes cleanly instead of
 * leaving a half-written file mistaken for a real model.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? NoFUDApp)?.container
        val entry = ModelCatalog.current
        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, entry.filename)
        val partFile = File(modelsDir, "${entry.filename}.part")

        if (target.exists()) return Result.success()

        if (!hasEnoughFreeSpace(modelsDir, entry.sizeBytes)) {
            return failure("Not enough free storage — need at least ${entry.sizeBytes / (1024 * 1024)} MB free.")
        }

        return try {
            downloadWithProgress(entry.downloadUrl, partFile)
            setProgress(workDataOf(PROGRESS_PERCENT to 100))
            if (!verifySha256(partFile, entry.sha256)) {
                partFile.delete()
                return failure("Downloaded file failed integrity check — please retry.")
            }
            if (!partFile.renameTo(target)) {
                partFile.delete()
                return failure("Could not finalize the downloaded model file.")
            }
            container?.prefs?.setOnDeviceModelDownloadedVersion(entry.version)
            Result.success()
        } catch (e: IOException) {
            partFile.delete()
            // Storage-full and interrupted-connection both surface as IOException;
            // retry is safe since the partial file is always discarded above.
            Result.retry()
        }
    }

    private fun downloadWithProgress(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        FoodAnalysisService.defaultClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading model")
            val body = response.body ?: throw IOException("Empty response body downloading model")
            val total = body.contentLength().takeIf { it > 0 } ?: ModelCatalog.current.sizeBytes
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
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

    private fun hasEnoughFreeSpace(dir: File, requiredBytes: Long): Boolean {
        val stat = android.os.StatFs(dir.path)
        val margin = 200L * 1024 * 1024
        return stat.availableBytes >= requiredBytes + margin
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(FAILURE_REASON to message))

    companion object {
        const val UNIQUE_NAME = "ondevice_model_download"
        const val PROGRESS_PERCENT = "progressPercent"
        const val FAILURE_REASON = "failureReason"

        fun enqueue(context: Context, overWifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (overWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
