package org.codeberg.fitguy.nofud.services.grounding

/**
 * Product gate for optional grounded food entry.
 *
 * Keep **false** until accuracy / UX readiness criteria in `docs/GROUNDED_ENTRY.md`
 * are met. The offline USDA SQLite ships in **debug** APKs only (`src/debug/assets`);
 * release builds omit it. Entry UI stays hidden while this flag is false.
 */
object GroundedEntryFeature {
    const val ENABLED: Boolean = false
}
