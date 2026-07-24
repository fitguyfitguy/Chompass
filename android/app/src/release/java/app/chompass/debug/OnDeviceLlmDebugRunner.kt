package app.chompass.debug

import app.chompass.AppContainer

/** Release stub — LiteRT-LM is debugImplementation only. */
internal object OnDeviceLlmDebugRunner {
    suspend fun run(container: AppContainer, config: OnDeviceLlmDebugConfig) = Unit
}
