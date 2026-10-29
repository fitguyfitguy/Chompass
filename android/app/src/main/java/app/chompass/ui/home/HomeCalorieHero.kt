package app.chompass.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppMotion
import app.chompass.ui.theme.success

// ── Calorie hero ─────────────────────────────────────────────────────

/**
 * Verbatim port of the calorie hero block in HomeView.body
 * (ios/calorietracker/ContentView.swift, lines ~322–362):
 *
 *   VStack(spacing: 20) {
 *     VStack(spacing: 4) {
 *       Text("\(selectedCalories)")
 *         .font(.system(size: 72, weight: .bold, design: .rounded))
 *         .foregroundStyle(LinearGradient(colors: AppColors.calorieGradient,
 *                                         startPoint: .topLeading,
 *                                         endPoint: .bottomTrailing))
 *         .contentTransition(.numericText())
 *         .animation(.snappy, value: selectedCalories)
 *       Text("of \(calorieGoal) kcal")
 *         .font(.system(.callout, design: .rounded, weight: .medium))
 *         .foregroundStyle(.tertiary)
 *     }
 *     GeometryReader { geo in
 *       ZStack(alignment: .leading) {
 *         Capsule().fill(AppColors.calorie.opacity(0.10)).frame(height: 10)
 *         Capsule().fill(LinearGradient(.leading, .trailing))
 *                  .frame(width: max(10, geo.size.width * progress), height: 10)
 *                  .shadow(color: AppColors.calorie.opacity(0.35), radius: 8, y: 3)
 *                  .animation(.spring(response: 0.8, dampingFraction: 0.75), value: selectedCalories)
 *       }
 *     }.frame(height: 10).padding(.horizontal, 24)
 *     Text("\(caloriesRemaining) left")
 *       .font(.system(.footnote, design: .rounded, weight: .medium))
 *       .foregroundStyle(.secondary)
 *   }
 *   .padding(.vertical, 20)
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

/**
 * The hero's 180° arc as one shared canvas: a circle at 2:1 or a shallow
 * ellipse on wider canvases. The oval spans the width minus the stroke and
 * exactly twice the height, so the diameter sits on the canvas's bottom edge.
 * Fills, the planned ghost, burn shades, the ADD_ACTIVE bonus tail, and the
 * base-boundary notch are angle-based and identical for every variant; only
 * the canvas size and stroke differ.
 */
