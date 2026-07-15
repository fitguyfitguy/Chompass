package org.codeberg.fitguy.nofud.services.ondevice

/**
 * Maps the `litertlm-android` library version this app is built against to the
 * model artifact it expects. A future library bump that needs a different
 * quantization/build of the model becomes a data change here — nothing else
 * in the download pipeline needs to know about it.
 */
data class OnDeviceModelEntry(
    val version: String,
    val displayName: String,
    val filename: String,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

object ModelCatalog {
    /**
     * Gemma 4 E2B-it native/mobile `.litertlm` build from `litert-community` on
     * Hugging Face — NOT the `-web` variant, which fails to load on Android
     * (`TF_LITE_PREFILL_DECODE not found in the model`). Verified against the
     * same model validated in the debug smoke test (docs/ON_DEVICE_LLM.md).
     * sha256 is the repo's LFS object hash, confirmed via the HF paths-info API.
     */
    val current = OnDeviceModelEntry(
        version = "gemma-4-e2b-it-1",
        displayName = "Gemma 4 E2B-it",
        filename = "gemma-4-E2B-it.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        sizeBytes = 2_588_147_712L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
    )

    /** Human-facing link for the in-app Hugging Face consent/disclosure step. */
    const val MODEL_CARD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
}
