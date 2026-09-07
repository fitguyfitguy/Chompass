package app.chompass.services.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OllamaModel(
    val id: String,
    val parameterSize: String? = null,
    val quantization: String? = null,
) {
    val subtitle: String?
        get() {
            val parts = listOfNotNull(
                parameterSize?.takeIf { it.isNotBlank() },
                quantization?.takeIf { it.isNotBlank() },
            )
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

object OllamaClient {
    private const val DEFAULT_BASE = "http://localhost:11434/v1"

    fun tagsUrl(baseUrl: String): String {
        val normalized = AiHttp.normalizeCustomBaseUrl(baseUrl.ifBlank { DEFAULT_BASE })
        val stripped = normalized.trimEnd('/').removeSuffix("/v1")
        return "$stripped/api/tags"
    }

    fun parseTags(body: String): List<OllamaModel> {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val models = json.optJSONArray("models") ?: return emptyList()
        val out = ArrayList<OllamaModel>(models.length())
        for (i in 0 until models.length()) {
            val obj = models.optJSONObject(i) ?: continue
            val id = obj.optString("model").ifBlank { obj.optString("name") }.trim()
            if (id.isEmpty()) continue
            val details = obj.optJSONObject("details")
            out += OllamaModel(
                id = id,
                parameterSize = details?.optString("parameter_size")?.takeIf { it.isNotBlank() },
                quantization = details?.optString("quantization_level")?.takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    suspend fun listModels(
        baseUrl: String,
        allowInsecureHttp: Boolean,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
    ): List<OllamaModel> {
        val url = tagsUrl(baseUrl)
        AiHttp.assertCleartextAllowed(url, allowInsecureHttp)
        val shortClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        val body = RetryPolicy.execute {
            shortClient.newCall(Request.Builder().url(url).get().build())
        }
        return parseTags(body)
    }
}
