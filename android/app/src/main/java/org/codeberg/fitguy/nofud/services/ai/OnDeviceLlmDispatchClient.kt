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
    suspend fun analyze(
        gateway: OnDeviceLlmGateway,
        prompt: String,
        imageBytesList: List<ByteArray>,
    ): String = if (imageBytesList.isEmpty()) {
        gateway.generate(systemPrompt = "", userPrompt = prompt)
    } else {
        gateway.generateWithImage(userPrompt = prompt, imageBytes = imageBytesList.first())
    }
}
