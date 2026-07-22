package org.codeberg.fitguy.nofud.models

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic plausibility checks for grounded nutrient values.
 * These never invent numbers — they only flag or lightly correct unit mistakes.
 */
object GroundingValidator {

    data class ValidationResult(
        val notes: List<String> = emptyList(),
        /** Corrected calories when an obvious kcal/kJ mix-up is detected. */
        val correctedCalories: Int? = null,
        /** Corrected sodium in milligrams when an obvious grams→mg mistake is detected. */
        val correctedSodiumMg: Double? = null,
    )

    /**
     * Atwater energy from macros (kcal). Protein/carbs 4 kcal/g, fat 9 kcal/g.
     * Fiber and alcohol are ignored for this consumer-facing check.
     */
    fun atwaterKcal(proteinG: Double, carbsG: Double, fatG: Double): Double =
        4.0 * proteinG + 4.0 * carbsG + 9.0 * fatG

    fun validateServing(
        analysisName: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        servingGrams: Double,
        sodiumMg: Double? = null,
        caloriesPer100g: Double? = null,
        proteinPer100g: Double? = null,
        carbsPer100g: Double? = null,
        fatPer100g: Double? = null,
    ): ValidationResult {
        val notes = mutableListOf<String>()
        var correctedCalories: Int? = null
        var correctedSodium: Double? = null

        if (servingGrams <= 0) {
            notes += "Serving mass must be positive (got $servingGrams g for $analysisName)."
        }
        if (protein < 0 || carbs < 0 || fat < 0 || calories < 0) {
            notes += "Negative nutrient values are invalid."
        }

        // Per-100g bounds for common whole foods (loose — flags data bugs, not cuisine outliers).
        fun checkPer100(label: String, value: Double?, max: Double) {
            if (value != null && value > max) {
                notes += "$label per 100 g looks implausible ($value > $max)."
            }
            if (value != null && value < 0) {
                notes += "$label per 100 g is negative."
            }
        }
        checkPer100("Calories", caloriesPer100g, 950.0)
        checkPer100("Protein", proteinPer100g, 100.0)
        checkPer100("Carbs", carbsPer100g, 100.0)
        checkPer100("Fat", fatPer100g, 100.0)

        val atwater = atwaterKcal(protein, carbs, fat)
        if (calories > 0 && atwater > 0) {
            val ratio = calories / atwater
            // Classic kcal listed as kJ (~×4.184) — offer a correction hint.
            if (ratio in 3.5..5.0) {
                val fixed = (calories / 4.184).roundToInt()
                notes += "Calories look like kilojoules (reported $calories vs Atwater ${atwater.roundToInt()} kcal)."
                correctedCalories = fixed
            } else if (abs(calories - atwater) / maxOf(atwater, 1.0) > 0.35 && servingGrams >= 5) {
                notes += "Calories (${calories}) diverge from Atwater estimate (${atwater.roundToInt()} kcal) by >35%."
            }
        }

        if (sodiumMg != null) {
            if (sodiumMg < 0) {
                notes += "Sodium is negative."
            } else if (sodiumMg > 0 && sodiumMg < 0.5 && servingGrams >= 20) {
                // Likely still in grams (e.g. 0.12 g sodium left unconverted).
                notes += "Sodium ($sodiumMg) looks like grams rather than milligrams."
                correctedSodium = sodiumMg * 1000.0
            } else if (sodiumMg > 15_000) {
                notes += "Sodium ($sodiumMg mg) exceeds a plausible single-serving upper bound."
            }
        }

        return ValidationResult(
            notes = notes,
            correctedCalories = correctedCalories,
            correctedSodiumMg = correctedSodium,
        )
    }

    /** Detect duplicate component names (case-insensitive) that would double-count. */
    fun duplicateComponentNames(names: List<String>): List<String> {
        val seen = mutableMapOf<String, Int>()
        for (name in names) {
            val key = name.trim().lowercase()
            if (key.isEmpty()) continue
            seen[key] = (seen[key] ?: 0) + 1
        }
        return seen.filter { it.value > 1 }.keys.toList()
    }
}
