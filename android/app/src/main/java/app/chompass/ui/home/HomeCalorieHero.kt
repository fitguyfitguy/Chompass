package app.chompass.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ActiveBurnShade
import app.chompass.models.ActiveCalorieSource
import app.chompass.models.EnergyFormat
import app.chompass.models.GoalDerivation
import app.chompass.models.GoalFormulaReference
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.LocaleFormat
import app.chompass.models.NutritionConstants
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.components.energyText
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.navigation.LocalLaunchFillEpoch
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.ui.components.pressScale
import app.chompass.ui.components.AnimatedNumberText
import app.chompass.ui.theme.AppMotion
import app.chompass.ui.theme.CapsuleShape
import app.chompass.ui.theme.heroGradientEnd
import app.chompass.ui.theme.success
import app.chompass.ui.progress.LegendSwatch

// ── Calorie hero ─────────────────────────────────────────────────────

/**
 * The Home calorie hero: the selected kcal value in display numerals over a
 * 10dp progress capsule (accent fill, soft glow), with the goal and remaining
 * labels beneath. Changes animate — counter swap and fill springs via the
 * AppMotion specs in the composables below.
 */
private data class CalorieHeroInput(
    val current: Int,
    val baseGoal: Int,
    val planned: Int,
    val activeCalories: Int,
    val displayMode: HomeCalorieDisplayMode,
    val activeCalorieSource: ActiveCalorieSource?,
    val showActiveCalories: Boolean,
    val liveActiveBurn: Int,
    val burnShade: ActiveBurnShade?,
    val restingBurn: Int?,
    val freezeProgress: Boolean,
    val dayTypeLabel: String?,
    val dayTypeColor: Color?,
    val onDayTypeClick: (() -> Unit)?,
    val untracked: Boolean,
    val onUntrackedClick: (() -> Unit)?,
    val recalcDetailsAvailable: Boolean,
    val onShowRecalcDetails: (() -> Unit)?,
    val goalDerivation: GoalDerivation?,
    val preview: Boolean,
)

@Composable
internal fun CalorieHero(
    current: Int,
    baseGoal: Int,
    /** Planned (not yet eaten) kcal; drawn as a fainter arc under the logged fill. */
    planned: Int = 0,
    activeCalories: Int,
    displayMode: HomeCalorieDisplayMode,
    activeCalorieSource: ActiveCalorieSource? = null,
    /** When true, Settings asks to surface today's active burn in the hero. */
    showActiveCalories: Boolean = false,
    /** Today's live active burn (measured + manual), mode-independent — the
     *  caption number. Unlike [activeCalories] it is non-zero in STATIC mode. */
    liveActiveBurn: Int = 0,
    /**
     * Energy-balance shades for the arc (live active vs the day's active norm).
     * Non-null for ADD_ACTIVE with a live measured source (Health Connect or
     * debug), including the measured-0 morning; PAL-estimate/manual-only days
     * keep the legacy budget tail.
     */
    burnShade: ActiveBurnShade? = null,
    /** Resting (basal) burn so far, when known. Feeds the budget sheet's burned-today total. */
    restingBurn: Int? = null,
    freezeProgress: Boolean = false,
    /** #60: today's day-type label ("Training day"); null hides the chip. */
    dayTypeLabel: String? = null,
    dayTypeColor: androidx.compose.ui.graphics.Color? = null,
    /** Opens the day-type quick-switch sheet; chip hidden when null (previews). */
    onDayTypeClick: (() -> Unit)? = null,
    untracked: Boolean = false,
    onUntrackedClick: (() -> Unit)? = null,
    /** True when a goal-change explanation exists; shows the ⓘ dialog's recalc-details link. */
    recalcDetailsAvailable: Boolean = false,
    /** Opens the recalc details sheet (closes the ⓘ budget dialog first). */
    onShowRecalcDetails: (() -> Unit)? = null,
    /** Standing goal derivation for the ⓘ dialog's formula-baseline block. */
    goalDerivation: GoalDerivation? = null,
    layout: HomeCalorieHeroLayout? = null,
    /** Sample render inside pickers/previews: frozen progress, no budget sheet. */
    preview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val input = CalorieHeroInput(
        current = current,
        baseGoal = baseGoal,
        planned = planned,
        activeCalories = activeCalories,
        displayMode = displayMode,
        activeCalorieSource = activeCalorieSource,
        showActiveCalories = showActiveCalories,
        liveActiveBurn = liveActiveBurn,
        burnShade = burnShade,
        restingBurn = restingBurn,
        freezeProgress = freezeProgress || preview,
        dayTypeLabel = dayTypeLabel,
        dayTypeColor = dayTypeColor,
        onDayTypeClick = onDayTypeClick,
        untracked = untracked,
        onUntrackedClick = onUntrackedClick,
        recalcDetailsAvailable = recalcDetailsAvailable,
        onShowRecalcDetails = onShowRecalcDetails,
        goalDerivation = goalDerivation,
        preview = preview,
    )
    when (layout) {
        HomeCalorieHeroLayout.COMPACT_HORIZONTAL -> CompactHorizontalCalorieHero(input, modifier)
        HomeCalorieHeroLayout.SIDE_GAUGE -> SideGaugeCalorieHero(input, modifier)
        HomeCalorieHeroLayout.COMPACT_ARC -> CompactArcCalorieHero(input, modifier)
        HomeCalorieHeroLayout.HIDDEN -> Unit
        null -> DefaultCalorieHero(input, modifier)
    }
}

/** Derived state shared by every hero variant (one budget read per layout). */
private data class CalorieHeroDerived(
    val effectiveGoal: Int,
    /** Number the arc end / goal line reads against (expected day when shading). */
    val target: Int,
    val remaining: Int,
    val fillRatio: Float,
    val plannedFillRatio: Float,
    val shadesActive: Boolean,
    /** Planned kcal for the "+X planned" caption; 0 hides caption, segment, and legend row. */
    val planned: Int,
)

private fun calorieHeroDerived(input: CalorieHeroInput): CalorieHeroDerived = with(input) {
    val effectiveGoal = HomeCalorieDisplay.effectiveGoal(displayMode, baseGoal, activeCalories)
    val shade = burnShade
    val shadesActive = shade != null && shade.typical > 0 &&
        displayMode == HomeCalorieDisplayMode.ADD_ACTIVE
    // Expected-day target (display-only): sedentary base + the larger of the
    // active norm and live burn. The arc end, goal line, and remaining read
    // against it; the budget (base + live) is unchanged.
    val target = if (shadesActive) {
        HomeCalorieDisplay.expectedTarget(baseGoal, shade!!.typical, shade.live)
    } else {
        effectiveGoal
    }
    // On the shade arc the eaten fill shares the burn scale (arc end = target),
    // so eaten and burn read against the same denominator.
    val fillRatio = if (shadesActive) {
        HomeCalorieDisplay.burnShadeEatenFraction(current, baseGoal, shade!!.typical, shade.live)
    } else {
        HomeCalorieDisplay.progressRatio(displayMode, current, baseGoal, activeCalories)
    }
    val plannedFillRatio = if (planned <= 0) {
        0f
    } else if (shadesActive) {
        HomeCalorieDisplay.burnShadeEatenFraction(
            current + planned,
            baseGoal,
            shade!!.typical,
            shade.live,
        )
    } else {
        HomeCalorieDisplay.progressRatio(displayMode, current + planned, baseGoal, activeCalories)
    }.coerceAtLeast(fillRatio)
    CalorieHeroDerived(
        effectiveGoal = effectiveGoal,
        target = target,
        remaining = (target - current).coerceAtLeast(0),
        fillRatio = fillRatio,
        plannedFillRatio = plannedFillRatio,
        shadesActive = shadesActive,
        planned = planned,
    )
}

/** Shared fill animation: launch-epoch restart + spring; frozen heroes snap. */
@Composable
private fun rememberArcAnimation(
    ratio: Float,
    freezeProgress: Boolean,
): Animatable<Float, AnimationVector1D> {
    val epoch = LocalLaunchFillEpoch.current
    var lastEpoch by rememberSaveable { mutableIntStateOf(0) }
    val animatedRatio = remember { Animatable(if (lastEpoch == epoch) ratio else 0f) }
    LaunchedEffect(epoch, ratio) {
        if (freezeProgress) {
            animatedRatio.snapTo(ratio)
            return@LaunchedEffect
        }
        val spec = AppMotion.fillSpring<Float>()
        if (lastEpoch != epoch) {
            animatedRatio.snapTo(0f)
            animatedRatio.animateTo(ratio, spec)
            lastEpoch = epoch
        } else {
            animatedRatio.animateTo(ratio, spec)
        }
    }
    return animatedRatio
}

