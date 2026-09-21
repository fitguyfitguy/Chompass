package app.chompass.ui.home

import app.chompass.models.FoodConstituent
import app.chompass.models.MicronutrientValues
import app.chompass.services.ai.ConstituentReconcile
import kotlin.math.roundToInt
/**
 * Shared math for food entry edit sheets ([FoodResultSheet], [EditFoodEntrySheet]).
 *
 * All nutrient values are stored in base (per-100 g) units and scaled to the
 * logged serving size for display and editing. This class was extracted after
 * the Codeberg #10 serving-scaling bug class: the helper block was copy-pasted
 * verbatim between the two sheets, so a fix applied to one sheet could miss
 * the other. Keep all scaling/parsing math here so the two sheets cannot
 * drift.
 *
 * @param scale serving scale factor (logged grams / base grams), see
 *   [ServingUnitOption.servingScale].
 * @param emDashText localized em dash used for missing values.
 */
class FoodEntryEditMath(
    private val scale: Double,
    private val emDashText: String,
) {
    /** Scale an integer base value (calories) to the logged serving. */
    fun scaledInt(v: Int) = (v * scale).roundToInt()

    /** Scale a base macro value to the logged serving. */
    fun scaledMacro(v: Double) = v * scale

    /** Scale a base micro value to the logged serving, rounded to 0.1. */
    fun scaledD(v: Double?) = v?.let { ((it * scale) * 10).roundToInt() / 10.0 }

    /** Format a scaled value for display; em dash when missing. */
    fun displayD(v: Double?) = v?.let { String.format("%.1f", it) } ?: emDashText

    /** Format a scaled value for an editable text field; empty when missing. */
    fun editD(v: Double?) = v?.let { String.format("%.1f", it) }.orEmpty()

    /** Parse a decimal text field into a non-negative Double?, null when blank/invalid. */
    fun decimalValue(text: String): Double? = parseDecimalValue(text)

    /** Convert an edited display value back to base units (0.0 when blank/invalid). */
    fun baseDoubleFromText(text: String): Double = (parseDecimalValue(text) ?: 0.0) / scale.coerceAtLeast(0.0001)

    /** Convert an edited display value back to base units; null when blank/invalid. */
    fun baseOptionalFromText(text: String): Double? = parseDecimalValue(text)?.let { it / scale.coerceAtLeast(0.0001) }

    internal fun bakeScale(
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        micros: MicronutrientValues,
        constituents: List<FoodConstituent>,
        servingGrams: Double,
    ): BakedNutritionEdit = BakedNutritionEdit(
        calories = scaledInt(calories),
        protein = scaledMacro(protein),
        carbs = scaledMacro(carbs),
        fat = scaledMacro(fat),
        micros = micros.scaled(scale),
        constituents = ConstituentReconcile.scaleAll(constituents, scale),
        baseServingGrams = servingGrams,
    )
}

/** Snapshot after baking the current scale into the edit buffers (unlock). */
internal data class BakedNutritionEdit(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val micros: MicronutrientValues,
    val constituents: List<FoodConstituent>,
    val baseServingGrams: Double,
)

/**
 * Parse a decimal text field into a non-negative Double?, null when blank/invalid.
 *
 * Top-level so non-scaling call sites (e.g. [HomeDialogs] micro editors) share
 * the same parsing rule as the edit sheets.
 */
fun parseDecimalValue(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }
