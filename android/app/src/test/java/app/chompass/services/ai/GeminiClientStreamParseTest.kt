package app.chompass.services.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * #68: gemini-3.8-flash under load can cut the SSE stream mid-reply. The
 * partial text used to return as success and fail food-JSON parsing with
 * InvalidResponse. analyzeStreaming must treat a stream without finishReason
 * as incomplete and retry the request as one shot.
 */
class GeminiClientStreamParseTest {
    private fun textChunk(text: String, finishReason: String? = null): String {
        val candidate = JSONObject()
            .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))
        if (finishReason != null) candidate.put("finishReason", finishReason)
        return JSONObject().put("candidates", JSONArray().put(candidate)).toString()
    }

    private fun oneShotBody(text: String): String = textChunk(text, "STOP")

    @Test
    fun parseStreamChunk_finalChunkCarriesTextAndFinishReason() {
        val chunk = GeminiClient.parseStreamChunk(textChunk("tail", "STOP"))!!
        assertEquals("tail", chunk.text)
        assertEquals("STOP", chunk.finishReason)
    }

    @Test
    fun parseStreamChunk_nonJsonYieldsNull_usageOnlyChunkYieldsNoTextNoFinish() {
        assertNull(GeminiClient.parseStreamChunk("not json"))
        val usage = GeminiClient.parseStreamChunk(
            """{"usageMetadata":{"totalTokenCount":12}}"""
        )!!
        assertEquals("", usage.text)
        assertNull(usage.finishReason)
    }

    @Test
    fun parseText_joinsAllTextParts() {
        val parts = JSONArray()
            .put(JSONObject().put("text", "{\"a"))
            .put(JSONObject().put("thoughtSignature", "x"))
            .put(JSONObject().put("text", "\":1}"))
        val body = JSONObject()
            .put("candidates", JSONArray().put(JSONObject().put("content", JSONObject().put("parts", parts))))
            .toString()
        assertEquals("{\"a\":1}", GeminiClient.parseText(body))
    }

    @Test
    fun parseText_blankPartsThrowInvalidResponse() {
        val parts = JSONArray().put(JSONObject().put("thoughtSignature", "x"))
        val body = JSONObject()
            .put("candidates", JSONArray().put(JSONObject().put("content", JSONObject().put("parts", parts))))
            .toString()
        try {
            GeminiClient.parseText(body)
            fail("expected AiError.InvalidResponse")
        } catch (e: AiError) {
            assertEquals(AiError.InvalidResponse, e)
        }
    }

    @Test
    fun analyzeStreaming_cutStreamWithoutFinishReason_retriesOneShot() = runBlocking {
        val server = MockWebServer()
        // Stream leg: two text chunks, then the server closes without a
        // finishReason chunk (the observed #68 failure shape).
        val sse = Buffer().writeUtf8("data: ${textChunk("{\"a\":")}\n\ndata: ${textChunk("1}")}\n\n")
        server.enqueue(MockResponse().setBody(sse))
        // One-shot fallback: complete answer with finishReason.
        server.enqueue(MockResponse().setBody(oneShotBody("{\"a\":1}")))
        server.start()
        try {
            val deltas = mutableListOf<String>()
            val result = GeminiClient.analyzeStreaming(
                OkHttpClient(),
                server.url("/v1beta").toString(),
                "gemini-3.8-flash",
                "key",
                "prompt",
                emptyList(),
            ) { deltas.add(it) }
            assertEquals("{\"a\":1}", result)
            // The cut stream's pieces still surfaced before the retry.
            assertEquals(listOf("{\"a\":", "1}"), deltas)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
        Unit
    }

    @Test
    fun parseText_maxTokensFinishThrowsResponseTruncated() {
        val body = JSONObject()
            .put(
                "candidates",
                JSONArray().put(
                    JSONObject()
                        .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "{\"a\":"))))
                        .put("finishReason", "MAX_TOKENS")
                ),
            )
            .toString()
        try {
            GeminiClient.parseText(body)
            fail("expected AiError.ResponseTruncated")
        } catch (e: AiError) {
            assertEquals(AiError.ResponseTruncated, e)
        }
    }

    @Test
    fun analyzeStreaming_maxTokensFinish_retriesOneShot() = runBlocking {
        val server = MockWebServer()
        // Stream completes but the model's own budget cut the answer short.
        val sse = Buffer().writeUtf8("data: ${textChunk("{\"a\":", "MAX_TOKENS")}\n\n")
        server.enqueue(MockResponse().setBody(sse))
        server.enqueue(MockResponse().setBody(oneShotBody("{\"a\":1}")))
        server.start()
        try {
            val result = GeminiClient.analyzeStreaming(
                OkHttpClient(),
                server.url("/v1beta").toString(),
                "gemini-3.8-flash",
                "key",
                "prompt",
                emptyList(),
            ) {}
            assertEquals("{\"a\":1}", result)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
        Unit
    }

    @Test
    fun analyzeStreaming_completeStream_doesNotRetryOneShot() = runBlocking {
        val server = MockWebServer()
        val sse = Buffer().writeUtf8("data: ${textChunk("{\"a\":1}", "STOP")}\n\n")
        server.enqueue(MockResponse().setBody(sse))
        server.start()
        try {
            val result = GeminiClient.analyzeStreaming(
                OkHttpClient(),
                server.url("/v1beta").toString(),
                "gemini-3.8-flash",
                "key",
                "prompt",
                emptyList(),
            ) {}
            assertEquals("{\"a\":1}", result)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
        Unit
    }
}
