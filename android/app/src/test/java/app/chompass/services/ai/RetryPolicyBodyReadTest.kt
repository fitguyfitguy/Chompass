package app.chompass.services.ai

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * #123 regression: a server dropping the connection mid-body must surface as a
 * mapped [AiError.Network] (or keep its HTTP status on the error path), not
 * escape as a raw IOException. Uses a real transport (MockWebServer) because a
 * mid-read EOF needs a lying Content-Length, which hand-built ResponseBodies
 * never enforce.
 */
class RetryPolicyBodyReadTest {
    private fun postCall(server: MockWebServer, client: OkHttpClient): () -> okhttp3.Call {
        val request = Request.Builder()
            .url(server.url("/v1/chat/completions"))
            .post("{\"model\":\"test\"}".toRequestBody("application/json".toMediaType()))
            .build()
        return { client.newCall(request) }
    }

    @Test
    fun midBodyDisconnect_mapsToNetworkError() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", 500)
                    .setBody("{\"choices\":[{\"message\":{\"content\":\"partial\"}}]}")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            )
            val error = runCatching {
                RetryPolicy.executeText(callFactory = postCall(server, OkHttpClient()))
            }.exceptionOrNull()
            assertTrue("expected AiError.Network, got $error", error is AiError.Network)
            assertTrue(
                "expected unexpected-end-of-stream cause in ${error?.message}",
                error?.message?.contains("unexpected end of stream") == true,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun truncatedErrorBody_keepsHttpStatus() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Length", 500)
                    .setBody("{\"error\":{\"message\":\"boom\"}}")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            )
            val error = runCatching {
                RetryPolicy.execute(postCall(server, OkHttpClient()))
            }.exceptionOrNull()
            val api = error as? AiError.Api
            assertTrue("expected AiError.Api, got $error", api != null)
            assertEquals(500, api?.httpStatus)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun parsedErrorBody_carriesProviderMessage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("{\"error\":{\"message\":\"Invalid API Key\"}}")
            )
            val error = runCatching {
                RetryPolicy.execute(postCall(server, OkHttpClient()))
            }.exceptionOrNull()
            val api = error as? AiError.Api
            assertTrue("expected AiError.Api, got $error", api != null)
            assertEquals(401, api?.httpStatus)
            assertEquals("Invalid API Key", api?.providerMessage)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun unparsableErrorBody_hasNullProviderMessage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("<html>gateway oops</html>")
            )
            val error = runCatching {
                RetryPolicy.execute(postCall(server, OkHttpClient()))
            }.exceptionOrNull()
            val api = error as? AiError.Api
            assertTrue("expected AiError.Api, got $error", api != null)
            assertEquals(500, api?.httpStatus)
            assertNull(api?.providerMessage)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun bodyReadTimeout_mapsToNetworkError() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("should never finish")
                    .setBodyDelay(2, TimeUnit.SECONDS)
            )
            val error = runCatching {
                RetryPolicy.executeText(callFactory = postCall(server, client))
            }.exceptionOrNull()
            assertTrue("expected AiError.Network, got $error", error is AiError.Network)
            assertTrue(
                "expected timeout cause in ${error?.message}",
                error?.message?.contains("timeout") == true,
            )
        } finally {
            server.shutdown()
        }
    }
}
