package app.chompass.services.ai

import app.chompass.models.FoodConstituent
import app.chompass.models.ServingUnitOption
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bounded constituent reconciliation matching
 * `docs/benchmarks/food_accuracy/reconcile_constituents.py`.
 *
 * Top-level meal nutrition is authoritative. Constituents are scaled to match
 * or dropped when the relative mismatch exceeds [MAX_REL_ERROR].
 */
object ConstituentReconcile {
    const val RECONCILE_TOL = 0.05
    const val MAX_REL_ERROR = 0.50
    const val MAX_CONSTITUENTS = 12

    fun reconcile(
        analysis: FoodAnalysis,
        constituents: List<FoodConstituent> = analysis.constituents,
        maxRelError: Double = MAX_REL_ERROR,
    ): FoodAnalysis {
        val rows = constituents
            .asSequence()
            .filter { it.name.isNotBlank() && it.servingSizeGrams > 0 && it.calories >= 0 }
            .filter { it.protein >= 0 && it.carbs >= 0 && it.fat >= 0 }
            .take(MAX_CONSTITUENTS)
            .toList()
        if (rows.isEmpty() || analysis.servingSizeGrams == null || analysis.servingSizeGrams <= 0) {
            return analysis.copy(constituents = emptyList())
        }

        val mealServing = analysis.servingSizeGrams
        val sumG = rows.sumOf { it.servingSizeGrams }
        val sumCal = rows.sumOf { it.calories.toDouble() }
        val sumP = rows.sumOf { it.protein }
        val sumC = rows.sumOf { it.carbs }
        val sumF = rows.sumOf { it.fat }

        val gErr = relError(sumG, mealServing)
        val mErr = macroRelError(
            sumCal, sumP, sumC, sumF,
            analysis.calories.toDouble(), analysis.protein, analysis.carbs, analysis.fat,
        )
        if (gErr == null || mErr == null || gErr > maxRelError || mErr > maxRelError) {
            return analysis.copy(constituents = emptyList())
        }

        var scaled = scaleRows(rows, analysis)
        scaled = fixRoundingResiduals(scaled, analysis)
        if (scaled.any {
                it.servingSizeGrams <= 0 || it.calories < 0 ||
                    it.protein < 0 || it.carbs < 0 || it.fat < 0
            }
        ) {
            return analysis.copy(constituents = emptyList())
        }
        return analysis.copy(constituents = scaled)
    }

    /** Scale every constituent when the whole-meal serving changes. */
    fun scaleAll(constituents: List<FoodConstituent>, factor: Double): List<FoodConstituent> {
        if (factor == 1.0 || constituents.isEmpty()) return constituents
        return constituents.map { it.scaled(factor) }
    }

    /**
     * Rebuild meal totals from edited constituent rows. Empty list means an
     * indivisible food — caller should keep prior aggregates.
     */
    fun aggregatesFrom(constituents: List<FoodConstituent>): Aggregate? {
        if (constituents.isEmpty()) return null
        return Aggregate(
            calories = constituents.sumOf { it.calories },
            protein = round1(constituents.sumOf { it.protein }),
            carbs = round1(constituents.sumOf { it.carbs }),
            fat = round1(constituents.sumOf { it.fat }),
            servingSizeGrams = round1(constituents.sumOf { it.servingSizeGrams }),
        )
    }

    data class Aggregate(
        val calories: Int,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val servingSizeGrams: Double,
    )

    private fun scaleRows(rows: List<FoodConstituent>, meal: FoodAnalysis): List<FoodConstituent> {
        val mealServing = meal.servingSizeGrams ?: return rows
        val grams = scaleDoubles(rows.map { it.servingSizeGrams }, mealServing)
        val cals = scaleInts(rows.map { it.calories }, meal.calories)
        val protein = scaleDoubles(rows.map { it.protein }, meal.protein)
        val carbs = scaleDoubles(rows.map { it.carbs }, meal.carbs)
        val fat = scaleDoubles(rows.map { it.fat }, meal.fat)
        return rows.indices.map { i ->
            val g = round1(grams[i])
            val selected = rows[i].selectedServingUnit?.let { id ->
                ServingUnitOption.optionMatching(id, rows[i].servingUnitOptions)
            }
            rows[i].copy(
                servingSizeGrams = g,
                calories = cals[i],
                protein = round1(protein[i]),
                carbs = round1(carbs[i]),
                fat = round1(fat[i]),
                selectedServingQuantity = selected
                    ?.takeUnless { it.isGramUnit }
                    ?.quantityFor(g)
                    ?: rows[i].selectedServingQuantity,
            )
        }
    }

    private fun fixRoundingResiduals(
        rows: List<FoodConstituent>,
        meal: FoodAnalysis,
    ): List<FoodConstituent> {
        if (rows.isEmpty()) return rows
        val mealServing = meal.servingSizeGrams ?: return rows
        val head = rows.dropLast(1)
        val last = rows.last()
        return head + last.copy(
            servingSizeGrams = round1(mealServing - head.sumOf { it.servingSizeGrams }),
            protein = round1(meal.protein - head.sumOf { it.protein }),
            carbs = round1(meal.carbs - head.sumOf { it.carbs }),
            fat = round1(meal.fat - head.sumOf { it.fat }),
            calories = meal.calories - head.sumOf { it.calories },
        )
    }

    private fun scaleDoubles(values: List<Double>, target: Double): List<Double> {
        val total = values.sum()
        if (total <= 0.0) {
            if (target <= 0.0) return values
            val each = target / values.size
            val out = MutableList(values.size) { each }
            out[out.lastIndex] = target - each * (values.size - 1)
            return out
        }
        val factor = target / total
        val out = values.map { it * factor }.toMutableList()
        out[out.lastIndex] = target - out.dropLast(1).sum()
        return out
    }

    private fun scaleInts(values: List<Int>, target: Int): List<Int> {
        val total = values.sum().toDouble()
        if (total <= 0.0) {
            if (target <= 0) return values
            val each = (target.toDouble() / values.size).roundToInt()
            val out = MutableList(values.size) { each }
            out[out.lastIndex] = target - each * (values.size - 1)
            return out
        }
        val factor = target / total
        val out = values.map { (it * factor).roundToInt() }.toMutableList()
        out[out.lastIndex] = target - out.dropLast(1).sum()
        return out
    }

    private fun relError(sum: Double, total: Double): Double? {
        if (total <= 0.0) return if (sum == 0.0) null else Double.POSITIVE_INFINITY
        return abs(sum - total) / total
    }

    private fun macroRelError(
        sumCal: Double,
        sumP: Double,
        sumC: Double,
        sumF: Double,
        mealCal: Double,
        mealP: Double,
        mealC: Double,
        mealF: Double,
    ): Double? {
        val denom = abs(mealCal) + abs(mealP) + abs(mealC) + abs(mealF)
        if (denom <= 0.0) {
            return if (sumCal + sumP + sumC + sumF == 0.0) null else Double.POSITIVE_INFINITY
        }
        val err = abs(sumCal - mealCal) + abs(sumP - mealP) + abs(sumC - mealC) + abs(sumF - mealF)
        return err / denom
    }

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
}
