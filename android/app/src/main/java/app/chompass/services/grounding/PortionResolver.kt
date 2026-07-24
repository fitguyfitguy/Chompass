package app.chompass.services.grounding

import app.chompass.models.RecognizedFoodComponent
import app.chompass.models.ServingUnitHeuristics
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Deterministic portion resolution for grounded entry.
 *
 * Precedence:
 * 1. Explicit gram override (user correction UI)
 * 2. Recognized estimatedGrams
 * 3. quantity × unit using USDA/OFF serving, then household defaults / heuristics
 * 4. Selected candidate servingSizeGrams
 * 5. Unresolved (null) — never silently invent 100 g
 */
object PortionResolver {

    enum class Source {
        OVERRIDE,
        ESTIMATED_GRAMS,
        QUANTITY_UNIT,
        CANDIDATE_SERVING,
        HEURISTIC,
        UNRESOLVED,
    }

    data class Result(
        val grams: Double?,
        val source: Source,
        val evidence: String?,
        val needsUserConfirmation: Boolean,
    ) {
        val isResolved: Boolean get() = grams != null && grams > 0
    }

    private val HOUSEHOLD_GRAMS = mapOf(
        "g" to 1.0,
        "gram" to 1.0,
        "grams" to 1.0,
        "kg" to 1000.0,
        "ml" to 1.0,
        "l" to 1000.0,
        "liter" to 1000.0,
        "litre" to 1000.0,
        "oz" to 28.35,
        "ounce" to 28.35,
        "ounces" to 28.35,
        "lb" to 453.6,
        "pound" to 453.6,
        "tbsp" to 15.0,
        "tablespoon" to 15.0,
        "tablespoons" to 15.0,
        "tsp" to 5.0,
        "teaspoon" to 5.0,
        "teaspoons" to 5.0,
        "cup" to 240.0,
        "cups" to 240.0,
        "can" to 330.0,
        "cans" to 330.0,
        "glass" to 240.0,
        "glasses" to 240.0,
        "scoop" to 30.0,
        "scoops" to 30.0,
        "slice" to 30.0,
        "slices" to 30.0,
        "piece" to 50.0,
        "pieces" to 50.0,
        "bar" to 50.0,
        "bars" to 50.0,
        "large" to 50.0,
        "medium" to 118.0,
        "small" to 80.0,
    )

    fun resolve(
        component: RecognizedFoodComponent,
        gramOverride: Double? = null,
        candidateServingGrams: Double? = null,
        candidateServingUnit: String? = null,
    ): Result {
        if (gramOverride != null && gramOverride > 0) {
            return Result(gramOverride, Source.OVERRIDE, "user_override=${gramOverride}g", false)
        }
        component.estimatedGrams?.takeIf { it > 0 }?.let {
            return Result(it, Source.ESTIMATED_GRAMS, "estimated_grams=$it", false)
        }

        val qty = component.quantity
        val unit = component.unit?.trim()?.lowercase(Locale.US)
        if (qty != null && qty > 0 && !unit.isNullOrBlank()) {
            val fromCandidate = unitGramsFromCandidate(unit, candidateServingUnit, candidateServingGrams)
            if (fromCandidate != null) {
                return Result(
                    grams = qty * fromCandidate,
                    source = Source.QUANTITY_UNIT,
                    evidence = "quantity=${qty}×${unit} via candidate serving ${fromCandidate}g",
                    needsUserConfirmation = false,
                )
            }
            HOUSEHOLD_GRAMS[unit]?.let { per ->
                return Result(
                    grams = qty * per,
                    source = Source.QUANTITY_UNIT,
                    evidence = "quantity=${qty}×${unit} household ${per}g",
                    needsUserConfirmation = unit in setOf("slice", "slices", "piece", "pieces", "large", "medium", "small"),
                )
            }
            ServingUnitHeuristics.matchingRule(component.name)?.let { rule ->
                if (rule.unit.equals(unit, ignoreCase = true) ||
                    unit in setOf("piece", "pieces", "slice", "slices")
                ) {
                    return Result(
                        grams = qty * rule.defaultGramsPerUnit,
                        source = Source.HEURISTIC,
                        evidence = "heuristic ${rule.id}: ${qty}×${rule.defaultGramsPerUnit}g",
                        needsUserConfirmation = true,
                    )
                }
            }
        }

        // Unit-only heuristics: "1 large egg" style via portion_hint quantity parse.
        parseHintQuantity(component.portionHint)?.let { (hintQty, hintUnit) ->
            val gramsPer = unitGramsFromCandidate(hintUnit, candidateServingUnit, candidateServingGrams)
                ?: HOUSEHOLD_GRAMS[hintUnit]
                ?: ServingUnitHeuristics.matchingRule(component.name)?.defaultGramsPerUnit
            if (gramsPer != null) {
                return Result(
                    grams = hintQty * gramsPer,
                    source = Source.HEURISTIC,
                    evidence = "portion_hint ${hintQty}×${hintUnit}",
                    needsUserConfirmation = true,
                )
            }
        }

        candidateServingGrams?.takeIf { it > 0 }?.let {
            return Result(
                grams = it,
                source = Source.CANDIDATE_SERVING,
                evidence = "candidate_serving=${it}g",
                needsUserConfirmation = true,
            )
        }

        ServingUnitHeuristics.matchingRule(component.name)?.let { rule ->
            return Result(
                grams = rule.defaultGramsPerUnit,
                source = Source.HEURISTIC,
                evidence = "heuristic default ${rule.id}=${rule.defaultGramsPerUnit}g",
                needsUserConfirmation = true,
            )
        }

        return Result(
            grams = null,
            source = Source.UNRESOLVED,
            evidence = component.portionHint ?: "portion unresolved",
            needsUserConfirmation = true,
        )
    }

    fun gramsOrNull(result: Result): Double? = result.grams?.takeIf { it > 0 }

    fun formatEvidence(result: Result): String =
        result.evidence ?: result.source.name.lowercase(Locale.US)

    private fun unitGramsFromCandidate(
        unit: String,
        candidateUnit: String?,
        candidateGrams: Double?,
    ): Double? {
        if (candidateGrams == null || candidateGrams <= 0) return null
        val cu = candidateUnit?.trim()?.lowercase(Locale.US) ?: return null
        if (cu == unit || cu.contains(unit) || unit.contains(cu)) return candidateGrams
        // USDA often stores "cup" while recognition says "cups"
        if (cu.removeSuffix("s") == unit.removeSuffix("s")) return candidateGrams
        return null
    }

    private fun parseHintQuantity(hint: String?): Pair<Double, String>? {
        if (hint.isNullOrBlank()) return null
        val m = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*([a-zA-Z]+)""").find(hint.trim()) ?: return null
        val qty = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = m.groupValues[2].lowercase(Locale.US)
        if (qty <= 0) return null
        return qty to unit
    }

    fun roundGrams(grams: Double): Double = (grams * 10.0).roundToInt() / 10.0
}
