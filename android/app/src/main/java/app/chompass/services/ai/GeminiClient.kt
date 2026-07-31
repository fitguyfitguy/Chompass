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

    suspend fun analyze(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        imageBytesList: List<ByteArray>,
        enableGoogleSearch: Boolean = false,
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
            AiSse.read(response) { payload ->
                val piece = parseStreamChunkText(payload) ?: return@read
                if (piece.isNotEmpty()) {
                    assembled.append(piece)
                    onDelta(piece)
                }
            }
            assembled.toString().ifBlank { throw AiError.InvalidResponse }
        } catch (e: AiError) {
            throw e
        } catch (_: Throwable) {
            analyze(client, baseUrl, model, apiKey, prompt, imageBytesList, enableGoogleSearch)
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

    private fun parseText(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: throw AiError.InvalidResponse
        val candidates = json.optJSONArray("candidates") ?: throw AiError.InvalidResponse
        val first = candidates.optJSONObject(0) ?: throw AiError.InvalidResponse
        val content = first.optJSONObject("content") ?: throw AiError.InvalidResponse
        val parts = content.optJSONArray("parts") ?: throw AiError.InvalidResponse
        val text = parts.optJSONObject(0)?.optString("text").orEmpty()
        if (text.isEmpty()) throw AiError.InvalidResponse
        return text
    }

    private fun parseStreamChunkText(payload: String): String? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val candidates = json.optJSONArray("candidates") ?: return null
        val first = candidates.optJSONObject(0) ?: return null
        val content = first.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            val text = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (text.isNotEmpty()) out.append(text)
        }
        return out.toString().takeIf { it.isNotEmpty() }
    }
}
