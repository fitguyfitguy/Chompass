package app.chompass.services.mealie

import app.chompass.models.Recipe
import app.chompass.services.ai.AiHttp
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.ai.LocalEndpointTrust
import app.chompass.services.ai.RetryPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Mealie REST client. Bearer token, user-CA trust, same cleartext gate as Custom AI.
 */
object MealieClient {
    fun listUrl(baseUrl: String): String {
        val root = rootUrl(baseUrl)
        return root.toHttpUrl().newBuilder()
            .addPathSegments("api/recipes")
            .addQueryParameter("perPage", "-1")
            .addQueryParameter("orderBy", "name")
            .build()
            .toString()
    }

    fun detailUrl(baseUrl: String, slug: String): String {
        val root = rootUrl(baseUrl)
        return root.toHttpUrl().newBuilder()
            .addPathSegments("api/recipes")
            .addPathSegment(slug)
            .build()
            .toString()
    }

    suspend fun listRecipes(
        baseUrl: String,
        token: String,
        allowInsecureHttp: Boolean,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
    ): List<MealieRecipeSummary> {
        val url = listUrl(baseUrl)
        val body = get(url, token, allowInsecureHttp, client)
        return MealieRecipeMapper.parseSummaryList(body)
    }

    suspend fun getRecipe(
        baseUrl: String,
        token: String,
        slug: String,
        allowInsecureHttp: Boolean,
        client: OkHttpClient = FoodAnalysisService.defaultClient,
    ): Recipe? {
        val url = detailUrl(baseUrl, slug)
        val body = get(url, token, allowInsecureHttp, client)
        return MealieRecipeMapper.fromDetailJson(body)
    }

    private fun rootUrl(baseUrl: String): String =
        AiHttp.normalizeCustomBaseUrl(baseUrl).trimEnd('/')

    private suspend fun get(
        url: String,
        token: String,
        allowInsecureHttp: Boolean,
        base: OkHttpClient,
    ): String {
        AiHttp.assertCleartextAllowed(url, allowInsecureHttp)
        val http = LocalEndpointTrust.withUserCaTrust(
            base.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build(),
        )
        val sanitized = AiHttp.sanitizeApiKey(token)
            ?: throw IllegalArgumentException("Mealie token is blank")
        return RetryPolicy.execute {
            http.newCall(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $sanitized")
                    .get()
                    .build(),
            )
        }
    }
}
