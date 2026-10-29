package app.chompass.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Central motion tokens, beside [AppSpacing]: a spec
 * change lands everywhere at once.
 *
 * Four springs, two durations, one easing pair — the whole vocabulary:
 *  - The two fill springs are the app's existing tuned idiom, previously
 *    inlined as literals at every Animatable site (ring, macro cards, hero arc).
 *  - The press and spatial springs cover interaction feedback; they implement
 *    the two-spring interaction idiom studied in
 *    `docs/local/MORPHE_DESIGN_ANALYSIS.md` §1.2–§1.3. Parameter values are
 *    reimplemented there from scratch (the reference app is GPL-3.0; only
 *    numbers and techniques transfer, never code).
 *
 * Durations are raw milliseconds for `tween(...)` call sites; easings are the
 * two Material curves the tweens pair with.
 */
object AppMotion {
    /** Steady-state fill: macro cards and the hero arc animate toward progress on this. */
    fun <T> fillSpring(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 55f)

    /** Launch replay of the activity ring: the slow first-run spec (response ≈ 1.2s). */
    fun <T> fillFirstRunSpring(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 30f)

    /** Ring updates after launch: faster than [fillSpring] so corrections don't lag (response ≈ 0.6s). */
    fun <T> ringUpdateSpring(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 110f)

    /** Press feedback: sinks quickly, settles with a small bounce on release. */
    fun <T> pressSpring(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Selection and layout settle: soft reach with no overshoot. */
    fun <T> spatialSpring(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 400f)

    /** Fast fade duration (ms), used inside spring-driven exits. */
    const val TweenFastMs = 180

    /** Default UI transition duration (ms) for color/fade/slide changes. */
    const val TweenDefaultMs = 220

    /** Shared-Z page transition duration (ms). */
    const val TweenNavMs = 300

    /** Short fade split inside nav transitions (ms): outgoing window / incoming delay. */
    const val NavFadeShortMs = 90

    /** Standard accelerate-decelerate curve for container/position transitions. */
    val EasingStandard = FastOutSlowInEasing

    /** Decelerate curve for enter transitions (fades, slides arriving). */
    val EasingDecelerate = LinearOutSlowInEasing
}
