package app.chompass.services.ondevice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.chompass.AppContainer
import app.chompass.ChompassApp
import app.chompass.R
import app.chompass.services.ChompassLaunchIntents
import app.chompass.services.NotificationService
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.ui.navigation.ChompassRoutes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams the catalog model file to `filesDir/models/<file>.part`, verifies
 * its SHA-256, then atomically renames it into place. Runs as a WorkManager
 * job promoted to a `dataSync` foreground service so a 2.6–3.6 GB fetch
 * survives screen-off and the 10-minute JobScheduler cap (Codeberg #51).
 *
 * Retries resume from the partial file via an HTTP Range request. The `.part`
 * file is never truncated on a 200, and an incomplete stream is retried
 * instead of SHA-deleted. Only integrity failure and a full disk surface
 * as permanent errors.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val entry = catalogEntry()
        val part = File(File(applicationContext.filesDir, "models"), "${entry.filename}.part")
        return foregroundInfo(ModelDownloadPolicy.partProgress(part.length(), entry.sizeBytes))
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as? ChompassApp)?.container
        // Codeberg #20 phase 2: with the master AI switch off, downloading a
        // model (Gemma is on-device but still an AI feature) is pointless.
        if (container?.prefs?.aiFeaturesEnabled?.first() == false) return Result.success()
        val entry = catalogEntry()
        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, entry.filename)
        val partFile = File(modelsDir, "${entry.filename}.part")

        if (target.exists()) return Result.success()

        if (!hasEnoughFreeSpace(modelsDir, entry, partFile)) {
            return failure("Not enough free storage. Need at least ${entry.sizeBytes / (1024 * 1024)} MB free.")
        }

        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { Log.w(TAG, "op=download phase=foreground_failed", it) }

        // A previous run may have finished streaming but died before
        // verify/rename (e.g. process kill during SHA-256). Verify the complete
        // file instead of re-downloading it.
        if (ModelDownloadPolicy.isComplete(partFile.length(), entry.sizeBytes)) {
            return finalizeDownload(entry, partFile, target, container)
        }

        return try {
            val outcome = downloadWithProgress(entry, partFile)
            if (outcome != DownloadOutcome.STREAMED &&
                !ModelDownloadPolicy.isComplete(partFile.length(), entry.sizeBytes)
            ) {
                Log.i(TAG, "op=download phase=incomplete outcome=$outcome bytes=${partFile.length()}")
                return Result.retry()
            }
            setProgress(workDataOf(PROGRESS_PERCENT to 100))
            runCatching { setForeground(foregroundInfo(100)) }
            finalizeDownload(entry, partFile, target, container)
        } catch (e: CancellationException) {
            // Screen-off / 10-min stop / unique-work cancel: keep `.part`.
            Log.i(TAG, "op=download phase=cancelled bytes=${partFile.length()}")
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "op=download phase=io bytes=${partFile.length()}", e)
            if (ModelDownloadPolicy.isComplete(partFile.length(), entry.sizeBytes)) {
                // Stream finished; the failure is in verify/rename. Keep the
                // complete file — the next retry re-verifies it.
                return failure("Downloaded file failed integrity check. Please retry.")
            }
            if (!hasEnoughFreeSpace(modelsDir, entry, partFile)) {
                partFile.delete()
                return failure("Not enough free storage to complete the download. Free up space and retry.")
            }
            Result.retry()
        }
    }

    /**
     * Streams the model file, resuming from any existing `.part` bytes via an
     * HTTP Range request. Never truncates a non-empty `.part` (a 200 with a
     * full body skips the prefix; anything else retries).
     */
    private suspend fun downloadWithProgress(entry: OnDeviceModelEntry, dest: File): DownloadOutcome {
        val resumedBytes = dest.length()
        val request = Request.Builder()
            .url(entry.downloadUrl)
            .apply { if (resumedBytes > 0) header("Range", "bytes=$resumedBytes-") }
            .build()
        downloadClient.newCall(request).execute().use { response ->
            val contentLength = response.body?.contentLength() ?: -1L
            val disposition = ModelDownloadPolicy.streamDisposition(
                responseCode = response.code,
                resumedBytes = resumedBytes,
                contentLength = contentLength,
                catalogSize = entry.sizeBytes,
            )
            Log.i(
                TAG,
                "op=download phase=response code=${response.code} resumed=$resumedBytes " +
                    "contentLength=$contentLength disposition=$disposition",
            )
            when (disposition) {
                ModelDownloadPolicy.StreamDisposition.ALREADY_COMPLETE ->
                    return DownloadOutcome.ALREADY_COMPLETE
                ModelDownloadPolicy.StreamDisposition.RETRY ->
                    return DownloadOutcome.RETRY_LATER
                ModelDownloadPolicy.StreamDisposition.APPEND,
                ModelDownloadPolicy.StreamDisposition.WRITE_FROM_START,
                ModelDownloadPolicy.StreamDisposition.SKIP_PREFIX,
                -> Unit
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading model")
            val body = response.body ?: throw IOException("Empty response body downloading model")
            var downloaded = if (disposition == ModelDownloadPolicy.StreamDisposition.WRITE_FROM_START) {
                0L
            } else {
                resumedBytes
            }
            val append = disposition != ModelDownloadPolicy.StreamDisposition.WRITE_FROM_START
            FileOutputStream(dest, append).use { out ->
                body.byteStream().use { input ->
                    if (disposition == ModelDownloadPolicy.StreamDisposition.SKIP_PREFIX && resumedBytes > 0) {
                        ModelDownloadPolicy.skipFully(input, resumedBytes)
                    }
                    val buffer = ByteArray(64 * 1024)
                    var lastReportedPercent = -1
                    var bytesSinceSync = 0L
                    while (currentCoroutineContext().isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        bytesSinceSync += read
                        if (bytesSinceSync >= SYNC_EVERY_BYTES) {
                            out.flush()
                            out.fd.sync()
                            bytesSinceSync = 0L
                        }
                        val total = entry.sizeBytes
                        val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 99)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            setProgressAsync(workDataOf(PROGRESS_PERCENT to percent))
                            setForegroundAsync(foregroundInfo(percent))
                        }
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
        }
        return if (ModelDownloadPolicy.isComplete(dest.length(), entry.sizeBytes)) {
            DownloadOutcome.STREAMED
        } else {
            DownloadOutcome.INCOMPLETE
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
        Log.i(TAG, "op=download phase=done version=${entry.version}")
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

    private fun catalogEntry(): OnDeviceModelEntry =
        ModelCatalog.forVersion(inputData.getString(MODEL_VERSION)) ?: ModelCatalog.default

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        ensureDownloadChannel()
        val ctx = applicationContext
        val intent = ChompassLaunchIntents.openApp(ctx, ChompassRoutes.SETTINGS_AI)
        val content = PendingIntent.getActivity(
            ctx,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (percent >= 100) {
            ctx.getString(R.string.on_device_model_verifying)
        } else {
            ctx.getString(R.string.on_device_model_downloading, percent)
        }
        val notification = NotificationCompat.Builder(ctx, NotificationService.CHANNEL_MODEL_DOWNLOAD)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.notif_model_download_title))
            .setContentText(text)
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureDownloadChannel() {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(NotificationService.CHANNEL_MODEL_DOWNLOAD) != null) return
        val channel = NotificationChannel(
            NotificationService.CHANNEL_MODEL_DOWNLOAD,
            applicationContext.getString(R.string.notif_channel_model_download),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = applicationContext.getString(R.string.notif_channel_model_download_desc)
            setSound(null, null)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(FAILURE_REASON to message))

    private enum class DownloadOutcome { STREAMED, ALREADY_COMPLETE, INCOMPLETE, RETRY_LATER }

    companion object {
        const val UNIQUE_NAME = "ondevice_model_download"
        const val MODEL_VERSION = "modelVersion"
        const val PROGRESS_PERCENT = "progressPercent"
        const val FAILURE_REASON = "failureReason"

        private const val TAG = "FudModelDownload"
        private const val NOTIFICATION_ID = 6101
        private const val SYNC_EVERY_BYTES = 8L * 1024 * 1024

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
                        .build(),
                )
                .build()
            val wm = WorkManager.getInstance(context)
            val infos = runCatching {
                wm.getWorkInfosForUniqueWork(UNIQUE_NAME).get(1, TimeUnit.SECONDS)
            }.getOrNull().orEmpty()
            val active = infos.firstOrNull { !it.state.isFinished }
            val activeVersion = active?.tags
                ?.firstOrNull { it.startsWith("model:") }
                ?.removePrefix("model:")
            val policy = ModelDownloadPolicy.uniqueWorkPolicy(
                activeUnfinished = active != null,
                activeVersion = activeVersion,
                requestedVersion = entry.version,
            )
            Log.i(TAG, "op=download phase=enqueue version=${entry.version} policy=$policy")
            wm.enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }

        fun modelTag(version: String): String = "model:$version"
    }
}
