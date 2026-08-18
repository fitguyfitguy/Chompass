package app.chompass.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ActiveBurnShade
import app.chompass.models.ActiveCalorieSource
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.LocaleFormat
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.navigation.LocalLaunchFillEpoch
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
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
@Composable
internal fun CalorieHero(
    current: Int,
    baseGoal: Int,
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
) {
    val ratio = HomeCalorieDisplay.progressRatio(displayMode, current, baseGoal, activeCalories)
    val effectiveGoal = HomeCalorieDisplay.effectiveGoal(displayMode, baseGoal, activeCalories)
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
    var showBudgetSheet by remember { mutableStateOf(false) }
    val goalLabel = when {
        shadesActive -> target
        displayMode == HomeCalorieDisplayMode.ADD_ACTIVE -> effectiveGoal
        else -> baseGoal
    }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
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
            // Base-boundary notch angle (degrees from the arc's left origin),
            // when a boundary exists. Drawn after the eaten fill so the boundary
            // stays visible even when the fill covers it.
            var notchAngle: Float? = null
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
                // The base boundary is drawn after the eaten fill (see below),
                // so it stays visible even when the fill covers it.
                notchAngle = 180f + baseAngle
            } else if (displayMode == HomeCalorieDisplayMode.ADD_ACTIVE && activeCalories > 0 && effectiveGoal > 0) {
                // Activity-earned zone: [baseGoal → effectiveGoal], a fixed-tint
                // segment on the same budget axis. The teal progress fill sweeps
                // over it, so eaten crossing the boundary notch = dipping into the
                // calories you burned. Fixed alpha only — no burn thermometer, no
                // success dot; the tick at the base-goal boundary marks where your
                // static budget ends.
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
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * (if (freezeProgress) fillRatio else animatedRatio.value),
                useCenter = false,
                topLeft = mainTopLeft,
                size = mainArcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
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

        Column(
            modifier = Modifier.padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(R.string.home_calories_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = muted
            )
            Text(
                LocaleFormat.integer(current),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // Decluttered goal line: "of <expected day>". The ⓘ affordance opens a sheet that
            // explains the breakdown (expected day, burned today, source) instead of jamming
            // the active figures into this one-line caption. The a11y keeps the budget truth
            // (base + live burn); the burn caption carries the live-vs-typical read.
            val explainsBudget = displayMode == HomeCalorieDisplayMode.ADD_ACTIVE
            val goalDescription = when {
                !explainsBudget -> null
                integratesBurn && activeCalorieSource == ActiveCalorieSource.ESTIMATED ->
                    stringResource(R.string.home_calorie_budget_a11y, effectiveGoal, baseGoal, activeCalories) +
                        ". " + stringResource(R.string.home_calorie_active_estimated_a11y)
                else ->
                    stringResource(R.string.home_calorie_budget_a11y, effectiveGoal, baseGoal, activeCalories)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_calorie_of_goal, LocaleFormat.integer(goalLabel)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = muted,
                    modifier = goalDescription?.let { desc ->
                        Modifier.semantics { contentDescription = desc }
                    } ?: Modifier,
                )
                if (explainsBudget) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.home_calorie_budget_info),
                        tint = tertiary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showBudgetSheet = true },
                            ),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    stringResource(R.string.home_calories_left, LocaleFormat.integer(remaining)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (shadesActive) {
                BurnShadeCaption(burn = shade!!)
            } else if (showActiveCalories && liveActiveBurn > 0) {
                BurnCaption(active = liveActiveBurn)
            }
        }
    }
    if (showBudgetSheet) {
        BudgetExplanationDialog(
            goal = baseGoal,
            active = activeCalories,
            typical = shade?.typical,
            source = activeCalorieSource,
            burnedToday = restingBurn?.let { it + liveActiveBurn },
            onDismiss = { showBudgetSheet = false },
        )
    }
}

@Composable
private fun BurnCaption(active: Int) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val a11y = stringResource(R.string.home_active_burn_a11y, active)
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
            stringResource(R.string.home_active_burn_caption, active),
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
    val over = HomeCalorieDisplay.isActiveBurnOverTypical(burn.live, burn.typical)
    val a11y = if (over) {
        stringResource(R.string.home_active_burn_over_a11y, burn.live, burn.live - burn.typical)
    } else {
        stringResource(R.string.home_active_burn_progress_a11y, burn.live, burn.typical)
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
            stringResource(R.string.home_active_burn_caption_progress, burn.live, burn.typical),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tertiary,
        )
        if (over) {
            Text(
                stringResource(R.string.home_active_burn_over, burn.live - burn.typical),
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
    source: ActiveCalorieSource?,
    burnedToday: Int? = null,
    onDismiss: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    FudGlassDialog(onDismissRequest = onDismiss) {
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
                    LocaleFormat.integer(goal),
                    LocaleFormat.integer(typical ?: active),
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
            if (burnedToday != null) {
                Text(
                    stringResource(R.string.home_budget_burned_today, burnedToday),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        FudGlassDialogActions(
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
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AppColors.Calorie.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp)
        )
    }
}
