package app.chompass.services.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Upstream #540: the coach calls [RetryPolicy.execute] from the main dispatcher, so response
 * bodies have to be read on OkHttp's thread, not on the caller's (Android throws
 * NetworkOnMainThreadException when a main-thread body read has to wait on the network).
 */
class RetryPolicyThreadingTest {
    private val server = MockWebServer()
    private val recorder = BodyReadRecorder()
    private val caller = callerDispatcher()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder().addInterceptor(recorder.interceptor).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        caller.close()
    }

    private fun request() = Request.Builder().url(server.url("/v1/chat/completions")).build()

    /** A body that trickles in after the headers, like a slow self-hosted model's reply. */
    private fun slowBody(body: String) = MockResponse().setBody(body).throttleBody(1_024, 10, TimeUnit.MILLISECONDS)

    private fun completion(chars: Int) =
        """{"choices":[{"finish_reason":"stop","message":{"content":"${"x".repeat(chars)}"}}]}"""

    @Test
    fun successBodyIsReadOffTheCallingThread() {
        val body = completion(8_000)
        server.enqueue(slowBody(body))

        val raw = runBlocking(caller) { RetryPolicy.execute { client.newCall(request()) } }

        assertEquals(body, raw)
        assertTrue("body was never read", recorder.threads.isNotEmpty())
        assertFalse("body read on the caller's thread", recorder.readOnCaller)
    }

    @Test
    fun recorderSeesABodyReadOnTheCallingThread() {
        // Control for the checks above: plain await() leaves the body for the caller to read.
        server.enqueue(slowBody(completion(8_000)))

        runBlocking(caller) { client.newCall(request()).await().use { it.body!!.string() } }

        assertTrue(recorder.readOnCaller)
    }

    @Test
    fun retriedResponsesAreAllReadOffTheCallingThread() {
        val body = completion(4_000)
        server.enqueue(slowBody("""{"error":{"message":"overloaded ${"y".repeat(4_000)}"}}""").setResponseCode(503))
        server.enqueue(slowBody(body))

        val raw = runBlocking(caller) {
            RetryPolicy.executeText(longArrayOf(1)) { client.newCall(request()) }.body
        }

        assertEquals(body, raw)
        assertEquals(2, server.requestCount)
        assertFalse("body read on the caller's thread", recorder.readOnCaller)
    }

    @Test
    fun nonRetryableErrorBodyIsReadOffTheCallingThread() {
        server.enqueue(
            slowBody("""{"error":{"message":"Rate limit exceeded ${"z".repeat(2_000)}"}}""").setResponseCode(429)
        )

        try {
            runBlocking(caller) { RetryPolicy.execute { client.newCall(request()) } }
            fail("Expected failure")
        } catch (error: AiError.Api) {
            assertEquals(429, error.httpStatus)
        }
        assertEquals(1, server.requestCount)
        assertFalse("body read on the caller's thread", recorder.readOnCaller)
    }

    @Test
    fun streamingErrorBodiesAreReadOffTheCallingThread() {
        // open() keeps successful bodies streaming for SSE; its capped error-body read must
        // still leave the caller's (main) dispatcher.
        server.enqueue(slowBody("""{"error":{"message":"busy ${"q".repeat(4_000)}"}}""").setResponseCode(503))
        server.enqueue(slowBody(completion(2_000)))

        runBlocking(caller) {
            RetryPolicy.open({ client.newCall(request()) }, longArrayOf(1)).close()
        }

        assertEquals(2, server.requestCount)
        assertTrue("error body was never read", recorder.threads.isNotEmpty())
        assertFalse("body read on the caller's thread", recorder.readOnCaller)
    }

    @Test
    fun cancellingWhileTheBodyIsStreamingCancelsTheCall() {
        // 1 byte per second: the body would take minutes to arrive.
        server.enqueue(MockResponse().setBody(completion(200)).throttleBody(1, 1, TimeUnit.SECONDS))
        val call = AtomicReference<Call>()

        val job = CoroutineScope(caller).launch {
            RetryPolicy.execute { client.newCall(request()).also(call::set) }
        }
        assertTrue("body read never started", recorder.firstRead.await(5, TimeUnit.SECONDS))

        job.cancel()
        joinWithin(job, seconds = 5)
        assertTrue(call.get().isCanceled())
    }

    @Test
    fun cancellingBeforeTheHeadersArriveCancelsTheCall() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val call = AtomicReference<Call>()

        val job = CoroutineScope(caller).launch {
            RetryPolicy.execute { client.newCall(request()).also(call::set) }
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        job.cancel()
        joinWithin(job, seconds = 5)
        assertTrue(call.get().isCanceled())
        assertTrue(recorder.threads.isEmpty())
    }

    @Test
    fun bodyCutOffMidwayIsANetworkError() {
        server.enqueue(
            MockResponse().setBody(completion(8_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        try {
            runBlocking(caller) { RetryPolicy.execute { client.newCall(request()) } }
            fail("Expected failure")
        } catch (error: AiError.Network) {
            assertNotNull(error.cause)
        }
        assertFalse("body read on the caller's thread", recorder.readOnCaller)
    }

    private fun joinWithin(job: Job, seconds: Long) = runBlocking {
        withTimeout(TimeUnit.SECONDS.toMillis(seconds)) { job.join() }
    }
}
