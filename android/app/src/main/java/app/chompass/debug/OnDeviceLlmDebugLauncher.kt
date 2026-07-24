package app.chompass.debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.chompass.AppContainer

/** Dispatches the on-device LLM smoke test on debug builds; no-op on release. */
object OnDeviceLlmDebugLauncher {
    fun launchIfRequested(scope: CoroutineScope, container: AppContainer, config: OnDeviceLlmDebugConfig) {
        if (!config.enabled) return
        scope.launch {
            OnDeviceLlmDebugRunner.run(container, config)
        }
    }
}
