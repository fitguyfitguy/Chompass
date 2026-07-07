package org.codeberg.fitguy.nofud.services.update

import android.content.Context

object AndroidUpdateChecker {
    const val RELEASE_PACKAGE_NAME = "org.codeberg.fitguy.nofud"
    const val PLAY_STORE_WEB_URL =
        "https://codeberg.org/fitguy/nofud/releases"
    const val PLAY_STORE_MARKET_URL = PLAY_STORE_WEB_URL

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName
            ?.substringBefore("-")
            ?.ifBlank { null }
            ?: "Unknown"

    suspend fun check(context: Context, current: String): AndroidUpdateState =
        AndroidUpdateState.UpToDate(current = current, latest = null)
}
