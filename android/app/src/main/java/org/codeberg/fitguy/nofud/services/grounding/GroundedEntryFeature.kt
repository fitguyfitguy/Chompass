package org.codeberg.fitguy.nofud.services.grounding

/**
 * Product gate for optional grounded food entry.
 *
 * Keep **false** until accuracy / UX readiness criteria in `docs/GROUNDED_ENTRY.md`
 * are met. Code and offline USDA assets may still ship; the Add-food tile and
 * entry sheets stay hidden.
 */
object GroundedEntryFeature {
    const val ENABLED: Boolean = false
}
