package org.codeberg.fitguy.nofud.services.ondevice

/**
 * Maps the `litertlm-android` library version this app is built against to the
 * model artifacts it expects. A future library bump that needs a different
 * quantization/build of the model becomes a data change here — nothing else
 * in the download pipeline needs to know about it.
 */
data class OnDeviceModelEntry(
    /** Catalog version written to prefs on successful download (staleness detection). */
    val version: String,
    /** Matches [org.codeberg.fitguy.nofud.models.AIProvider.ON_DEVICE] model ids. */
    val modelId: String,
    val displayName: String,
    val filename: String,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val modelCardUrl: String,
)

object ModelCatalog {
    /**
     * Gemma 4 E2B-it native/mobile `.litertlm` build from `litert-community` on
     * Hugging Face — NOT the `-web` variant, which fails to load on Android
     * (`TF_LITE_PREFILL_DECODE not found in the model`). Verified against the
     * same model validated in the debug smoke test (docs/ON_DEVICE_LLM.md).
     * sha256 is the repo's LFS object hash, confirmed via the HF paths-info API.
     */
    val E2B = OnDeviceModelEntry(
        version = "gemma-4-e2b-it-1",
        modelId = "gemma-4-E2B-it",
        displayName = "Gemma 4 E2B-it",
        filename = "gemma-4-E2B-it.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        sizeBytes = 2_588_147_712L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        modelCardUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
    )

    /**
     * Gemma 4 E4B-it (~4B params) — higher quality, ~3.4 GB download, slower
     * inference. Same native/mobile `.litertlm` requirement as [E2B].
     */
    val E4B = OnDeviceModelEntry(
        version = "gemma-4-e4b-it-1",
        modelId = "gemma-4-E4B-it",
        displayName = "Gemma 4 E4B-it",
        filename = "gemma-4-E4B-it.litertlm",
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        sizeBytes = 3_659_530_240L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        modelCardUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
    )

    val entries: List<OnDeviceModelEntry> = listOf(E2B, E4B)

    val default: OnDeviceModelEntry = E2B

    fun forModelId(modelId: String?): OnDeviceModelEntry =
        entries.firstOrNull { it.modelId == modelId?.trim() } ?: default

    fun forVersion(version: String?): OnDeviceModelEntry? =
        entries.firstOrNull { it.version == version?.trim() }
}
