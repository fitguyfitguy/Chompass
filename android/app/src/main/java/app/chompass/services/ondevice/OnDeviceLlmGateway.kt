package app.chompass.services.ondevice

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.services.ai.AiError
import app.chompass.services.ai.ON_DEVICE_LLM_TAG
import app.chompass.services.ai.OnDeviceLlmClient

/**
 * Process-scoped gateway to the on-device LLM engine. Given ~30-90s cold
 * init, the engine is created lazily on first dispatch (opt-out users pay
 * zero cost) and kept resident until [unload] is called explicitly from
 * Settings — per-call reload is not viable at this latency.
 *
 * Text loads retry once on the CPU backend when GPU/OpenCL init fails
 * (mid-tier devices can have broken/absent OpenCL); vision stays GPU-only —
 * CPU vision crashes on the 2nd image turn (upstream LiteRT-LM #2056), so a
 * vision init failure surfaces as a catchable [AiError.OnDeviceEngineInit]
 * instead of a hard crash.
 */
class OnDeviceLlmGateway(
    private val context: Context,
    private val prefs: PreferencesStore,
    private val engineFactory: (String, String, Backend, Boolean) -> OnDeviceLlmEngine = ::createEngine,
) {
    private val lock = Mutex()
    private var engine: OnDeviceLlmEngine? = null
    private var loadedModelId: String? = null

    val isLoaded: Boolean get() = engine != null

    suspend fun isModelDownloaded(): Boolean {
        val entry = selectedEntry()
        return ModelDownloadManager(context).isDownloaded(entry)
    }

    /** Text-only Tier A call. Throws if the model isn't downloaded. */
    suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val loaded = ensureEngine(vision = false)
        return loaded.generate(systemPrompt, userPrompt)
    }

    /**
     * Tier B call with a single image. Throws if the model isn't downloaded,
     * or — the one combination known to OOM-kill the process — if free memory
     * is too low for a vision call on top of a loaded model (observed on E4B,
     * GPU+GPU vision; applies to any model on mid-tier RAM).
     */
    suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String = ""): String {
        val entry = selectedEntry()
        if (!OnDeviceCapability.hasEnoughAvailableMemoryForVision(context, entry)) {
            throw AiError.OnDeviceLowMemory
        }
        val loaded = ensureEngine(vision = true)
        return loaded.generateWithImage(userPrompt, imageBytes, systemPrompt)
    }

    /** Frees the resident engine on demand — exposed as a Settings action. */
    suspend fun unload() {
        lock.withLock {
            engine?.close()
            engine = null
            loadedModelId = null
        }
    }

    private suspend fun selectedEntry(): OnDeviceModelEntry {
        val modelId = prefs.selectedAIModel.first()
        val entry = ModelCatalog.forModelId(AIProvider.ON_DEVICE.supportedModelOrDefault(modelId))
        // Defensive: a persisted E4B selection on a device below the E4B floor
        // (e.g. a 6 GB phone) resolves to the default E2B instead of OOM'ing.
        return if (OnDeviceCapability.isModelSupported(context, entry)) entry else ModelCatalog.default
    }

    private suspend fun ensureEngine(vision: Boolean): OnDeviceLlmEngine {
        val entry = selectedEntry()
        if (!ModelDownloadManager(context).isDownloaded(entry)) {
            throw AiError.OnDeviceModelNotDownloaded
        }
        return lock.withLock {
            val existing = engine
            if (existing != null &&
                loadedModelId == entry.modelId &&
                (!vision || existing.visionEnabled)
            ) {
                return@withLock existing
            }
            existing?.close()
            engine = null
            loadedModelId = entry.modelId
            val modelPath = ModelDownloadManager(context).modelFile(entry).absolutePath
            val cacheDir = File(context.cacheDir, "litert").apply { mkdirs() }.absolutePath
            val preferred = OnDeviceCapability.preferredBackend(context, entry, vision)
            val created = try {
                createAndLoad(modelPath, cacheDir, preferred, vision)
            } catch (gpuFailure: Throwable) {
                // GPU/OpenCL init failed. Text retries once on CPU — the
                // documented fallback for devices without working OpenCL.
                // Vision never retries on CPU (2nd-image crash, #2056) and
                // surfaces a catchable error instead.
                if (vision || preferred !is Backend.GPU) throw AiError.OnDeviceEngineInit(gpuFailure)
                Log.w(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=engineInit_fallback backend=cpu reason=${gpuFailure.message}"
                )
                try {
                    createAndLoad(modelPath, cacheDir, Backend.CPU(numOfThreads = 4), vision)
                } catch (cpuFailure: Throwable) {
                    throw AiError.OnDeviceEngineInit(cpuFailure)
                }
            }
            engine = created
            created
        }
    }

    private suspend fun createAndLoad(
        modelPath: String,
        cacheDir: String,
        backend: Backend,
        vision: Boolean,
    ): OnDeviceLlmEngine {
        val created = engineFactory(modelPath, cacheDir, backend, vision)
        try {
            created.ensureLoaded()
        } catch (e: Throwable) {
            created.close()
            throw e
        }
        return created
    }

    private companion object {
        fun createEngine(modelPath: String, cacheDir: String, backend: Backend, vision: Boolean): OnDeviceLlmEngine =
            OnDeviceLlmClient(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = backend,
                enableVision = vision,
            )
    }
}
