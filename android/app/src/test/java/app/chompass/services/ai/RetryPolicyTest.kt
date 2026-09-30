package app.chompass.services.ai

import app.chompass.R
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RetryPolicyTest {
    @Test
    fun overloadStatusesAreRetryable() {
        assertTrue(RetryPolicy.isRetryableHttpStatus(503))
        assertTrue(RetryPolicy.isRetryableHttpStatus(529))
    }

    @Test
    fun rateLimitIsNotRetryable() {
        assertFalse(RetryPolicy.isRetryableHttpStatus(429))
    }

    @Test
    fun clientErrorsAreNotRetryable() {
        assertFalse(RetryPolicy.isRetryableHttpStatus(400))
        assertFalse(RetryPolicy.isRetryableHttpStatus(401))
    }

    @Test
    fun dnsMissThenHttp200_returnsBody() = runBlocking {
        val factory = script(
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            ok("ok"),
        )
        val body = RetryPolicy.open(factory, NO_DELAY).use { it.body!!.string() }
        assertEquals("ok", body)
        assertEquals(2, factory.count)
    }

    @Test
    fun dnsMissExhausted_throwsNetworkWithPlatformLine() = runBlocking {
        val factory = script(
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            fail(UnknownHostException(ANDROID_DNS_LINE)),
        )
        val network = openError(factory) as AiError.Network
        assertEquals("Network error: $ANDROID_DNS_LINE", network.message)
        assertEquals(R.string.ai_error_network_format, network.messageRes)
        assertEquals(ANDROID_DNS_LINE, network.formatArgs[0])
        assertEquals(4, factory.count)
    }

    @Test
    fun wrappedUnknownHost_isRetried() = runBlocking {
        val wrapped = IOException("wrap").apply {
            initCause(UnknownHostException(ANDROID_DNS_LINE))
        }
        val factory = script(fail(wrapped), ok("ok"))
        val body = RetryPolicy.open(factory, NO_DELAY).use { it.body!!.string() }
        assertEquals("ok", body)
        assertEquals(2, factory.count)
    }

    @Test
    fun plainIoExceptionWithAndroidLine_isNotRetried() = runBlocking {
        val factory = script(fail(IOException(ANDROID_DNS_LINE)), ok("ok"))
        val error = openError(factory)
        assertTrue("expected Network, got $error", error is AiError.Network)
        assertEquals(1, factory.count)
    }

    @Test
    fun socketTimeout_isNotRetried() = runBlocking {
        val factory = script(fail(SocketTimeoutException("timeout")), ok("ok"))
        val error = openError(factory)
        assertTrue("expected Network, got $error", error is AiError.Network)
        assertEquals(1, factory.count)
    }

    @Test
    fun canceledIoException_isNotRetried() = runBlocking {
        val factory = script(fail(IOException("Canceled")), ok("ok"))
        val error = openError(factory)
        assertTrue("expected Network, got $error", error is AiError.Network)
        assertEquals(1, factory.count)
    }

    @Test
    fun http503Then200_isStillRetried() = runBlocking {
        val factory = script(http(503, "busy"), ok("ok"))
        val body = RetryPolicy.open(factory, NO_DELAY).use { it.body!!.string() }
        assertEquals("ok", body)
        assertEquals(2, factory.count)
    }

    @Test
    fun http429_isNotRetried() = runBlocking {
        val factory = script(http(429, "slow down"), ok("ok"))
        val error = openError(factory)
        assertTrue("expected Api, got $error", error is AiError.Api)
        assertEquals(1, factory.count)
    }

    @Test
    fun overloadThenDnsMiss_sharesAttemptBudget() = runBlocking {
        val factory = script(
            http(503, "busy"),
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            fail(UnknownHostException(ANDROID_DNS_LINE)),
            ok("ok"),
        )
        val error = openError(factory)
        assertTrue("expected Network, got $error", error is AiError.Network)
        assertEquals(4, factory.count)
    }

    @Test
    fun isDnsMiss_matchesUnknownHostInCauseChainOnly() {
        assertTrue(RetryPolicy.isDnsMiss(UnknownHostException(ANDROID_DNS_LINE)))
        val wrapped = IOException("wrap").apply {
            initCause(UnknownHostException(ANDROID_DNS_LINE))
        }
        assertTrue(RetryPolicy.isDnsMiss(wrapped))
        assertFalse(RetryPolicy.isDnsMiss(IOException(ANDROID_DNS_LINE)))
        assertFalse(RetryPolicy.isDnsMiss(SocketTimeoutException("timeout")))
        assertFalse(RetryPolicy.isDnsMiss(IOException("Canceled")))
    }

    private suspend fun openError(factory: ScriptedFactory): Throwable {
        val result = runCatching { RetryPolicy.open(factory, NO_DELAY) }
        result.getOrNull()?.close()
        val error = result.exceptionOrNull()
        assertTrue("expected an exception, got success", error != null)
        return error!!
    }
}

private const val ANDROID_DNS_LINE =
    "Unable to resolve host \"generativelanguage.googleapis.com\": No address associated with hostname"

private val NO_DELAY = longArrayOf(0, 0, 0)

private class ScriptedFactory(private val calls: List<Call>) : () -> Call {
    var count = 0
        private set

    override fun invoke(): Call {
        check(count < calls.size) { "factory invoked ${count + 1} times; only ${calls.size} scripted" }
        return calls[count++]
    }
}

private fun script(vararg calls: Call) = ScriptedFactory(calls.toList())

private fun fail(error: IOException): Call = ScriptedCall(failure = error)

private fun ok(body: String): Call = ScriptedCall(code = 200, body = body)

private fun http(code: Int, body: String): Call = ScriptedCall(code = code, body = body)

private class ScriptedCall(
    private val failure: IOException? = null,
    private val code: Int = 200,
    private val body: String = "",
) : Call {
    private val req: Request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/")
        .build()

    override fun request(): Request = req

    override fun execute(): Response = error("unused")

    override fun enqueue(responseCallback: Callback) {
        val failure = failure
        if (failure != null) {
            responseCallback.onFailure(this, failure)
        } else {
            responseCallback.onResponse(this, response())
        }
    }

    override fun cancel() {}

    override fun isExecuted(): Boolean = false

    override fun isCanceled(): Boolean = false

    override fun timeout(): Timeout = Timeout()

    override fun clone(): Call = error("unused")

    private fun response(): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .body(body.toResponseBody("text/plain".toMediaType()))
            .build()
}
