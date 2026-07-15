package org.codeberg.fitguy.nofud.services.ondevice

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

    fun modelFile(): File = File(modelsDir(), ModelCatalog.current.filename)

    fun isDownloaded(): Boolean = modelFile().exists()

    fun modelsDir(): File = File(context.filesDir, "models")

    fun startDownload(overWifiOnly: Boolean) {
        ModelDownloadWorker.enqueue(context, overWifiOnly)
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.UNIQUE_NAME)
    }

    fun delete(): Boolean {
        cancelDownload()
        val file = modelFile()
        val partFile = File(modelsDir(), "${ModelCatalog.current.filename}.part")
        partFile.delete()
        return !file.exists() || file.delete()
    }

    /** Live download state, driven by [ModelDownloadWorker]'s progress + WorkManager state. */
    fun state(): Flow<OnDeviceDownloadState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
            .map { infos ->
                val info = infos.firstOrNull()
                when {
                    info == null -> if (isDownloaded()) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
                    info.state == WorkInfo.State.RUNNING -> {
                        val percent = info.progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, 0)
                        if (percent >= 100) OnDeviceDownloadState.Verifying else OnDeviceDownloadState.Downloading(percent)
                    }
                    info.state == WorkInfo.State.ENQUEUED -> OnDeviceDownloadState.Downloading(0)
                    info.state == WorkInfo.State.SUCCEEDED -> OnDeviceDownloadState.Downloaded
                    info.state == WorkInfo.State.FAILED -> {
                        val reason = info.outputData.getString(ModelDownloadWorker.FAILURE_REASON)
                        OnDeviceDownloadState.Failed(reason ?: "Download failed")
                    }
                    info.state == WorkInfo.State.CANCELLED -> OnDeviceDownloadState.NotDownloaded
                    else -> if (isDownloaded()) OnDeviceDownloadState.Downloaded else OnDeviceDownloadState.NotDownloaded
                }
            }
}
