package app.chompass.models

/**
 * Capability-based model routing (upstream #195): image-bearing requests use
 * the provider's vision-model slot when one is set, otherwise the primary
 * model. Text-only requests always use the primary model.
 *
 * Pure function — unit-testable without a client; used by FoodAnalysisService
 * and ChatService at request resolution time.
 */
fun resolveModelForRequest(
    provider: AIProvider,
    selectedModel: String?,
    visionModel: String?,
    hasImages: Boolean,
): String {
    val primary = provider.supportedModelOrDefault(selectedModel)
    if (!hasImages) return primary
    val vision = visionModel?.takeIf { it.isNotBlank() } ?: return primary
    return provider.supportedModelOrDefault(vision)
}