private data class HeroBandTargets(
    val eaten: Float,
    val planned: Float,
    val base: Float,
    val typical: Float,
    val live: Float,
)

/** Settled band fractions. Shades off means typical and live spring toward zero. */
private fun heroBandTargets(
    eaten: Float,
    planned: Float,
    shadesActive: Boolean,
    shade: ActiveBurnShade?,
    baseGoal: Int,
    target: Int,
    displayMode: HomeCalorieDisplayMode,
): HeroBandTargets {
    val base = when {
        shadesActive && shade != null ->
            HomeCalorieDisplay.burnShadeBaseFraction(baseGoal, shade.typical, shade.live)
        displayMode == HomeCalorieDisplayMode.ADD_ACTIVE && target > baseGoal ->
            (baseGoal.toFloat() / target).coerceIn(0f, 1f)
        else -> 0f
    }
    val typical = if (shadesActive && shade != null) {
        HomeCalorieDisplay.burnShadeTypicalFraction(baseGoal, shade.typical, shade.live)
    } else {
        0f
    }
    val live = if (shadesActive && shade != null) {
        HomeCalorieDisplay.burnShadeLiveFraction(baseGoal, shade.live, shade.typical)
    } else {
        0f
    }
    return HeroBandTargets(eaten, planned, base, typical, live)
}

private class HeroBandAnims(
    val eaten: Animatable<Float, AnimationVector1D>,
    val planned: Animatable<Float, AnimationVector1D>,
    val base: Animatable<Float, AnimationVector1D>,
    val typical: Animatable<Float, AnimationVector1D>,
    val live: Animatable<Float, AnimationVector1D>,
    val targets: HeroBandTargets,
    private val freezeProgress: Boolean,
) {
    fun eatenNow(): Float = if (freezeProgress) targets.eaten else eaten.value
    fun plannedNow(): Float = if (freezeProgress) targets.planned else planned.value
    fun baseNow(): Float = if (freezeProgress) targets.base else base.value
    fun typicalNow(): Float = if (freezeProgress) targets.typical else typical.value
    fun liveNow(): Float = if (freezeProgress) targets.live else live.value
}

@Composable
private fun rememberHeroBandAnims(
    targets: HeroBandTargets,
    freezeProgress: Boolean,
): HeroBandAnims = HeroBandAnims(
    eaten = rememberArcAnimation(targets.eaten, freezeProgress),
    planned = rememberArcAnimation(targets.planned, freezeProgress),
    base = rememberArcAnimation(targets.base, freezeProgress),
    typical = rememberArcAnimation(targets.typical, freezeProgress),
    live = rememberArcAnimation(targets.live, freezeProgress),
    targets = targets,
    freezeProgress = freezeProgress,
)

/**
 * Planned fill-end→planned-end band over the 0.35 ghost. 0.50 sits above the
 * ghost and below the 0.55 consumed-over-burn overlay so the same primary hue
 * never collides with either stack (peach/fig/plum: 0.55-on-tertiary is burn;
 * 0.50-on-ghost is planned). Estimated-active is tertiary@0.45, a different hue.
 */
private const val PlannedSegmentAlpha = 0.50f

/** Animated bar bands below half a pixel are gone; the next stack may draw. */
private const val BarBandMinPx = 0.5f

/**
 * The hero's 180° arc as one shared canvas: a circle at 2:1 or a shallow
 * ellipse on wider canvases. The oval spans the width minus the stroke and
 * exactly twice the height, so the diameter sits on the canvas's bottom edge.
 * Fills, the planned ghost, burn shades, and the ADD_ACTIVE bonus tail are
 * angle-based and identical for every variant; only
 * the canvas size and stroke differ.
 */
