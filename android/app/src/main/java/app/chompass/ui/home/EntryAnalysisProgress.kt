package app.chompass.ui.home

import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.grounding.GroundedFoodEntryService

enum class EntryAnalysisPhase {
    Preparing,
    /** Still-image barcode decode + Open Food Facts soft context (photo AI). */
    LookingUpBarcode,
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
