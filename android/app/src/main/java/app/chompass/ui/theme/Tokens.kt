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

    /** 28.dp — modal sheet/dialog chrome and large capsule primaries. */
    val Sheet = 28.dp

    /** 24.dp — sheet pill cards, large action pills, camera action buttons. */
    val PillCard = 24.dp

    /** 20.dp — prompt chips, capsule buttons, note boxes (canonical; absorbs the old 19.dp drift). */
    val Pill = 20.dp

    /** 16.dp — cards, list rows, media tiles, onboarding cards. */
    val Card = 16.dp

    /** 12.dp — photo thumbnails, compact rows, transcript boxes, code blocks. */
    val Tile = 12.dp

    /** 10.dp — segmented tab tracks and small indicator chips. */
    val Track = 10.dp

    /** 8.dp — small chips, selection squares, inner tiles. */
    val Chip = 8.dp
}

/**
 * Vertical/horizontal spacing steps. Values mirror the app's iOS-verbatim
 * layouts (see "Deliberate choices" in `docs/local/UI_AUDIT_PLAN.md`); the PWA
 * mirrors the same steps as `--space-*` custom properties in `web/app/css/main.css`.
 */
object AppSpacing {
    /** 4.dp — icon-to-label gaps, tight intra-row spacing. */
    val Xs = 4.dp

    /** 8.dp — intra-group gaps, chip padding. */
    val Sm = 8.dp

    /** 12.dp — card-internal padding. */
    val Md = 12.dp

    /** 16.dp — screen gutters, section padding. */
    val Lg = 16.dp

    /** 20.dp — sheet gutters, hero padding. */
    val Xl = 20.dp

    /** 24.dp — section separation, large block gaps. */
    val Xxl = 24.dp
}

/**
 * Text-opacity tokens for on-surface/on-background text and icon tints. Canonical
 * values: [Muted] follows iOS `.secondary` (0.6, the app's design source of truth);
 * [Faint] and [Disabled] are the dominant existing alphas. Per-screen migration
 * canonicalizes stray values (0.5/0.55/0.58 → [Muted], 0.42/0.48 → [Faint], 0.35 →
 * [Disabled]) in batches with screenshot-diff review (see Phase 2.1 in the UI audit
 * plan). Scrim/overlay alphas (0.06–0.3) stay raw values. The emphasis band
 * (0.62–0.94) is tokenized below (2026-09-24, PLAN_5.2.0 §1b).
 */
object AppTextOpacity {
    /** Secondary/helper text — captions, footnotes, icon tints (iOS `.secondary`). */
    const val Muted = 0.6f

    /** Tertiary/de-emphasized labels. */
    const val Faint = 0.45f

    /** Disabled or inactive controls and icons. */
    const val Disabled = 0.4f

    /** Captions, hints, faint icon tints. */
    const val Subtle = 0.62f

    /** Supporting text, tertiary actions, dim labels. */
    const val Secondary = 0.7f

    /** Emphasized labels and tints. */
    const val Emphasized = 0.8f

    /** Primary body text and meal names. */
    const val Strong = 0.85f

    /** Almost-full-emphasis titles. */
    const val NearFull = 0.94f
}