@Composable
private fun CalorieArcCanvas(
    modifier: Modifier,
    stroke: Dp,
    fillRatio: Float,
    plannedFillRatio: Float,
    freezeProgress: Boolean,
    trackColor: Color,
    progressColor: Color,
    progressEndColor: Color,
    bonusColor: Color,
    liveBurnColor: Color,
    successColor: Color,
    shadesActive: Boolean,
    shade: ActiveBurnShade?,
    baseGoal: Int,
    effectiveGoal: Int,
    displayMode: HomeCalorieDisplayMode,
    semanticsDescription: String? = null,
) {
    val bands = rememberHeroBandAnims(
        heroBandTargets(
            eaten = fillRatio,
            planned = plannedFillRatio,
            shadesActive = shadesActive,
            shade = shade,
            baseGoal = baseGoal,
            target = effectiveGoal,
            displayMode = displayMode,
        ),
        freezeProgress,
    )
    // Accent ramp for the eaten stroke: the theme color at the base, 20%
    // toward white at the tip, so the hero reads as the theme color with
    // depth instead of one flat band on every accent. Built once per color
    // pair: the shader resolves against each draw's bounds, so every frame
    // reuses the same brush.
    val eatenBrush = remember(progressColor, progressEndColor) {
        Brush.horizontalGradient(listOf(progressColor, progressEndColor))
    }
    Canvas(
        modifier = modifier.semantics {
            // TalkBack tracks the spring. The description stays the settled day.
            progressBarRangeInfo = ProgressBarRangeInfo(bands.eatenNow(), 0f..1f, 0)
            semanticsDescription?.let { contentDescription = it }
        }
    ) {
        val eatenFrac = bands.eatenNow()
        val plannedFrac = bands.plannedNow()
        val baseFrac = bands.baseNow()
        val typicalFrac = bands.typicalNow()
        val liveFrac = bands.liveNow()
        val strokePx = stroke.toPx()
        val inset = strokePx / 2f
        val arcSize = Size(size.width - strokePx, 2 * size.height - strokePx)
        val topLeft = Offset(inset, inset)
        val cx = size.width / 2f
        val cy = size.height
        val aMid = arcSize.width / 2f
        val bMid = arcSize.height / 2f
        fun bandPx(fraction: Float) = (Math.PI * aMid * fraction).toFloat()
        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        // Eaten fill first so live/typical shades (and the ADD_ACTIVE
        // bonus tail) paint on top and stay readable when intake covers
        // the active zone. The consumed stretch gets a translucent eaten
        // pass back over the shades (below), so the burn still reads as
        // partly eaten instead of one solid band.
        val eatenDegrees = 180f * eatenFrac
        // Activity-zone start (the base boundary) for the consumed re-pass.
        var zoneStartAngle: Float? = null
        if (plannedFrac > eatenFrac) {
            val plannedDraw = plannedFrac.coerceIn(0f, 1f)
            drawArc(
                color = progressColor.copy(alpha = 0.35f),
                startAngle = 180f,
                sweepAngle = 180f * plannedDraw,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            val plannedSweep = (180f * plannedDraw - eatenDegrees).coerceAtLeast(0f)
            if (plannedSweep > 0f) {
                // Round far cap. The start cap sits under the eaten stroke
                // drawn next, so the join stays one profile instead of a
                // square cut at the eaten tip.
                drawArc(
                    color = progressColor.copy(alpha = PlannedSegmentAlpha),
                    startAngle = 180f + eatenDegrees,
                    sweepAngle = plannedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        drawArc(
            brush = eatenBrush,
            startAngle = 180f,
            sweepAngle = eatenDegrees,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        // Half the stroke expressed in ring degrees at the semi-major axis.
        // Band joins tuck this much underneath the band that follows, so the
        // edge between two bands is one flush boundary instead of two stacked
        // caps pinching the ring; the consumed overlay below needs no cap
        // angles at all.
        val capExtension = if (aMid > 0f) {
            Math.toDegrees((strokePx / 2.0) / aMid.toDouble()).toFloat()
        } else {
            0f
        }
        // Shade sweeps spring to zero instead of popping off. While either
        // band is still visible, keep that stack and skip the activity tail
        // so the shared base boundary glides across the swap.
        val shadeVisible = bandPx(typicalFrac) > BarBandMinPx || bandPx(liveFrac) > BarBandMinPx
        if (shadeVisible) {
            val baseAngle = 180f * baseFrac
            val typicalSweep = 180f * typicalFrac
            val liveSweep = 180f * liveFrac
            val liveMaxSweep = (180f - baseAngle).coerceAtLeast(0f)
            val withinTypicalSweep = liveSweep.coerceAtMost(typicalSweep)
            val overTypicalSweep = (liveSweep - typicalSweep).coerceAtLeast(0f)
            if (typicalSweep > 0f) {
                val sweep = (typicalSweep + if (overTypicalSweep > 0f) capExtension else 0f)
                    .coerceAtMost(180f - baseAngle)
                drawArc(
                    color = bonusColor,
                    startAngle = 180f + baseAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            if (withinTypicalSweep > 0f) {
                val sweep = withinTypicalSweep.coerceAtMost(liveMaxSweep)
                drawArc(
                    color = liveBurnColor,
                    startAngle = 180f + baseAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            if (overTypicalSweep > 0f) {
                val start = 180f + baseAngle + typicalSweep
                val sweep = overTypicalSweep.coerceAtMost((liveMaxSweep - typicalSweep).coerceAtLeast(0f))
                if (typicalSweep > 0f) {
                    drawArc(
                        color = successColor,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    val capAngle = Math.toRadians((start + sweep).toDouble())
                    drawCircle(
                        successColor,
                        radius = strokePx / 2f,
                        center = Offset(
                            cx + (aMid * Math.cos(capAngle)).toFloat(),
                            cy + (bMid * Math.sin(capAngle)).toFloat(),
                        ),
                    )
                } else {
                    drawArc(
                        color = successColor,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
            zoneStartAngle = 180f + baseAngle
        } else if (bandPx(baseFrac) > BarBandMinPx) {
            val baseSweep = 180f * baseFrac
            val tailSweep = (180f - baseSweep).coerceAtLeast(0f)
            if (tailSweep > 0f) {
                val start = 180f + baseSweep
                drawArc(
                    color = bonusColor,
                    startAngle = start,
                    sweepAngle = tailSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                zoneStartAngle = start
            }
        }
        zoneStartAngle?.let { zoneStart ->
            val span = consumedShadeOverlaySpan(
                zoneStart = zoneStart,
                eatenEnd = 180f + eatenDegrees,
                arcEnd = 360f,
            )
            if (span != null) {
                drawArc(
                    color = progressColor.copy(alpha = 0.55f),
                    startAngle = span.start,
                    sweepAngle = span.sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * Slide-and-fade figure. Frozen heroes stay on plain [Text];
 * [AnimatedNumberText] already forces `softWrap = false`.
 */
@Composable
private fun HeroFigureText(
    text: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    autoSize: TextAutoSize? = null,
    contentDescription: String? = null,
) {
    if (animate) {
        AnimatedNumberText(
            value = text,
            modifier = modifier,
            style = style,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            autoSize = autoSize,
            contentDescription = contentDescription,
        )
    } else {
        Text(
            text = text,
            modifier = if (contentDescription == null) {
                modifier
            } else {
                modifier.semantics { this.contentDescription = contentDescription }
            },
            style = style,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
            autoSize = autoSize,
        )
    }
}

/**
 * "of <goal>" + ⓘ on one compact line. The whole line is the touch target so
 * the slim bowl stack keeps a sane tap area without a 48dp icon slot.
 */
@Composable
private fun BudgetGoalLine(
    goalText: String,
    color: Color,
    tint: Color,
    onOpen: () -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
    maxTextWidth: Dp = Dp.Unspecified,
    animateNumbers: Boolean = false,
) {
    val onClickLabel = stringResource(R.string.home_calorie_budget_info)
    val src = remember { MutableInteractionSource() }
    val textModifier = if (maxTextWidth == Dp.Unspecified) {
        Modifier
    } else {
        Modifier.widthIn(max = maxTextWidth)
    }
    val goalAutoSize = TextAutoSize.StepBased(
        minFontSize = 8.sp,
        maxFontSize = 12.sp,
        stepSize = 0.5.sp,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .pressScale(src)
            .clickable(
                interactionSource = src,
                indication = null,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics(mergeDescendants = true) { description?.let { contentDescription = it } },
    ) {
        if (animateNumbers) {
            HeroFigureText(
                text = goalText,
                animate = true,
                modifier = textModifier,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
                autoSize = goalAutoSize,
            )
        } else {
            Text(
                goalText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
                autoSize = goalAutoSize,
                modifier = textModifier,
            )
        }
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The budget-a11y truth for the goal line, exactly as the tall arc stated it. */
@Composable
private fun calorieBudgetDescription(input: CalorieHeroInput): String? = with(input) {
    if (displayMode != HomeCalorieDisplayMode.ADD_ACTIVE) return@with null
    val unit = LocalEnergyUnit.current
    val effectiveGoal = HomeCalorieDisplay.effectiveGoal(displayMode, baseGoal, activeCalories)
    val integratesBurn = activeCalories > 0 && displayMode != HomeCalorieDisplayMode.STATIC
    val base = stringResource(
        R.string.home_calorie_budget_a11y,
        EnergyFormat.quantity(effectiveGoal, unit),
        EnergyFormat.quantity(baseGoal, unit),
        EnergyFormat.quantity(activeCalories, unit),
        energyUnitLabel(),
    )
    if (integratesBurn && activeCalorieSource == ActiveCalorieSource.ESTIMATED) {
        base + ". " + stringResource(R.string.home_calorie_active_estimated_a11y)
    } else {
        base
    }
}

/** "+X planned" using the same formatted quantity as the eaten number. */
@Composable
private fun plannedCaptionText(planned: Int): String? {
    if (planned <= 0) return null
    val unit = LocalEnergyUnit.current
    return stringResource(
        R.string.home_planned_caption,
        LocaleFormat.integer(EnergyFormat.quantity(planned, unit)),
    )
}

@Composable
private fun PlannedCaptionText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    maxTextWidth: Dp,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Medium,
    animate: Boolean = false,
) {
    val autoSize = TextAutoSize.StepBased(
        minFontSize = 8.sp,
        maxFontSize = fontSize,
        stepSize = 0.5.sp,
    )
    if (animate) {
        HeroFigureText(
            text = text,
            animate = true,
            modifier = modifier.widthIn(max = maxTextWidth),
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            autoSize = autoSize,
        )
    } else {
        Text(
            text = text,
            modifier = modifier.widthIn(max = maxTextWidth),
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            autoSize = autoSize,
        )
    }
}

@Composable
private fun burnShadeA11y(burn: ActiveBurnShade): String {
    val unit = LocalEnergyUnit.current
    val liveQ = EnergyFormat.quantity(burn.live, unit)
    val typicalQ = EnergyFormat.quantity(burn.typical, unit)
    val overQ = EnergyFormat.quantity(burn.live - burn.typical, unit)
    val over = HomeCalorieDisplay.isActiveBurnOverTypical(burn.live, burn.typical)
    val dayType = burn.typicalDayTypeName.takeIf { burn.typicalIsDayType && !it.isNullOrBlank() }
    return if (over) {
        stringResource(R.string.home_active_burn_over_a11y, liveQ, overQ)
    } else if (dayType != null) {
        stringResource(R.string.home_active_burn_progress_day_type_a11y, liveQ, typicalQ, dayType)
    } else {
        stringResource(R.string.home_active_burn_progress_a11y, liveQ, typicalQ)
    }
}

@Composable
private fun calorieArcContextDescription(input: CalorieHeroInput, d: CalorieHeroDerived): String? {
    if (d.planned <= 0 && !d.shadesActive) return null
    val parts = ArrayList<String>(2)
    plannedCaptionText(d.planned)?.let { parts += it }
    if (d.shadesActive) {
        input.burnShade?.let { parts += burnShadeA11y(it) }
    }
    return parts.joinToString(". ").ifEmpty { null }
}

/** Burn caption line: shades when active, else the live burn the user asked to see. */
@Composable
private fun CalorieBurnCaption(
    input: CalorieHeroInput,
    shadesActive: Boolean,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    animateNumbers: Boolean = false,
) {
    val capped = if (maxWidth == Dp.Unspecified) modifier else modifier.widthIn(max = maxWidth)
    with(input) {
        if (shadesActive) {
            BurnShadeCaption(
                burn = burnShade!!,
                modifier = capped,
                center = maxWidth != Dp.Unspecified,
                animateNumbers = animateNumbers,
            )
        } else if (showActiveCalories && liveActiveBurn > 0) {
            BurnCaption(
                active = liveActiveBurn,
                modifier = capped,
                animateNumbers = animateNumbers,
            )
        }
    }
}

/**
 * Bowl interior at the burn-caption line. The centered arcs have no parent
 * width, so a long locale paints past the stroke unless the row is capped
 * here. 200dp sits inside the 260dp arc's chord; 156dp inside the 200dp arc.
 */
private val DefaultArcBurnCaptionBudget = 200.dp
private val CompactArcBurnCaptionBudget = 156.dp

/**
 * Default centered arc: 260dp circle with the eaten number in the bowl, the goal
 * and burn captions under it, the chips in the top-left wedge and the
 * calories-left caption in the top-right one.
 */
@Composable
private fun DefaultCalorieHero(input: CalorieHeroInput, modifier: Modifier = Modifier) {
    with(input) {
        val unit = LocalEnergyUnit.current
        val d = calorieHeroDerived(input)

        val budgetDescription = calorieBudgetDescription(input)
        val plannedCaption = plannedCaptionText(d.planned)
        val ringA11y = calorieArcContextDescription(input, d)
        val unitLabel = energyUnitLabel()
        var showBudgetSheet by remember { mutableStateOf(false) }
        // Ring color roles, one meaning per hue across every mode:
        // - trackColor (neutral) = unfilled budget, always.
        // - progressColor (primary) = consumed/eaten, ramped 20% toward white
        //   along the stroke. The flat primary is reused only by the
        //   consumed-tint pass over the burn bands. Planned stays on primary
        //   (0.35 ghost, 0.50 fill-end→planned-end); it does not get its own hue.
        // - tertiary = activity-derived calories, exclusively — the estimate zone
        //   (fixed tint) and the live burn shade (opaque) share it.
        // - success = over-typical active burn.
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
        val progressEndColor = heroGradientEnd(progressColor)
        val bonusColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        val liveBurnColor = MaterialTheme.colorScheme.tertiary
        val tertiary = MaterialTheme.colorScheme.tertiary
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val successColor = MaterialTheme.colorScheme.success

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The half-circle leaves an empty wedge at each top corner: the chips
            // sit in the left one, the calories-left caption in the right one.
            val sideMargin = ((maxWidth - 260.dp) / 2).coerceIn(0.dp, 200.dp)
            val cornerWidth = sideMargin + 48.dp
            val showDayTypeChip = dayTypeLabel != null && onDayTypeClick != null
            val showUntrackedChip = onUntrackedClick != null
            val showsChips = showDayTypeChip || showUntrackedChip
            // Exactly one chip: hang its 48dp touch target into the empty wedge
            // (topBand) so the pill's top stays level with the remaining text
            // without growing the arc box. Two chips stack at natural height.
            val singleChip = showDayTypeChip != showUntrackedChip
            val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))

            CalorieArcCanvas(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(2f),
                stroke = 16.dp,
                fillRatio = d.fillRatio,
                plannedFillRatio = d.plannedFillRatio,
                freezeProgress = freezeProgress,
                trackColor = trackColor,
                progressColor = progressColor,
                progressEndColor = progressEndColor,
                bonusColor = bonusColor,
                liveBurnColor = liveBurnColor,
                successColor = successColor,
                shadesActive = d.shadesActive,
                shade = burnShade,
                baseGoal = baseGoal,
                effectiveGoal = d.effectiveGoal,
                displayMode = displayMode,
                semanticsDescription = ringA11y,
            )

            // Chips sit under the section's "Today" label, in the empty wedge at
            // the arc's top left. Stacked, not side by side: the wedge narrows
            // downward, so each chip is capped to its width.
            if (showsChips) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .widthIn(max = (cornerWidth - 16.dp).coerceAtLeast(48.dp))
                        .then(if (singleChip) Modifier.topBand(24.dp) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (dayTypeLabel != null && onDayTypeClick != null) {
                        DayTypeChip(
                            label = dayTypeLabel,
                            color = dayTypeColor,
                            onClick = onDayTypeClick,
                            modifier = if (showUntrackedChip) Modifier
                            else Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                    if (onUntrackedClick != null) {
                        UntrackedChip(
                            untracked = untracked,
                            onClick = onUntrackedClick,
                            modifier = if (showDayTypeChip) Modifier
                            else Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }

            // Bowl stack inside the fixed 130dp canvas: 28dp above the number
            // when the burn caption is off, 20dp when it is on (its own 4dp pad
            // sits under the goal box), so both states clear the lower stroke.
            val burnCaptionVisible = d.shadesActive || (showActiveCalories && liveActiveBurn > 0)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (burnCaptionVisible) 20.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroFigureText(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    animate = !freezeProgress,
                    modifier = Modifier.widthIn(max = 140.dp),
                    contentDescription = eatenA11y,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 30.sp, maxFontSize = 45.sp, stepSize = 1.sp),
                )
                // Goal caption directly under the eaten number; the burn caption
                // follows it (see CalorieBurnCaption below).
                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (budgetDescription != null) {
                        BudgetGoalLine(
                            goalText = stringResource(
                                R.string.home_calorie_of_goal,
                                LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                                unitLabel,
                            ),
                            color = muted,
                            tint = tertiary,
                            onOpen = { if (!preview) showBudgetSheet = true },
                            description = budgetDescription,
                            maxTextWidth = 180.dp,
                            animateNumbers = !freezeProgress,
                        )
                    } else {
                        HeroFigureText(
                            text = stringResource(
                                R.string.home_calorie_of_goal,
                                LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                                unitLabel,
                            ),
                            animate = !freezeProgress,
                            modifier = Modifier.widthIn(max = 200.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = muted,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 8.sp,
                                maxFontSize = 12.sp,
                                stepSize = 0.5.sp,
                            ),
                        )
                    }
                }
                CalorieBurnCaption(
                    input = input,
                    shadesActive = d.shadesActive,
                    modifier = Modifier.padding(top = 4.dp),
                    maxWidth = DefaultArcBurnCaptionBudget,
                    animateNumbers = !freezeProgress,
                )
            }

            // Calories left, top-right wedge. Planned caption stacks under it;
            // the column is no longer pinned to 48dp — the wedge is taller than
            // the two lines need.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HeroFigureText(
                    text = stringResource(
                        R.string.home_calories_left,
                        LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                    ),
                    animate = !freezeProgress,
                    modifier = Modifier.widthIn(max = (cornerWidth - 16.dp).coerceAtLeast(48.dp)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 8.sp,
                        maxFontSize = 12.sp,
                        stepSize = 0.5.sp,
                    ),
                )
                plannedCaption?.let { caption ->
                    PlannedCaptionText(
                        text = caption,
                        color = muted,
                        fontSize = 12.sp,
                        maxTextWidth = (cornerWidth - 16.dp).coerceAtLeast(48.dp),
                        animate = !freezeProgress,
                    )
                }
            }
        }
        if (showBudgetSheet) {
            BudgetExplanationDialog(
                goal = baseGoal,
                active = activeCalories,
                typical = burnShade?.typical,
                typicalIsDayType = burnShade?.typicalIsDayType == true,
                typicalDayTypeName = burnShade?.typicalDayTypeName,
                blendedTypical = burnShade?.blendedTypical,
                source = activeCalorieSource,
                burnedToday = restingBurn?.let { it + liveActiveBurn },
                onDismiss = { showBudgetSheet = false },
                recalcDetailsAvailable = recalcDetailsAvailable && onShowRecalcDetails != null,
                onShowRecalcDetails = onShowRecalcDetails,
                goalDerivation = goalDerivation,
                planned = planned,
            )
        }
    }
}

/** Side-gauge hero: half-arc bowl on the left, numbers and captions on the right. */
@Composable
private fun SideGaugeCalorieHero(input: CalorieHeroInput, modifier: Modifier = Modifier) {
    with(input) {
        val unit = LocalEnergyUnit.current
        val d = calorieHeroDerived(input)

        val budgetDescription = calorieBudgetDescription(input)
        val plannedCaption = plannedCaptionText(d.planned)
        val ringA11y = calorieArcContextDescription(input, d)
        val unitLabel = energyUnitLabel()
        val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
        var showBudgetSheet by remember { mutableStateOf(false) }
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
        val progressEndColor = heroGradientEnd(progressColor)
        val bonusColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        val liveBurnColor = MaterialTheme.colorScheme.tertiary
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val tertiary = MaterialTheme.colorScheme.tertiary
        val successColor = MaterialTheme.colorScheme.success

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f),
            ) {
                CalorieArcCanvas(
                    modifier = Modifier.matchParentSize(),
                    stroke = 10.dp,
                    fillRatio = d.fillRatio,
                    plannedFillRatio = d.plannedFillRatio,
                    freezeProgress = freezeProgress,
                    trackColor = trackColor,
                    progressColor = progressColor,
                    progressEndColor = progressEndColor,
                    bonusColor = bonusColor,
                    liveBurnColor = liveBurnColor,
                    successColor = successColor,
                    shadesActive = d.shadesActive,
                    shade = burnShade,
                    baseGoal = baseGoal,
                    effectiveGoal = d.effectiveGoal,
                    displayMode = displayMode,
                    semanticsDescription = ringA11y,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HeroFigureText(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    animate = !freezeProgress,
                    modifier = Modifier.widthIn(max = 140.dp),
                    contentDescription = eatenA11y,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 20.sp,
                        maxFontSize = 28.sp,
                        stepSize = 1.sp,
                    ),
                )
                // Goal directly under the number, as in the centered hero.
                if (displayMode == HomeCalorieDisplayMode.ADD_ACTIVE) {
                    BudgetGoalLine(
                        goalText = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                            unitLabel,
                        ),
                        color = muted,
                        tint = tertiary,
                        onOpen = { if (!preview) showBudgetSheet = true },
                        description = budgetDescription,
                        maxTextWidth = 180.dp,
                        animateNumbers = !freezeProgress,
                    )
                } else {
                    HeroFigureText(
                        text = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                            unitLabel,
                        ),
                        animate = !freezeProgress,
                        modifier = Modifier.widthIn(max = 180.dp),
                        fontSize = 12.sp,
                        color = muted,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 12.sp,
                            stepSize = 0.5.sp,
                        ),
                    )
                }
                // FlowRow: the capture row keeps its chips beside the caption at 1x
                // and wraps them under it at 1.3x instead of squeezing the caption.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    HeroFigureText(
                        text = stringResource(
                            R.string.home_calories_left,
                            LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                        ),
                        animate = !freezeProgress,
                        modifier = Modifier.widthIn(max = 130.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = 13.sp,
                            stepSize = 0.5.sp,
                        ),
                    )
                    plannedCaption?.let { caption ->
                        PlannedCaptionText(
                            text = caption,
                            color = muted,
                            fontSize = 12.sp,
                            maxTextWidth = 130.dp,
                            animate = !freezeProgress,
                        )
                    }
                    if (dayTypeLabel != null && onDayTypeClick != null) {
                        DayTypeChip(label = dayTypeLabel, color = dayTypeColor, onClick = onDayTypeClick)
                    }
                    if (onUntrackedClick != null) {
                        UntrackedChip(untracked = untracked, onClick = onUntrackedClick)
                    }
                }
                CalorieBurnCaption(
                    input = input,
                    shadesActive = d.shadesActive,
                    animateNumbers = !freezeProgress,
                )
            }
        }
        if (showBudgetSheet) {
            BudgetExplanationDialog(
                goal = baseGoal,
                active = activeCalories,
                typical = burnShade?.typical,
                typicalIsDayType = burnShade?.typicalIsDayType == true,
                typicalDayTypeName = burnShade?.typicalDayTypeName,
                blendedTypical = burnShade?.blendedTypical,
                source = activeCalorieSource,
                burnedToday = restingBurn?.let { it + liveActiveBurn },
                onDismiss = { showBudgetSheet = false },
                recalcDetailsAvailable = recalcDetailsAvailable && onShowRecalcDetails != null,
                onShowRecalcDetails = onShowRecalcDetails,
                goalDerivation = goalDerivation,
                planned = planned,
            )
        }
    }
}

/** Shrunken centered arc: the tall hero's composition at 200dp with a 12dp stroke. */
@Composable
private fun CompactArcCalorieHero(input: CalorieHeroInput, modifier: Modifier = Modifier) {
    with(input) {
        val unit = LocalEnergyUnit.current
        val d = calorieHeroDerived(input)

        val budgetDescription = calorieBudgetDescription(input)
        val plannedCaption = plannedCaptionText(d.planned)
        val ringA11y = calorieArcContextDescription(input, d)
        val unitLabel = energyUnitLabel()
        val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
        var showBudgetSheet by remember { mutableStateOf(false) }
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
        val progressEndColor = heroGradientEnd(progressColor)
        val bonusColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        val liveBurnColor = MaterialTheme.colorScheme.tertiary
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val tertiary = MaterialTheme.colorScheme.tertiary
        val successColor = MaterialTheme.colorScheme.success

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 200dp arc (r=100): the caption line's bottom leaves a narrower
            // wedge than the 260dp hero, so the corner caps scale with it.
            val sideMargin = ((maxWidth - 200.dp) / 2).coerceIn(0.dp, 200.dp)
            val cornerWidth = sideMargin + 40.dp
            val showDayTypeChip = dayTypeLabel != null && onDayTypeClick != null
            val showUntrackedChip = onUntrackedClick != null
            val showsChips = showDayTypeChip || showUntrackedChip
            // Exactly one chip: hang its 48dp touch target into the empty wedge
            // (topBand) without growing the arc box; two stack at natural height.
            val singleChip = showDayTypeChip != showUntrackedChip

            CalorieArcCanvas(
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(2f),
                stroke = 12.dp,
                fillRatio = d.fillRatio,
                plannedFillRatio = d.plannedFillRatio,
                freezeProgress = freezeProgress,
                trackColor = trackColor,
                progressColor = progressColor,
                progressEndColor = progressEndColor,
                bonusColor = bonusColor,
                liveBurnColor = liveBurnColor,
                successColor = successColor,
                shadesActive = d.shadesActive,
                shade = burnShade,
                baseGoal = baseGoal,
                effectiveGoal = d.effectiveGoal,
                displayMode = displayMode,
                semanticsDescription = ringA11y,
            )

            if (showsChips) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .widthIn(max = (cornerWidth - 16.dp).coerceAtLeast(40.dp))
                        .then(if (singleChip) Modifier.topBand(24.dp) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (dayTypeLabel != null && onDayTypeClick != null) {
                        DayTypeChip(
                            label = dayTypeLabel,
                            color = dayTypeColor,
                            onClick = onDayTypeClick,
                            modifier = if (showUntrackedChip) Modifier
                            else Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                    if (onUntrackedClick != null) {
                        UntrackedChip(
                            untracked = untracked,
                            onClick = onUntrackedClick,
                            modifier = if (showDayTypeChip) Modifier
                            else Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroFigureText(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    animate = !freezeProgress,
                    modifier = Modifier.widthIn(max = 120.dp),
                    contentDescription = eatenA11y,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 20.sp,
                        maxFontSize = 28.sp,
                        stepSize = 1.sp,
                    ),
                )
                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (budgetDescription != null) {
                        BudgetGoalLine(
                            goalText = stringResource(
                                R.string.home_calorie_of_goal,
                                LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                                unitLabel,
                            ),
                            color = muted,
                            tint = tertiary,
                            onOpen = { if (!preview) showBudgetSheet = true },
                            description = budgetDescription,
                            maxTextWidth = 130.dp,
                            animateNumbers = !freezeProgress,
                        )
                    } else {
                        HeroFigureText(
                            text = stringResource(
                                R.string.home_calorie_of_goal,
                                LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                                unitLabel,
                            ),
                            animate = !freezeProgress,
                            modifier = Modifier.widthIn(max = 150.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = muted,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 8.sp,
                                maxFontSize = 12.sp,
                                stepSize = 0.5.sp,
                            ),
                        )
                    }
                }
                CalorieBurnCaption(
                    input = input,
                    shadesActive = d.shadesActive,
                    maxWidth = CompactArcBurnCaptionBudget,
                    animateNumbers = !freezeProgress,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .height(44.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(12.dp),
                    )
                    HeroFigureText(
                        text = stringResource(
                            R.string.home_calories_left,
                            LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                        ),
                        animate = !freezeProgress,
                        modifier = Modifier.widthIn(max = (cornerWidth - 15.dp).coerceAtLeast(40.dp)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 12.sp,
                            stepSize = 0.5.sp,
                        ),
                    )
                }
                plannedCaption?.let { caption ->
                    PlannedCaptionText(
                        text = caption,
                        color = muted,
                        fontSize = 12.sp,
                        maxTextWidth = (cornerWidth - 16.dp).coerceAtLeast(40.dp),
                        animate = !freezeProgress,
                    )
                }
            }
        }
        if (showBudgetSheet) {
            BudgetExplanationDialog(
                goal = baseGoal,
                active = activeCalories,
                typical = burnShade?.typical,
                typicalIsDayType = burnShade?.typicalIsDayType == true,
                typicalDayTypeName = burnShade?.typicalDayTypeName,
                blendedTypical = burnShade?.blendedTypical,
                source = activeCalorieSource,
                burnedToday = restingBurn?.let { it + liveActiveBurn },
                onDismiss = { showBudgetSheet = false },
                recalcDetailsAvailable = recalcDetailsAvailable && onShowRecalcDetails != null,
                onShowRecalcDetails = onShowRecalcDetails,
                goalDerivation = goalDerivation,
                planned = planned,
            )
        }
    }
}

@Composable
private fun CompactHorizontalCalorieHero(input: CalorieHeroInput, modifier: Modifier = Modifier) {
    with(input) {
        val unit = LocalEnergyUnit.current
        val d = calorieHeroDerived(input)
        val bands = rememberHeroBandAnims(
            heroBandTargets(
                eaten = d.fillRatio,
                planned = d.plannedFillRatio,
                shadesActive = d.shadesActive,
                shade = burnShade,
                baseGoal = baseGoal,
                target = d.target,
                displayMode = displayMode,
            ),
            freezeProgress,
        )
        val eatenAnim = bands.eaten
        val plannedAnim = bands.planned
        val baseAnim = bands.base
        val typicalAnim = bands.typical
        val liveAnim = bands.live
        val baseTarget = bands.targets.base
        val typicalTarget = bands.targets.typical
        val liveTarget = bands.targets.live
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
        val progressEndColor = heroGradientEnd(progressColor)
        val activeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val tertiary = MaterialTheme.colorScheme.tertiary
        val successColor = MaterialTheme.colorScheme.success
        val unitLabel = energyUnitLabel()
        val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
        val budgetDescription = calorieBudgetDescription(input)
        val plannedCaption = plannedCaptionText(d.planned)
        val ringA11y = calorieArcContextDescription(input, d)
        var showBudgetSheet by remember { mutableStateOf(false) }

        val eatenBrush = remember(progressColor, progressEndColor) {
            Brush.horizontalGradient(listOf(progressColor, progressEndColor))
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .semantics {
                        // TalkBack tracks the spring. The description stays the
                        // settled day, same as the arcs.
                        val eatenNow = if (freezeProgress) d.fillRatio else eatenAnim.value
                        progressBarRangeInfo = ProgressBarRangeInfo(eatenNow, 0f..1f, 0)
                        ringA11y?.let { contentDescription = it }
                    }
            ) {
                val eatenFrac = if (freezeProgress) d.fillRatio else eatenAnim.value
                val plannedFrac = if (freezeProgress) d.plannedFillRatio else plannedAnim.value
                val baseFrac = if (freezeProgress) baseTarget else baseAnim.value
                val typicalFrac = if (freezeProgress) typicalTarget else typicalAnim.value
                val liveFrac = if (freezeProgress) liveTarget else liveAnim.value
                val radius = size.height / 2f
                drawRoundRect(trackColor, cornerRadius = CornerRadius(radius, radius))
                if (plannedFrac > eatenFrac) {
                    drawRoundRect(
                        progressColor.copy(alpha = 0.35f),
                        size = Size(size.width * plannedFrac, size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    val fillX = size.width * eatenFrac
                    val plannedX = size.width * plannedFrac
                    val segW = plannedX - fillX
                    if (segW > 0f) {
                        // Square left meets the eaten bar at full height.
                        // Round right is the planned end.
                        drawPath(
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(fillX, 0f, plannedX, size.height),
                                        topRight = CornerRadius(radius, radius),
                                        bottomRight = CornerRadius(radius, radius),
                                    ),
                                )
                            },
                            progressColor.copy(alpha = PlannedSegmentAlpha),
                        )
                    }
                }
                val eatenRight = size.width * eatenFrac
                val plannedFollows = plannedFrac > eatenFrac && eatenRight > 0f
                if (plannedFollows) {
                    // Square right end meets the planned stretch at full height.
                    // A round tip here leaves corner wedges against that edge.
                    drawPath(
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(0f, 0f, eatenRight, size.height),
                                    topLeft = CornerRadius(radius, radius),
                                    bottomLeft = CornerRadius(radius, radius),
                                ),
                            )
                        },
                        eatenBrush,
                    )
                } else {
                    drawRoundRect(
                        brush = eatenBrush,
                        size = Size(eatenRight, size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
                // Shade widths spring to zero instead of popping off. While
                // either band is still visible, keep that stack and skip the
                // flat zone so the shared base boundary glides across the swap.
                val baseX = size.width * baseFrac
                val typicalPx = size.width * typicalFrac
                val livePx = size.width * liveFrac
                var boundaryX: Float? = null
                if (typicalPx > BarBandMinPx || livePx > BarBandMinPx) {
                    val typicalW = typicalPx.coerceAtMost((size.width - baseX).coerceAtLeast(0f))
                    val liveW = livePx
                    val withinTypicalW = liveW.coerceAtMost(typicalW)
                    val overW = (liveW - typicalW).coerceAtLeast(0f)
                        .coerceAtMost((size.width - baseX - typicalW).coerceAtLeast(0f))
                    // Estimated-active zone: fixed dim segment [base → base+typical].
                    // Round at the zone start; when the over-typical stretch
                    // follows, the right edge squares off and tucks up to half
                    // a cap underneath it so the shared boundary is one flush
                    // edge — with no stretch following, the rounded right end
                    // is the zone end.
                    if (typicalW > 0f) {
                        if (overW > 0f) {
                            drawPath(
                                Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            rect = Rect(
                                                baseX,
                                                0f,
                                                baseX + typicalW + kotlin.math.min(radius, overW),
                                                size.height,
                                            ),
                                            topLeft = CornerRadius(radius, radius),
                                            bottomLeft = CornerRadius(radius, radius),
                                        ),
                                    )
                                },
                                activeColor,
                            )
                        } else {
                            drawRoundRect(
                                activeColor,
                                topLeft = Offset(baseX, 0f),
                                size = Size(typicalW, size.height),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    }
                    // Live-active shade grows from the base boundary through the
                    // typical zone, shading over the estimated zone. The right
                    // end is round only when it is the zone end; when the
                    // over-typical stretch follows, it meets that band flush.
                    if (withinTypicalW > 0f) {
                        if (overW > 0f) {
                            drawPath(
                                Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            rect = Rect(baseX, 0f, baseX + withinTypicalW, size.height),
                                            topLeft = CornerRadius(radius, radius),
                                            bottomLeft = CornerRadius(radius, radius),
                                        ),
                                    )
                                },
                                tertiary,
                            )
                        } else {
                            drawRoundRect(
                                tertiary,
                                topLeft = Offset(baseX, 0f),
                                size = Size(withinTypicalW, size.height),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    }
                    // Over-typical stretch turns success color; the bar end is the
                    // expected-day target, so the live tip lands at the bar end.
                    // Square left edge joins the estimated zone flush over its
                    // tucked-under extension; the rounded right end is the zone
                    // end.
                    if (overW > 0f) {
                        drawPath(
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(baseX + typicalW, 0f, baseX + typicalW + overW, size.height),
                                        topRight = CornerRadius(radius, radius),
                                        bottomRight = CornerRadius(radius, radius),
                                    ),
                                )
                            },
                            successColor,
                        )
                    }
                    boundaryX = baseX
                } else if (baseX > BarBandMinPx) {
                    // Flat activity-earned zone, only once the shade stack has
                    // shrunk away. The same base animatable is the boundary.
                    drawRoundRect(
                        activeColor,
                        topLeft = Offset(baseX, 0f),
                        size = Size((size.width - baseX).coerceAtLeast(0f), size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    boundaryX = baseX
                }
                boundaryX?.let { zoneStart ->
                    // Consumed stretch of the activity zone: the same span the
                    // arc tints, drawn as a full round rect. Both ends are
                    // round — the left keeps the zone-start cap, the right is
                    // the eaten tip's own cap shape — so a partial eat inside
                    // a band ends round, never square.
                    val span = consumedShadeOverlaySpan(
                        zoneStart = zoneStart,
                        eatenEnd = size.width * eatenFrac,
                        arcEnd = size.width,
                    )
                    if (span != null) {
                        drawRoundRect(
                            progressColor.copy(alpha = 0.55f),
                            topLeft = Offset(span.start, 0f),
                            size = Size(span.sweep, size.height),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                }
            }
            // FlowRow: one line at 1x, wraps instead of squeezing to zero width
            // when localized labels grow at 1.3x on a narrow screen.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                HeroFigureText(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    animate = !freezeProgress,
                    modifier = Modifier.widthIn(max = 90.dp),
                    contentDescription = eatenA11y,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = progressColor,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = 22.sp,
                        stepSize = 1.sp,
                    ),
                )
                // One tap target for the budget sheet: the goal line itself.
                if (budgetDescription != null) {
                    BudgetGoalLine(
                        goalText = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                            unitLabel,
                        ),
                        color = muted,
                        tint = tertiary,
                        onOpen = { if (!preview) showBudgetSheet = true },
                        description = budgetDescription,
                        maxTextWidth = 140.dp,
                        animateNumbers = !freezeProgress,
                    )
                } else {
                    HeroFigureText(
                        text = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                            unitLabel,
                        ),
                        animate = !freezeProgress,
                        modifier = Modifier.widthIn(max = 140.dp),
                        fontSize = 13.sp,
                        color = muted,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 13.sp,
                            stepSize = 0.5.sp,
                        ),
                    )
                }
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = progressColor,
                    modifier = Modifier.size(13.dp),
                )
                HeroFigureText(
                    text = stringResource(
                        R.string.home_calories_left,
                        LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                    ),
                    animate = !freezeProgress,
                    modifier = Modifier.widthIn(max = 120.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = progressColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = 13.sp,
                        stepSize = 0.5.sp,
                    ),
                )
                // Planned caption parity with the arc heroes: only drawn when planned > 0.
                plannedCaption?.let { caption ->
                    PlannedCaptionText(
                        text = caption,
                        color = muted,
                        fontSize = 13.sp,
                        maxTextWidth = 120.dp,
                        animate = !freezeProgress,
                    )
                }
                // Chips are capped so their labels shrink instead of pushing the
                // row past the width on narrow screens at 1.3x.
                if (dayTypeLabel != null && onDayTypeClick != null) {
                    Box(Modifier.widthIn(max = 110.dp)) {
                        DayTypeChip(label = dayTypeLabel, color = dayTypeColor, onClick = onDayTypeClick)
                    }
                }
                if (onUntrackedClick != null) {
                    Box(Modifier.widthIn(max = 110.dp)) {
                        UntrackedChip(untracked = untracked, onClick = onUntrackedClick)
                    }
                }
            }
            // Burn caption parity with the arc heroes: only drawn when the user
            // asked for the live burn, so it costs nothing otherwise.
            CalorieBurnCaption(
                input = input,
                shadesActive = d.shadesActive,
                animateNumbers = !freezeProgress,
            )
        }
        if (showBudgetSheet) {
            BudgetExplanationDialog(
                goal = baseGoal,
                active = activeCalories,
                typical = burnShade?.typical,
                typicalIsDayType = burnShade?.typicalIsDayType == true,
                typicalDayTypeName = burnShade?.typicalDayTypeName,
                blendedTypical = burnShade?.blendedTypical,
                source = activeCalorieSource,
                burnedToday = restingBurn?.let { it + liveActiveBurn },
                onDismiss = { showBudgetSheet = false },
                recalcDetailsAvailable = recalcDetailsAvailable && onShowRecalcDetails != null,
                onShowRecalcDetails = onShowRecalcDetails,
                goalDerivation = goalDerivation,
                planned = planned,
            )
        }
    }
}

