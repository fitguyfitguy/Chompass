package app.chompass.models

import app.chompass.R
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.toMicronutrients
import kotlin.math.roundToInt

/**
 * Which entry-level macro the AI fill-missing scan may ask for. Entry-level
 * zeros are the Mealie-import / logged-by-name signature (0 already renders
 * as an em dash, so a fill is display-equivalent to missing → filled).
 * Legit zeros (black coffee) count as gaps by design: the scan never
 * auto-fills, the user drives it from the edit sheet.
 */
enum class GapMacroField(val labelRes: Int) {
    CALORIES(R.string.unit_kcal),
    PROTEIN(R.string.nutrition_label_protein),
    CARBS(R.string.nutrition_label_carbs),
    FAT(R.string.nutrition_label_fat),
}

/** Result of the fill-missing scan over one entry. Pure data, no UI. */
data class EntryGapReport(
    val missingMacros: Set<GapMacroField>,
    val missingMicros: Set<MicronutrientField>,
    val zeroMacroConstituents: List<FoodConstituent>,
) {
    val hasGaps: Boolean
        get() = missingMacros.isNotEmpty() ||
            missingMicros.isNotEmpty() ||
            zeroMacroConstituents.isNotEmpty()
}

/**
 * A constituent row the AI may fill: a named row whose four macros are all
 * zero — the shape [MealieRecipeMapper.parseIngredients] writes for every
 * parsed ingredient. Partial zeros are legit keto-style rows and stay
 * untouched; blank-name rows can never be matched back to an analysis row.
 */
private fun FoodConstituent.isFillableRow(): Boolean =
    name.isNotBlank() && calories == 0 && protein == 0.0 && carbs == 0.0 && fat == 0.0

/** Scan an entry for values the Fill-missing action may ask the AI for. */
fun FoodEntry.gapReport(): EntryGapReport {
    val missingMacros = buildSet {
        if (calories == 0) add(GapMacroField.CALORIES)
        if (protein == 0.0) add(GapMacroField.PROTEIN)
        if (carbs == 0.0) add(GapMacroField.CARBS)
        if (fat == 0.0) add(GapMacroField.FAT)
    }
    val micros = MicronutrientValues.from(this)
    val missingMicros = MicronutrientField.entries.filterTo(mutableSetOf()) { micros[it] == null }
    val zeroMacroConstituents = constituents.filter { it.isFillableRow() }
    return EntryGapReport(missingMacros, missingMicros, zeroMacroConstituents)
}

/**
 * Merge an AI analysis into an entry, writing ONLY missing values: entry
 * macros where the stored value is zero, micros where the stored field is
 * null, and all-zero constituent rows matched by normalized name. Stored
 * non-zero / non-null values are never overwritten; analysis constituents
 * without a stored name match are dropped, never appended.
 *
 * When [FoodEntry.servingSizeGrams] and [FoodAnalysis.servingSizeGrams] are
 * both known and differ, every written value is scaled by
 * entryGrams / analysisGrams (micros at 1-decimal, like
 * [MicronutrientValues.scaled]). The micros composition signature is
 * re-stamped after a successful fill; when nothing was fillable the entry
 * is returned unchanged.
 */
fun FoodEntry.fillMissingFrom(analysis: FoodAnalysis): FoodEntry {
    val entryGrams = servingSizeGrams
    val analysisGrams = analysis.servingSizeGrams
    val gramsFactor = if (entryGrams != null && analysisGrams != null && analysisGrams > 0.0) {
        entryGrams / analysisGrams
    } else {
        1.0
    }
    fun scaleG(v: Double): Double = kotlin.math.round(v * gramsFactor * 10) / 10.0

    var changed = false

    var calories = this.calories
    var protein = this.protein
    var carbs = this.carbs
    var fat = this.fat
    if (calories == 0 && analysis.calories != 0) {
        calories = (analysis.calories * gramsFactor).roundToInt()
        changed = true
    }
    if (protein == 0.0 && analysis.protein != 0.0) {
        protein = scaleG(analysis.protein)
        changed = true
    }
    if (carbs == 0.0 && analysis.carbs != 0.0) {
        carbs = scaleG(analysis.carbs)
        changed = true
    }
    if (fat == 0.0 && analysis.fat != 0.0) {
        fat = scaleG(analysis.fat)
        changed = true
    }

    // Field-wise micro merge: write only into null fields, never applyTo the
    // raw analysis (that blind-overwrites stored values with nulls).
    val storedMicros = MicronutrientValues.from(this)
    val scaledAnalysisMicros =
        if (gramsFactor == 1.0) analysis.toMicronutrients() else analysis.toMicronutrients().scaled(gramsFactor)
    var mergedMicros = storedMicros
    for (field in MicronutrientField.entries) {
        if (storedMicros[field] == null) {
            val incoming = scaledAnalysisMicros[field]
            if (incoming != null) {
                mergedMicros = mergedMicros.with(field, incoming)
                changed = true
            }
        }
    }

    // Constituent rows: match by normalized name (favoriteKey convention),
    // fill each stored all-zero row from the first unmatched analysis row
    // with the same name. Unmatched analysis rows are dropped.
    val analysisRowsByName = HashMap<String, ArrayDeque<FoodConstituent>>()
    for (row in analysis.constituents) {
        analysisRowsByName.getOrPut(row.name.trim().lowercase()) { ArrayDeque() }.addLast(row)
    }
    val filledConstituents = if (constituents.isEmpty()) {
        constituents
    } else {
        constituents.map { row ->
            val queue = analysisRowsByName[row.name.trim().lowercase()]
            val candidate = queue?.removeFirstOrNull()
            if (candidate != null && row.isFillableRow() &&
                (candidate.calories != 0 || candidate.protein != 0.0 || candidate.carbs != 0.0 || candidate.fat != 0.0)
            ) {
                changed = true
                row.copy(
                    calories = (candidate.calories * gramsFactor).roundToInt(),
                    protein = scaleG(candidate.protein),
                    carbs = scaleG(candidate.carbs),
                    fat = scaleG(candidate.fat),
                )
            } else {
                row
            }
        }
    }

    if (!changed) return this

    val base = copy(
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        constituents = filledConstituents,
    )
    return mergedMicros.applyTo(base).copy(
        microsCompositionSignature = microsCompositionSignature(filledConstituents),
    )
}
