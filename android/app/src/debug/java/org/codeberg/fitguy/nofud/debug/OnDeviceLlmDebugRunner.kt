package org.codeberg.fitguy.nofud.debug

import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.services.OnDeviceLlmSmokeTest

/** Debug implementation — runs the LiteRT-LM smoke test harness. */
internal object OnDeviceLlmDebugRunner {
    suspend fun run(container: AppContainer, config: OnDeviceLlmDebugConfig) {
        OnDeviceLlmSmokeTest(container, config).run()
    }
}
