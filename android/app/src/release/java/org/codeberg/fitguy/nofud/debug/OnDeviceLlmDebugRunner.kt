package org.codeberg.fitguy.nofud.debug

import org.codeberg.fitguy.nofud.AppContainer

/** Release stub — LiteRT-LM is debugImplementation only. */
internal object OnDeviceLlmDebugRunner {
    suspend fun run(container: AppContainer, config: OnDeviceLlmDebugConfig) = Unit
}
