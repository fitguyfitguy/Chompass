package app.chompass.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import app.chompass.models.LocaleFormat
import app.chompass.models.NutritionConstants
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.components.energyText
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.navigation.LocalLaunchFillEpoch
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.success

// ── Calorie hero ─────────────────────────────────────────────────────

/**
 * Home calorie hero: a 260×130dp half-circle gauge.
 *
 * The eaten number sits in the bowl with the goal caption and the active-burn
 * caption directly under it. The chips (day type, untracked) sit under the
 * section's "Today" label, in the empty wedge at the arc's top left, and the
 * calories-left caption in the matching wedge at the top right.
 */
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
    modifier: Modifier = Modifier,
) {
    val ratio = HomeCalorieDisplay.progressRatio(displayMode, current, baseGoal, activeCalories)
    val effectiveGoal = HomeCalorieDisplay.effectiveGoal(displayMode, baseGoal, activeCalories)
    val unit = LocalEnergyUnit.current
    val integratesBurn = activeCalories > 0 && displayMode != HomeCalorieDisplayMode.STATIC
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
    val remaining = (target - current).coerceAtLeast(0)
    // On the shade arc the eaten fill shares the burn scale (arc end = target),
    // so eaten and burn read against the same denominator.
    val fillRatio = if (shadesActive) {
        HomeCalorieDisplay.burnShadeEatenFraction(current, baseGoal, shade!!.typical, shade.live)
    } else {
        ratio
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
    var showBudgetSheet by remember { mutableStateOf(false) }
    val goalLabel = when {
        shadesActive -> target
        else -> effectiveGoal
    }
    val unitLabel = energyUnitLabel()
    // Corner captions. The goal line carries the budget truth (base + live
    // burn) for TalkBack; its row is the budget-sheet button in ADD_ACTIVE.
    val explainsBudget = displayMode == HomeCalorieDisplayMode.ADD_ACTIVE
    val goalDescription = when {
        !explainsBudget -> null
        integratesBurn && activeCalorieSource == ActiveCalorieSource.ESTIMATED ->
            stringResource(R.string.home_calorie_budget_a11y, EnergyFormat.quantity(effectiveGoal, unit), EnergyFormat.quantity(baseGoal, unit), EnergyFormat.quantity(activeCalories, unit), unitLabel) +
                ". " + stringResource(R.string.home_calorie_active_estimated_a11y)
        else ->
            stringResource(R.string.home_calorie_budget_a11y, EnergyFormat.quantity(effectiveGoal, unit), EnergyFormat.quantity(baseGoal, unit), EnergyFormat.quantity(activeCalories, unit), unitLabel)
    }
    val infoLabel = stringResource(R.string.home_calorie_budget_info)
    val eatenA11y = stringResource(R.string.home_calories_eaten_a11y, energyText(current))
    val showsChips = (dayTypeLabel != null && onDayTypeClick != null) || onUntrackedClick != null
    val epoch = LocalLaunchFillEpoch.current
    var lastEpoch by rememberSaveable { mutableIntStateOf(0) }
    val animatedRatio = remember { Animatable(if (lastEpoch == epoch) fillRatio else 0f) }
    LaunchedEffect(epoch, fillRatio) {
        if (freezeProgress) {
            animatedRatio.snapTo(fillRatio)
            return@LaunchedEffect
        }
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 55f)
        if (lastEpoch != epoch) {
            animatedRatio.snapTo(0f)
            animatedRatio.animateTo(fillRatio, spec)
            lastEpoch = epoch
        } else {
            animatedRatio.animateTo(fillRatio, spec)
        }
    }
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
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val tertiary = MaterialTheme.colorScheme.tertiary
    val successColor = MaterialTheme.colorScheme.success

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        // The half-circle leaves an empty wedge at each top corner: the goal
        // and remaining captions sit in those wedges instead of stacking under
        // the number. 64dp is the wedge width at the caption line's bottom; the
        // arc's side margin (hero width minus the 260dp arc) is added on top.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val sideMargin = ((maxWidth - 260.dp) / 2).coerceIn(0.dp, 200.dp)
            val cornerWidth = sideMargin + 48.dp

            Canvas(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(2f)
                    .semantics {
                        // The budget sheet carries the exact numbers; the arc still
                        // reports its fill so TalkBack users get progress (UI-audit 2.7).
                        progressBarRangeInfo = ProgressBarRangeInfo(animatedRatio.value, 0f..1f, 0)
                    }
            ) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2f
                val mainArcSize = Size(size.width - stroke, size.width - stroke)
                val mainTopLeft = Offset(inset, inset)
                val cx = size.width / 2f
                val cy = size.width / 2f
                val rMid = (size.width - stroke) / 2f
                val rIn = rMid - stroke / 2f + 2.dp.toPx()
                val rOut = rMid + stroke / 2f - 2.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = mainTopLeft,
                    size = mainArcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
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
                        topLeft = mainTopLeft,
                        size = mainArcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                drawArc(
                    color = progressColor,
                    startAngle = 180f,
                    sweepAngle = 180f * (if (freezeProgress) fillRatio else animatedRatio.value),
                    useCenter = false,
                    topLeft = mainTopLeft,
                    size = mainArcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (shadesActive) {
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
                            topLeft = mainTopLeft,
                            size = mainArcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
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
                            topLeft = mainTopLeft,
                            size = mainArcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    val overTypicalSweep = (liveSweep - typicalSweep).coerceAtLeast(0f)
                    if (overTypicalSweep > 0f) {
                        drawArc(
                            color = successColor,
                            startAngle = 180f + baseAngle + typicalSweep,
                            sweepAngle = overTypicalSweep.coerceAtMost((liveMaxSweep - typicalSweep).coerceAtLeast(0f)),
                            useCenter = false,
                            topLeft = mainTopLeft,
                            size = mainArcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
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
                            topLeft = mainTopLeft,
                            size = mainArcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
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
                            cx + (rIn * Math.cos(notchRad)).toFloat(),
                            cy + (rIn * Math.sin(notchRad)).toFloat(),
                        ),
                        end = Offset(
                            cx + (rOut * Math.cos(notchRad)).toFloat(),
                            cy + (rOut * Math.sin(notchRad)).toFloat(),
                        ),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            // Chips sit under the section's "Today" label, in the empty wedge at
            // the arc's top left. Stacked (not side by side) because the wedge
            // narrows downward; each chip is capped to the wedge width.
            if (showsChips) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .widthIn(max = (cornerWidth - 16.dp).coerceAtLeast(48.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Day-type chip (#60); hidden when the plan is off / past-day view.
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
                Text(
                    text = LocaleFormat.integer(EnergyFormat.quantity(current, unit)),
                    modifier = Modifier
                        .widthIn(max = 140.dp)
                        .semantics { contentDescription = eatenA11y },
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
                // Goal caption directly under the eaten number. In ADD_ACTIVE the
                // whole row is the budget-sheet button (the ⓘ is its icon);
                // STATIC shows a plain line.
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .then(
                            if (explainsBudget) {
                                Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClickLabel = infoLabel,
                                        role = Role.Button,
                                        onClick = { showBudgetSheet = true },
                                    )
                                    .semantics(mergeDescendants = true) {
                                        goalDescription?.let { contentDescription = it }
                                    }
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(
                            R.string.home_calorie_of_goal,
                            LocaleFormat.integer(EnergyFormat.quantity(goalLabel, unit)),
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
                    if (explainsBudget) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = tertiary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp),
                        )
                    }
                }
                // Active burn, third line of the bowl: it explains the arc's
                // activity zone, so it reads directly under the goal.
                if (shadesActive) {
                    BurnShadeCaption(burn = shade!!)
                } else if (showActiveCalories && liveActiveBurn > 0) {
                    BurnCaption(active = liveActiveBurn)
                }
            }

            // Remaining caption, top-right wedge.
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
                        LocaleFormat.integer(EnergyFormat.quantity(remaining, unit)),
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
    }
    if (showBudgetSheet) {
        BudgetExplanationDialog(
            goal = baseGoal,
            active = activeCalories,
            typical = shade?.typical,
            typicalIsDayType = shade?.typicalIsDayType == true,
            typicalDayTypeName = shade?.typicalDayTypeName,
            blendedTypical = shade?.blendedTypical,
            source = activeCalorieSource,
            burnedToday = restingBurn?.let { it + liveActiveBurn },
            onDismiss = { showBudgetSheet = false },
            recalcDetailsAvailable = recalcDetailsAvailable && onShowRecalcDetails != null,
            onShowRecalcDetails = onShowRecalcDetails,
            goalDerivation = goalDerivation,
        )
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
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (untracked) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
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
            // The chip lives in the hero's top-left wedge, which narrows at 1.3×.
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
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier
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
                // The chip lives in the hero's top-left wedge, which narrows at 1.3×.
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
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Calorie.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.Calorie.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp)
        )
    }
}

/** Signed kcal for the derivation rows: "+250" / "-250" / "0" (mirrors RecalcResultSheet). */
private fun signedKcal(value: Int): String =
    if (value > 0) "+" + LocaleFormat.integer(value) else LocaleFormat.integer(value)
