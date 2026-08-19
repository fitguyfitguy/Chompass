package app.chompass.services.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.chompass.models.AIProvider
import app.chompass.data.OpenRouterReasoningEffort
import app.chompass.R
import app.chompass.services.PerfLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.Locale

/**
 * OpenAI-compatible format — used by OpenAI, xAI Grok, OpenRouter, Together AI,
 * Groq, Hugging Face, Fireworks AI, DeepInfra, Mistral, Ollama, and the
 * Custom (OpenAI-compatible) provider.
 *
 *   POST <base>/chat/completions
 *   Header: Authorization: Bearer <apiKey>
 *   Body:   {model, messages: [{role, content: [{type, ...}]}], max_tokens/max_completion_tokens}
 */
object OpenAICompatibleClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * OpenRouter `reasoning` request body (upstream #194). Returns null when the
     * caller is not on OpenRouter. AUTO preserves the historical behavior:
     * reasoning is excluded from the response always, with a low effort budget on
     * compact retries only. Explicit efforts apply to every request.
     */
    internal fun reasoningBody(
        effort: OpenRouterReasoningEffort?,
        compactRetry: Boolean,
    ): JSONObject? {
        if (effort == null) return null
        val body = JSONObject().put("exclude", true)
        val effortValue = if (effort == OpenRouterReasoningEffort.AUTO) {
            if (compactRetry) "low" else null
        } else {
            effort.requestValue
        }
        if (effortValue != null) body.put("effort", effortValue)
        return body
    }

    suspend fun analyze(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String?,
        prompt: String,
        imageBytesList: List<ByteArray>,
        provider: AIProvider,
        maxTokens: Int,
        reasoningEffort: OpenRouterReasoningEffort = OpenRouterReasoningEffort.AUTO,
    ): String {
        val url = "$baseUrl/chat/completions"

        suspend fun request(requestPrompt: String, compactRetry: Boolean): OpenAITextResponse {
            val content = JSONArray().apply {
                imageBytesList.forEach {
                    put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(it)}")
                            )
                    )
                }
                put(JSONObject().put("type", "text").put("text", requestPrompt))
            }

            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put(tokenLimitParameter(provider, model), maxTokens)
            if (provider == AIProvider.OPENROUTER) {
                reasoningBody(reasoningEffort, compactRetry)?.let { body.put("reasoning", it) }
            }

            val builder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMedia))
            if (!apiKey.isNullOrEmpty()) builder.addHeader("Authorization", "Bearer $apiKey")
            if (provider == AIProvider.OPENROUTER) {
                builder.addHeader("HTTP-Referer", "https://codeberg.org/fitguy/chompass")
                builder.addHeader("X-Title", "Fud AI")
            }

            val bodyStr = RetryPolicy.execute { client.newCall(builder.build()) }
            return OpenAIResponseParser.parse(bodyStr)
        }

        var response = request(prompt, compactRetry = false)
        if (response.needsCompactRetry) {
            response = request(compactRetryPrompt(prompt, maxTokens), compactRetry = true)
            if (response.wasTruncated) {
                throw AiError.Api("The AI response was truncated twice. Try a shorter description or another model.", messageRes = R.string.ai_error_truncated_twice_description)
            }
        }
        return response.text ?: throw AiError.InvalidResponse
    }

    /**
     * Streaming chat/completions. Invokes [onDelta] with each text fragment;
     * returns the full concatenated assistant text when the stream ends.
     * Falls back to non-streaming [analyze] when the endpoint rejects `stream`.
     */
    suspend fun analyzeStreaming(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String?,
        prompt: String,
        imageBytesList: List<ByteArray>,
        provider: AIProvider,
        maxTokens: Int,
        onDelta: (String) -> Unit,
        reasoningEffort: OpenRouterReasoningEffort = OpenRouterReasoningEffort.AUTO,
    ): String {
        val url = "$baseUrl/chat/completions"

        suspend fun streamOnce(requestPrompt: String, compactRetry: Boolean): Pair<String, Boolean> {
            val content = JSONArray().apply {
                imageBytesList.forEach {
                    put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(it)}")
                            )
                    )
                }
                put(JSONObject().put("type", "text").put("text", requestPrompt))
            }

            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put(tokenLimitParameter(provider, model), maxTokens)
                .put("stream", true)
            if (provider == AIProvider.OPENROUTER) {
                reasoningBody(reasoningEffort, compactRetry)?.let { body.put("reasoning", it) }
            }

            val builder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(jsonMedia))
            if (!apiKey.isNullOrEmpty()) builder.addHeader("Authorization", "Bearer $apiKey")
            if (provider == AIProvider.OPENROUTER) {
                builder.addHeader("HTTP-Referer", "https://codeberg.org/fitguy/chompass")
                builder.addHeader("X-Title", "Fud AI")
            }

            val assembled = StringBuilder()
            var finishReason: String? = null
            val response = RetryPolicy.open { client.newCall(builder.build()) }
            AiSse.read(response) { payload ->
                val chunk = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return@read
                val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@read
                finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull ?: finishReason
                val delta = choice["delta"]?.jsonObject ?: return@read
                val piece = when (val contentNode = delta["content"]) {
                    is JsonPrimitive -> contentNode.contentOrNull
                    is JsonArray -> contentNode.mapNotNull {
                        runCatching { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                    }.joinToString("")
                    else -> null
                }
                if (!piece.isNullOrEmpty()) {
                    assembled.append(piece)
                    onDelta(piece)
                }
            }
            return assembled.toString() to (finishReason == "length")
        }

        return try {
            var (text, truncated) = streamOnce(prompt, compactRetry = false)
            if (text.isBlank() || truncated) {
                // Compact retry uses the non-streaming path so partial UI state
                // is not polluted by a truncated first attempt.
                return analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, provider, maxTokens)
            }
            text
        } catch (e: AiError) {
            throw e
        } catch (_: Throwable) {
            // Endpoint may not support streaming — fall back to the classic path.
            analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, provider, maxTokens)
        }
    }

    private fun compactRetryPrompt(prompt: String, maxTokens: Int): String =
        "$prompt\n\nIMPORTANT: The previous response did not contain a complete answer. Return only the requested compact JSON object, with no reasoning, explanation, or markdown. Keep the complete response under $maxTokens tokens."

    suspend fun chat(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String?,
        systemPrompt: String,
        history: List<Pair<String, String>>, // (role: "user"|"assistant", content)
        userMessage: String,
        provider: AIProvider,
        maxTokens: Int
    ): String {
        val url = "$baseUrl/chat/completions"

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put(tokenLimitParameter(provider, model), maxTokens)

        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
        if (!apiKey.isNullOrEmpty()) builder.addHeader("Authorization", "Bearer $apiKey")
        if (provider == AIProvider.OPENROUTER) {
            builder.addHeader("HTTP-Referer", "https://codeberg.org/fitguy/chompass")
            builder.addHeader("X-Title", "Fud AI")
        }

        val response = OpenAIResponseParser.parse(RetryPolicy.execute { client.newCall(builder.build()) })
        if (response.wasTruncated) {
            throw AiError.Api("The AI response was truncated. Try a shorter question or a different model.", messageRes = R.string.ai_error_truncated)
        }
        return response.text ?: throw AiError.InvalidResponse
    }

    fun tokenLimitParameter(provider: AIProvider, model: String): String {
        return if (
            provider == AIProvider.OPENAI ||
            (provider == AIProvider.CUSTOM_OPENAI && usesOpenAICompletionTokenLimit(model))
        ) {
            "max_completion_tokens"
        } else {
            "max_tokens"
        }
    }

    private fun usesOpenAICompletionTokenLimit(model: String): Boolean {
        val normalized = model
            .trim()
            .lowercase(Locale.US)
            .substringAfterLast("/")

        return normalized.startsWith("gpt-5") ||
            normalized.startsWith("o1") ||
            normalized.startsWith("o3") ||
            normalized.startsWith("o4")
    }
}

