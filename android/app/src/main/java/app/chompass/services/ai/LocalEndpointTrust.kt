package app.chompass.services.ai

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Lets a single OkHttp client also trust CA certificates the user installed on
 * the device (Settings → Security → Install a certificate), not just the system
 * store — so self-hosted endpoints with self-signed certs verify.
 *
 * Scope: applied only to the Custom (OpenAI-compatible) provider — the only
 * provider whose base URL is user-entered ([AiHttp.usesUserCaTrust]). The
 * release network-security-config is left untouched, so cloud providers,
 * OLLAMA (loopback), WebDAV and STT keep the platform default (system CAs
 * only): a user-installed CA can never intercept cloud AI traffic.
 */
internal object LocalEndpointTrust {
    private val trusted = ConcurrentHashMap<OkHttpClient, OkHttpClient>()

    /** Returns a client sharing [base]'s pools/timeouts but trusting user CAs too. Cached per base. */
    fun withUserCaTrust(base: OkHttpClient): OkHttpClient =
        trusted.computeIfAbsent(base, ::build)

    private fun build(base: OkHttpClient): OkHttpClient {
        // AndroidCAStore = system + user-installed CAs (public API since API 14, no permission).
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(store)
        val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        return base.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, tm)
            .build()
    }
}
