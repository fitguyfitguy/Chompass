package app.chompass.services.ai

import app.chompass.ui.home.EntryAnalysisPhase
import app.chompass.ui.home.FoodAnalysisProgress
import kotlinx.coroutines.delay
/**
 * Debug-only scripted food-analysis response for the `demo_ai` intent extra
 * (marketing usage-video capture). Replays the real streaming contract the way
 * a provider would: [EntryAnalysisPhase.CallingAi] with progressively richer
 * [PartialFoodAnalysis] payloads, then a final raw JSON for the normal
 * parse/finalize path. Never reachable in release builds (gated by
 * BuildConfig.DEBUG in [FoodAnalysisService.callAi]).
 */
internal object DemoFoodAnalysis {
    /** Entry-analysis ops that get the demo response; other calls stay real. */
    val ENTRY_OPS = setOf("analyzeText", "analyzeAuto", "analyzeFood", "analyzeFoodMulti")

    private const val DEMO_FOOD_NAME = "Chicken rice bowl with avocado"

    /** Matches ENTRY_JSON_SCHEMA; parsed by the real FoodJsonParser downstream. */
    private const val FIXTURE_JSON =
        """{"name":"$DEMO_FOOD_NAME","calories":640,"protein":42.0,"carbs":58.0,"fat":24.0,"serving_size_grams":420.0,"emoji":"🥙","sugar":4.0,"added_sugar":1.0,"fiber":9.0,"saturated_fat":5.0,"monounsaturated_fat":8.0,"polyunsaturated_fat":4.0,"sodium":880.0,"potassium":760.0,"unit_options":[{"unit":"bowl","quantity":1.0,"grams_per_unit":420.0}],"constituents":[]}"""

    suspend fun run(onProgress: (FoodAnalysisProgress) -> Unit): String {
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.CallingAi))
        delay(700)
        onProgress(
            FoodAnalysisProgress.Partial(
                partial = PartialFoodAnalysis(
                    name = DEMO_FOOD_NAME,
                    emoji = "🥙",
                )
            )
        )
        delay(450)
        onProgress(
            FoodAnalysisProgress.Partial(
                partial = PartialFoodAnalysis(
                    name = DEMO_FOOD_NAME,
                    emoji = "🥙",
                    calories = 640,
                    protein = 42.0,
                )
            )
        )
        delay(450)
        onProgress(
            FoodAnalysisProgress.Partial(
                partial = PartialFoodAnalysis(
                    name = DEMO_FOOD_NAME,
                    emoji = "🥙",
                    calories = 640,
                    protein = 42.0,
                    carbs = 58.0,
                    fat = 24.0,
                    fiber = 9.0,
                )
            )
        )
        delay(450)
        onProgress(
            FoodAnalysisProgress.Partial(
                partial = PartialFoodAnalysis(
                    name = DEMO_FOOD_NAME,
                    emoji = "🥙",
                    calories = 640,
                    protein = 42.0,
                    carbs = 58.0,
                    fat = 24.0,
                    servingSizeGrams = 420.0,
                    fiber = 9.0,
                    micronutrientCount = 6,
                    hasUnitOptions = true,
                )
            )
        )
        delay(400)
        return FIXTURE_JSON
    }
}
