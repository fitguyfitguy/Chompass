package app.chompass.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import app.chompass.ui.theme.ChompassTraitsDefaults

/** The [HazeState] whose sources scroll behind the bottom nav bar; null where
 *  no bar is showing (onboarding) or the host hasn't created one. */
val LocalBottomBarHaze = staticCompositionLocalOf<HazeState?> { null }

/** Marks this node as a blur source for the bottom bar when one exists. */
fun Modifier.bottomBarHazeSource(state: HazeState?): Modifier =
    if (state != null) hazeSource(state) else this

/** Alpha of the tint laid over the blur. 0.55 buried the blur entirely —
 *  light-on-light content read as a flat strip (device pass 2026-10-29);
 *  0.30 keeps the frost visible in both themes. */
const val GlassTintAlpha = 0.30f

/**
 * The style behind [Modifier.hazeGlassFill]: the blur picks up [backdrop]-colored
 * ground, [tint] laid over it at [GlassTintAlpha]; without a blur engine the
 * full [tint] over [backdrop] stands in, so the chrome stays opaque either way.
 */
fun hazeGlassStyle(tint: Color, backdrop: Color): HazeStyle {
    val solid = tint.compositeOver(backdrop)
    return HazeStyle(
        backgroundColor = backdrop,
        tints = listOf(HazeTint(solid.copy(alpha = GlassTintAlpha))),
        fallbackTint = HazeTint(solid),
    )
}

/**
 * Frosted-glass fill for chrome floating over content that scrolls beneath it:
 * that content blurred, [tint] laid on thin ([GlassTintAlpha]).
 *
 * [haze] is the [HazeState] the scrolling content marks with `hazeSource`;
 * pass null where nothing blurs (no source in scope, or a window that cannot
 * see one) and the fill degrades to solid [tint] over
 * [MaterialTheme.colorScheme.surfaceContainerLow] — the chrome stays opaque,
 * never transparent-into-nothing.
 */
@Composable
fun Modifier.hazeGlassFill(
    tint: Color,
    shape: Shape,
    haze: HazeState?,
): Modifier {
    val backdrop = MaterialTheme.colorScheme.surfaceContainerLow
    // Traits off (Appearance toggle), no blur source registered yet (empty
    // tab, pre-first-layout), or no blur engine: same solid fallback — the
    // chrome stays opaque, never transparent-into-nothing.
    if (haze == null || haze.areas.isEmpty() || !ChompassTraitsDefaults.glassBlur()) {
        return background(tint.compositeOver(backdrop), shape)
    }
    val style = remember(tint, backdrop) { hazeGlassStyle(tint, backdrop) }
    return clip(shape).hazeEffect(haze, style)
}
