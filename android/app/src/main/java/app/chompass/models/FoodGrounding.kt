package app.chompass.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where nutrient values for a grounded entry (or one of its components) came from.
 * Kept distinct so Open Food Facts (ODbL), USDA (CC0), history, and model estimates
 * are never collapsed into one opaque "AI" badge.
 */
@Serializable
enum class NutrientSourceKind {
    @SerialName("usda") USDA,
    @SerialName("openFoodFacts") OPEN_FOOD_FACTS,
    @SerialName("history") HISTORY,
    @SerialName("nutritionLabel") NUTRITION_LABEL,
    @SerialName("modelEstimate") MODEL_ESTIMATE,
}

/** Nutrient reporting basis used when scaling to the logged amount. */
@Serializable
enum class NutrientBasis {
    @SerialName("per100g") PER_100G,
    @SerialName("perServing") PER_SERVING,
    @SerialName("absolute") ABSOLUTE,
}

/**
 * Compact provenance attached to [FoodEntry] / [app.chompass.services.ai.FoodAnalysis].
 * All fields are optional with defaults so older DataStore/export JSON still decodes.
 */
@Serializable
data class FoodGroundingProvenance(
    val sourceKind: NutrientSourceKind = NutrientSourceKind.MODEL_ESTIMATE,
    /** Stable ID in the source namespace (FDC id, OFF barcode, history favoriteKey, …). */
    val sourceId: String? = null,
    val sourceName: String? = null,
    val nutrientBasis: NutrientBasis = NutrientBasis.ABSOLUTE,
    val datasetVersion: String? = null,
    val retrievedAtEpochMs: Long? = null,
    /** Free-text evidence: OCR span, history match, visual cue, etc. */
    val identityEvidence: String? = null,
    val portionEvidence: String? = null,
    val identityConfirmed: Boolean = false,
    val portionConfirmed: Boolean = false,
    val userCorrected: Boolean = false,
    /** Component-level breakdown when the meal was grounded from multiple foods. */
    val components: List<GroundedComponentProvenance> = emptyList(),
    val validationNotes: List<String> = emptyList(),
)

@Serializable
data class GroundedComponentProvenance(
    val name: String,
    val grams: Double,
    val sourceKind: NutrientSourceKind,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val candidateRank: Int? = null,
    val matchedBy: String? = null,
)

/**
 * A retrieval candidate offered to the model/user before nutrient totals are computed.
 */
@Serializable
data class GroundingCandidate(
    val sourceKind: NutrientSourceKind,
    val sourceId: String,
    val displayName: String,
    val score: Double,
    val brand: String? = null,
    val foodCategory: String? = null,
    /** USDA `data_type` (`survey_fndds_food` / `foundation_food`) when from the offline index. */
    val dataType: String? = null,
    /** True when energy (kcal) is missing in the source row — do not auto-scale as 0 kcal. */
    val incompleteEnergy: Boolean = false,
    /** Per-100g macros when available (USDA / OFF). */
    val caloriesPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val servingSizeGrams: Double? = null,
    val matchedBy: String? = null,
    val datasetVersion: String? = null,
)

/**
 * One recognized food component from the recognition pass (before grounding).
 */
@Serializable
data class RecognizedFoodComponent(
    val name: String,
    val brand: String? = null,
    val preparation: String? = null,
    /** Estimated edible grams for this component; null when unknown. */
    val estimatedGrams: Double? = null,
    val portionHint: String? = null,
    val barcode: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
)

@Serializable
data class FoodRecognitionResult(
    val mealName: String,
    val emoji: String? = null,
    val components: List<RecognizedFoodComponent>,
    val notes: String? = null,
)

/**
 * Separate confidence facets — never collapse into a single score.
 */
@Serializable
data class GroundingConfidence(
    /** 0..1 how sure we are about food identity. */
    val identity: Double = 0.0,
    /** 0..1 how sure we are about portion/grams. */
    val portion: Double = 0.0,
    /** 0..1 trust in the nutrient source (USDA/OFF/history vs model). */
    val nutrientSource: Double = 0.0,
)
