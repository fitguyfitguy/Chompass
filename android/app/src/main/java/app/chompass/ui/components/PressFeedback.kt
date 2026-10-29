package app.chompass.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.chompass.ui.theme.AppMotion

/**
 * Sinks the node to [pressedScale] while [interactionSource] reports a press,
 * springing back with [AppMotion.pressSpring] on release.
 *
 * The animated value is read inside the `graphicsLayer` block, so a held press
 * repaints the layer instead of recomposing the composition every frame.
 * Draw-only: layout and touch target stay at full size.
 *
 * Convention for clickable surfaces: hoist one source and share it —
 * `val src = remember { MutableInteractionSource() }`, then pass the same
 * instance to `clickable(interactionSource = src, indication = LocalIndication.current)`
 * (default ripple stays) and to `.pressScale(src)`. Rows that set
 * `indication = null` (iOS-look rows) keep the null indication and add only
 * the scale.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "pressScale",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}
