package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Chompass brand palette, transcribed from `docs/brand/logo-system.html`.
 *
 * Two ramps carry the identity: warm **peach** (interaction, the wordmark's bitten "o",
 * the ring's light end) and **fig** (the plum neutrals and the default accent). The mark's
 * ring gradient is [Ripened] on light surfaces and [RipenedGlow] on dark ones.
 *
 * Keep `scripts/generate_icons.py` and the website/PWA `--teal*` tokens in sync with the
 * values here; the study is the source of truth for all of them.
 */
object BrandPalette {
    // Peach — warm ramp: cream tints through the ring's orange mid to a deep fig-red.
    val Peach100 = Color(0xFFFFF0E4)
    val Peach200 = Color(0xFFFFD9C2)
    val Peach300 = Color(0xFFFFB994)
    val Peach400 = Color(0xFFFF9A6C)
    val Peach500 = Color(0xFFFF7A4D)
    val Peach600 = Color(0xFFE85D3A)
    val Peach700 = Color(0xFFC44428)
    val Peach800 = Color(0xFF8C2E18)
    val Peach900 = Color(0xFF3D1608)

    /**
     * Peach accent fill for [AppThemeColor.PEACH]: [Peach700] nudged one step darker so a
     * blush `onPrimary` label clears WCAG AA (4.57:1 instead of 4.49:1). The study's
     * [Peach700] remains the ramp value used by the wordmark's bitten "o".
     */
    val PeachAccent = Color(0xFFC24327)

    // Fig — the plum ramp: light blush through the default accent to near-black plum.
    val Fig100 = Color(0xFFF7E9F0)
    val Fig200 = Color(0xFFEBCDDD)
    val Fig300 = Color(0xFFD6A2BE)
    val Fig400 = Color(0xFFB8759C)
    val Fig500 = Color(0xFF8F4B78)
    val Fig600 = Color(0xFF6E2F5C)
    val Fig700 = Color(0xFF512046)
    val Fig800 = Color(0xFF371430)
    val Fig900 = Color(0xFF1F0C1B)

    /** Neutral ink for text and the needle's dark facet. */
    val Ink = Color(0xFF1C131A)

    /** Warm light background (launcher tile, splash label, site cream). */
    val Cream = Color(0xFFFFF6EC)

    /** Warm light text on plum surfaces. */
    val Blush = Color(0xFFFBF0F4)

    /** Deepest warm dark background. */
    val Plum = Color(0xFF170E16)

    /** Ring gradient on light surfaces: cream-peach → orange → fig-red → fig. */
    val Ripened: List<Pair<Float, Color>> = listOf(
        0f to Peach200,
        0.32f to Peach400,
        0.68f to Color(0xFFE0685E),
        1f to Color(0xFF8C2E4A),
    )

    /** Ring gradient on dark surfaces: the same bite, lit for a plum ground. */
    val RipenedGlow: List<Pair<Float, Color>> = listOf(
        0f to Color(0xFFFFE8D6),
        0.38f to Color(0xFFFFA07A),
        0.70f to Color(0xFFD9555C),
        1f to Fig900,
    )

    /** Fig ramp for fig-accented fills (macro bars, chart accents). */
    val FigGradient: List<Pair<Float, Color>> = listOf(0f to Fig300, 1f to Fig800)
}
