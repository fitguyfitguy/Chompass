package app.chompass.debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.chompass.AppContainer

/** Dispatches the debug goal-calculation matrix on debug builds; no-op on release. */
object GoalCalcMatrixDebugLauncher {
    fun launchIfRequested(scope: CoroutineScope, container: AppContainer, scenarios: String, repeatCount: Int = 1) {
        scope.launch {
            GoalCalcMatrixDebugRunner.run(container, scenarios, repeatCount)
        }
    }
}
