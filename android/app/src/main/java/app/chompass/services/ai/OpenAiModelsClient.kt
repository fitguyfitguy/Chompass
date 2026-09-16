package app.chompass.services.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lists models on a custom OpenAI-compatible endpoint (GET /models, Codeberg
 * #103). Mirrors [OllamaClient]: 5-second caps, retry policy, cleartext gate.
 * The bearer key is sent only when the user saved one — keyless LAN servers
 * (LM Studio, llama.cpp) are the common case.
 */
object OpenAiModelsClient {
    /** Custom base URLs already include /v1 — no stripping, unlike Ollama's tagsUrl. */
    fun modelsUrl(baseUrl: String): String =
        AiHttp.normalizeCustomBaseUrl(baseUrl).trimEnd('/') + "/models"

    fun parseModels(body: String): List<String> {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<String>(data.length())
        for (i in 0 until data.length()) {
            val id = data.optJSONObject(i)?.optString("id")?.trim() ?: continue
            if (id.isNotEmpty()) out += id
        }
        return out
    }

    suspend fun listModels(
        baseUrl: String,
        apiKey: String?,
        allowInsecureHttp: Boolean,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
    ): List<String> {
        val url = modelsUrl(baseUrl)
        AiHttp.assertCleartextAllowed(url, allowInsecureHttp)
        val shortClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        val body = RetryPolicy.execute {
            val builder = Request.Builder().url(url).get()
            if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
            shortClient.newCall(builder.build())
        }
        return parseModels(body)
    }
}
