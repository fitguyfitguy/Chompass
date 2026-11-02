package app.chompass.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
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

    /** Ambient record/listening pulse duration (ms). */
    const val TweenPulseMs = 800

    // --- Brand mark intro (welcome step) -----------------------------------
    // Timeline transcribed from docs/brand/logo-system.html §"mark motion":
    // the ring draws, the needle spins three turns and settles into the bite,
    // the dial flinches, then the needle sways ±2.4° forever.

    /** Settle time before the ring starts drawing (ms). */
    const val MarkIntroDelayMs = 120

    /** Ring trim-in duration (ms). */
    const val MarkDrawMs = 900

    /** Needle spin start offset (ms). */
    const val MarkNeedleDelayMs = 180

    /** Needle spin duration (ms): −1080° → overshoot → settle. */
    const val MarkNeedleMs = 1450

    /** Chomp start offset (ms) — lands after the needle passes the bite. */
    const val MarkChompDelayMs = 1080

    /** Chomp squash-and-stretch duration (ms). */
    const val MarkChompMs = 550

    /** Delay before the idle sway starts on a first-run mark (ms). */
    const val MarkSwayStartMs = 2100

    /** Idle sway loop length (ms). */
    const val MarkSwayMs = 7000

    /** General mark ease-out (emphasised decelerate). */
    val MarkEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Ring draw ease: steady in and out, so the trim never snaps. */
    val MarkDrawEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Chomp + ring-pulse ease: the study's overshoot curve, what makes the bite feel fleshy. */
    val MarkChompEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** Fade the mark in when it enters (study `cp-markIn`, .45 s ease). */
    const val MarkInMs = 450

    /** Idle float: the whole mark rises and settles on this loop (study `float`, 7 s). */
    const val MarkFloatMs = 7000

    /** Float travel as a fraction of the mark's own side (study: 10 px on its ~360 px hero). */
    const val MarkFloatRise = 0.028f

    /** CSS `ease`: the mark-in fade. */
    val MarkInEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** CSS `ease-in-out`: the idle float. */
    val MarkFloatEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Standard accelerate-decelerate curve for container/position transitions. */
    val EasingStandard = FastOutSlowInEasing

    /** Decelerate curve for enter transitions (fades, slides arriving). */
    val EasingDecelerate = LinearOutSlowInEasing
}
