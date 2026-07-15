package org.codeberg.fitguy.nofud.services.ai

import org.codeberg.fitguy.nofud.services.ondevice.OnDeviceLlmGateway

/**
 * Thin [FoodAnalysisService.dispatch] adapter for [org.codeberg.fitguy.nofud.models.AIProvider.ON_DEVICE]
 * — mirrors the stateless shape of [GeminiClient] / [AnthropicClient], routing
 * to [OnDeviceLlmGateway.generate] for text-only prompts or
 * [OnDeviceLlmGateway.generateWithImage] when an image is present. Only the
 * first image is used — Tier A/B parity with the debug smoke test, which
 * sends one image per analysis.
 */
object OnDeviceLlmDispatchClient {
    /** Tighter than [AiImageBytes.UPLOAD_MAX_DIMENSION] — shrinks vision-encoder memory pressure on-device. */
    private const val ON_DEVICE_VISION_MAX_DIMENSION = 1024

    suspend fun analyze(
        gateway: OnDeviceLlmGateway,
        prompt: String,
        imageBytesList: List<ByteArray>,
    ): String = if (imageBytesList.isEmpty()) {
        gateway.generate(systemPrompt = "", userPrompt = prompt)
    } else {
        val imageBytes = AiImageBytes.jpegForUpload(
            imageBytesList.first(),
            maxDimension = ON_DEVICE_VISION_MAX_DIMENSION,
        )
        gateway.generateWithImage(userPrompt = prompt, imageBytes = imageBytes)
    }
}
