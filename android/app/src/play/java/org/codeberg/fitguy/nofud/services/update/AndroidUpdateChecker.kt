package org.codeberg.fitguy.nofud.services.update

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AndroidUpdateChecker {
    const val RELEASE_PACKAGE_NAME = "org.codeberg.fitguy.nofud"
    const val PLAY_STORE_WEB_URL =
        "https://play.google.com/store/apps/details?id=$RELEASE_PACKAGE_NAME"
    const val PLAY_STORE_MARKET_URL = "market://details?id=$RELEASE_PACKAGE_NAME"

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName
            ?.substringBefore("-")
            ?.ifBlank { null }
            ?: "Unknown"

    suspend fun check(context: Context, current: String): AndroidUpdateState {
        if (context.packageName != RELEASE_PACKAGE_NAME) {
            return AndroidUpdateState.UpToDate(current = current, latest = null)
        }
        return try {
            val info = AppUpdateManagerFactory.create(context).appUpdateInfo.awaitTask()
            when (info.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE,
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    if (info.isPlayableUpdate()) {
                        AndroidUpdateState.Available(
                            current = current,
                            latest = info.playVersionLabel()
                        )
                    } else {
                        AndroidUpdateState.UpToDate(current = current, latest = current)
                    }
                }
                else -> AndroidUpdateState.UpToDate(current = current, latest = current)
            }
        } catch (_: Throwable) {
            AndroidUpdateState.Failed(current)
        }
    }

    private fun AppUpdateInfo.isPlayableUpdate(): Boolean {
        return isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ||
            isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ||
            updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
    }

    private fun AppUpdateInfo.playVersionLabel(): String {
        val versionCode = availableVersionCode()
        return if (versionCode > 0) "build $versionCode" else "Google Play"
    }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.cancel(CancellationException("Play update check was cancelled"))
                }
            }
        }
}
