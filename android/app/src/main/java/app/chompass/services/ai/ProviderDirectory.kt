package app.chompass.services.ai

import app.chompass.models.AIProvider
import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One reviewed OpenAI-compatible endpoint from the bundled provider directory
 * (Codeberg #143). [visionModels] is the entry's vision-flagged lineup, capped
 * at 25 by the generator; long tails stay reachable via the runtime /models
 * merge and the custom-model text field.
 */
@Serializable
data class ProviderDirectoryEntry(
    val id: String,
    val name: String,
    val baseUrl: String,
    val docsUrl: String,
    val visionModels: List<String> = emptyList(),
)

/**
 * The bundled provider directory asset (`assets/provider_directory.json`),
 * generated release-time by `scripts/generate_provider_directory.py` from
 * https://models.dev/api.json and validated against
 * `contracts/provider-directory-1.0.schema.json` in release:check-parity.
 * No runtime registry fetch: the trust root is the signed APK.
 */
@Serializable
data class ProviderDirectory(
    val version: Int,
    val source: String,
    val fetchedAt: String,
    val entries: List<ProviderDirectoryEntry> = emptyList(),
) {
    companion object {
        private const val TAG = "ProviderDirectory"
        private const val ASSET_NAME = "provider_directory.json"

        /** Forward-compatible with schema bumps: unknown new fields are ignored. */
        val json = Json { ignoreUnknownKeys = true }

        /** Pure; null on any parse failure — the feature silently degrades to hand-typed-only. */
        fun parse(text: String): ProviderDirectory? = runCatching {
            json.decodeFromString<ProviderDirectory>(text)
        }.getOrNull()

        /** Reads the bundled asset; null when missing or corrupt (never crashes the sheet). */
        fun load(context: Context): ProviderDirectory? = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                parse(reader.readText())
            }
        }.onFailure {
            Log.w(TAG, "provider_directory.json unavailable: ${it.message}")
        }.getOrNull()

        /**
         * Entry matching [baseUrl]: exact match after trim() + trailing-slash
         * trim, no scheme folding. A hand-edited or no-longer-bundled URL
         * misses → the sheets fall back to plain custom behavior.
         */
        fun ProviderDirectory?.findEntry(baseUrl: String?): ProviderDirectoryEntry? {
            val needle = baseUrl?.trim()?.trimEnd('/') ?: return null
            if (needle.isEmpty()) return null
            return this?.entries?.firstOrNull { it.baseUrl.trimEnd('/') == needle }
        }

        /** Directory display name for a custom profile, or null when the URL is not bundled. */
        fun ProviderDirectory?.matchedName(provider: AIProvider, baseUrl: String?): String? =
            if (provider == AIProvider.CUSTOM_OPENAI) findEntry(baseUrl)?.name else null
    }
}
