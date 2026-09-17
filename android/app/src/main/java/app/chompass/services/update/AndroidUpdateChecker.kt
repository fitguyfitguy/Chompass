package app.chompass.services.update

import android.content.Context
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.ai.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Update check for the single F-Droid / Codeberg distribution. One anonymous
 * GET of the Forgejo "latest release" endpoint; nothing app-specific is sent
 * (no version, no identifiers). The installed version is only ever compared
 * locally. Replaces the removed Play-flavor checker (42076fc77c).
 */
object AndroidUpdateChecker {
    const val RELEASE_PACKAGE_NAME = "app.chompass"
    const val PLAY_STORE_WEB_URL =
        "https://codeberg.org/fitguy/chompass/releases"
    const val PLAY_STORE_MARKET_URL = PLAY_STORE_WEB_URL

    private const val LATEST_RELEASE_API =
        "https://codeberg.org/api/v1/repos/fitguy/chompass/releases/latest"

    /** Short budget: the check rides the app start and must never hold it. */
    private val client: OkHttpClient by lazy {
        FoodAnalysisService.defaultClient.newBuilder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName
            ?.substringBefore("-")
            ?.ifBlank { null }
            ?: "Unknown"

    suspend fun check(context: Context, current: String): AndroidUpdateState {
        val latest = fetchLatestVersion() ?: return AndroidUpdateState.Failed(current)
        return if (isNewer(latest, current)) {
            AndroidUpdateState.Available(current = current, latest = latest)
        } else {
            AndroidUpdateState.UpToDate(current = current, latest = latest)
        }
    }

    private suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(LATEST_RELEASE_API).build()
            client.newCall(request).await()
        }.map { response ->
            response.use { body ->
                if (!body.isSuccessful) {
                    null
                } else {
                    body.body?.string()?.let(::parseLatestVersion)
                }
            }
        }.getOrNull()
    }

    /** `"v5.1.0"` inside Forgejo's release JSON, or null when the tag is unusable. */
    internal fun parseLatestVersion(body: String): String? =
        runCatching { JSONObject(body).optString("tag_name") }.getOrNull()
            ?.trim()
            ?.trimStart('v', 'V')
            ?.takeIf { it.isNotEmpty() && it[0].isDigit() }

    /** Segment-wise semantic compare; "5.2" is newer than "5.1.9", equal is not newer. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        if (candidate == "Unknown" || current == "Unknown") return false
        val candidateParts = candidate.split('.').map { segmentNumber(it) }
        val currentParts = current.split('.').map { segmentNumber(it) }
        for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val c = candidateParts.getOrElse(index) { 0L }
            val k = currentParts.getOrElse(index) { 0L }
            if (c != k) return c > k
        }
        return false
    }

    private fun segmentNumber(segment: String): Long =
        segment.takeWhile(Char::isDigit).toLongOrNull() ?: 0L
}
