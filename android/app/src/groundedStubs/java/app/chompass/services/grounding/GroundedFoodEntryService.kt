package app.chompass.services.grounding

import android.content.Context
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.models.FoodRecognitionResult
import app.chompass.models.GroundingCandidate
import app.chompass.models.RecognizedFoodComponent
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.ui.home.FoodAnalysisProgress

/**
 * Release stub — real implementation lives in the `grounded` source set (debug/test).
 * [GroundedEntryFeature.ENABLED] stays false in shipping builds.
 */
class GroundedFoodEntryService(
    foodAnalysis: FoodAnalysisService,
    foodRepository: FoodRepository,
    prefs: PreferencesStore,
    usdaIndex: UsdaFoodIndex,
    recognizeDelegate: (suspend (
        description: String?,
        images: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ) -> FoodRecognitionResult)? = null,
    barcodeLookup: (suspend (String) -> FoodAnalysis)? = null,
    estimateFallback: (suspend (String) -> FoodAnalysis)? = null,
) {
    init {
        // Touch params so release R8 keeps the signature aligned with debug.
        @Suppress("UNUSED_EXPRESSION")
        foodAnalysis to foodRepository to prefs to usdaIndex to
            recognizeDelegate to barcodeLookup to estimateFallback
    }

    data class ComponentResolution(
        val component: RecognizedFoodComponent,
        val selected: GroundingCandidate?,
        val candidates: List<GroundingCandidate>,
        val analysis: FoodAnalysis?,
        val needsUserChoice: Boolean,
        val question: String? = null,
        val fallbackReason: GroundingFallbackReason? = null,
        val portionUnresolved: Boolean = false,
    )

    data class GroundedResult(
        val analysis: FoodAnalysis,
        val resolutions: List<ComponentResolution>,
        val recognition: FoodRecognitionResult,
    )

    suspend fun analyze(
        description: String? = null,
        imageBytesList: List<ByteArray> = emptyList(),
        onProgress: (FoodAnalysisProgress) -> Unit = {},
        selectedSourceIds: Map<Int, String> = emptyMap(),
        gramOverrides: Map<Int, Double> = emptyMap(),
        priorRecognition: FoodRecognitionResult? = null,
        allowedSourceIds: Set<String>? = null,
    ): GroundedResult {
        throw UnsupportedOperationException(
            "GroundedFoodEntryService is not packaged in release while GroundedEntryFeature.ENABLED=false"
        )
    }
}
