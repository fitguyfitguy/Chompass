package app.chompass.sync

/**
 * Normalize a user-entered WebDAV file URL.
 *
 * Users often paste a host path without a scheme, or accidentally stack schemes
 * (e.g. `https://https://…` after fixing an earlier `http://` entry). Release
 * builds also block cleartext, so a missing scheme defaults to HTTPS.
 */
fun normalizeWebDavUrl(raw: String): String {
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
    rest = rest.trimStart('/')
    if (rest.isEmpty()) return raw.trim()

    val scheme = if (preferHttp) "http" else "https"
    return "$scheme://$rest"
}
