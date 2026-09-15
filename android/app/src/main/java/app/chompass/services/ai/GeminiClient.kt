package app.chompass.services.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Gemini format:
 *   POST <base>/models/<model>:generateContent
 *   Header: X-goog-api-key: <apiKey>
 *   Body:   {systemInstruction?, contents: [{role?, parts: [...]}], tools?}
 */
object GeminiClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    internal fun googleSearchTool(): JSONObject =
        JSONObject().put("google_search", JSONObject())

    internal fun buildToolsArray(
        enableGoogleSearch: Boolean,
        functionDeclarations: JSONArray? = null,
    ): JSONArray? {
        val tools = JSONArray()
        if (enableGoogleSearch) tools.put(googleSearchTool())
        if (functionDeclarations != null && functionDeclarations.length() > 0) {
            tools.put(JSONObject().put("functionDeclarations", functionDeclarations))
        }
        return if (tools.length() > 0) tools else null
    }

    /**
     * Gemini 3 rejects requests that combine google_search with
     * functionDeclarations unless the client opts into server-side tool
     * invocations (#90). Harmless on Gemini 2.5 and older.
     */
    internal fun serverSideToolConfig(): JSONObject =
        JSONObject().put("includeServerSideToolInvocations", true)

    /**
     * Per-request generationConfig for Gemini calls: thinking level per model
     * family, response-length cap, JSON response mode. Returns null when
     * nothing applies so the request body stays byte-identical to the
     * pre-config shape for those calls. GEMINI cannot take custom model ids
     * (AIProvider.models is a closed catalog), so the thinking table is
     * closed over it: flash-lite thinks minimally (its value is latency),
     * the 3.5-3.8 flash families think low, and everything else gets NO
     * thinkingConfig — 2.5 uses the older thinkingBudget API and pro defaults
     * to dynamic, so omitting is the safe no-op.
     */
    internal fun generationConfig(model: String, maxTokens: Int?, jsonResponse: Boolean): JSONObject? {
        val config = JSONObject()
        val thinkingLevel = when {
            model.contains("flash-lite") -> "minimal"
            Regex("gemini-3\\.[5-8]-flash").containsMatchIn(model) -> "low"
            else -> null
        }
        if (thinkingLevel != null) {
            config.put("thinkingConfig", JSONObject().put("thinkingLevel", thinkingLevel))
        }
        if (maxTokens != null && maxTokens > 0) config.put("maxOutputTokens", maxTokens)
        if (jsonResponse) config.put("responseMimeType", "application/json")
        return if (config.length() == 0) null else config
    }

    suspend fun analyze(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        imageBytesList: List<ByteArray>,
        enableGoogleSearch: Boolean = false,
        maxTokens: Int? = null,
        jsonResponse: Boolean = false,
    ): String {
        val url = "$baseUrl/models/$model:generateContent"

        val parts = JSONArray().apply {
            imageBytesList.forEach {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", Base64.getEncoder().encodeToString(it))
                    )
                )
            }
            put(JSONObject().put("text", prompt))
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            buildToolsArray(enableGoogleSearch)?.let { put("tools", it) }
            generationConfig(model, maxTokens, jsonResponse)?.let { put("generationConfig", it) }
        }

        val requestBody = body.toString().toRequestBody(jsonMedia)
        val bodyStr = RetryPolicy.execute {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-goog-api-key", apiKey)
                    .post(requestBody)
                    .build()
            )
        }

        return parseText(bodyStr)
    }

    /**
     * Streaming generateContent (`:streamGenerateContent?alt=sse`). Invokes
     * [onDelta] with each text fragment; returns the full concatenated text.
     * Falls back to [analyze] when streaming is unavailable.
     */
    suspend fun analyzeStreaming(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        imageBytesList: List<ByteArray>,
        enableGoogleSearch: Boolean = false,
        maxTokens: Int? = null,
        jsonResponse: Boolean = false,
        onDelta: (String) -> Unit,
    ): String {
        val url = "$baseUrl/models/$model:streamGenerateContent?alt=sse"

        val parts = JSONArray().apply {
            imageBytesList.forEach {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", Base64.getEncoder().encodeToString(it))
                    )
                )
            }
            put(JSONObject().put("text", prompt))
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            buildToolsArray(enableGoogleSearch)?.let { put("tools", it) }
            generationConfig(model, maxTokens, jsonResponse)?.let { put("generationConfig", it) }
        }

        return try {
            val assembled = StringBuilder()
            val response = RetryPolicy.open {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .addHeader("X-goog-api-key", apiKey)
                        .post(body.toString().toRequestBody(jsonMedia))
                        .build()
                )
            }
            var finishReason: String? = null
            AiSse.read(response) { payload ->
                val chunk = parseStreamChunk(payload) ?: return@read
                if (chunk.finishReason != null) finishReason = chunk.finishReason
                if (chunk.text.isNotEmpty()) {
                    assembled.append(chunk.text)
                    onDelta(chunk.text)
                }
            }
            // Gemini ends the SSE stream with a chunk carrying finishReason.
            // A stream without it was cut server-side mid-reply (observed on
            // gemini-3.8-flash under load, #68), and MAX_TOKENS means the
            // model's own budget cut the answer short. Both leave partial
            // text that would parse as truncated JSON; fail this leg so the
            // catch below retries the request as one shot.
            if (finishReason == null || finishReason == "MAX_TOKENS") throw StreamIncomplete()
            assembled.toString().ifBlank { throw AiError.InvalidResponse }
        } catch (e: AiError) {
            throw e
        } catch (_: Throwable) {
            analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, enableGoogleSearch, maxTokens, jsonResponse)
        }
    }

    /**
     * Multi-turn variant for the coach chat. Uses systemInstruction + contents[{role: user|model, parts: [{text}]}].
     */
    suspend fun chat(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        history: List<Pair<String, String>>, // (role, content) role in {"user","model"}
        userMessage: String,
        enableGoogleSearch: Boolean = false,
        maxTokens: Int? = null,
    ): String {
        val url = "$baseUrl/models/$model:generateContent"

        val contents = JSONArray()
        for ((role, content) in history) {
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", content)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
        )

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            put("contents", contents)
            buildToolsArray(enableGoogleSearch)?.let { put("tools", it) }
            generationConfig(model, maxTokens, jsonResponse = false)?.let { put("generationConfig", it) }
        }

        val requestBody = body.toString().toRequestBody(jsonMedia)
        val bodyStr = RetryPolicy.execute {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-goog-api-key", apiKey)
                    .post(requestBody)
                    .build()
            )
        }

        return parseText(bodyStr)
    }

    /**
     * Lightweight BYOK probe: GET /models with the key header.
     * Success means the key is accepted; does not run generateContent.
     */
    suspend fun validateApiKey(
        client: OkHttpClient,
        baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        apiKey: String,
    ) {
        val url = "${baseUrl.trimEnd('/')}/models"
        RetryPolicy.execute {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("X-goog-api-key", apiKey)
                    .get()
                    .build()
            )
        }
    }

    internal fun parseText(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: throw AiError.InvalidResponse
        val candidates = json.optJSONArray("candidates") ?: throw AiError.InvalidResponse
        val first = candidates.optJSONObject(0) ?: throw AiError.InvalidResponse
        // Surface an honest truncation error instead of handing a cut answer
        // to the food-JSON parser (#68).
        if (first.optString("finishReason") == "MAX_TOKENS") throw AiError.ResponseTruncated
        val content = first.optJSONObject("content") ?: throw AiError.InvalidResponse
        val parts = content.optJSONArray("parts") ?: throw AiError.InvalidResponse
        // Gemini 3 models can split the answer across several parts; join
        // every text part instead of reading parts[0] only (#68).
        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            text.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
        if (text.isBlank()) throw AiError.InvalidResponse
        return text.toString()
    }

    /** One parsed SSE chunk: concatenated text parts plus the candidate's finishReason, if set. */
    internal data class GeminiStreamChunk(val text: String, val finishReason: String?)

    /**
     * Parses one `streamGenerateContent?alt=sse` payload. Returns null only for
     * non-JSON payloads; chunks without candidate text (usage-only, thought
     * parts) yield empty text so the caller can still read finishReason.
     */
    internal fun parseStreamChunk(payload: String): GeminiStreamChunk? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val candidates = json.optJSONArray("candidates") ?: return GeminiStreamChunk("", null)
        val first = candidates.optJSONObject(0) ?: return GeminiStreamChunk("", null)
        val finishReason = first.optString("finishReason").takeIf { it.isNotEmpty() }
        val parts = first.optJSONObject("content")?.optJSONArray("parts")
        val out = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotEmpty()) out.append(text)
            }
        }
        return GeminiStreamChunk(out.toString(), finishReason)
    }

    /**
     * Signals an SSE stream that ended without finishReason (server-side cut).
     * Deliberately not an [AiError]: analyzeStreaming rethrows AiErrors but
     * falls back to one-shot [analyze] for anything else.
     */
    private class StreamIncomplete : Exception()
}
