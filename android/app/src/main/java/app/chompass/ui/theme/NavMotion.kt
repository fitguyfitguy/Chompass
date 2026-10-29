package app.chompass.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

/**
 * Nav transition specs (Morphe §1.2), consumed by `ChompassNavHost`:
 *
 *  - **Push** (settings subscreens, detail screens): shared-Z axis — the
 *    incoming page starts at 0.8 scale and fades in after a short delay while
 *    the outgoing page grows toward 1.1 and fades out early. Popping mirrors
 *    the direction: the revealed page arrives from 1.1, the dismissed page
 *    sinks to 0.8. One 300ms tween on the scale channel; the fade splits
 *    90ms/210ms so the two crossfades never fully overlap.
 *  - **Tab switches**: Material fade-through — the outgoing tab fades out fast,
 *    the incoming tab fades in on the decelerate curve after the same short
 *    delay. No scale, so siblings read as lateral, not stacked.
 *
 * Predictive back rides [pushPopExit] automatically (nav-compose uses the pop
 * exit spec during the gesture).
 */
object ChompassNavTransitions {

    /** Forward navigation: incoming page scales 0.8→1, fading in after the short fade. */
    fun pushEnter(): EnterTransition =
        fadeIn(tween(AppMotion.TweenNavMs, delayMillis = AppMotion.NavFadeShortMs, easing = AppMotion.EasingStandard)) +
            scaleIn(
                tween(AppMotion.TweenNavMs, easing = AppMotion.EasingStandard),
                initialScale = 0.8f,
            )

    /** Forward navigation: outgoing page grows toward 1.1 and fades out inside the short window. */
    fun pushExit(): ExitTransition =
        fadeOut(tween(AppMotion.NavFadeShortMs, easing = AppMotion.EasingStandard)) +
            scaleOut(tween(AppMotion.TweenNavMs, easing = AppMotion.EasingStandard), targetScale = 1.1f)

    /** Back navigation: revealed page scales 1.1→1, fading in after the short fade. */
    fun pushPopEnter(): EnterTransition =
        fadeIn(tween(AppMotion.TweenNavMs, delayMillis = AppMotion.NavFadeShortMs, easing = AppMotion.EasingStandard)) +
            scaleIn(
                tween(AppMotion.TweenNavMs, easing = AppMotion.EasingStandard),
                initialScale = 1.1f,
            )

    /** Back navigation: dismissed page sinks to 0.8 and fades out inside the short window. */
    fun pushPopExit(): ExitTransition =
        fadeOut(tween(AppMotion.NavFadeShortMs, easing = AppMotion.EasingStandard)) +
            scaleOut(tween(AppMotion.TweenNavMs, easing = AppMotion.EasingStandard), targetScale = 0.8f)

    /** Tab switch in: fade-through arrival on the decelerate curve, no scale. */
    fun tabEnter(): EnterTransition =
        fadeIn(tween(AppMotion.TweenDefaultMs, delayMillis = AppMotion.NavFadeShortMs, easing = AppMotion.EasingDecelerate))

    /** Tab switch out: fast fade inside the short window; pop uses the same pair. */
    fun tabExit(): ExitTransition =
        fadeOut(tween(AppMotion.NavFadeShortMs, easing = AppMotion.EasingStandard))
}
