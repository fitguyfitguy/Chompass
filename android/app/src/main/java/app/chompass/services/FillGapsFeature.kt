package app.chompass.services

/**
 * Product gate for AI fill-missing (entry gaps: Home banner, FillGapsSheet,
 * Settings row, edit-sheet / favorite-editor / recipe-builder Fill missing).
 *
 * Disabled again for the 5.10.0 stable cut (2026-10-21): the fix rides
 * v5.10.0-beta.1 while the re-offer behavior beds in. The beta re-opened the
 * gate after the #135 answer stamping closed the re-offer cause.
 * Flip this together with web FILL_GAPS_ENABLED. Do not delete the implementation.
 */
object FillGapsFeature {
    const val ENABLED: Boolean = false
}
