package app.chompass.services.ai

import app.chompass.models.AIProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object AiHttp {
    /**
     * Fixed read timeout for builtin cloud endpoints (WP6 cold-start fix):
     * DeepInfra showed no first byte within 150 s on a cold start (warm:
     * 27.9 s) — beyond the default client's 60 s, so a cold-start request
     * died while the model spun up. The user-configured timeout stays
     * local-only: it exists for slow local GPUs, not cloud queues.
     */
    const val CLOUD_READ_TIMEOUT_SECONDS = 240

    /**
     * Read timeout for one provider: user-configurable endpoints
     * ([AIProvider.usesConfigurableRequestTimeout]) honor the settings
     * seconds; builtin cloud endpoints use the fixed cold-start budget
     * regardless of the settings value. A bundled directory host is a fixed
     * https cloud endpoint: [bundledDirectory] is honored only for
     * [AIProvider.CUSTOM_OPENAI] and then uses the cloud budget. Any
     * wall-clock watchdog covering a call MUST be derived from this value,
     * never from the raw settings seconds, or it would fire before the read
     * timeout it shadows.
     */
    fun effectiveReadTimeoutSeconds(
        provider: AIProvider,
        settingsSeconds: Int,
        bundledDirectory: Boolean = false,
    ): Int {
        val bundledCloud = bundledDirectory && provider == AIProvider.CUSTOM_OPENAI
        return if (provider.usesConfigurableRequestTimeout && !bundledCloud) {
            settingsSeconds
        } else {
            CLOUD_READ_TIMEOUT_SECONDS
        }
    }

    fun clientWithReadTimeout(base: OkHttpClient, seconds: Int): OkHttpClient =
        base.newBuilder()
            .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
            .build()

    /**
     * User-configurable endpoints (unmatched custom OpenAI-compatible + Ollama)
     * apply the settings timeout ([localTimeoutSeconds]); builtin cloud
     * endpoints, and a bundled directory host, apply the fixed cold-start
     * budget ([CLOUD_READ_TIMEOUT_SECONDS]). Ollama and an unmatched custom
     * URL additionally trust user-installed CA certs ([usesUserCaTrust]).
     * [bundledDirectory] is ignored unless [provider] is [AIProvider.CUSTOM_OPENAI].
     */
    fun clientForProvider(
        base: OkHttpClient,
        provider: AIProvider,
        localTimeoutSeconds: Int,
        trustBuilder: (OkHttpClient) -> OkHttpClient = LocalEndpointTrust::withUserCaTrust,
        bundledDirectory: Boolean = false,
    ): OkHttpClient {
        var client = clientWithReadTimeout(
            base,
            effectiveReadTimeoutSeconds(provider, localTimeoutSeconds, bundledDirectory),
        )
        if (usesUserCaTrust(provider, bundledDirectory)) client = trustBuilder(client)
        return client
    }

    /**
     * Ollama and an unmatched custom URL trust certs installed on the phone.
     * A bundled directory host does not: it is a fixed https cloud endpoint.
     * [bundledDirectory] is ignored unless [provider] is [AIProvider.CUSTOM_OPENAI].
     */
    fun usesUserCaTrust(provider: AIProvider, bundledDirectory: Boolean = false): Boolean {
        if (bundledDirectory && provider == AIProvider.CUSTOM_OPENAI) return false
        return provider == AIProvider.CUSTOM_OPENAI || provider == AIProvider.OLLAMA
    }

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
