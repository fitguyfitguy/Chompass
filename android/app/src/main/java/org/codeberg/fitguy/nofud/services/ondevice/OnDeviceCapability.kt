package org.codeberg.fitguy.nofud.services.ondevice

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend

/**
 * Single source of truth for whether this device can reasonably run the
 * on-device model, used by both the Settings provider picker and the model
 * download-eligibility check (see ON_DEVICE_LLM production plan, Phase 3).
 *
 * The RAM floor is provisional — validated only on Pixel 9a (Tensor G4,
 * GPU/OpenCL backend, 30-90s cold init). A lower/mid-tier, non-Tensor device
 * pass is still outstanding; tighten or relax this threshold once that data
 * exists.
 */
object OnDeviceCapability {
    private const val MIN_RAM_BYTES = 6L * 1024 * 1024 * 1024

    /** Fixed headroom required on top of the model file size before starting a vision call. */
    private const val VISION_MEMORY_HEADROOM_BYTES = 1_500L * 1024 * 1024

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    fun isSupported(context: Context): Boolean =
        hasSupportedAbi() && hasEnoughRam(context)

    private fun hasSupportedAbi(): Boolean =
        Build.SUPPORTED_ABIS.any { it in SUPPORTED_ABIS }

    private fun hasEnoughRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem >= MIN_RAM_BYTES
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
     * [hasEnoughRam] total-RAM check at install time says nothing about
     * memory actually free at inference time, and a vision call on top of an
     * already-loaded model is the point where OOM kills have been observed.
     */
    fun hasEnoughAvailableMemoryForVision(context: Context, entry: OnDeviceModelEntry): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory) return false
        return info.availMem >= entry.sizeBytes + VISION_MEMORY_HEADROOM_BYTES
    }
}
