package app.chompass.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
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
                .header("Authorization", webDavBasicAuth(username, password))
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
        mode: WebDavPutMode,
    ): PutResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", webDavBasicAuth(username, password))
            .header("Content-Type", "application/json; charset=utf-8")
            .put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        when (mode) {
            WebDavPutMode.CreateOnly -> builder.header("If-None-Match", "*")
            is WebDavPutMode.IfMatch -> builder.header("If-Match", mode.etag)
            WebDavPutMode.Unconditional -> Unit
        }
        http.newCall(builder.build()).execute().use { response ->
            when (response.code) {
                412 -> PutResult(etag = null, conflict = true)
                in 200..299 -> PutResult(etag = response.header("ETag"), conflict = false)
                else -> error("WebDAV PUT failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * Binary GET for photo downloads (#122). String [get] corrupts binary
     * through charset decoding — never reuse it for images. Null on 404
     * (the ref stays advertised and is retried on the next sync).
     */
    suspend fun getBinary(url: String, username: String, password: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", webDavBasicAuth(username, password))
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> null
                    in 200..299 -> response.body?.bytes()
                    else -> error("WebDAV GET failed: HTTP ${response.code}")
                }
            }
        }

    /**
     * Unconditional binary PUT for content-addressed photo files (#122):
     * identical bytes map to the same ref, so no ETag precondition applies.
     * 409 (missing parent collection) maps to [PutResult.conflict] so the
     * caller can MKCOL and retry once.
     */
    suspend fun putBytes(
        url: String,
        username: String,
        password: String,
        bytes: ByteArray,
        contentType: String = "image/jpeg",
    ): PutResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", webDavBasicAuth(username, password))
            .header("Content-Type", contentType)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            when (response.code) {
                409 -> PutResult(etag = null, conflict = true)
                in 200..299 -> PutResult(etag = response.header("ETag"), conflict = false)
                else -> error("WebDAV PUT failed: HTTP ${response.code}")
            }
        }
    }

    /** MKCOL for the photos collection: 201 created, 405 already exists, 409 tolerated (caller retries the PUT). */
    suspend fun mkcol(url: String, username: String, password: String): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", webDavBasicAuth(username, password))
                .method("MKCOL", null)
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    201, 405, 409 -> Unit
                    else -> error("WebDAV MKCOL failed: HTTP ${response.code}")
                }
            }
        }
}

/** How to condition a WebDAV PUT for create vs update. */
sealed class WebDavPutMode {
    /** Only succeed if the resource does not exist yet. */
    data object CreateOnly : WebDavPutMode()

    /** Only succeed if the current ETag matches. */
    data class IfMatch(val etag: String) : WebDavPutMode()

    /** Overwrite with no precondition (no / broken ETag from the server). */
    data object Unconditional : WebDavPutMode()
}

/**
 * Choose PUT preconditions from a prior GET.
 *
 * Important: a missing ETag on an existing file must NOT use If-None-Match: *
 * (that means "create only" and 412s on every subsequent sync).
 */
internal fun webDavPutMode(etag: String?, notFound: Boolean): WebDavPutMode = when {
    notFound -> WebDavPutMode.CreateOnly
    !etag.isNullOrBlank() -> WebDavPutMode.IfMatch(normalizeEtagForIfMatch(etag))
    else -> WebDavPutMode.Unconditional
}

/**
 * If-Match uses strong comparison; weak validators (W/"…") never match.
 * Strip the weak prefix so Apache/Hetzner-style ETags can still be used.
 */
internal fun normalizeEtagForIfMatch(etag: String): String {
    val trimmed = etag.trim()
    return if (trimmed.startsWith("W/", ignoreCase = true)) {
        trimmed.substring(2).trimStart()
    } else {
        trimmed
    }
}

/**
 * Basic auth matching curl / RFC 7617 (UTF-8). OkHttp's default is ISO-8859-1,
 * which 401s against hosts like Hetzner Storage Box when the password has ß, §, etc.
 */
internal fun webDavBasicAuth(username: String, password: String): String =
    Credentials.basic(username, password, StandardCharsets.UTF_8)
