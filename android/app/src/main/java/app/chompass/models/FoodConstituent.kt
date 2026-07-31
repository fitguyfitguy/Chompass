package app.chompass.models

import kotlinx.serialization.Serializable

/**
 * One edible row inside a composite meal. When non-empty on [FoodEntry] /
 * [app.chompass.services.ai.FoodAnalysis], row grams and macros sum to the
 * meal totals (see [app.chompass.services.ai.ConstituentReconcile]).
 */
@Serializable
data class FoodConstituent(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servingSizeGrams: Double,
    val emoji: String? = null,
    val servingUnitOptions: List<ServingUnitOption> = emptyList(),
    val selectedServingUnit: String? = null,
    val selectedServingQuantity: Double? = null,
) {
    fun scaled(factor: Double): FoodConstituent {
        if (factor == 1.0) return this
        val grams = servingSizeGrams * factor
        val selected = selectedServingUnit?.let { unitId ->
            ServingUnitOption.optionMatching(unitId, servingUnitOptions)
        }
        return copy(
            calories = (calories * factor).toInt().coerceAtLeast(0),
            protein = protein * factor,
            carbs = carbs * factor,
            fat = fat * factor,
            servingSizeGrams = grams,
            selectedServingQuantity = selected
                ?.takeUnless { it.isGramUnit }
                ?.quantityFor(grams)
                ?: selectedServingQuantity?.let { it * factor },
        )
    }
}
