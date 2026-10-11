package app.chompass.services

/**
 * Product gate for AI fill-missing (entry gaps: Home banner, FillGapsSheet,
 * Settings row, edit-sheet / favorite-editor / recipe-builder Fill missing).
 *
 * **WIP — disabled for release.** The engine, scan, sheet, and merge code stay
 * compiled and unit-tested; this flag only hides every user-facing entry
 * point. Flip to true when the fill-missing device pass is green.
 */
object FillGapsFeature {
    const val ENABLED: Boolean = false
}
