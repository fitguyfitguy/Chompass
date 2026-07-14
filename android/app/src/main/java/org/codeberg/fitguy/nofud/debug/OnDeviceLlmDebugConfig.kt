package org.codeberg.fitguy.nofud.debug

/** Debug intent configuration for the on-device LLM smoke test harness. */
data class OnDeviceLlmDebugConfig(
    val enabled: Boolean = false,
    val backendName: String = "gpu",
    val enableMtp: Boolean = false,
    val modelFilename: String = OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
    /** `all`, `a`, or `c` */
    val tier: String = "all",
    /** `full`, `compact`, `fewshot_units`, or `twopass` */
    val promptMode: String = "full",
    /** Tier A repeat count for warm-cache latency comparison (default 1). */
    val repeatCount: Int = 1,
)

object OnDeviceLlmDefaults {
    const val DEFAULT_MODEL_FILENAME = "gemma-e2b-int4.litertlm"
    /** Hugging Face `litert-community/functiongemma-270m-ft-mobile-actions` (exact filename may vary). */
    const val FUNCTIONGEMMA_MODEL_FILENAME = "functiongemma-270m-int4.litertlm"
    /** Gemma 4 E4B-it int4 native/mobile build for quality comparison experiments. */
    const val E4B_MODEL_FILENAME = "gemma-e4b-int4.litertlm"
}
