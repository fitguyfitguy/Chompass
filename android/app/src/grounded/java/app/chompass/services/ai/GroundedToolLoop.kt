package app.chompass.services.ai

import app.chompass.models.AIProvider
import app.chompass.services.grounding.GroundingTools
import app.chompass.ui.home.EntryAnalysisPhase
import app.chompass.ui.home.FoodAnalysisProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Bounded tool loop for grounded entry (max [MAX_TOOL_ROUNDS] rounds).
 * Patterned on [app.chompass.services.ai.ChatService] but uses
 * [GroundingTools] and requires a successful [finalize_grounding] call.
 */
object GroundedToolLoop {
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    const val MAX_TOOL_ROUNDS = 4

    fun systemPrompt(): String = """
        You ground a meal for a calorie tracker using tools only.
        Rules:
        1. Call search_usda (and search_history when useful) before picking any USDA/history source_id.
        2. Prefer survey_fndds_food / FNDDS rows for cooked or generic meals; avoid flour, powder, dry, pie, or dessert false friends unless the user text says so.
        3. Split multi-item meals into separate components; each needs its own source_id or reject_to_estimate.
        4. Never invent calories, protein, carbs, or fat — only choose among tool results.
        5. Set grams to the edible amount when reasonably clear; otherwise omit grams and set quantity/unit when known.
        6. If no good match exists, set reject_to_estimate=true (model estimate later) or needs_user_choice=true.
        7. When done, you MUST call finalize_grounding with meal_name and components.
        8. Do not answer with plain text only — finish via finalize_grounding.
        9. Only use source_id values returned by tools in this conversation.
    """.trimIndent()

    data class LoopResult(
        val finalize: GroundingTools.FinalizePayload,
        val roundsUsed: Int,
        val tools: GroundingTools,
    )

