package app.chompass.services.ai

import app.chompass.models.AIProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object AiHttp {
    fun clientWithReadTimeout(base: OkHttpClient, seconds: Int): OkHttpClient =
        base.newBuilder()
            .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
            .build()

    /**
     * Local/custom endpoints use the user-configured timeout; cloud providers keep the default client.
     * Custom (OpenAI-compatible) endpoints additionally trust user-installed CA certs ([usesUserCaTrust]).
     */
    fun clientForProvider(
        base: OkHttpClient,
        provider: AIProvider,
        localTimeoutSeconds: Int,
        trustBuilder: (OkHttpClient) -> OkHttpClient = LocalEndpointTrust::withUserCaTrust,
    ): OkHttpClient {
        var client = if (provider.usesConfigurableRequestTimeout) {
            clientWithReadTimeout(base, localTimeoutSeconds)
        } else {
            base
        }
        if (usesUserCaTrust(provider)) client = trustBuilder(client)
        return client
    }

    /** Only the user-entered endpoint trusts certs installed on the phone. */
    fun usesUserCaTrust(provider: AIProvider): Boolean = provider == AIProvider.CUSTOM_OPENAI

    /**
     * Normalize a user-entered custom base URL: trim whitespace/stacked schemes and default a
     * missing scheme to https (release builds block cleartext). Mirrors `WebDavUrl.normalizeWebDavUrl`
     * (an explicit `http://` is preserved; it only works in the debug build).
     */
    fun normalizeCustomBaseUrl(raw: String): String {
        var rest = raw.trim()
        if (rest.isEmpty()) return rest

        var preferHttp = false
        var sawScheme = false
        while (true) {
            val lower = rest.lowercase()
            when {
                lower.startsWith("https://") -> {
                    rest = rest.substring(8)
                    preferHttp = false
                    sawScheme = true
                }
                lower.startsWith("http://") -> {
                    rest = rest.substring(7)
                    if (!sawScheme) preferHttp = true
                    sawScheme = true
                }
                else -> break
            }
        }
        rest = rest.trimEnd('/')
        if (rest.isEmpty()) return raw.trim()

        val scheme = if (preferHttp) "http" else "https"
        return "$scheme://$rest"
    }

    fun sanitizeApiKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
}
