package app.chompass.services.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Retries transient overload (503/529, same as iOS) and a DNS miss
 * ([UnknownHostException], including as a cause) on the same 1s/2s/4s ladder.
 * Other [IOException]s fail on the first attempt.
 * HTTP 429 is not retried — quota is exhausted and immediate retries only burn more
 * requests; callers may try a different fallback model instead.
 * On final HTTP failure, throws [AiError.Api] with a user-friendly message.
 * A DNS miss that exhausts the ladder throws [AiError.Network] with the platform
 * line unchanged.
 * The caller supplies a factory that builds a fresh [Call] per attempt
 * because OkHttp [Call] instances can only be executed once.
 */
internal data class AiHttpText(
    val body: String,
    val contentType: String?,
)

object RetryPolicy {
    private val delays = longArrayOf(1_000, 2_000, 4_000)
    /** Largest error-body prefix buffered for message parsing (64 KB). */
    private const val MAX_ERROR_BODY_BYTES = 64L * 1024

    internal fun isRetryableHttpStatus(code: Int): Boolean =
        code == 503 || code == 529

    internal fun isDnsMiss(error: IOException): Boolean {
        var t: Throwable? = error
        while (t != null) {
            if (t is UnknownHostException) return true
            t = t.cause
        }
        return false
    }

    suspend fun execute(callFactory: () -> Call): String =
        executeText(callFactory).body

    /** Like [execute], but keeps the response Content-Type for parse-failure logs. */
    internal suspend fun executeText(callFactory: () -> Call): AiHttpText {
        open(callFactory).use { response ->
            return AiHttpText(
                body = response.body?.string().orEmpty(),
                contentType = response.header("Content-Type"),
            )
        }
    }

    /**
     * Like [execute], but returns the successful [Response] with its body stream
     * still open for SSE/chunked reading. Caller must close the response.
     */
    suspend fun open(callFactory: () -> Call): Response = open(callFactory, delays)

    internal suspend fun open(callFactory: () -> Call, retryDelaysMs: LongArray): Response {
        var lastMessage = "Request failed"
        for (attempt in 0..retryDelaysMs.size) {
            val response = try {
                callFactory().await()
            } catch (io: IOException) {
                if (isDnsMiss(io) && attempt < retryDelaysMs.size) {
                    delay(retryDelaysMs[attempt])
                    continue
                }
                throw AiError.Network(io)
            }

            if (response.isSuccessful) return response

            // Cap the buffered error body: an oversized (or hostile) body must
            // not be read in full just to parse one message string out of it.
            val bodyStr = response.use { resp ->
                resp.body?.source()?.let { source ->
                    source.request(MAX_ERROR_BODY_BYTES + 1)
                    source.readUtf8(minOf(source.buffer.size, MAX_ERROR_BODY_BYTES))
                }.orEmpty()
            }
            val code = response.code
            val raw = parseErrorMessage(bodyStr)?.takeIf { it.isNotEmpty() } ?: "HTTP $code"
            lastMessage = friendlyMessage(code, raw)

            if (isRetryableHttpStatus(code) && attempt < retryDelaysMs.size) {
                delay(retryDelaysMs[attempt])
                continue
            }
            throw AiError.Api(lastMessage, messageRes = friendlyMessageRes(code, raw), httpStatus = code)
        }
        throw AiError.Api(lastMessage)
    }

    internal fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            when (val errorNode = json.opt("error")) {
                is JSONObject -> errorNode.optString("message").takeIf { it.isNotEmpty() }
                is String -> errorNode.takeIf { it.isNotEmpty() }
                else -> null
            }
        }.getOrNull()
    }
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
    // Abort the socket when the caller cancels (watchdog timeout, sheet dismiss)
    // so a stalled stream does not keep an IO thread or connection alive.
    cont.invokeOnCancellation { cancel() }
}
