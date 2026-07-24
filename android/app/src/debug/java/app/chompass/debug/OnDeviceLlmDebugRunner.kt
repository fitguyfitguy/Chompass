package app.chompass.debug

import app.chompass.AppContainer
import app.chompass.services.OnDeviceLlmSmokeTest

/** Debug implementation — runs the LiteRT-LM smoke test harness. */
internal object OnDeviceLlmDebugRunner {
    suspend fun run(container: AppContainer, config: OnDeviceLlmDebugConfig) {
        OnDeviceLlmSmokeTest(container, config).run()
    }
}
