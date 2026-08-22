package app.chompass.debug

import app.chompass.AppContainer
import app.chompass.services.GoalCalcMatrixTest

/** Debug implementation — runs the goal-calculation matrix against the on-device model. */
internal object GoalCalcMatrixDebugRunner {
    suspend fun run(container: AppContainer, scenarios: String, repeatCount: Int = 1) {
        GoalCalcMatrixTest(container, scenarios.ifBlank { null }, repeatCount).run()
    }
}