/**
 * Reports a fixed [height] band while letting the measured content hang past
 * it: the single-chip wedge keeps a 48dp touch target without growing the
 * arc box.
 */
private fun Modifier.topBand(height: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
    )
    layout(placeable.width, height.roundToPx()) { placeable.place(0, 0) }
}

/** Compact tappable pill beside the hero's remaining caption: today's day type (#60). */
@Composable
private fun UntrackedChip(
    untracked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (untracked) R.string.home_untracked_chip else R.string.home_mark_untracked_chip,
    )
    val a11y = stringResource(
        if (untracked) R.string.home_untracked_chip_a11y else R.string.home_mark_untracked_chip_a11y,
    )
    val chipInteraction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CapsuleShape,
        color = if (untracked) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        interactionSource = chipInteraction,
        // No minimumInteractiveComponentSize here: it centers the pill in a
        // 48dp slot, dropping it below the remaining-caption baseline. The
        // arc heroes restore the 48dp touch via topBand on the single chip.
        modifier = modifier
            .pressScale(chipInteraction)
            .widthIn(max = 140.dp)
            .semantics { contentDescription = a11y },
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (untracked) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Wedge-capped in the arc layouts: shrink the label instead of
            // ellipsizing it at 1.3x.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 11.sp,
                stepSize = 0.5.sp,
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DayTypeChip(
    label: String,
    color: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.home_day_type_chip_a11y, label)
    // Pinned color wins over the tonal container; light colors flip text dark.
    val container = color ?: MaterialTheme.colorScheme.secondaryContainer
    val content = if (color != null) {
        if (container.luminance() > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val chipInteraction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CapsuleShape,
        color = container,
        interactionSource = chipInteraction,
        // Same reasoning as UntrackedChip: no 48dp slot, the pill aligns with
        // the remaining caption; topBand restores the touch where it fits.
        modifier = modifier
            .pressScale(chipInteraction)
            // The chip shares the remaining-caption line: cap it so a long
            // day-type name ellipsizes instead of pushing the caption around.
            .widthIn(max = 140.dp)
            .semantics { contentDescription = a11y },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Wedge-capped in the arc layouts: shrink the label instead of
                // ellipsizing it at 1.3x.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 11.sp,
                    stepSize = 0.5.sp,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun BurnCaption(
    active: Int,
    modifier: Modifier = Modifier,
    animateNumbers: Boolean = false,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val activeQ = EnergyFormat.quantity(active, LocalEnergyUnit.current)
    val a11y = stringResource(R.string.home_active_burn_a11y, activeQ)
    val caption = stringResource(R.string.home_active_burn_caption, activeQ)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.semantics { contentDescription = a11y }
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = tertiary,
            modifier = Modifier.size(11.dp)
        )
        if (animateNumbers) {
            HeroFigureText(
                text = caption,
                animate = true,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tertiary,
                maxLines = Int.MAX_VALUE,
            )
        } else {
            Text(
                caption,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tertiary,
            )
        }
    }
}

