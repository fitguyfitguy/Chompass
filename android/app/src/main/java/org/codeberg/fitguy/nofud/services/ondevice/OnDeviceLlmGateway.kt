package org.codeberg.fitguy.nofud.services.ondevice

import android.content.Context
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.codeberg.fitguy.nofud.services.ai.AiError
import org.codeberg.fitguy.nofud.services.ai.OnDeviceLlmClient

/**
 * Process-scoped gateway to the on-device LLM engine. Given ~30-90s cold
 * init, the engine is created lazily on first dispatch (opt-out users pay
 * zero cost) and kept resident until [unload] is called explicitly from
 * Settings — per-call reload is not viable at this latency.
 */
class OnDeviceLlmGateway(private val context: Context) {

    private val lock = Mutex()
    private var client: OnDeviceLlmClient? = null

    val isLoaded: Boolean get() = client != null

    fun isModelDownloaded(): Boolean = ModelDownloadManager(context).isDownloaded()

    /** Text-only Tier A call. Throws if the model isn't downloaded. */
    suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val engine = ensureEngine(vision = false)
        return engine.generate(systemPrompt, userPrompt)
    }

    /** Tier B call with a single image. Throws if the model isn't downloaded. */
    suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String = ""): String {
        val engine = ensureEngine(vision = true)
        return engine.generateWithImage(userPrompt, imageBytes, systemPrompt)
    }

    /** Frees the resident engine (~1-2GB) on demand — exposed as a Settings action. */
    suspend fun unload() {
        lock.withLock {
            client?.close()
            client = null
        }
    }

    private suspend fun ensureEngine(vision: Boolean): OnDeviceLlmClient {
        if (!isModelDownloaded()) {
            throw AiError.OnDeviceModelNotDownloaded
        }
        return lock.withLock {
            val existing = client
            if (existing != null && (!vision || existing.visionEnabled)) return@withLock existing
            existing?.close()
            val modelPath = ModelDownloadManager(context).modelFile().absolutePath
            val cacheDir = File(context.cacheDir, "litert").apply { mkdirs() }.absolutePath
            val created = OnDeviceLlmClient(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = OnDeviceCapability.preferredBackend(context),
                enableVision = vision,
            )
            created.ensureLoaded()
            client = created
            created
        }
    }
}
