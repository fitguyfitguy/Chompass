package app.chompass.services.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
     * compact retries only. Explicit efforts apply to every request. DISABLED
     * stays exclude-only: no effort budget, not even on compact retries.
     */
    internal fun reasoningBody(
        effort: OpenRouterReasoningEffort?,
        compactRetry: Boolean,
    ): JSONObject? {
        if (effort == null) return null
        val body = JSONObject().put("exclude", true)
        val effortValue = when (effort) {
            OpenRouterReasoningEffort.AUTO -> if (compactRetry) "low" else null
            OpenRouterReasoningEffort.DISABLED -> null
            else -> effort.requestValue
        }
        if (effortValue != null) body.put("effort", effortValue)
        return body
    }

    /** Providers that offer the user-facing reasoning-effort lever: OpenRouter
     *  (#194 reasoning object) plus Custom OpenAI-compatible and Ollama hosts
     *  (#97 flat `reasoning_effort` field). */
    internal fun usesReasoningEffort(provider: AIProvider): Boolean =
        provider == AIProvider.OPENROUTER || usesFlatReasoningEffort(provider)

    /** Providers whose chat endpoint takes the flat `reasoning_effort` field (#97). */
    internal fun usesFlatReasoningEffort(provider: AIProvider): Boolean =
        provider == AIProvider.CUSTOM_OPENAI || provider == AIProvider.OLLAMA

    /**
     * Flat `reasoning_effort` value for Custom OpenAI-compatible and Ollama hosts.
     * Null = omit the field (AUTO, or a provider that does not take it); omitting
     * keeps requests byte-identical for existing users.
     */
    internal fun flatReasoningEffort(provider: AIProvider, effort: OpenRouterReasoningEffort?): String? {
        if (!usesFlatReasoningEffort(provider) || effort == null) return null
        return if (effort == OpenRouterReasoningEffort.AUTO) null else effort.requestValue
    }

    /** Puts the provider's reasoning fields onto a chat/completions body (null effort = none). */
    private fun putReasoningFields(
        body: JSONObject,
        provider: AIProvider,
        effort: OpenRouterReasoningEffort?,
        compactRetry: Boolean,
    ) {
        when {
            provider == AIProvider.OPENROUTER ->
                reasoningBody(effort, compactRetry)?.let { body.put("reasoning", it) }
            usesFlatReasoningEffort(provider) ->
                flatReasoningEffort(provider, effort)?.let { body.put("reasoning_effort", it) }
        }
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
        /** Flat PerfLog suffix for retry-attempt legs, e.g. "downshift=macros" (#97). */
        perfTag: String? = null,
    ): String {
        val url = "$baseUrl/chat/completions"

        suspend fun request(requestPrompt: String, compactRetry: Boolean, sendReasoning: Boolean): OpenAITextResponse {
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
            putReasoningFields(body, provider, reasoningEffort.takeIf { sendReasoning }, compactRetry)

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

        suspend fun ladder(sendReasoning: Boolean): String {
            var response = request(prompt, compactRetry = false, sendReasoning)
            if (response.needsCompactRetry) {
                response = request(compactRetryPrompt(prompt, maxTokens), compactRetry = true, sendReasoning)
                if (response.wasTruncated) {
                    if (PerfLog.enabled) {
                        PerfLog.event(
                            "op=analyzeText finish=${response.finishReason} chars=${response.text?.length ?: -1} " +
                                "maxTokens=$maxTokens compact=true" + perfSuffix(perfTag)
                        )
                    }
                    throw AiError.Api("The AI response was truncated twice. Try a shorter description or another model.", messageRes = R.string.ai_error_truncated_twice_description)
                }
            }
            return stripThinking(response.text ?: throw AiError.InvalidResponse)
        }

        // #97: a selfhosted server may 400 on the reasoning_effort field it does
        // not know. When the rejection text points at reasoning, drop every
        // reasoning field and run the ladder once more.
        if (flatReasoningEffort(provider, reasoningEffort) == null) return ladder(sendReasoning = true)
        return try {
            ladder(sendReasoning = true)
        } catch (e: AiError.Api) {
            if (e.httpStatus == 400 && e.message.orEmpty().contains("reasoning", ignoreCase = true)) {
                if (PerfLog.enabled) {
                    PerfLog.event("op=analyzeText reasoningEffortDropped=1" + perfSuffix(perfTag))
                }
                ladder(sendReasoning = false)
            } else {
                throw e
            }
        }
    }

    /**
     * Streaming chat/completions. Invokes [onDelta] with each text fragment;
     * returns the assembled text plus how the stream ended. Falls back to
     * non-streaming [analyze] when the endpoint rejects `stream` or the reply
     * truncated. A finish-less stream (non-blank text, no finish_reason) is
     * returned flagged as [OpenAIStreamResult.streamIncomplete] — the caller
     * retries non-streaming only when the text fails to parse (#97), so
     * servers that omit finish_reason on success pay no double request.
     */
    internal suspend fun analyzeStreaming(
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
        /** Flat PerfLog suffix for retry-attempt legs, e.g. "downshift=macros" (#97). */
        perfTag: String? = null,
    ): OpenAIStreamResult {
        val url = "$baseUrl/chat/completions"

        suspend fun streamOnce(requestPrompt: String, compactRetry: Boolean): Triple<String, Boolean, String?> {
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
            putReasoningFields(body, provider, reasoningEffort, compactRetry)

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
                // JsonNull-safe: real streams carry "finish_reason":null on
                // content chunks; only a non-null primitive says the stream ended.
                val finishNode = choice["finish_reason"]
                if (finishNode is JsonPrimitive && finishNode !is JsonNull) {
                    finishReason = finishNode.content
                }
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
            if (PerfLog.enabled) {
                PerfLog.event(
                    "op=analyzeText stream finish=$finishReason chars=${assembled.length} " +
                        "maxTokens=$maxTokens compact=$compactRetry" + perfSuffix(perfTag)
                )
            }
            return Triple(assembled.toString(), finishReason == "length", finishReason)
        }

        return try {
            val (streamedText, truncated, finishReason) = streamOnce(prompt, compactRetry = false)
            // Blocks span chunks, so thinking is stripped from the assembled
            // text only; raw deltas still reach the partial-JSON preview.
            val text = stripThinking(streamedText)
            if (text.isBlank() || truncated) {
                // Compact retry uses the non-streaming path so partial UI state
                // is not polluted by a truncated first attempt.
                return oneShotResult(
                    analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, provider, maxTokens, reasoningEffort, perfTag = perfTag)
                )
            }
            // Finish-less (no finish_reason, non-blank text): some local
            // servers cut the stream mid-reply (#97), others omit
            // finish_reason on success — flag it and let the caller decide.
            OpenAIStreamResult(text, finishReason, streamIncomplete = finishReason == null)
        } catch (e: AiError.Api) {
            if (e.httpStatus == 400 && flatReasoningEffort(provider, reasoningEffort) != null &&
                e.message.orEmpty().contains("reasoning", ignoreCase = true)
            ) {
                // Same #97 400 net as [analyze]: the stream request was rejected
                // over reasoning_effort, so answer non-streaming without any
                // reasoning field instead of re-running the ladder with it.
                if (PerfLog.enabled) {
                    PerfLog.event("op=analyzeText reasoningEffortDropped=1 stream=1" + perfSuffix(perfTag))
                }
                oneShotResult(
                    analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, provider, maxTokens, OpenRouterReasoningEffort.AUTO, perfTag = perfTag)
                )
            } else {
                throw e
            }
        } catch (e: AiError) {
            throw e
        } catch (_: Throwable) {
            // Endpoint may not support streaming — fall back to the classic path.
            oneShotResult(
                analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, provider, maxTokens, reasoningEffort, perfTag = perfTag)
            )
        }
    }

    /** A reply that already went through the non-streaming ladder is final. */
    private fun oneShotResult(text: String) =
        OpenAIStreamResult(text, finishReason = null, streamIncomplete = false)

    private fun perfSuffix(tag: String?): String = tag?.let { " $it" } ?: ""

    private fun compactRetryPrompt(prompt: String, maxTokens: Int): String =
        "$prompt\n\nIMPORTANT: The previous response did not contain a complete answer. Return only the requested compact JSON object, with no reasoning, explanation, or markdown. Keep the complete response under $maxTokens tokens."

    /**
     * Strips inline reasoning some local models embed in `content`
     * (`<think>…</think>`, `<thinking>…</thinking>`; Qwen-class via LM
     * Studio / Ollama / Unsloth — #97). Those servers inline it unless the
     * client asks for structured reasoning, and the food-JSON parser cannot
     * see past it. Blocks can span streamed chunks, so this runs on fully
     * assembled text, never per delta. A trailing unterminated open tag is
     * cut to the end: its content is reasoning, never the answer.
     */
    internal fun stripThinking(text: String): String {
        val closed = THINK_BLOCK_REGEX.replace(text, "")
        val open = THINK_OPEN_REGEX.find(closed) ?: return closed.trim()
        return closed.substring(0, open.range.first).trim()
    }

    private val THINK_BLOCK_REGEX = Regex("(?si)<think(?:ing)?>.*?</think(?:ing)?>")
    private val THINK_OPEN_REGEX = Regex("(?i)<think(?:ing)?>")

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
        return stripThinking(response.text ?: throw AiError.InvalidResponse)
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

/**
 * Streamed reply plus how the stream ended (#97). A finish-less stream
 * (non-blank text, no finish_reason) is suspect — some local servers cut
 * mid-reply while others omit finish_reason on success — so the client
 * returns the text flagged instead of retrying blindly. Schema-agnostic:
 * no knowledge of the reply's JSON shape lives here.
 */
internal data class OpenAIStreamResult(
    val text: String,
    val finishReason: String?,
    val streamIncomplete: Boolean,
)

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
