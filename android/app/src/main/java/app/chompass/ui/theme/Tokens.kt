package app.chompass.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Corner-radius tokens. Values mirror the app's iOS-verbatim layouts (see
 * "Deliberate choices" in `docs/local/UI_AUDIT_PLAN.md`); names capture the role so a
 * future radius change lands everywhere at once. M3 shapes (`Shapes` in Shape.kt)
 * remain for `MaterialTheme.shapes.*` slots (4/8/12/16/28 dp).
 */
object AppRadii {
    /** 22.dp — grouped diary/meal section cards and their first/last-row rounding. */
    val SectionCard = 22.dp

    /** 18.dp — dropdown menus, coach chat bubbles, stat/activity cards. */
    val Container = 18.dp

    /** 14.dp — photo tiles, wheel-picker fields, chips, menu rows. */
    val Field = 14.dp
}

/**
 * Text-opacity tokens for on-surface/on-background text and icon tints. Canonical
 * values are the dominant existing alphas (0.55 / 0.45 / 0.4); per-screen migration
 * canonicalizes stray values (0.5/0.58 → [Muted], 0.42/0.48 → [Faint], 0.35 →
 * [Disabled]) in batches with screenshot-diff review (see Phase 2.1 in the UI audit
 * plan). Scrim/overlay alphas (0.06–0.3) are intentionally not tokens.
 */
object AppTextOpacity {
    /** Secondary/helper text — footnotes, captions, section subtitles (iOS `.secondary`-adjacent). */
    const val Muted = 0.55f

    /** Tertiary/de-emphasized labels. */
    const val Faint = 0.45f

    /** Disabled or inactive controls and icons. */
    const val Disabled = 0.4f
}
