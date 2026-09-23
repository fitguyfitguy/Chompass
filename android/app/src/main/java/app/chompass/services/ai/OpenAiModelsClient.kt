package app.chompass.services.ai

import android.util.Log
import app.chompass.models.AIProvider
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
    private const val TAG = "OpenAiModels"

    /** Custom base URLs already include /v1 — no stripping, unlike Ollama's tagsUrl. */
    fun modelsUrl(baseUrl: String): String =
        AiHttp.normalizeCustomBaseUrl(baseUrl).trimEnd('/') + "/models"

    fun parseModels(body: String): List<String> {
        val json = runCatching { JSONObject(body) }
            .onFailure { e ->
                Log.w(TAG, "Undecodable /models body '${body.take(120)}' — returning no models", e)
            }
            .getOrNull() ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<String>(data.length())
        for (i in 0 until data.length()) {
            val id = data.optJSONObject(i)?.optString("id")?.trim() ?: continue
            if (id.isNotEmpty()) out += id
        }
        return out
    }

    /** One /models entry with the metadata the lineup filter needs (#107). */
    data class OpenAiModel(
        val id: String,
        val created: Long,
        /** Service owner; only "openai" counts as the official lineup. */
        val ownedBy: String,
        /** Announced shutdown date; blank when none was announced. */
        val shutdownDate: String,
    )

    fun parseModelsWithMeta(body: String): List<OpenAiModel> {
        val json = runCatching { JSONObject(body) }
            .onFailure { e ->
                Log.w(TAG, "Undecodable /models body '${body.take(120)}' — returning no models", e)
            }
            .getOrNull() ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<OpenAiModel>(data.length())
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isEmpty()) continue
            out += OpenAiModel(
                id = id,
                created = obj.optLong("created"),
                ownedBy = obj.optString("owned_by").trim(),
                shutdownDate = obj.optString("shutdown_date").trim(),
            )
        }
        return out
    }

    /**
     * Official, still-serving GPT lineup ids, newest first (#107): id shape,
     * owner openai, no announced shutdown. The shape check is shared with
     * AIProvider.isOpenAiLineupId so picker and validation never disagree.
     */
    fun filterGptLineup(models: List<OpenAiModel>): List<String> =
        models.filter {
            it.ownedBy == "openai" && it.shutdownDate.isEmpty() && AIProvider.isOpenAiLineupId(it.id)
        }.sortedByDescending { it.created }.map { it.id }

    /**
     * Curated list keeps its order, tier tags, and offline role; runtime ids
     * already curated stay put, new ones append (#107). Null runtime (no key,
     * fetch failed) leaves the curated list intact.
     */
    fun mergeOverCurated(curated: List<String>, runtime: List<String>?): List<String> {
        if (runtime.isNullOrEmpty()) return curated.toList()
        val known = curated.toHashSet()
        return curated + runtime.filterNot(known::contains).distinct()
    }

    suspend fun listModels(
        baseUrl: String,
        apiKey: String?,
        allowInsecureHttp: Boolean,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
    ): List<String> = parseModels(fetchModelsBody(baseUrl, apiKey, allowInsecureHttp, client))

    /** Metadata variant for the built-in OpenAI lineup (#107). */
    suspend fun listModelsWithMeta(
        baseUrl: String,
        apiKey: String?,
        allowInsecureHttp: Boolean,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
    ): List<OpenAiModel> = parseModelsWithMeta(fetchModelsBody(baseUrl, apiKey, allowInsecureHttp, client))

    private suspend fun fetchModelsBody(
        baseUrl: String,
        apiKey: String?,
        allowInsecureHttp: Boolean,
        client: OkHttpClient,
    ): String {
        val url = modelsUrl(baseUrl)
        AiHttp.assertCleartextAllowed(url, allowInsecureHttp)
        val shortClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        return RetryPolicy.execute {
            val builder = Request.Builder().url(url).get()
            if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
            shortClient.newCall(builder.build())
        }
    }
}
