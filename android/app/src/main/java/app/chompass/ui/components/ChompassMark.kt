package app.chompass.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlaceholderVerticalAlign as VerticalAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.theme.AppMotion
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.BrandPalette
import app.chompass.ui.theme.ChompassTheme
import app.chompass.ui.theme.ChompassTraitsDefaults
import kotlinx.coroutines.launch

/**
 * The Chompass mark: a bitten compass ring with a two-facet needle pointing into the bite.
 *
 * Geometry, palette and timing are transcribed from `docs/brand/logo-system.html`; the SVG
 * emitters (`scripts/assets/build_icon_mark.py`) carry the same path constants, so the
 * launcher icon and this Canvas are the same drawing at different sizes.
 */
internal object ChompassMarkGeometry {
    /** Coordinate box of the mark, matching the study's viewBox. */
    const val Box = 200f

    const val RingStroke = 16f
    const val RingDegreesStart = -1080f

    const val Ring =
        "M171.7 106.3A72 72 0 1 1 118.6 30.5A34 34 0 0 0 159 58.7A34 34 0 0 0 171.7 106.3Z"
    const val NeedleA = "M151.6 63.9L104.3 106.1L83.6 111.5Z"
    const val NeedleB = "M151.6 63.9L83.6 111.5L95.7 93.9Z"

    /** Lowercase "o" counter for the wordmark: 60-unit box, 8-unit stroke. */
    const val BittenO = "M54.15 23.53A25 25 0 1 1 40.57 7.34A9 9 0 0 0 41.49 20.36A9 9 0 0 0 54.15 23.53Z"
    const val BittenOBox = 60f
    const val BittenOStroke = 8f

    /** The chomp hinges at the bite, not at the dial centre. */
    val DialPivot = Offset(142f, 56f)
    val NeedlePivot = Offset(100f, 100f)
    const val HubRadius = 7f
    const val PinRadius = 2.5f

    /** Ring gradient runs corner to corner of the 200 box (study axis). */
    val RingGradientStart = Offset(20f, 10f)
    val RingGradientEnd = Offset(180f, 190f)
}

/**
 * Resolved mark palette: the study's light values, or its glow variant for dark surfaces.
 *
 * There is no `hub` colour: the pivot is cleared out of the mark's offscreen layer
 * instead, so the ground — including the ambient wax field — shows through the hole.
 * A flat "background colour" disc only matched a flat ground.
 */
internal data class ChompassMarkColors(
    val ringStops: List<Pair<Float, Color>>,
    val needleA: Color,
    val needleB: Color,
    val pin: Color,
)

/** Parsed mark geometry; built once per composition, reused by the draw phase. */
private class ChompassMarkPaths {
    private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

    val ring: Path = path(ChompassMarkGeometry.Ring)
    val needleA: Path = path(ChompassMarkGeometry.NeedleA)
    val needleB: Path = path(ChompassMarkGeometry.NeedleB)
    val ringMeasure: PathMeasure = PathMeasure().apply { setPath(ring, false) }
}

@Composable
private fun rememberMarkPaths(): ChompassMarkPaths = remember { ChompassMarkPaths() }

@Composable
private fun markColors(): ChompassMarkColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        ChompassMarkColors(
            ringStops = BrandPalette.RipenedGlow,
            needleA = BrandPalette.Cream,
            needleB = Color(0xFFFFC9A8),
            pin = BrandPalette.Cream,
        )
    } else {
        ChompassMarkColors(
            ringStops = BrandPalette.Ripened,
            needleA = BrandPalette.Ink,
            needleB = Color(0xFF4A3346),
            pin = BrandPalette.Ink,
        )
    }
}

/**
 * Draws the brand mark, optionally playing the first-run intro and the idle sway.
 *
 * [playIntro] plays the full 1.6 s sequence (ring draw → three needle turns → chomp →
 * sway). With [idleSway] the needle keeps drifting ±2.4° on a 7 s loop. Both stop dead
 * while the device is in battery saver or animations are removed.
 */