/** Energy-balance caption: live active vs the day's active norm, plus the over-typical state. */
@Composable
private fun BurnShadeCaption(
    burn: ActiveBurnShade,
    modifier: Modifier = Modifier,
    center: Boolean = false,
    animateNumbers: Boolean = false,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val success = MaterialTheme.colorScheme.success
    val unit = LocalEnergyUnit.current
    val liveQ = EnergyFormat.quantity(burn.live, unit)
    val typicalQ = EnergyFormat.quantity(burn.typical, unit)
    val overQ = EnergyFormat.quantity(burn.live - burn.typical, unit)
    val over = HomeCalorieDisplay.isActiveBurnOverTypical(burn.live, burn.typical)
    val dayType = burn.typicalDayTypeName.takeIf { burn.typicalIsDayType && !it.isNullOrBlank() }
    val a11y = burnShadeA11y(burn)
    val progressText = if (dayType != null) {
        stringResource(R.string.home_active_burn_caption_progress_day_type, liveQ, typicalQ, dayType)
    } else {
        stringResource(R.string.home_active_burn_caption_progress, liveQ, typicalQ)
    }
    val progressAutoSize = TextAutoSize.StepBased(
        minFontSize = 8.sp,
        maxFontSize = 12.sp,
        stepSize = 0.5.sp,
    )
    // weight needs a finite max. The arc passes widthIn; the side gauge and
    // the bar are already bounded by their parent. A long day-type name
    // shrinks, then ellipsizes, instead of painting past the arc.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            5.dp,
            if (center) Alignment.CenterHorizontally else Alignment.Start,
        ),
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = tertiary,
            modifier = Modifier.size(11.dp),
        )
        if (animateNumbers) {
            HeroFigureText(
                text = progressText,
                animate = true,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                autoSize = progressAutoSize,
            )
        } else {
            Text(
                progressText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tertiary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = progressAutoSize,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (over) {
            val overText = stringResource(R.string.home_active_burn_over, overQ)
            if (animateNumbers) {
                HeroFigureText(
                    text = overText,
                    animate = true,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = success,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Text(
                    overText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = success,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun BudgetExplanationDialog(
    goal: Int,
    active: Int,
    typical: Int? = null,
    typicalIsDayType: Boolean = false,
    typicalDayTypeName: String? = null,
    blendedTypical: Int? = null,
    source: ActiveCalorieSource?,
    burnedToday: Int? = null,
    onDismiss: () -> Unit,
    recalcDetailsAvailable: Boolean = false,
    onShowRecalcDetails: (() -> Unit)? = null,
    goalDerivation: GoalDerivation? = null,
    planned: Int = 0,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unit = LocalEnergyUnit.current
    val unitLabel = energyUnitLabel()
    ChompassDialog(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.home_calorie_budget_sheet_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Expected day: sedentary base + the usual active burn (typical). On
            // estimate-only days typical is null and the row falls back to the
            // effective budget, which equals the target there.
            Text(
                stringResource(
                    R.string.home_calories_goal_plus_active,
                    LocaleFormat.integer(EnergyFormat.quantity(goal, unit)),
                    LocaleFormat.integer(EnergyFormat.quantity(typical ?: active, unit)),
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            when (source) {
                ActiveCalorieSource.MEASURED -> Text(
                    stringResource(R.string.home_calorie_budget_source_measured),
                    fontSize = 13.sp,
                    color = muted,
                )
                ActiveCalorieSource.ESTIMATED -> Text(
                    stringResource(R.string.home_calorie_active_estimated_a11y),
                    fontSize = 13.sp,
                    color = muted,
                )
                ActiveCalorieSource.MANUAL -> Text(
                    stringResource(R.string.home_calorie_budget_source_manual),
                    fontSize = 13.sp,
                    color = muted,
                )
                ActiveCalorieSource.UNAVAILABLE, null -> {}
            }
            if (typicalIsDayType && typicalDayTypeName != null && typical != null && blendedTypical != null && blendedTypical > 0) {
                Text(
                    stringResource(
                        R.string.home_calorie_budget_day_type_typical,
                        typicalDayTypeName,
                        EnergyFormat.quantity(typical, unit),
                        EnergyFormat.quantity(blendedTypical, unit),
                        unitLabel,
                    ),
                    fontSize = 13.sp,
                    color = muted,
                )
            }
            if (burnedToday != null) {
                Text(
                    stringResource(R.string.home_budget_burned_today, EnergyFormat.quantity(burnedToday, unit), unitLabel),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                stringResource(R.string.home_budget_legend_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
            ) {
                val eaten = MaterialTheme.colorScheme.primary
                val rampEnd = heroGradientEnd(eaten)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.width(18.dp).height(3.dp)) {
                        drawLine(
                            brush = Brush.horizontalGradient(listOf(eaten, rampEnd)),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = size.height,
                        )
                    }
                    Text(
                        stringResource(R.string.home_budget_legend_eaten),
                        fontSize = 12.sp,
                        color = muted,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 12.sp,
                            stepSize = 0.5.sp,
                        ),
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
                if (planned > 0) {
                    LegendSwatch(
                        color = eaten.copy(alpha = PlannedSegmentAlpha),
                        label = stringResource(R.string.diary_planned),
                    )
                }
                LegendSwatch(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
                    label = stringResource(R.string.home_budget_legend_estimated_active),
                )
                LegendSwatch(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.home_budget_legend_live_burn),
                )
                LegendSwatch(
                    color = MaterialTheme.colorScheme.success,
                    label = stringResource(R.string.home_budget_legend_over_typical),
                )
            }
            // Standing goal derivation (checkable chain): where today's target
            // came from — same baseline the recalc sheet shows, but computed
            // from the live profile so it is always current.
            goalDerivation?.let { d ->
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Text(
                    stringResource(R.string.recalc_sheet_formula_baseline),
                    fontSize = 13.sp,
                    color = muted,
                )
                Text(
                    stringResource(
                        R.string.recalc_sheet_bmr_row,
                        LocaleFormat.integer(EnergyFormat.quantity(d.bmrKcal, unit)),
                        unitLabel,
                    ),
                    fontSize = 13.sp,
                    color = muted,
                )
                Text(
                    stringResource(
                        R.string.recalc_sheet_tdee_row,
                        GoalFormulaReference.formatMultiplier(d.activityMultiplier),
                        LocaleFormat.integer(EnergyFormat.quantity(d.tdeeKcal, unit)),
                        unitLabel,
                    ),
                    fontSize = 13.sp,
                    color = muted,
                )
                Text(
                    stringResource(
                        R.string.recalc_sheet_pace_row,
                        signedKcal(EnergyFormat.quantity(d.paceKcal, unit)),
                        unitLabel,
                    ),
                    fontSize = 13.sp,
                    color = muted,
                )
                Text(
                    stringResource(
                        R.string.recalc_sheet_formula_target_row,
                        LocaleFormat.integer(EnergyFormat.quantity(d.formulaTargetKcal, unit)),
                        unitLabel,
                    ),
                    fontSize = 13.sp,
                    color = muted,
                )
                if (d.formulaClamped) {
                    Text(
                        stringResource(
                            R.string.home_budget_clamped_row,
                            LocaleFormat.integer(EnergyFormat.quantity(d.rawTargetKcal, unit)),
                            LocaleFormat.integer(EnergyFormat.quantity(d.formulaTargetKcal, unit)),
                        ),
                        fontSize = 13.sp,
                        color = muted,
                    )
                    Text(
                        if (d.rawTargetKcal < d.formulaTargetKcal) {
                            stringResource(R.string.home_budget_clamped_floor_hint)
                        } else {
                            stringResource(
                                R.string.home_budget_clamped_ceiling_hint,
                                LocaleFormat.integer(EnergyFormat.quantity(d.ceilingKcal, unit)),
                            )
                        },
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
                val bfAge = d.bfAgeDays
                if (bfAge != null && bfAge > NutritionConstants.BODY_FAT_RECENCY_DAYS) {
                    Text(
                        stringResource(R.string.home_budget_bf_stale, bfAge.toInt()),
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
            }
            // Hero ⓘ → recalc details: one tappable row into the persisted goal-change
            // explanation (AI Recalculate or Adaptive) — closes this dialog first.
            if (recalcDetailsAvailable) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onShowRecalcDetails?.invoke()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_recalc_details),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )
    }
}

// ── Macro card ───────────────────────────────────────────────────────

// MacroCard lives in ui/components/MacroCard.kt; imported above.

@Composable
internal fun ViewMoreButton() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .padding(horizontal = 10.dp)
    ) {
        Text(
            stringResource(R.string.home_view_more),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Signed kcal for the derivation rows: "+250" / "-250" / "0" (mirrors RecalcResultSheet). */
private fun signedKcal(value: Int): String =
    if (value > 0) "+" + LocaleFormat.integer(value) else LocaleFormat.integer(value)

/**
 * Consumed overlay extent over the activity zone, shared by the arc and the
 * bar: the eaten extent clipped to the zone — [zoneStart, eatenEnd], with the
 * bar passing pixel x values and the arc ring degrees. Both surfaces draw the
 * span with round ends ([StrokeCap.Round] stroke on the arc, full round rect
 * on the bar), so the caps themselves cover the zone-start cap behind the
 * span and, ahead of it, the eaten tip and any band cap it has reached. A
 * partial eat never ends square, and no cap bookkeeping feeds the span.
 * Returns null before the zone is reached.
 */
internal data class ShadeOverlaySpan(val start: Float, val sweep: Float)

internal fun consumedShadeOverlaySpan(
    zoneStart: Float,
    eatenEnd: Float,
    arcEnd: Float,
): ShadeOverlaySpan? {
    if (eatenEnd <= zoneStart) return null
    val sweep = eatenEnd.coerceAtMost(arcEnd) - zoneStart
    if (sweep <= 0f) return null
    return ShadeOverlaySpan(zoneStart, sweep)
}
