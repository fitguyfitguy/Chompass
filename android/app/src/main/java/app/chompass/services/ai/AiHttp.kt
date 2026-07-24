package app.chompass.services.ai

import app.chompass.models.AIProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object AiHttp {
    fun clientWithReadTimeout(base: OkHttpClient, seconds: Int): OkHttpClient =
        base.newBuilder()
            .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
            .build()

    /** Local/custom endpoints use the user-configured timeout; cloud providers keep the default client. */
    fun clientForProvider(base: OkHttpClient, provider: AIProvider, localTimeoutSeconds: Int): OkHttpClient =
        if (provider.usesConfigurableRequestTimeout) {
            clientWithReadTimeout(base, localTimeoutSeconds)
        } else {
            base
        }

    fun sanitizeApiKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
}
