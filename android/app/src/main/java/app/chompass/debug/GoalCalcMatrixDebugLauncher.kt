package app.chompass.debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.chompass.AppContainer

/** Dispatches the debug goal-calculation matrix on debug builds; no-op on release. */
object GoalCalcMatrixDebugLauncher {
    fun launchIfRequested(
        scope: CoroutineScope,
        container: AppContainer,
        scenarios: String,
        repeatCount: Int = 1,
        tier: String = "auto",
        provider: String = "on_device",
        model: String = "",
    ) {
        scope.launch {
            GoalCalcMatrixDebugRunner.run(container, scenarios, repeatCount, tier, provider, model)
        }
    }
}
