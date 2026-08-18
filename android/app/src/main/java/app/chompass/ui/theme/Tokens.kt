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
 * values: [Muted] follows iOS `.secondary` (0.6, the app's design source of truth);
 * [Faint] and [Disabled] are the dominant existing alphas. Per-screen migration
 * canonicalizes stray values (0.5/0.55/0.58 → [Muted], 0.42/0.48 → [Faint], 0.35 →
 * [Disabled]) in batches with screenshot-diff review (see Phase 2.1 in the UI audit
 * plan). Scrim/overlay alphas (0.06–0.3) and emphasis tones (0.62–0.94) are
 * intentionally not tokens.
 */
object AppTextOpacity {
    /** Secondary/helper text — captions, footnotes, icon tints (iOS `.secondary`). */
    const val Muted = 0.6f

    /** Tertiary/de-emphasized labels. */
    const val Faint = 0.45f

    /** Disabled or inactive controls and icons. */
    const val Disabled = 0.4f
}