    suspend fun run(
        client: OkHttpClient,
        provider: AIProvider,
        model: String,
        baseUrl: String,
        apiKey: String?,
        maxTokens: Int,
        tools: GroundingTools,
        userMessage: String,
        imageBytesList: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): LoopResult {
        if (provider.apiFormat == AIProvider.ApiFormat.ON_DEVICE) {
            throw AiError.Api("Grounded tool loop is not available for on-device models.")
        }
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Recognizing))
        val images = if (imageBytesList.isEmpty()) {
            imageBytesList
        } else {
            withContext(Dispatchers.Default) {
                imageBytesList.map { AiImageBytes.jpegForUpload(it) }
            }
        }
        val system = systemPrompt()
        val finalize = when (provider.apiFormat) {
            AIProvider.ApiFormat.OPENAI_COMPATIBLE -> runOpenAI(
                client, provider, baseUrl, model, apiKey, maxTokens, system, userMessage, images, tools, onProgress,
            )
            AIProvider.ApiFormat.ANTHROPIC -> runAnthropic(
                client, baseUrl, model, apiKey!!, maxTokens, system, userMessage, images, tools, onProgress,
            )
            AIProvider.ApiFormat.GEMINI -> runGemini(
                client, baseUrl, model, apiKey!!, system, userMessage, images, tools, onProgress,
            )
            AIProvider.ApiFormat.ON_DEVICE -> error("unreachable")
        }
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Resolving))
        return LoopResult(finalize = finalize, roundsUsed = MAX_TOOL_ROUNDS, tools = tools)
    }

    private suspend fun runOpenAI(
        client: OkHttpClient,
        provider: AIProvider,
        baseUrl: String,
        model: String,
        apiKey: String?,
        maxTokens: Int,
        systemPrompt: String,
        userMessage: String,
        images: List<ByteArray>,
        tools: GroundingTools,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ): GroundingTools.FinalizePayload {
        val url = "$baseUrl/chat/completions"
        val toolsArr = JSONArray()
        for (name in GroundingTools.TOOL_NAMES) {
            toolsArr.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", name)
                            put("description", GroundingTools.TOOL_DESCRIPTIONS[name] ?: "")
                            put("parameters", GroundingTools.parametersSchema(name))
                        },
                    )
                },
            )
        }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.put(
            JSONObject().put("role", "user").put("content", openAIUserContent(userMessage, images)),
        )

        repeat(MAX_TOOL_ROUNDS) { round ->
            suspend fun request(compactRetry: Boolean): JSONObject {
                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("tools", toolsArr)
                    // Last round: force finalize so the model cannot exit without a pick.
                    if (round == MAX_TOOL_ROUNDS - 1 && tools.lastFinalize == null) {
                        put(
                            "tool_choice",
                            JSONObject()
                                .put("type", "function")
                                .put("function", JSONObject().put("name", "finalize_grounding")),
                        )
                    } else {
                        put("tool_choice", "auto")
                    }
                    put(OpenAICompatibleClient.tokenLimitParameter(provider, model), maxTokens)
                    if (provider == AIProvider.OPENROUTER && compactRetry) {
                        put("reasoning", JSONObject().put("effort", "low").put("exclude", true))
                    }
                }
                val builder = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                if (!apiKey.isNullOrEmpty()) builder.addHeader("Authorization", "Bearer $apiKey")
                if (provider == AIProvider.OPENROUTER) {
                    builder.addHeader("HTTP-Referer", "https://codeberg.org/fitguy/chompass")
                    builder.addHeader("X-Title", "Fud AI")
                }
                val raw = RetryPolicy.execute { client.newCall(builder.build()) }
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: throw AiError.InvalidResponse
                val parsed = OpenAIResponseParser.parse(raw)
                var message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?: throw AiError.InvalidResponse
                if (parsed.needsCompactRetry && message.optJSONArray("tool_calls") == null && !compactRetry) {
                    return request(compactRetry = true)
                }
                return message
            }

            var message = request(compactRetry = false)

            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                messages.put(message)
                for (i in 0 until toolCalls.length()) {
                    val call = toolCalls.optJSONObject(i) ?: continue
                    val function = call.optJSONObject("function") ?: continue
                    val name = function.optString("name").takeIf { it.isNotEmpty() } ?: continue
                    val id = call.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    mapToolPhase(name, onProgress)
                    val args = runCatching {
                        JSONObject(function.optString("arguments", "{}"))
                    }.getOrNull() ?: JSONObject()
                    val result = GroundingTools.truncateToolResult(tools.execute(name, args))
                    messages.put(
                        JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", id)
                            put("content", result)
                        },
                    )
                    tools.lastFinalize?.let { return it }
                }
                return@repeat
            }
            // Plain text without finalize — nudge once if rounds remain.
            if (round < MAX_TOOL_ROUNDS - 1 && tools.lastFinalize == null) {
                messages.put(message)
                messages.put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "Call finalize_grounding now with your best source picks."),
                )
                return@repeat
            }
        }
        return tools.lastFinalize
            ?: throw AiError.Api("Grounded entry did not finalize within $MAX_TOOL_ROUNDS tool rounds.")
    }

    private suspend fun runAnthropic(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        maxTokens: Int,
        systemPrompt: String,
        userMessage: String,
        images: List<ByteArray>,
        tools: GroundingTools,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ): GroundingTools.FinalizePayload {
        val url = "$baseUrl/messages"
        val toolsArr = JSONArray()
        for (name in GroundingTools.TOOL_NAMES) {
            toolsArr.put(
                JSONObject().apply {
                    put("name", name)
                    put("description", GroundingTools.TOOL_DESCRIPTIONS[name] ?: "")
                    put("input_schema", GroundingTools.parametersSchema(name))
                },
            )
        }
        val messages = JSONArray()
        messages.put(
            JSONObject().put("role", "user").put("content", anthropicUserContent(userMessage, images)),
        )

        repeat(MAX_TOOL_ROUNDS) { round ->
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", maxTokens)
                put("system", systemPrompt)
                put("tools", toolsArr)
                put("messages", messages)
            }
            val raw = RetryPolicy.execute {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .post(body.toString().toRequestBody(JSON_MEDIA))
                        .build(),
                )
            }
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: throw AiError.InvalidResponse
            val contentArr = json.optJSONArray("content") ?: throw AiError.InvalidResponse
            val toolUses = mutableListOf<JSONObject>()
            for (i in 0 until contentArr.length()) {
                val block = contentArr.optJSONObject(i) ?: continue
                if (block.optString("type") == "tool_use") toolUses.add(block)
            }
            if (toolUses.isNotEmpty()) {
                messages.put(JSONObject().put("role", "assistant").put("content", contentArr))
                val toolResults = JSONArray()
                for (use in toolUses) {
                    val id = use.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    val name = use.optString("name").takeIf { it.isNotEmpty() } ?: continue
                    mapToolPhase(name, onProgress)
                    val input = use.optJSONObject("input") ?: JSONObject()
                    val result = GroundingTools.truncateToolResult(tools.execute(name, input))
                    toolResults.put(
                        JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", id)
                            put("content", result)
                        },
                    )
                    tools.lastFinalize?.let { return it }
                }
                messages.put(JSONObject().put("role", "user").put("content", toolResults))
                return@repeat
            }
            if (round < MAX_TOOL_ROUNDS - 1 && tools.lastFinalize == null) {
                messages.put(JSONObject().put("role", "assistant").put("content", contentArr))
                messages.put(
                    JSONObject().put("role", "user").put(
                        "content",
                        "Call finalize_grounding now with your best source picks.",
                    ),
                )
                return@repeat
            }
        }
        return tools.lastFinalize
            ?: throw AiError.Api("Grounded entry did not finalize within $MAX_TOOL_ROUNDS tool rounds.")
    }

    private suspend fun runGemini(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        images: List<ByteArray>,
        tools: GroundingTools,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ): GroundingTools.FinalizePayload {
        val url = "$baseUrl/models/$model:generateContent"
        val declarations = JSONArray()
        for (name in GroundingTools.TOOL_NAMES) {
            declarations.put(
                JSONObject().apply {
                    put("name", name)
                    put("description", GroundingTools.TOOL_DESCRIPTIONS[name] ?: "")
                    put("parameters", GroundingTools.parametersSchema(name))
                },
            )
        }
        val contents = JSONArray()
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", geminiUserParts(userMessage, images))
            },
        )

        repeat(MAX_TOOL_ROUNDS) { round ->
            val body = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                )
                put("contents", contents)
                GeminiClient.buildToolsArray(enableGoogleSearch = false, declarations)?.let { put("tools", it) }
            }
            val raw = RetryPolicy.execute {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-goog-api-key", apiKey)
                        .post(body.toString().toRequestBody(JSON_MEDIA))
                        .build(),
                )
            }
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: throw AiError.InvalidResponse
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: throw AiError.InvalidResponse
            val content = candidate.optJSONObject("content") ?: throw AiError.InvalidResponse
            val parts = content.optJSONArray("parts") ?: throw AiError.InvalidResponse

            val functionCalls = mutableListOf<JSONObject>()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                part.optJSONObject("functionCall")?.let { functionCalls.add(it) }
            }
            if (functionCalls.isNotEmpty()) {
                contents.put(JSONObject().apply { put("role", "model"); put("parts", parts) })
                val responseParts = JSONArray()
                for (call in functionCalls) {
                    val name = call.optString("name").takeIf { it.isNotEmpty() } ?: continue
                    mapToolPhase(name, onProgress)
                    val args = call.optJSONObject("args") ?: JSONObject()
                    val resultStr = GroundingTools.truncateToolResult(tools.execute(name, args))
                    val resultObj = runCatching { JSONObject(resultStr) }.getOrNull() ?: JSONObject()
                    responseParts.put(
                        JSONObject().apply {
                            put(
                                "functionResponse",
                                JSONObject().apply {
                                    put("name", name)
                                    put("response", JSONObject().put("content", resultObj))
                                },
                            )
                        },
                    )
                    tools.lastFinalize?.let { return it }
                }
                contents.put(JSONObject().apply { put("role", "user"); put("parts", responseParts) })
                return@repeat
            }
            if (round < MAX_TOOL_ROUNDS - 1 && tools.lastFinalize == null) {
                contents.put(JSONObject().apply { put("role", "model"); put("parts", parts) })
                contents.put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put(
                                    "text",
                                    "Call finalize_grounding now with your best source picks.",
                                ),
                            ),
                        )
                    },
                )
                return@repeat
            }
        }
        return tools.lastFinalize
            ?: throw AiError.Api("Grounded entry did not finalize within $MAX_TOOL_ROUNDS tool rounds.")
    }

    private fun mapToolPhase(name: String, onProgress: (FoodAnalysisProgress) -> Unit) {
        when (name) {
            "search_history" -> onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.SearchingHistory))
            "search_usda", "lookup_barcode" ->
                onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.SearchingUsda))
            "finalize_grounding" -> onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Resolving))
            else -> onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.CallingAi))
        }
    }

    private fun openAIUserContent(text: String, images: List<ByteArray>): Any {
        if (images.isEmpty()) return text
        return JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", text))
            for (bytes in images) {
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put(
                                "url",
                                "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(bytes)}",
                            ),
                        ),
                )
            }
        }
    }

    private fun anthropicUserContent(text: String, images: List<ByteArray>): Any {
        if (images.isEmpty()) return text
        return JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", text))
            for (bytes in images) {
                put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", Base64.getEncoder().encodeToString(bytes)),
                        ),
                )
            }
        }
    }

    private fun geminiUserParts(text: String, images: List<ByteArray>): JSONArray =
        JSONArray().apply {
            for (bytes in images) {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", Base64.getEncoder().encodeToString(bytes)),
                    ),
                )
            }
            put(JSONObject().put("text", text))
        }
}
