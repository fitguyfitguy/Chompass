package app.chompass.debug

import app.chompass.AppContainer
import app.chompass.services.GoalCalcMatrixTest

/** Debug implementation — runs the goal-calculation matrix against the requested provider/tier. */
internal object GoalCalcMatrixDebugRunner {
    suspend fun run(container: AppContainer, scenarios: String, repeatCount: Int = 1, tier: String = "auto", provider: String = "on_device") {
        GoalCalcMatrixTest(container, scenarios.ifBlank { null }, repeatCount, tier, provider).run()
    }
}
