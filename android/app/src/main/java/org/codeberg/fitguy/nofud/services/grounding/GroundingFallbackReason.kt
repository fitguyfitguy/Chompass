package org.codeberg.fitguy.nofud.services.grounding

/**
 * Typed outcomes when a component cannot be scaled from a database row.
 * Surfaces in provenance / logs so estimate fallbacks are never silent.
 */
enum class GroundingFallbackReason {
    REJECT_TO_ESTIMATE,
    NO_MATCH,
    INVALID_SOURCE_ID,
    INCOMPLETE_ENERGY,
    PROVIDER_FAILURE,
    UNRESOLVED_PORTION,
    ON_DEVICE_DETERMINISTIC,
}
