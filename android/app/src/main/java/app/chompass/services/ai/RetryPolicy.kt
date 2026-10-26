package app.chompass.services.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Headers
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
    internal const val MAX_ERROR_BODY_BYTES = 64L * 1024

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
        executeText(callFactory = callFactory).body

    /**
     * Like [execute], but keeps the response Content-Type for parse-failure logs.
     * Main-safe: the body is read on OkHttp's callback thread in [Call.awaitBody], so
     * callers on the main dispatcher (the coach) never touch the socket (upstream #540).
     * The retry ladder mirrors [open]; kept separate because success returns a fully
     * read body while open() hands over a live streaming [Response].
     */
    internal suspend fun executeText(retryDelaysMs: LongArray = delays, callFactory: () -> Call): AiHttpText {
        var lastMessage = "Request failed"
        for (attempt in 0..retryDelaysMs.size) {
            val buffered = try {
                callFactory().awaitBody()
            } catch (io: IOException) {
                // A server closing mid-body (llama.cpp context drop, Ollama crash) throws
                // inside awaitBody. Map it like every other IO failure (#123): the raw
                // escape rendered as the generic "Chat failed" string and left no diagnostic.
                if (isDnsMiss(io) && attempt < retryDelaysMs.size) {
                    delay(retryDelaysMs[attempt])
                    continue
                }
                throw AiError.Network(io)
            }

            if (buffered.isSuccessful) {
                return AiHttpText(
                    body = buffered.body,
                    contentType = buffered.header("Content-Type"),
                )
            }

            val code = buffered.code
            val raw = parseErrorMessage(buffered.body)?.takeIf { it.isNotEmpty() } ?: "HTTP $code"
            lastMessage = friendlyMessage(code, raw)

            if (isRetryableHttpStatus(code) && attempt < retryDelaysMs.size) {
                delay(retryDelaysMs[attempt])
                continue
            }
            throw AiError.Api(
                lastMessage,
                messageRes = friendlyMessageRes(code, raw),
                httpStatus = code,
                providerMessage = raw.takeIf { it != "HTTP $code" },
            )
        }
        throw AiError.Api(lastMessage)
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
            // Read on IO: like awaitBody's reads, a throttled error body must not
            // block the caller's (main) dispatcher on the socket.
            val bodyStr = response.use { resp ->
                withContext(Dispatchers.IO) {
                    // A truncated error body must not mask the HTTP status: fall back to
                    // the empty string so the message becomes "HTTP <code>".
                    runCatching {
                        resp.body?.source()?.let { source ->
                            source.request(MAX_ERROR_BODY_BYTES + 1)
                            source.readUtf8(minOf(source.buffer.size, MAX_ERROR_BODY_BYTES))
                        }.orEmpty()
                    }.getOrDefault("")
                }
            }
            val code = response.code
            val raw = parseErrorMessage(bodyStr)?.takeIf { it.isNotEmpty() } ?: "HTTP $code"
            lastMessage = friendlyMessage(code, raw)

            if (isRetryableHttpStatus(code) && attempt < retryDelaysMs.size) {
                delay(retryDelaysMs[attempt])
                continue
            }
            throw AiError.Api(
                lastMessage,
                messageRes = friendlyMessageRes(code, raw),
                httpStatus = code,
                providerMessage = raw.takeIf { it != "HTTP $code" },
            )
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

/**
 * A response whose body [awaitBody] has already read to the end and closed.
 */
internal class BufferedResponse(val code: Int, val headers: Headers, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299

    fun header(name: String): String? = headers[name]
}

/**
 * Like [await], but reads the whole body on OkHttp's callback thread before resuming, so a
 * caller on the main dispatcher never touches the socket (Android throws
 * NetworkOnMainThreadException when a body read has to wait on the network; upstream #540).
 * Error bodies stay capped at [RetryPolicy.MAX_ERROR_BODY_BYTES], like in [RetryPolicy.open].
 * The coroutine stays suspended until the body is read, so cancelling it during a slow body
 * still cancels the call.
 */
internal suspend fun Call.awaitBody(): BufferedResponse = suspendCancellableCoroutine { cont ->
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (!cont.isActive) {
                response.close()
                return
            }
            // Anything thrown out of this callback would crash OkHttp's thread instead of
            // reaching the caller, so failures are handed to the continuation.
            val result = runCatching {
                response.use { resp ->
                    val body = if (resp.isSuccessful) {
                        resp.body?.string().orEmpty()
                    } else {
                        // A truncated error body must not mask the HTTP status: fall back to
                        // the empty string so the message becomes "HTTP <code>".
                        runCatching {
                            resp.body?.source()?.let { source ->
                                source.request(RetryPolicy.MAX_ERROR_BODY_BYTES + 1)
                                source.readUtf8(minOf(source.buffer.size, RetryPolicy.MAX_ERROR_BODY_BYTES))
                            }.orEmpty()
                        }.getOrDefault("")
                    }
                    BufferedResponse(resp.code, resp.headers, body)
                }
            }
            if (cont.isActive) cont.resumeWith(result)
        }
    })
    cont.invokeOnCancellation { cancel() }
}
