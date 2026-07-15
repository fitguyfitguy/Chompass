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
     * GPU/OpenCL is the default backend (see [Backend.GPU]); CPU is offered as
     * a fallback with a "will be slower" disclosure rather than blocking
     * devices without a working OpenCL driver outright — Phase 0's device
     * matrix will refine this into an actual driver-presence check.
     */
    fun preferredBackend(context: Context): Backend = Backend.GPU()
}
