package app.chompass.services

/**
 * Product gate for AI fill-missing (entry gaps: Home banner, FillGapsSheet,
 * Settings row, edit-sheet / favorite-editor / recipe-builder Fill missing).
 *
 * On for the 5.10.0-beta.1 test cut: the re-offer cause (unanswered zeros
 * re-offered forever) closed by the #135 answer stamping. Stable stays gated.
 * Flip this together with web FILL_GAPS_ENABLED. Do not delete the implementation.
 */
object FillGapsFeature {
    const val ENABLED: Boolean = true
}
