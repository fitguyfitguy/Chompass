package app.chompass.services.ai

/**
 * Validated subset of food-analysis fields observed while a response is still
 * streaming or after the primary parse. Only complete, type-checked JSON values
 * are ever present — incomplete strings/numbers stay private in the assembler.
 */
data class PartialFoodAnalysis(
    val name: String? = null,
    val emoji: String? = null,
    val calories: Int? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingSizeGrams: Double? = null,
    val fiber: Double? = null,
    /** Count of optional micronutrient keys that have arrived with a numeric value. */
    val micronutrientCount: Int = 0,
    val hasUnitOptions: Boolean = false,
    /** True while the provider stream is still open; false for post-parse previews. */
    val streaming: Boolean = true,
) {
    val hasAnyField: Boolean
        get() = name != null || emoji != null || calories != null ||
            protein != null || carbs != null || fat != null ||
            servingSizeGrams != null || fiber != null ||
            micronutrientCount > 0 || hasUnitOptions

    fun toPreviewAnalysis(): FoodAnalysis? {
        val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
        return FoodAnalysis(
            name = resolvedName,
            calories = calories ?: 0,
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fat = fat ?: 0.0,
            servingSizeGrams = servingSizeGrams ?: 100.0,
            emoji = emoji,
            fiber = fiber,
        )
    }

    companion object {
        fun fromComplete(analysis: FoodAnalysis, streaming: Boolean = false): PartialFoodAnalysis {
            val micros = listOf(
                analysis.sugar, analysis.addedSugar, analysis.fiber, analysis.saturatedFat,
                analysis.monounsaturatedFat, analysis.polyunsaturatedFat, analysis.cholesterol,
                analysis.sodium, analysis.potassium, analysis.transFat, analysis.calcium,
                analysis.iron, analysis.magnesium, analysis.zinc, analysis.vitaminA,
                analysis.vitaminC, analysis.vitaminD, analysis.vitaminB12, analysis.vitaminE,
                analysis.vitaminK, analysis.folate, analysis.omega3,
            )
            return PartialFoodAnalysis(
                name = analysis.name,
                emoji = analysis.emoji,
                calories = analysis.calories,
                protein = analysis.protein,
                carbs = analysis.carbs,
                fat = analysis.fat,
                servingSizeGrams = analysis.servingSizeGrams,
                fiber = analysis.fiber,
                micronutrientCount = micros.count { it != null },
                hasUnitOptions = analysis.servingUnitOptions.isNotEmpty(),
                streaming = streaming,
            )
        }
    }
}
