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
     * Custom and Ollama endpoints (both user-entered URLs) additionally trust user-installed CA certs
     * ([usesUserCaTrust]).
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

    /** User-entered endpoints (custom OpenAI-compatible + Ollama) trust certs installed on the phone. */
    fun usesUserCaTrust(provider: AIProvider): Boolean =
        provider == AIProvider.CUSTOM_OPENAI || provider == AIProvider.OLLAMA

    /**
     * Gate for the release cleartext opt-in (issue #8 follow-up, design doc D2
     * Option B): with the release network-security-config now permitting
     * cleartext, http:// URLs to non-loopback hosts are rejected here unless the
     * user enabled "Allow insecure HTTP" in Settings → AI & Speech. Loopback
     * (localhost / 127.0.0.1) stays allowed unconditionally — that is the
     * default Ollama URL and the emulator alias.
     *
     * Called at every base-URL resolution site (ChatService, FoodAnalysisService
     * dispatch) so primary and fallback requests are covered.
     */
    fun assertCleartextAllowed(url: String, allowInsecureHttp: Boolean) {
        if (allowInsecureHttp || !url.startsWith("http://")) return
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty().lowercase()
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]") return
        throw AiError.InsecureHttpBlocked
    }

    /**
     * Normalize a user-entered custom base URL: trim whitespace/stacked schemes and default a
     * missing scheme to https (cleartext is the opt-in path — [assertCleartextAllowed]). Mirrors
     * `WebDavUrl.normalizeWebDavUrl` (an explicit `http://` is preserved and allowed once the
     * user enables "Allow insecure HTTP").
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
