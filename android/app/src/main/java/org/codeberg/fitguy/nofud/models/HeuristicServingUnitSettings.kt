package org.codeberg.fitguy.nofud.models

import kotlinx.serialization.Serializable

/** Per-rule user customization: disable a rule, and/or override its default grams-per-unit. */
@Serializable
data class HeuristicRuleOverride(
    val enabled: Boolean = true,
    val gramsPerUnit: Double? = null
)

/**
 * User overrides for [ServingUnitHeuristics.RULES], keyed by
 * [ServingUnitHeuristicRule.id]. A rule with no entry here uses its built-in
 * default (enabled, [ServingUnitHeuristicRule.defaultGramsPerUnit]).
 */
@Serializable
data class HeuristicServingUnitSettings(
    val overrides: Map<String, HeuristicRuleOverride> = emptyMap()
) {
    companion object {
        val Default = HeuristicServingUnitSettings()
    }
}
