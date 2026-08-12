package app.chompass.services.ai

import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Retries transient overload (503/529) with 1s/2s/4s exponential backoff (same as iOS).
 * HTTP 429 is not retried — quota is exhausted and immediate retries only burn more
 * requests; callers may try a different fallback model instead.
 * On final failure, throws [AiError.Api] with a user-friendly message.
 * The caller supplies a factory that builds a fresh [Call] per attempt
 * because OkHttp [Call] instances can only be executed once.
 */
object RetryPolicy {
    private val delays = longArrayOf(1_000, 2_000, 4_000)

    internal fun isRetryableHttpStatus(code: Int): Boolean =
        code == 503 || code == 529

    suspend fun execute(callFactory: () -> Call): String {
        open(callFactory).use { response ->
            return response.body?.string().orEmpty()
        }
    }

    /**
     * Like [execute], but returns the successful [Response] with its body stream
     * still open for SSE/chunked reading. Caller must close the response.
     */
    suspend fun open(callFactory: () -> Call): Response {
        var lastMessage = "Request failed"
        for (attempt in 0..delays.size) {
            val response = try {
                callFactory().await()
            } catch (io: IOException) {
                throw AiError.Network(io)
            }

            if (response.isSuccessful) return response

            val bodyStr = response.use { it.body?.string().orEmpty() }
            val code = response.code
            val raw = parseErrorMessage(bodyStr)?.takeIf { it.isNotEmpty() } ?: "HTTP $code"
            lastMessage = friendlyMessage(code, raw)

            if (isRetryableHttpStatus(code) && attempt < delays.size) {
                delay(delays[attempt])
                continue
            }
            throw AiError.Api(lastMessage, messageRes = friendlyMessageRes(code, raw))
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

private suspend fun Call.await(): Response = suspendCoroutine { cont ->
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
}