@Composable
private fun CalorieArcCanvas(
    modifier: Modifier,
    stroke: Dp,
    fillRatio: Float,
    plannedFillRatio: Float,
    freezeProgress: Boolean,
    animatedRatio: Animatable<Float, AnimationVector1D>,
    trackColor: Color,
    progressColor: Color,
    bonusColor: Color,
    liveBurnColor: Color,
    successColor: Color,
    muted: Color,
    shadesActive: Boolean,
    shade: ActiveBurnShade?,
    baseGoal: Int,
    effectiveGoal: Int,
    displayMode: HomeCalorieDisplayMode,
    activeCalories: Int,
    semanticsRatio: Float,
) {
    Canvas(
        modifier = modifier.semantics {
            // The budget sheet carries the exact numbers; the arc still
            // reports its fill so TalkBack users get progress (UI-audit 2.7).
            progressBarRangeInfo = ProgressBarRangeInfo(semanticsRatio, 0f..1f, 0)
        }
    ) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2f
        val arcSize = Size(size.width - strokePx, 2 * size.height - strokePx)
        val topLeft = Offset(inset, inset)
        val cx = size.width / 2f
        val cy = size.height
        // Notch endpoints ride the ellipse's stroke band: semi-axes at the
        // mid-radius, pulled 2dp inside the band edges.
        val aMid = arcSize.width / 2f
        val bMid = arcSize.height / 2f
        val aIn = aMid - strokePx / 2f + 2.dp.toPx()
        val aOut = aMid + strokePx / 2f - 2.dp.toPx()
        val bIn = bMid - strokePx / 2f + 2.dp.toPx()
        val bOut = bMid + strokePx / 2f - 2.dp.toPx()
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
        // the active zone. Notch still last.
        var notchAngle: Float? = null
        if (plannedFillRatio > fillRatio) {
            drawArc(
                color = progressColor.copy(alpha = 0.35f),
                startAngle = 180f,
                sweepAngle = 180f * plannedFillRatio.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        drawArc(
            color = progressColor,
            startAngle = 180f,
            sweepAngle = 180f * (if (freezeProgress) fillRatio else animatedRatio.value),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        if (shadesActive && shade != null) {
            // Single energy scale, one meaning per shade:
            //  - estimated-active zone = fixed dim segment [base → base+typical].
            //  - live-active shade = opaque, grows from the base boundary through
            //    the typical zone; the over-typical stretch turns success color.
            //  The arc end is the expected-day target and grows with live burn
            //  once it exceeds typical, so the live tip lands at the ring end.
            val typical = shade.typical
            val live = shade.live
            val baseAngle = 180f * HomeCalorieDisplay.burnShadeBaseFraction(baseGoal, typical, live)
            val typicalSweep = 180f * HomeCalorieDisplay.burnShadeTypicalFraction(baseGoal, typical, live)
            if (typicalSweep > 0f) {
                drawArc(
                    color = bonusColor,
                    startAngle = 180f + baseAngle,
                    sweepAngle = typicalSweep.coerceAtMost(180f - baseAngle),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            val liveSweep = 180f * HomeCalorieDisplay.burnShadeLiveFraction(baseGoal, live, typical)
            val liveMaxSweep = (180f - baseAngle).coerceAtLeast(0f)
            val withinTypicalSweep = liveSweep.coerceAtMost(typicalSweep)
            if (withinTypicalSweep > 0f) {
                drawArc(
                    color = liveBurnColor,
                    startAngle = 180f + baseAngle,
                    sweepAngle = withinTypicalSweep.coerceAtMost(liveMaxSweep),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            val overTypicalSweep = (liveSweep - typicalSweep).coerceAtLeast(0f)
            if (overTypicalSweep > 0f) {
                drawArc(
                    color = successColor,
                    startAngle = 180f + baseAngle + typicalSweep,
                    sweepAngle = overTypicalSweep.coerceAtMost((liveMaxSweep - typicalSweep).coerceAtLeast(0f)),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            notchAngle = 180f + baseAngle
        } else if (displayMode == HomeCalorieDisplayMode.ADD_ACTIVE && activeCalories > 0 && effectiveGoal > 0) {
            // Activity-earned zone: [baseGoal → effectiveGoal], a fixed-tint
            // segment on the same budget axis. Drawn after the eaten fill so
            // the tail stays visible when intake covers it. The tick at the
            // base-goal boundary marks where the static budget ends.
            val baseSweep = 180f * (baseGoal.toFloat() / effectiveGoal.toFloat()).coerceIn(0f, 1f)
            val tailSweep = (180f - baseSweep).coerceAtLeast(0f)
            if (tailSweep > 0f) {
                drawArc(
                    color = bonusColor,
                    startAngle = 180f + baseSweep,
                    sweepAngle = tailSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                notchAngle = 180f + baseSweep
            }
        }
        // Base-boundary notch, drawn on top of the eaten fill: where your
        // sedentary budget ends and the activity-earned zone begins. Riding
        // above the fill keeps the boundary readable even when eaten crosses
        // it — the fill visibly extends past the notch into the activity zone.
        notchAngle?.let { angle ->
            val notchRad = Math.toRadians(angle.toDouble())
            drawLine(
                color = muted,
                start = Offset(
                    cx + (aIn * Math.cos(notchRad)).toFloat(),
                    cy + (bIn * Math.sin(notchRad)).toFloat(),
                ),
                end = Offset(
                    cx + (aOut * Math.cos(notchRad)).toFloat(),
                    cy + (bOut * Math.sin(notchRad)).toFloat(),
                ),
                strokeWidth = 2.dp.toPx(),
            )
        }
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
) {
    val onClickLabel = stringResource(R.string.home_calorie_budget_info)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics(mergeDescendants = true) { description?.let { contentDescription = it } },
    ) {
        Text(
            goalText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            // Captions shrink rather than ellipsize: translations run long and
            // the bowl/column width is fixed at 1.3x font scale.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 12.sp,
                stepSize = 0.5.sp,
            ),
            modifier = if (maxTextWidth == Dp.Unspecified) {
                Modifier
            } else {
                Modifier.widthIn(max = maxTextWidth)
            },
        )
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

/** Burn caption line: shades when active, else the live burn the user asked to see. */
@Composable
private fun CalorieBurnCaption(input: CalorieHeroInput, shadesActive: Boolean) {
    with(input) {
        if (shadesActive) {
            BurnShadeCaption(burn = burnShade!!)
        } else if (showActiveCalories && liveActiveBurn > 0) {
            BurnCaption(active = liveActiveBurn)
        }
    }
}

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
        val animatedRatio = rememberArcAnimation(d.fillRatio, freezeProgress)
        val budgetDescription = calorieBudgetDescription(input)
        val unitLabel = energyUnitLabel()
        var showBudgetSheet by remember { mutableStateOf(false) }
        // Ring color roles, one meaning per hue across every mode:
        // - trackColor (neutral) = unfilled budget, always.
        // - progressColor (primary, opaque) = consumed/eaten. Never reused elsewhere.
        // - tertiary = activity-derived calories, exclusively — the estimate zone
        //   (fixed tint) and the live burn shade (opaque) share it.
        // - success = over-typical active burn.
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
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
            // The half-circle leaves an empty wedge at each top corner: the chips
            // sit in the left one, the calories-left caption in the right one.
            val sideMargin = ((maxWidth - 260.dp) / 2).coerceIn(0.dp, 200.dp)
            val cornerWidth = sideMargin + 48.dp
            val showsChips = (dayTypeLabel != null && onDayTypeClick != null) ||
                onUntrackedClick != null
            val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))

            CalorieArcCanvas(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(2f),
                stroke = 16.dp,
                fillRatio = d.fillRatio,
                plannedFillRatio = d.plannedFillRatio,
                freezeProgress = freezeProgress,
                animatedRatio = animatedRatio,
                trackColor = trackColor,
                progressColor = progressColor,
                bonusColor = bonusColor,
                liveBurnColor = liveBurnColor,
                successColor = successColor,
                muted = muted,
                shadesActive = d.shadesActive,
                shade = burnShade,
                baseGoal = baseGoal,
                effectiveGoal = d.effectiveGoal,
                displayMode = displayMode,
                activeCalories = activeCalories,
                semanticsRatio = animatedRatio.value,
            )

            // Chips sit under the section's "Today" label, in the empty wedge at
            // the arc's top left. Stacked, not side by side: the wedge narrows
            // downward, so each chip is capped to its width.
            if (showsChips) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .widthIn(max = (cornerWidth - 16.dp).coerceAtLeast(48.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (dayTypeLabel != null && onDayTypeClick != null) {
                        DayTypeChip(label = dayTypeLabel, color = dayTypeColor, onClick = onDayTypeClick)
                    }
                    if (onUntrackedClick != null) {
                        UntrackedChip(untracked = untracked, onClick = onUntrackedClick)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Counter swap: slide+fade when today's value changes
                // (AppMotion tokens; recipe from the design teardown,
                // docs/local/MORPHE_DESIGN_ANALYSIS.md §1.2).
                AnimatedContent(
                    targetState = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    transitionSpec = {
                        (fadeIn(tween(AppMotion.TweenDefaultMs, easing = AppMotion.EasingStandard)) +
                            slideInVertically(tween(AppMotion.TweenDefaultMs, easing = AppMotion.EasingStandard)) { it / 2 })
                            .togetherWith(
                                fadeOut(tween(AppMotion.TweenFastMs, easing = AppMotion.EasingStandard)) +
                                    slideOutVertically(tween(AppMotion.TweenFastMs, easing = AppMotion.EasingStandard)) { -it / 2 }
                            )
                    },
                    label = "eatenNumber",
                    modifier = Modifier.widthIn(max = 140.dp),
                ) { value ->
                    Text(
                        text = value,
                        modifier = Modifier.semantics { contentDescription = eatenA11y },
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 30.sp,
                            maxFontSize = 45.sp,
                            stepSize = 1.sp,
                        ),
                    )
                }
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
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.home_calorie_of_goal,
                                LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                                unitLabel,
                            ),
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
                CalorieBurnCaption(input = input, shadesActive = d.shadesActive)
            }

            // Calories left, top-right wedge.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(13.dp),
                )
                Text(
                    text = stringResource(
                        R.string.home_calories_left,
                        LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                    ),
                    modifier = Modifier.widthIn(max = (cornerWidth - 17.dp).coerceAtLeast(48.dp)),
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
        val animatedRatio = rememberArcAnimation(d.fillRatio, freezeProgress)
        val budgetDescription = calorieBudgetDescription(input)
        val unitLabel = energyUnitLabel()
        val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
        var showBudgetSheet by remember { mutableStateOf(false) }
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
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
                    animatedRatio = animatedRatio,
                    trackColor = trackColor,
                    progressColor = progressColor,
                    bonusColor = bonusColor,
                    liveBurnColor = liveBurnColor,
                    successColor = successColor,
                    muted = muted,
                    shadesActive = d.shadesActive,
                    shade = burnShade,
                    baseGoal = baseGoal,
                    effectiveGoal = d.effectiveGoal,
                    displayMode = displayMode,
                    activeCalories = activeCalories,
                    semanticsRatio = animatedRatio.value,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    modifier = Modifier
                        .widthIn(max = 140.dp)
                        .semantics { contentDescription = eatenA11y },
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
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                            unitLabel,
                        ),
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
                    Text(
                        text = stringResource(
                            R.string.home_calories_left,
                            LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                        ),
                        modifier = Modifier.widthIn(max = 130.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = 13.sp,
                            stepSize = 0.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (dayTypeLabel != null && onDayTypeClick != null) {
                        DayTypeChip(label = dayTypeLabel, color = dayTypeColor, onClick = onDayTypeClick)
                    }
                    if (onUntrackedClick != null) {
                        UntrackedChip(untracked = untracked, onClick = onUntrackedClick)
                    }
                }
                CalorieBurnCaption(input = input, shadesActive = d.shadesActive)
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
        val animatedRatio = rememberArcAnimation(d.fillRatio, freezeProgress)
        val budgetDescription = calorieBudgetDescription(input)
        val unitLabel = energyUnitLabel()
        val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
        var showBudgetSheet by remember { mutableStateOf(false) }
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
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
            val showsChips = (dayTypeLabel != null && onDayTypeClick != null) ||
                onUntrackedClick != null

            CalorieArcCanvas(
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(2f),
                stroke = 12.dp,
                fillRatio = d.fillRatio,
                plannedFillRatio = d.plannedFillRatio,
                freezeProgress = freezeProgress,
                animatedRatio = animatedRatio,
                trackColor = trackColor,
                progressColor = progressColor,
                bonusColor = bonusColor,
                liveBurnColor = liveBurnColor,
                successColor = successColor,
                muted = muted,
                shadesActive = d.shadesActive,
                shade = burnShade,
                baseGoal = baseGoal,
                effectiveGoal = d.effectiveGoal,
                displayMode = displayMode,
                activeCalories = activeCalories,
                semanticsRatio = animatedRatio.value,
            )

            if (showsChips) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .widthIn(max = (cornerWidth - 16.dp).coerceAtLeast(40.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (dayTypeLabel != null && onDayTypeClick != null) {
                        DayTypeChip(label = dayTypeLabel, color = dayTypeColor, onClick = onDayTypeClick)
                    }
                    if (onUntrackedClick != null) {
                        UntrackedChip(untracked = untracked, onClick = onUntrackedClick)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    modifier = Modifier
                        .widthIn(max = 120.dp)
                        .semantics { contentDescription = eatenA11y },
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
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.home_calorie_of_goal,
                                LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                                unitLabel,
                            ),
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
                CalorieBurnCaption(input = input, shadesActive = d.shadesActive)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .height(44.dp),
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
                Text(
                    text = stringResource(
                        R.string.home_calories_left,
                        LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                    ),
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
            )
        }
    }
}

@Composable
private fun CompactHorizontalCalorieHero(input: CalorieHeroInput, modifier: Modifier = Modifier) {
    with(input) {
        val unit = LocalEnergyUnit.current
        val d = calorieHeroDerived(input)
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
        val activeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val tertiary = MaterialTheme.colorScheme.tertiary
        val successColor = MaterialTheme.colorScheme.success
        val unitLabel = energyUnitLabel()
        val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
        val budgetDescription = calorieBudgetDescription(input)
        var showBudgetSheet by remember { mutableStateOf(false) }

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
                        progressBarRangeInfo = ProgressBarRangeInfo(d.fillRatio, 0f..1f, 0)
                    }
            ) {
                val radius = size.height / 2f
                drawRoundRect(trackColor, cornerRadius = CornerRadius(radius, radius))
                if (d.plannedFillRatio > d.fillRatio) {
                    drawRoundRect(
                        progressColor.copy(alpha = 0.35f),
                        size = Size(size.width * d.plannedFillRatio, size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
                drawRoundRect(
                    progressColor,
                    size = Size(size.width * d.fillRatio, size.height),
                    cornerRadius = CornerRadius(radius, radius),
                )
                if (d.shadesActive && burnShade != null) {
                    val shade = burnShade!!
                    val baseX = size.width * HomeCalorieDisplay.burnShadeBaseFraction(baseGoal, shade.typical, shade.live)
                    val typicalW = (size.width * HomeCalorieDisplay.burnShadeTypicalFraction(baseGoal, shade.typical, shade.live))
                        .coerceAtMost(size.width - baseX)
                    val liveW = size.width * HomeCalorieDisplay.burnShadeLiveFraction(baseGoal, shade.live, shade.typical)
                    // Estimated-active zone: fixed dim segment [base → base+typical].
                    if (typicalW > 0f) {
                        drawRoundRect(
                            activeColor,
                            topLeft = Offset(baseX, 0f),
                            size = Size(typicalW, size.height),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                    // Live-active shade: opaque, grows from the base boundary through the
                    // typical zone.
                    val withinTypicalW = liveW.coerceAtMost(typicalW)
                    if (withinTypicalW > 0f) {
                        drawRoundRect(
                            tertiary,
                            topLeft = Offset(baseX, 0f),
                            size = Size(withinTypicalW, size.height),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                    // Over-typical stretch turns success color; the bar end is the
                    // expected-day target, so the live tip lands at the bar end.
                    val overW = (liveW - typicalW).coerceAtLeast(0f)
                        .coerceAtMost((size.width - baseX - typicalW).coerceAtLeast(0f))
                    if (overW > 0f) {
                        drawRoundRect(
                            successColor,
                            topLeft = Offset(baseX + typicalW, 0f),
                            size = Size(overW, size.height),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                    if (baseX > 0f) {
                        drawLine(muted, Offset(baseX, 0f), Offset(baseX, size.height), strokeWidth = 2.dp.toPx())
                    }
                } else if (displayMode == HomeCalorieDisplayMode.ADD_ACTIVE && d.target > baseGoal) {
                    // Existing flat activity-earned zone, plus the same base notch
                    // the arc draws in this state.
                    val baseX = size.width * (baseGoal.toFloat() / d.target).coerceIn(0f, 1f)
                    drawRoundRect(
                        activeColor,
                        topLeft = Offset(baseX, 0f),
                        size = Size(size.width - baseX, size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    if (baseX > 0f) {
                        drawLine(muted, Offset(baseX, 0f), Offset(baseX, size.height), strokeWidth = 2.dp.toPx())
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
                Text(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    modifier = Modifier
                        .widthIn(max = 90.dp)
                        .semantics { contentDescription = eatenA11y },
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
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(d.target, unit)),
                            unitLabel,
                        ),
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
                Text(
                    text = stringResource(
                        R.string.home_calories_left,
                        LocaleFormat.integer(EnergyFormat.quantity(d.remaining, unit)),
                    ),
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
            CalorieBurnCaption(input = input, shadesActive = d.shadesActive)
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
            )
        }
    }
}

/** Compact tappable pill beside the hero's remaining caption: today's day type (#60). */
@Composable
private fun UntrackedChip(untracked: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        if (untracked) R.string.home_untracked_chip else R.string.home_mark_untracked_chip,
    )
    val a11y = stringResource(
        if (untracked) R.string.home_untracked_chip_a11y else R.string.home_mark_untracked_chip_a11y,
    )
    val chipInteraction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (untracked) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        interactionSource = chipInteraction,
        modifier = Modifier
            .pressScale(chipInteraction)
            .minimumInteractiveComponentSize()
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DayTypeChip(label: String, color: Color? = null, onClick: () -> Unit) {
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
        shape = CircleShape,
        color = container,
        interactionSource = chipInteraction,
        modifier = Modifier
            .pressScale(chipInteraction)
            // The chip shares the remaining-caption line: cap it so a long
            // day-type name ellipsizes instead of pushing the caption around.
            .minimumInteractiveComponentSize()
            .widthIn(max = 140.dp)
            .semantics { contentDescription = a11y },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
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
private fun BurnCaption(active: Int) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val activeQ = EnergyFormat.quantity(active, LocalEnergyUnit.current)
    val a11y = stringResource(R.string.home_active_burn_a11y, activeQ)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics { contentDescription = a11y }
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = tertiary,
            modifier = Modifier.size(11.dp)
        )
        Text(
            stringResource(R.string.home_active_burn_caption, activeQ),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tertiary,
        )
    }
}

/** Energy-balance caption: live active vs the day's active norm, plus the over-typical state. */
@Composable
private fun BurnShadeCaption(burn: ActiveBurnShade) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val success = MaterialTheme.colorScheme.success
    val unit = LocalEnergyUnit.current
    val liveQ = EnergyFormat.quantity(burn.live, unit)
    val typicalQ = EnergyFormat.quantity(burn.typical, unit)
    val overQ = EnergyFormat.quantity(burn.live - burn.typical, unit)
    val over = HomeCalorieDisplay.isActiveBurnOverTypical(burn.live, burn.typical)
    val dayType = burn.typicalDayTypeName.takeIf { burn.typicalIsDayType && !it.isNullOrBlank() }
    val a11y = if (over) {
        stringResource(R.string.home_active_burn_over_a11y, liveQ, overQ)
    } else if (dayType != null) {
        stringResource(R.string.home_active_burn_progress_day_type_a11y, liveQ, typicalQ, dayType)
    } else {
        stringResource(R.string.home_active_burn_progress_a11y, liveQ, typicalQ)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics { contentDescription = a11y }
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = tertiary,
            modifier = Modifier.size(11.dp)
        )
        Text(
            if (dayType != null) {
                stringResource(R.string.home_active_burn_caption_progress_day_type, liveQ, typicalQ, dayType)
            } else {
                stringResource(R.string.home_active_burn_caption_progress, liveQ, typicalQ)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tertiary,
        )
        if (over) {
            Text(
                stringResource(R.string.home_active_burn_over, overQ),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = success,
            )
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

// ── Macro card (iOS port) ────────────────────────────────────────────

// MacroCard moved to ui/components/MacroCard.kt as a verbatim port of
// HomeComponents.swift's struct MacroCard. Imported above.

@Composable
internal fun ViewMoreButton() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.home_view_more),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp)
        )
    }
}

/** Signed kcal for the derivation rows: "+250" / "-250" / "0" (mirrors RecalcResultSheet). */
private fun signedKcal(value: Int): String =
    if (value > 0) "+" + LocaleFormat.integer(value) else LocaleFormat.integer(value)
