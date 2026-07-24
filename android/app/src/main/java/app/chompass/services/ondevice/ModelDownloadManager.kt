package app.chompass.services.ondevice

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class OnDeviceDownloadState {
    object NotDownloaded : OnDeviceDownloadState()
    data class Downloading(val progressPercent: Int) : OnDeviceDownloadState()
    object Verifying : OnDeviceDownloadState()
    object Downloaded : OnDeviceDownloadState()
    data class Failed(val message: String) : OnDeviceDownloadState()
}

/**
 * Orchestrates the on-device model download via [ModelDownloadWorker] and
 * exposes its live state. The actual network/verify/atomic-rename work lives
 * in the worker so it survives process death; this class is just the
 * WorkManager-facing handle used by Settings UI.
 */
class ModelDownloadManager(private val context: Context) {

    fun modelFile(entry: OnDeviceModelEntry = ModelCatalog.default): File =
        File(modelsDir(), entry.filename)

    fun isDownloaded(entry: OnDeviceModelEntry = ModelCatalog.default): Boolean =
        modelFile(entry).exists()

    fun modelsDir(): File = File(context.filesDir, "models")

    fun startDownload(entry: OnDeviceModelEntry, overWifiOnly: Boolean) {
        ModelDownloadWorker.enqueue(context, entry, overWifiOnly)
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.UNIQUE_NAME)
    }

    fun delete(entry: OnDeviceModelEntry = ModelCatalog.default): Boolean {
        cancelDownload()
        val file = modelFile(entry)
        val partFile = File(modelsDir(), "${entry.filename}.part")
        partFile.delete()
        return !file.exists() || file.delete()
    }

    /** Live download state for [modelId], driven by [ModelDownloadWorker] + WorkManager. */
    fun state(modelId: String): Flow<OnDeviceDownloadState> {
        val entry = ModelCatalog.forModelId(modelId)
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
            .map { infos -> mapWorkState(infos.firstOrNull(), entry) }
    }

    private fun mapWorkState(info: WorkInfo?, entry: OnDeviceModelEntry): OnDeviceDownloadState {
        val activeVersion = info?.tags
            ?.firstOrNull { it.startsWith("model:") }
            ?.removePrefix("model:")
        val workMatchesEntry = activeVersion == null || activeVersion == entry.version
        return when {
            info == null -> if (isDownloaded(entry)) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
            !workMatchesEntry -> if (isDownloaded(entry)) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
            info.state == WorkInfo.State.RUNNING -> {
                val percent = info.progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, 0)
                if (percent >= 100) OnDeviceDownloadState.Verifying else OnDeviceDownloadState.Downloading(percent)
            }
            info.state == WorkInfo.State.ENQUEUED -> OnDeviceDownloadState.Downloading(0)
            info.state == WorkInfo.State.SUCCEEDED -> {
                if (ModelCatalog.forVersion(activeVersion) == entry || isDownloaded(entry)) {
                    OnDeviceDownloadState.Downloaded
                } else {
                    OnDeviceDownloadState.NotDownloaded
                }
            }
            info.state == WorkInfo.State.FAILED -> {
                val reason = info.outputData.getString(ModelDownloadWorker.FAILURE_REASON)
                OnDeviceDownloadState.Failed(reason ?: "Download failed")
            }
            info.state == WorkInfo.State.CANCELLED -> if (isDownloaded(entry)) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
            else -> if (isDownloaded(entry)) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
        }
    }
}
