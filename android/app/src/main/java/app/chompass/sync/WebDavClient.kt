package app.chompass.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal WebDAV client for a single sync document (GET / PUT with ETag).
 * Credentials stay on-device; Chompass does not operate a sync server.
 */
class WebDavClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    data class RemoteDocument(
        val body: String?,
        val etag: String?,
        val notFound: Boolean,
    )

    data class PutResult(val etag: String?, val conflict: Boolean)

    suspend fun get(url: String, username: String, password: String): RemoteDocument =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(username, password))
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> RemoteDocument(body = null, etag = null, notFound = true)
                    in 200..299 -> RemoteDocument(
                        body = response.body?.string(),
                        etag = response.header("ETag"),
                        notFound = false,
                    )
                    else -> error("WebDAV GET failed: HTTP ${response.code}")
                }
            }
        }

    suspend fun put(
        url: String,
        username: String,
        password: String,
        body: String,
        ifMatch: String?,
    ): PutResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("Content-Type", "application/json; charset=utf-8")
            .put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        when {
            ifMatch.isNullOrBlank() -> builder.header("If-None-Match", "*")
            else -> builder.header("If-Match", ifMatch)
        }
        http.newCall(builder.build()).execute().use { response ->
            when (response.code) {
                412 -> PutResult(etag = null, conflict = true)
                in 200..299 -> PutResult(etag = response.header("ETag"), conflict = false)
                else -> error("WebDAV PUT failed: HTTP ${response.code}")
            }
        }
    }
}
