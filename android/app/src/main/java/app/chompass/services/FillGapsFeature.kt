package app.chompass.services

/**
 * Product gate for AI fill-missing (entry gaps: Home banner, FillGapsSheet,
 * Settings row, edit-sheet / favorite-editor / recipe-builder Fill missing).
 *
 * Disabled for the 5.6.0 stable cut (2026-10-15). The re-offer bug is still open.
 * Flip this together with web FILL_GAPS_ENABLED. Do not delete the implementation.
 */
object FillGapsFeature {
    const val ENABLED: Boolean = false
}
