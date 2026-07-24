package app.chompass.models

import app.chompass.R

/**
 * How [app.chompass.services.ai.FoodAnalysisService] fills in a
 * non-gram serving unit (slice/ml/tbsp/etc.) when an AI analysis doesn't
 * already return one. Stored as a plain DataStore string key (not inside a
 * JSON blob), so it follows the [storageKey]/[fromStorage] convention used by
 * e.g. `HomeCalorieDisplayMode` rather than kotlinx serialization.
 */
enum class ServingUnitInferenceMode(val storageKey: String, val displayNameRes: Int, val subtitleRes: Int) {
    /** Never infer a unit — entries stay in grams. Fastest: no heuristic, no network call. */
    GRAMS_ONLY("gramsOnly", R.string.serving_unit_mode_grams_only, R.string.serving_unit_mode_grams_only_subtitle),

    /** Zero-network keyword table (see [ServingUnitHeuristics]). Instant, approximate. */
    HEURISTIC("heuristic", R.string.serving_unit_mode_heuristic, R.string.serving_unit_mode_heuristic_subtitle),

    /** Ask the AI provider in a second call when the first response omits a unit. Slowest, most accurate. */
    AI_CALL("aiCall", R.string.serving_unit_mode_ai_call, R.string.serving_unit_mode_ai_call_subtitle);

    companion object {
        val Default = GRAMS_ONLY

        fun fromStorage(raw: String?): ServingUnitInferenceMode =
            entries.firstOrNull { it.storageKey == raw } ?: Default
    }
}
