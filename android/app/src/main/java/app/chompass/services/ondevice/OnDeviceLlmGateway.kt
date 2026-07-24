package app.chompass.services.ondevice

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.services.ai.AiError
import app.chompass.services.ai.OnDeviceLlmClient

/**
 * Process-scoped gateway to the on-device LLM engine. Given ~30-90s cold
 * init, the engine is created lazily on first dispatch (opt-out users pay
 * zero cost) and kept resident until [unload] is called explicitly from
 * Settings — per-call reload is not viable at this latency.
 */
class OnDeviceLlmGateway(
    private val context: Context,
    private val prefs: PreferencesStore,
) {

    private val lock = Mutex()
    private var client: OnDeviceLlmClient? = null
    private var loadedModelId: String? = null

    val isLoaded: Boolean get() = client != null

    suspend fun isModelDownloaded(): Boolean {
        val entry = selectedEntry()
        return ModelDownloadManager(context).isDownloaded(entry)
    }

    /** Text-only Tier A call. Throws if the model isn't downloaded. */
    suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val engine = ensureEngine(vision = false)
        return engine.generate(systemPrompt, userPrompt)
    }

    /**
     * Tier B call with a single image. Throws if the model isn't downloaded, or — for E4B
     * specifically, the one combination known to OOM-kill the process on GPU+GPU vision — if
     * free memory is too low. E2B+vision runs GPU+GPU unchanged and doesn't need this guard.
     */
    suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String = ""): String {
        val entry = selectedEntry()
        if (entry.modelId == ModelCatalog.E4B.modelId &&
            !OnDeviceCapability.hasEnoughAvailableMemoryForVision(context, entry)
        ) {
            throw AiError.OnDeviceLowMemory
        }
        val engine = ensureEngine(vision = true)
        return engine.generateWithImage(userPrompt, imageBytes, systemPrompt)
    }

    /** Frees the resident engine on demand — exposed as a Settings action. */
    suspend fun unload() {
        lock.withLock {
            client?.close()
            client = null
            loadedModelId = null
        }
    }

    private suspend fun selectedEntry(): OnDeviceModelEntry {
        val modelId = prefs.selectedAIModel.first()
        return ModelCatalog.forModelId(AIProvider.ON_DEVICE.supportedModelOrDefault(modelId))
    }

    private suspend fun ensureEngine(vision: Boolean): OnDeviceLlmClient {
        val entry = selectedEntry()
        if (!ModelDownloadManager(context).isDownloaded(entry)) {
            throw AiError.OnDeviceModelNotDownloaded
        }
        return lock.withLock {
            val existing = client
            if (existing != null &&
                loadedModelId == entry.modelId &&
                (!vision || existing.visionEnabled)
            ) {
                return@withLock existing
            }
            existing?.close()
            loadedModelId = entry.modelId
            val modelPath = ModelDownloadManager(context).modelFile(entry).absolutePath
            val cacheDir = File(context.cacheDir, "litert").apply { mkdirs() }.absolutePath
            val created = OnDeviceLlmClient(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = OnDeviceCapability.preferredBackend(context, entry, vision),
                enableVision = vision,
            )
            created.ensureLoaded()
            client = created
            created
        }
    }
}
