package app.chompass.debug

/** Debug intent configuration for the on-device LLM smoke test harness. */
data class OnDeviceLlmDebugConfig(
    val enabled: Boolean = false,
    val backendName: String = "gpu",
    val enableMtp: Boolean = false,
    val modelFilename: String = OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME,
    /** `all`, `a`, `b`, `c`, or `daily` */
    val tier: String = "all",
    /** `full`, `compact`, `fewshot_units`, or `twopass` */
    val promptMode: String = "full",
    /** Tier A repeat count for warm-cache latency comparison (default 1). */
    val repeatCount: Int = 1,
    /** Delete LiteRT compile cache before run (cold disk cache). */
    val clearCache: Boolean = false,
)

object OnDeviceLlmDefaults {
    /**
     * Filenames match [app.chompass.services.ondevice.ModelCatalog] so the file
     * pushed via scripts/push_ondevice_model.sh (or the in-app download) serves
     * both the smoke-test harness and production dispatch — one file, one name.
     */
    const val DEFAULT_MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

    /** Gemma 4 E4B-it int4 native/mobile build (optional quality comparison; not run). */
    const val E4B_MODEL_FILENAME = "gemma-4-E4B-it.litertlm"

    /**
     * FunctionGemma HF artifacts — **not used for Chompass**. Tensor G5 file fails on OpenCL GPU;
     * mobile_actions is fine-tuned for Google's Mobile Actions demo, not Coach tools.
     * Kept for reference if a future Coach-relevant FunctionGemma `.litertlm` appears.
     */
    const val FUNCTIONGEMMA_TENSOR_G5_FILENAME =
        "functiongemma-270m-ft-mobile-actions_Google_Tensor_G5.litertlm"
    const val FUNCTIONGEMMA_MOBILE_ACTIONS_FILENAME = "mobile_actions_q8_ekv1024.litertlm"
}
