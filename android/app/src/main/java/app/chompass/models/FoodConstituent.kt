package app.chompass.models

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

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
    // Optional per-row micronutrients (#86), named like FoodEntry's fields.
    // Null = not estimated; they scale with the row's grams, never reconcile.
    val sugar: Double? = null,
    val addedSugar: Double? = null,
    val fiber: Double? = null,
    val saturatedFat: Double? = null,
    val monounsaturatedFat: Double? = null,
    val polyunsaturatedFat: Double? = null,
    val cholesterol: Double? = null,
    val sodium: Double? = null,
    val potassium: Double? = null,
    val transFat: Double? = null,
    val calcium: Double? = null,
    val iron: Double? = null,
    val magnesium: Double? = null,
    val zinc: Double? = null,
    val vitaminA: Double? = null,
    val vitaminC: Double? = null,
    val vitaminD: Double? = null,
    val vitaminB12: Double? = null,
    val vitaminE: Double? = null,
    val vitaminK: Double? = null,
    val folate: Double? = null,
    val omega3: Double? = null,
    /** Diary/sync/share wire only. AI constituent schema does not request or parse this. */
    val caffeine: Double? = null,
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
        ).microsScaled(factor)
    }

    /**
     * Micros ride the row's grams factor (per-100g semantics, #86): 1-dp
     * rounding, clamped >= 0. Never residual-fixed — macro reconcile owns the
     * meal totals; micros only track the row's mass.
     */
    fun microsScaled(factor: Double): FoodConstituent {
        if (factor == 1.0) return this
        fun m(v: Double?): Double? = v?.let {
            ((it * factor).coerceAtLeast(0.0) * 10.0).roundToInt() / 10.0
        }
        return copy(
            sugar = m(sugar),
            addedSugar = m(addedSugar),
            fiber = m(fiber),
            saturatedFat = m(saturatedFat),
            monounsaturatedFat = m(monounsaturatedFat),
            polyunsaturatedFat = m(polyunsaturatedFat),
            cholesterol = m(cholesterol),
            sodium = m(sodium),
            potassium = m(potassium),
            transFat = m(transFat),
            calcium = m(calcium),
            iron = m(iron),
            magnesium = m(magnesium),
            zinc = m(zinc),
            vitaminA = m(vitaminA),
            vitaminC = m(vitaminC),
            vitaminD = m(vitaminD),
            vitaminB12 = m(vitaminB12),
            vitaminE = m(vitaminE),
            vitaminK = m(vitaminK),
            folate = m(folate),
            omega3 = m(omega3),
            caffeine = m(caffeine),
        )
    }
}