@Composable
fun ChompassMark(
    modifier: Modifier = Modifier,
    playIntro: Boolean = false,
    idleSway: Boolean = true,
    contentDescription: String? = null,
) {
    val paused by rememberSystemMotionPaused()
    val animateIntro = playIntro && !paused
    val paths = rememberMarkPaths()
    val colors = markColors()

    val introAlpha = remember { Animatable(if (playIntro) 0f else 1f) }
    val drawProgress = remember { Animatable(1f) }
    val needleDegrees = remember { Animatable(0f) }
    val chompX = remember { Animatable(1f) }
    val chompY = remember { Animatable(1f) }
    val ringStroke = remember { Animatable(ChompassMarkGeometry.RingStroke) }

    LaunchedEffect(animateIntro) {
        if (!animateIntro) {
            // Static at rest: full ring, needle on the bite, no chomp swelling.
            introAlpha.snapTo(1f)
            drawProgress.snapTo(1f)
            needleDegrees.snapTo(0f)
            chompX.snapTo(1f)
            chompY.snapTo(1f)
            ringStroke.snapTo(ChompassMarkGeometry.RingStroke)
            return@LaunchedEffect
        }
        introAlpha.snapTo(0f)
        drawProgress.snapTo(0f)
        needleDegrees.snapTo(ChompassMarkGeometry.RingDegreesStart)
        chompX.snapTo(1f)
        chompY.snapTo(1f)
        ringStroke.snapTo(ChompassMarkGeometry.RingStroke)

        launch {
            // Study `cp-markIn`: the mark fades in as the ring starts drawing.
            introAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AppMotion.MarkInMs,
                    easing = AppMotion.MarkInEasing,
                ),
            )
        }
        launch {
            drawProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AppMotion.MarkDrawMs,
                    delayMillis = AppMotion.MarkIntroDelayMs,
                    easing = AppMotion.MarkDrawEasing,
                ),
            )
        }
        launch {
            // Three turns, then overshoot-and-settle into the bite (study keyframes).
            needleDegrees.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = AppMotion.MarkNeedleMs
                    delayMillis = AppMotion.MarkNeedleDelayMs
                    26f at 797 with AppMotion.MarkEasing
                    -11f at 1015 with AppMotion.MarkEasing
                    5f at 1189 with AppMotion.MarkEasing
                    -2f at 1334 with AppMotion.MarkEasing
                },
            )
        }
        launch {
            chompX.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = AppMotion.MarkChompMs
                    delayMillis = AppMotion.MarkChompDelayMs
                    0.955f at 121 with AppMotion.MarkChompEasing
                    1.028f at 275 with AppMotion.MarkChompEasing
                    0.993f at 407 with AppMotion.MarkChompEasing
                },
            )
        }
        launch {
            chompY.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = AppMotion.MarkChompMs
                    delayMillis = AppMotion.MarkChompDelayMs
                    1.042f at 121 with AppMotion.MarkChompEasing
                    0.976f at 275 with AppMotion.MarkChompEasing
                    1.006f at 407 with AppMotion.MarkChompEasing
                },
            )
        }
        launch {
            ringStroke.animateTo(
                targetValue = ChompassMarkGeometry.RingStroke,
                animationSpec = keyframes {
                    durationMillis = AppMotion.MarkChompMs
                    18.4f at 165 with AppMotion.MarkChompEasing
                    15.2f at 341 with AppMotion.MarkChompEasing
                },
            )
        }
    }

    // One idle clock for both loops. The sway/float are ambient motion, so the
    // user's "Ambient motion" switch stops them too (the intro is a one-shot and
    // still plays); battery saver / animations-off remain the system-level gate.
    val idleMotion = !paused && ChompassTraitsDefaults.ambientMotion()
    val idle = if (idleMotion) rememberInfiniteTransition(label = "markIdle") else null

    val swayState = if (idle != null && idleSway) {
        idle.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = AppMotion.MarkSwayMs
                    2.4f at 1960
                    -2.1f at 4340
                },
                initialStartOffset = StartOffset(if (playIntro) AppMotion.MarkSwayStartMs else 0),
            ),
            label = "markSwayDegrees",
        )
    } else {
        null
    }
    val sway = swayState?.value ?: 0f

    // Study `float`: the whole mark drifts up and settles, independent of the needle.
    val floatState = idle?.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = AppMotion.MarkFloatMs
                1f at AppMotion.MarkFloatMs / 2 with AppMotion.MarkFloatEasing
            },
        ),
        label = "markFloat",
    )
    val floatFraction = floatState?.value ?: 0f

    val ringBrush = remember(colors) {
        Brush.linearGradient(
            colorStops = colors.ringStops.toTypedArray(),
            start = ChompassMarkGeometry.RingGradientStart,
            end = ChompassMarkGeometry.RingGradientEnd,
        )
    }
    val semantics = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }

    Canvas(
        modifier
            .aspectRatio(1f)
            // The pivot is cleared out of this layer (see drawCircle below), so the
            // mark needs its own compositing layer; the ambient field shows through.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .then(semantics),
    ) {
        val unit = size.minDimension / ChompassMarkGeometry.Box
        val alpha = introAlpha.value
        val rise = floatFraction * size.minDimension * AppMotion.MarkFloatRise
        translate(top = -rise) {
            scale(unit, unit, pivot = Offset.Zero) {
                withTransform({
                    scale(chompX.value, chompY.value, pivot = ChompassMarkGeometry.DialPivot)
                }) {
                    val stroke = Stroke(
                        width = ringStroke.value,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    if (drawProgress.value >= 1f) {
                        drawPath(paths.ring, brush = ringBrush, alpha = alpha, style = stroke)
                    } else {
                        val trimmed = Path()
                        paths.ringMeasure.getSegment(
                            startDistance = 0f,
                            stopDistance = paths.ringMeasure.length * drawProgress.value,
                            destination = trimmed,
                            startWithMoveTo = true,
                        )
                        drawPath(trimmed, brush = ringBrush, alpha = alpha, style = stroke)
                    }
                }
                withTransform({
                    rotate(sway + needleDegrees.value, pivot = ChompassMarkGeometry.NeedlePivot)
                }) {
                    drawPath(paths.needleA, color = colors.needleA, alpha = alpha)
                    drawPath(paths.needleB, color = colors.needleB, alpha = alpha)
                    drawCircle(
                        color = Color.Black,
                        radius = ChompassMarkGeometry.HubRadius,
                        center = ChompassMarkGeometry.NeedlePivot,
                        alpha = alpha,
                        blendMode = BlendMode.Clear,
                    )
                    drawCircle(
                        color = colors.pin,
                        radius = ChompassMarkGeometry.PinRadius,
                        center = ChompassMarkGeometry.NeedlePivot,
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/**
 * The wordmark, with the bitten "o" drawn as a stroked path.
 *
 * The "o" keeps a fixed brand peach in both schemes rather than following the accent:
 * the study's guardrail is that plum there would over-explain the joke. Light ground
 * uses Peach700 (4.67:1 on cream) where the study's CSS uses Peach600 (3.24:1) — the
 * glyph is small and stroke-only, so the extra step keeps it legible at 13sp.
 */
private const val WordmarkTailMixLight = 0.035f
private const val WordmarkTailMixDark = 0.07f
private const val WordmarkSplitGap = 0.045f

/** Compose's inline placeholder rides above the baseline, while the site's
 *  bitten-o SVG box sits on it, so the drawn "o" reads ~3 px high at 26 sp.
 *  Drop the path by this many wordmark units (1 unit = 0.01 em) to put its ink
 *  bottom on the baseline like the site. */
private const val BittenOBaselineDrop = 5f

@Composable
fun ChompassWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val biteColor = if (dark) BrandPalette.Peach300 else BrandPalette.Peach700
    val tailColor = lerp(color, biteColor, if (dark) WordmarkTailMixDark else WordmarkTailMixLight)
    val biteId = "chompass-bite"
    val gapId = "chompass-split"
    val text = remember(tailColor) {
        buildAnnotatedString {
            append("ch")
            appendInlineContent(biteId, "o")
            append("mp")
            // Compose rejects an empty alternateText; a ZWNJ is silent in TalkBack
            // and keeps the spoken name "Chompass" (no word break at the seam).
            appendInlineContent(gapId, "\u200C")
            withStyle(SpanStyle(color = tailColor)) { append("ass") }
        }
    }
    val bitePath = remember {
        PathParser().parsePathString(ChompassMarkGeometry.BittenO).toPath()
    }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            letterSpacing = (-0.035).em,
        ),
        inlineContent = mapOf(
            biteId to InlineTextContent(
                // Square box like the study's 60-unit viewBox (CSS: .58em wide,
                // baseline-anchored); a taller box left the glyph riding high.
                placeholder = Placeholder(
                    width = 0.60.em,
                    height = 0.60.em,
                    placeholderVerticalAlign = VerticalAlign.AboveBaseline,
                ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val unit = size.minDimension / ChompassMarkGeometry.BittenOBox
                    scale(unit, unit, pivot = Offset.Zero) {
                        translate(top = BittenOBaselineDrop) {
                            drawPath(
                                path = bitePath,
                                color = biteColor,
                                style = Stroke(
                                    width = ChompassMarkGeometry.BittenOStroke,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
            },
            gapId to InlineTextContent(
                // Width only: an empty alt keeps the spoken name "Chompass".
                placeholder = Placeholder(
                    width = WordmarkSplitGap.em,
                    height = 0.1.em,
                    placeholderVerticalAlign = VerticalAlign.Center,
                ),
            ) {},
        ),
    )
}

/** Mark + wordmark + tagline lockup for the About screen: standing sway, no intro. */
@Composable
fun ChompassBrandHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChompassMark(modifier = Modifier.size(64.dp), idleSway = true)
        Spacer(Modifier.height(8.dp))
        ChompassWordmark(fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.about_brand_tagline),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Mark light", showBackground = true, backgroundColor = 0xFFFFF6EC)
@Preview(
    name = "Mark dark",
    showBackground = true,
    backgroundColor = 0xFF170E16,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChompassMarkPreview() {
    ChompassTheme(themeColor = AppThemeColor.PLUM) {
        ChompassMark(modifier = Modifier.size(160.dp), playIntro = false)
    }
}
