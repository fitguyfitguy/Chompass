package org.codeberg.fitguy.nofud.ui.home

import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis

enum class EntryAnalysisPhase {
    Preparing,
    CallingAi,
    Parsing,
}

sealed class FoodAnalysisProgress {
    data class Phase(val phase: EntryAnalysisPhase) : FoodAnalysisProgress()

    /** Primary parse done; units may still be pending. */
    data class Parsed(val analysis: FoodAnalysis, val unitsPending: Boolean) : FoodAnalysisProgress()

    /** Final result after any unit fallback. */
    data class Complete(val analysis: FoodAnalysis) : FoodAnalysisProgress()
}
