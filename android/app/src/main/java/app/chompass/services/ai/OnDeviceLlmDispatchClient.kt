package app.chompass.services.ai

import app.chompass.services.ondevice.OnDeviceLlmGateway

/**
 * Thin [FoodAnalysisService.dispatch] adapter for [app.chompass.models.AIProvider.ON_DEVICE]
 * — mirrors the stateless shape of [GeminiClient] / [AnthropicClient], routing
 * to [OnDeviceLlmGateway.generate] for text-only prompts or
 * [OnDeviceLlmGateway.generateWithImage] when an image is present. Only the
 * first image is used — Tier A/B parity with the debug smoke test, which
 * sends one image per analysis.
 */
object OnDeviceLlmDispatchClient {
    /** Tighter than [AiImageBytes.UPLOAD_MAX_DIMENSION] — shrinks vision-encoder memory pressure on-device. */
    private const val ON_DEVICE_VISION_MAX_DIMENSION = 1024

    /**
     * [modelId] is the model the dispatcher resolved for this leg (primary or
     * fallback); passing it through is what lets an on-device fallback load a
     * different model than the Settings-selected primary (Codeberg #54).
     */
    suspend fun analyze(
        gateway: OnDeviceLlmGateway,
        prompt: String,
        imageBytesList: List<ByteArray>,
        modelId: String? = null,
    ): String = if (imageBytesList.isEmpty()) {
        gateway.generate(systemPrompt = "", userPrompt = prompt, modelId = modelId)
    } else {
        val imageBytes = AiImageBytes.jpegForUpload(
            imageBytesList.first(),
            maxDimension = ON_DEVICE_VISION_MAX_DIMENSION,
        )
        gateway.generateWithImage(userPrompt = prompt, imageBytes = imageBytes, modelId = modelId)
    }
}
