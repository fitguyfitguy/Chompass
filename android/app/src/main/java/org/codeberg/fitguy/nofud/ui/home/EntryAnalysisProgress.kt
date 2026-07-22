package org.codeberg.fitguy.nofud.ui.home

import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
import org.codeberg.fitguy.nofud.services.grounding.GroundedFoodEntryService

enum class EntryAnalysisPhase {
    Preparing,
    CallingAi,
    Parsing,
    /** Grounded entry: model recognition of components (no nutrients yet). */
    Recognizing,
    SearchingHistory,
    SearchingUsda,
    Resolving,
}

sealed class FoodAnalysisProgress {
    data class Phase(val phase: EntryAnalysisPhase) : FoodAnalysisProgress()

    /** Primary parse done; units may still be pending. */
    data class Parsed(val analysis: FoodAnalysis, val unitsPending: Boolean) : FoodAnalysisProgress()

    /** Final result after any unit fallback. */
    data class Complete(val analysis: FoodAnalysis) : FoodAnalysisProgress()
}

/** Pending grounded-entry state waiting for candidate / portion confirmation. */
data class PendingGroundedReview(
    val result: GroundedFoodEntryService.GroundedResult,
    val description: String?,
    val imageBytes: ByteArray?,
)
