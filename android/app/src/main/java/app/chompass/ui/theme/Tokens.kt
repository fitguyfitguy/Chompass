package app.chompass.ui.theme

import androidx.compose.ui.unit.dp

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
