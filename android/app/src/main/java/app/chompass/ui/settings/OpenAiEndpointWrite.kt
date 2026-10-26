package app.chompass.ui.settings

import app.chompass.models.AIProvider
import app.chompass.models.AiProfile
import app.chompass.services.ai.AiHttp
import app.chompass.services.ai.ProviderDirectory
import app.chompass.services.ai.ProviderDirectory.Companion.findEntry

data class OpenAiEndpointWrite(
    val changed: Boolean,
    val provider: AIProvider,
    val baseUrl: String?,
    val model: String?,
    val clearVision: Boolean,
    val clearKey: Boolean,
)

/**
 * Plans a custom OpenAI-compatible endpoint write without touching the builtin
 * provider switch. Re-tapping the stored URL is a no-op (key kept). A different
 * path on the same host keeps the key, vision slot, and model. A host change
 * clears both and takes the directory entry's first vision model, or "" when
 * the URL is not in [directory].
 */
fun planOpenAiEndpointWrite(
    active: AiProfile,
    rawUrl: String,
    directory: ProviderDirectory?,
): OpenAiEndpointWrite {
    if (rawUrl.isBlank()) return unchanged(active)
    val stored = AiHttp.normalizeCustomBaseUrl(rawUrl)
    val sameEndpoint = active.provider == AIProvider.CUSTOM_OPENAI &&
        active.baseUrl?.trim()?.trimEnd('/') == stored.trimEnd('/')
    if (sameEndpoint) return unchanged(active)
    val hostChanged = active.provider != AIProvider.CUSTOM_OPENAI ||
        endpointHost(active.baseUrl) == null ||
        endpointHost(stored) == null ||
        endpointHost(active.baseUrl) != endpointHost(stored)
    val model = if (hostChanged) {
        directory.findEntry(stored)?.visionModels?.firstOrNull().orEmpty()
    } else {
        active.model
    }
    return OpenAiEndpointWrite(
        changed = true,
        provider = AIProvider.CUSTOM_OPENAI,
        baseUrl = stored,
        model = model,
        clearVision = hostChanged,
        clearKey = hostChanged,
    )
}

private fun unchanged(active: AiProfile) = OpenAiEndpointWrite(
    changed = false,
    provider = active.provider,
    baseUrl = active.baseUrl,
    model = active.model,
    clearVision = false,
    clearKey = false,
)

/** Lowercased URI host, or null when blank or unparseable. */
private fun endpointHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()?.takeIf { it.isNotBlank() }
}
