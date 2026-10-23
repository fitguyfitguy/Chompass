package app.chompass.services

/**
 * Product gate for AI fill-missing (entry gaps: Home banner, FillGapsSheet,
 * Settings row, edit-sheet / favorite-editor / recipe-builder Fill missing).
 *
 * On since 5.11.0 (2026-10-22): gaps that cannot be completed no longer
 * re-offer, and the fill list names empty ingredient rows. The 5.10 stable cut
 * kept this off while that behavior bedded in; the 5.11.0 release carries it.
 * Flip this together with web FILL_GAPS_ENABLED. Do not delete the implementation.
 */
object FillGapsFeature {
    const val ENABLED: Boolean = true
}
