package app.chompass.services

/**
 * Product gate for AI fill-missing (entry gaps: Home banner, FillGapsSheet,
 * Settings row, edit-sheet / favorite-editor / recipe-builder Fill missing).
 *
 * Enabled since the fill-missing rework: kind-scoped asks, grams-adapter,
 * containment name matching, opt-in row diffs, "No new values returned"
 * honesty, banner forever-opt-out (sheet action + Settings toggle), and
 * Recalculate as an AI re-estimate over the edited mix.
 */
object FillGapsFeature {
    const val ENABLED: Boolean = true
}
