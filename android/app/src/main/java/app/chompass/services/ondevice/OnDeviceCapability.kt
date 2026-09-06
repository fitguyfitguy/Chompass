package app.chompass.services.ondevice

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend

/**
 * Single source of truth for whether this device can reasonably run the
 * on-device model, used by both the Settings provider picker and the model
 * download-eligibility check (see ON_DEVICE_LLM production plan, Phase 3).
 *
 * Floors are expressed in *usable* RAM ([ActivityManager.MemoryInfo.totalMem]
 * = kernel MemTotal). ~0.3–0.6 GB of marketed RAM is reserved (GPU carveout,
 * kernel, modem) and never reported, so a "6 GB phone" reports ≈5.1–5.5 GiB:
 * [MIN_RAM_BYTES] = 4 GiB is the usable-RAM equivalent of "6 GB marketed"
 * (a 4 GB phone reports ≈3.5 GiB and still fails). [E4B_MIN_RAM_BYTES] gates
 * E4B separately at the 8 GB class — E4B + vision OOM-killed the process on
 * Pixel 9a, so it must not be offered to 6 GB devices.
 *
 * Still provisional: validated on Pixel 9a only; the Pixel 6a / CPU-fallback
 * device pass is outstanding (docs/ON_DEVICE_LLM.md device coverage note).
 */
object OnDeviceCapability {
    /** Usable-RAM floor ≈ "6 GB marketed" — coarse provider-level gate (E2B). */
    private const val MIN_RAM_BYTES = 4L * 1024 * 1024 * 1024

    /** E4B floor ≈ "8 GB marketed" — the class that validated E4B (Pixel 9a). */
    private const val E4B_MIN_RAM_BYTES = 7L * 1024 * 1024 * 1024

    /**
     * Headroom required on top of the model file size before starting a vision
     * call, scaled to the device's usable RAM: 10% of [ActivityManager.MemoryInfo.totalMem],
     * clamped to [VISION_HEADROOM_MIN_BYTES]…[VISION_HEADROOM_MAX_BYTES].
     *
     * The old fixed 1.5 GiB was sized against 6 GB phones and blocked everyday
     * 8 GB use: E2B (2.41 GiB) + 1.5 GiB ≈ 3.9 GiB free refused a OnePlus 6T
     * at 3.2–3.5 GiB free where the call runs fine (#46 reboot test, 09-05).
     * 10% keeps the floor proportional to what the device can spare and what
     * zram/swap can absorb, while the min clamp preserves a real cushion on
     * 6 GB devices and the max cap keeps the original conservatism where RAM
     * is plentiful.
     */
    internal fun visionMemoryHeadroomBytes(totalMemBytes: Long): Long =
        (totalMemBytes / 10).coerceIn(VISION_HEADROOM_MIN_BYTES, VISION_HEADROOM_MAX_BYTES)

    private const val VISION_HEADROOM_MIN_BYTES = 512L * 1024 * 1024
    private const val VISION_HEADROOM_MAX_BYTES = 1_500L * 1024 * 1024

    /**
     * Fixed headroom required on top of the model file size before loading the
     * engine at all (text or vision). Loading maps the weights into RSS (≈ the
     * file size) plus KV-cache/activation buffers; on a memory-starved device
     * that thrashes swap and can OOM-kill the process, so the load is refused
     * below this floor instead of degrading the whole phone.
     */
    internal val loadMemoryHeadroomBytes: Long = 512L * 1024 * 1024

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    fun isSupported(context: Context): Boolean =
        hasSupportedAbi() && hasEnoughRamFor(totalMem(context))

    /** Per-model gate: E4B clears a higher floor than the coarse [isSupported] one. */
    fun isModelSupported(context: Context, entry: OnDeviceModelEntry): Boolean =
        hasSupportedAbi() && hasEnoughRamFor(totalMem(context), entry)

    /** Pure decision — injectable for unit tests. null entry = coarse (any-model) floor. */
    internal fun hasEnoughRamFor(totalMemBytes: Long, entry: OnDeviceModelEntry? = null): Boolean =
        totalMemBytes >= ramFloorFor(entry)

    internal fun ramFloorFor(entry: OnDeviceModelEntry?): Long =
        if (entry?.modelId == ModelCatalog.E4B.modelId) E4B_MIN_RAM_BYTES else MIN_RAM_BYTES

    private fun hasSupportedAbi(): Boolean =
        Build.SUPPORTED_ABIS.any { it in SUPPORTED_ABIS }

    private fun totalMem(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    /**
     * GPU/OpenCL is the default backend (see [Backend.GPU]) for both text and
     * vision. E4B + vision is the one combination known to OOM-kill the
     * process on GPU+GPU (docs/ON_DEVICE_LLM.md), so text falls back to CPU
     * in that case — vision stays GPU since CPU vision crashes on the 2nd
     * image turn (upstream LiteRT-LM issue #2056).
     */
    fun preferredBackend(context: Context, entry: OnDeviceModelEntry, vision: Boolean): Backend =
        if (vision && entry.modelId == ModelCatalog.E4B.modelId) {
            Backend.CPU(numOfThreads = 4)
        } else {
            Backend.GPU()
        }

    /**
     * Preflight check run immediately before a vision call — the one-time
     * [hasEnoughRamFor] total-RAM check at install time says nothing about
     * memory actually free at inference time, and a vision call on top of an
     * already-loaded model is the point where OOM kills have been observed.
     * Headroom scales with the device (#46).
     */
    fun hasEnoughAvailableMemoryForVision(context: Context, entry: OnDeviceModelEntry): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory) return false
        return info.availMem >= entry.sizeBytes + visionMemoryHeadroomBytes(info.totalMem)
    }

    /**
     * Preflight check run immediately before loading the engine (any call).
     * Weaker than [hasEnoughAvailableMemoryForVision] (smaller headroom) — the
     * vision check still applies on top for image calls. Loading with less than
     * the model size + headroom available was observed to push the device into
     * swap thrash (3 GB swapped on a Pixel 9a with Telegram/YouTube/Instagram
     * resident) and stall every app, so the gateway refuses the load instead.
     */
    fun hasEnoughAvailableMemoryForLoad(context: Context, entry: OnDeviceModelEntry): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory) return false
        return info.availMem >= entry.sizeBytes + loadMemoryHeadroomBytes
    }
}