internal data class OpenAITextResponse(
    val text: String?,
    val finishReason: String?,
    val hasReasoning: Boolean,
    val messageJson: JSONObject? = null,
) {
    val wasTruncated: Boolean get() = finishReason == "length"
    val needsCompactRetry: Boolean get() = wasTruncated || (text == null && hasReasoning)
    val toolCalls: JSONArray? get() = messageJson?.optJSONArray("tool_calls")?.takeIf { it.length() > 0 }
}

internal object OpenAIResponseParser {
    fun parse(body: String): OpenAITextResponse = parseBody(body)

    /**
     * Accepts a normal chat.completion JSON object, a stream-shaped object
     * (`delta` / `choices[0].text`), or an already-buffered SSE body.
     * Local proxies (OmniRoute, some Ollama builds) assemble an upstream
     * stream and still return SSE or a delta object when the client did not
     * send `stream: true`.
     */
    fun parseBody(raw: String, contentType: String? = null): OpenAITextResponse {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) invalid(raw, contentType)
        return if (AiSse.looksLikeSse(trimmed)) {
            parseSse(trimmed, contentType)
        } else {
            parseJsonObject(trimmed, contentType)
        }
    }

    private fun parseSse(raw: String, contentType: String?): OpenAITextResponse {
        val payloads = AiSse.payloads(raw)
        if (payloads.isEmpty()) invalid(raw, contentType)
        if (payloads.size == 1) {
            return parseJsonObject(payloads[0], contentType)
        }
        val assembled = StringBuilder()
        var finishReason: String? = null
        var lastMessage: JsonObject? = null
        var lastDelta: JsonObject? = null
        var lastError: String? = null
        for (payload in payloads) {
            val json = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()
                ?: continue
            runCatching {
                json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { lastError = it }
            val choice = runCatching { json["choices"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
                ?: continue
            runCatching { choice["finish_reason"]?.jsonPrimitive?.contentOrNull }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { finishReason = it }
            val message = runCatching { choice["message"]?.jsonObject }.getOrNull()
            val delta = runCatching { choice["delta"]?.jsonObject }.getOrNull()
            if (message != null) lastMessage = message
            if (delta != null) lastDelta = delta
            val piece = contentPiece(delta?.get("content"))
                ?: contentPiece(message?.get("content"))
                ?: contentPiece(choice["text"])
            if (!piece.isNullOrEmpty()) assembled.append(piece)
        }
        if (finishReason == "error" || (assembled.isEmpty() && lastMessage == null && lastError != null)) {
            throw AiError.Api(
                lastError ?: "The AI provider returned an error.",
                messageRes = if (lastError == null) R.string.ai_error_provider_error else 0,
            )
        }
        val text = assembled.toString().trim().takeIf { it.isNotEmpty() }
            ?: lastMessage?.let { contentText(it["content"]) }
            ?: lastDelta?.let { contentText(it["content"]) }
        val messageJson = lastMessage?.let { runCatching { JSONObject(it.toString()) }.getOrNull() }
        val hasReasoning = hasReasoning(lastMessage) || hasReasoning(lastDelta)
        if (text == null && messageJson == null && !hasReasoning) invalid(raw, contentType)
        return OpenAITextResponse(text, finishReason, hasReasoning, messageJson)
    }

    private fun parseJsonObject(body: String, contentType: String?): OpenAITextResponse {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: invalid(body, contentType)
        val errorMessage = runCatching {
            json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val choice = runCatching { json["choices"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
        val finishReason = runCatching { choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull }
            .getOrNull()?.takeIf { it.isNotBlank() }
        if (finishReason == "error" || (choice == null && errorMessage != null)) {
            throw AiError.Api(
                errorMessage ?: "The AI provider returned an error.",
                messageRes = if (errorMessage == null) R.string.ai_error_provider_error else 0,
            )
        }
        if (choice == null) invalid(body, contentType)
        val message = runCatching { choice["message"]?.jsonObject }.getOrNull()
        val delta = runCatching { choice["delta"]?.jsonObject }.getOrNull()
        val text = contentText(message?.get("content"))
            ?: contentText(delta?.get("content"))
            ?: contentText(choice["text"])
        if (message == null && delta == null && text == null && errorMessage == null) {
            invalid(body, contentType)
        }
        val messageJson = message?.let { runCatching { JSONObject(it.toString()) }.getOrNull() }
        val hasReasoning = hasReasoning(message) || hasReasoning(delta)
        return OpenAITextResponse(text, finishReason, hasReasoning, messageJson)
    }

    private fun contentText(node: JsonElement?): String? = when (node) {
        is JsonPrimitive -> node.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> node.mapNotNull { element ->
            runCatching { element.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }.joinToString("\n").takeIf { it.isNotEmpty() }
        else -> null
    }

    /** Untrimmed fragment for SSE assembly so spaces between tokens survive. */
    private fun contentPiece(node: JsonElement?): String? = when (node) {
        is JsonPrimitive -> node.contentOrNull
        is JsonArray -> node.mapNotNull { element ->
            runCatching { element.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        }.joinToString("").takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun hasReasoning(obj: JsonObject?): Boolean {
        if (obj == null) return false
        fun nonEmptyString(key: String): Boolean = runCatching {
            obj[key]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
        }.getOrDefault(false)
        return nonEmptyString("reasoning") ||
            nonEmptyString("reasoning_content") ||
            runCatching { obj["reasoning_details"]?.jsonArray?.isNotEmpty() == true }.getOrDefault(false)
    }

    private fun invalid(raw: String, contentType: String?): Nothing {
        if (PerfLog.enabled) {
            val prefix = raw.take(200).replace('\n', ' ').replace('\r', ' ')
            PerfLog.event(
                "op=parse phase=invalid contentType=${contentType ?: "-"} chars=${raw.length} prefix=$prefix",
            )
        }
        throw AiError.InvalidResponse
    }
}
