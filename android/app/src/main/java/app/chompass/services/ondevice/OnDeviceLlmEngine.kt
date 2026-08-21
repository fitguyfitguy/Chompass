package app.chompass.services.ondevice

import java.io.Closeable

/**
 * Narrow contract the on-device gateway needs from the LiteRT-LM engine.
 * Kept as an interface so the gateway's backend-fallback logic (GPU→CPU
 * retry) is unit-testable without loading LiteRT native libs;
 * [app.chompass.services.ai.OnDeviceLlmClient] is the real implementation.
 */
interface OnDeviceLlmEngine : Closeable {
    val visionEnabled: Boolean

    /** Loads the model on first call; a no-op (returns 0) if already loaded. Returns load time in ms. */
    suspend fun ensureLoaded(): Long

    /** Single-shot text prompt/response (Tier A). */
    suspend fun generate(systemPrompt: String, userPrompt: String): String

    /** Single-shot image+text prompt/response (Tier B); requires [visionEnabled]. */
    suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String = ""): String
}
