package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * A named, self-contained AI configuration: provider + model + optional custom
 * base URL + optional vision slot + its own API key (stored in
 * [app.chompass.data.KeyStore] under the profile [id], never inside the JSON).
 *
 * Exactly one profile is active (DataStore `activeAiProfileId`, falling back to
 * the first list entry) and drives every AI feature; one optional profile id
 * (`fallbackAiProfileId`) serves as the failure fallback. Switching profiles
 * never disturbs another profile's model or key — the legacy single-slot
 * settings clobbered the selected model on every provider switch.
 */
@Serializable
data class AiProfile(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val name: String,
    val provider: AIProvider,
    /** null → [AIProvider.defaultModel]/[AIProvider.supportedModelOrDefault] at resolve time. */
    val model: String? = null,
    /** Custom base URL override (required for CUSTOM_OPENAI, ignored elsewhere). */
    val baseUrl: String? = null,
    /** Per-profile vision-model slot; null = primary model handles images too. */
    val visionModel: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    /** Per-profile advanced overrides; null = fall back to the global preference. */
    val readTimeoutSeconds: Int? = null,
    /** [app.chompass.data.OpenRouterReasoningEffort.storageKey]; null = global preference. */
    val reasoningEffort: String? = null,
    val geminiGoogleSearch: Boolean? = null,
)
