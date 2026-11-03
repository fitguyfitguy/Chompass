package app.chompass.services.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.chompass.ChompassApp
import kotlinx.coroutines.CancellationException

/**
 * Thin WorkManager wrapper over [OfflineIndexManager.download]: pack files
 * are tens of MB (not GB), so unlike the model download there is no
 * foreground service and no resume — a retry simply starts over. The
 * Wi-Fi-only default is enforced with the same connectivity constraint the
 * model download uses (`UNMETERED`).
 */
class OfflineIndexDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? ChompassApp)?.container ?: return Result.failure()
        val pack = OfflineIndexPacks.byId(inputData.getString(PACK_ID) ?: "") ?: return Result.failure()
        return try {
            container.offlineIndexManager.download(pack)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val PACK_ID = "packId"

        private fun uniqueName(packId: String) = "offline_index_$packId"

        fun enqueue(context: Context, packId: String, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<OfflineIndexDownloadWorker>()
                .setInputData(workDataOf(PACK_ID to packId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(packId), ExistingWorkPolicy.KEEP, request)
        }
    }
}
