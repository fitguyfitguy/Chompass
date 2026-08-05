package app.chompass.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ActiveBurnShade
import app.chompass.models.ActiveCalorieSource
import app.chompass.models.HomeCalorieDisplay
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.navigation.LocalLaunchFillEpoch
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.success
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

// ── Week strip (iOS port) ────────────────────────────────────────────

@Composable
internal fun WeekStripSection(selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val firstDow = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekStart = remember(selectedDate, firstDow) {
        val offset = ((selectedDate.dayOfWeek.value - firstDow.value) + 7) % 7
        selectedDate.minusDays(offset.toLong())
    }
    val today = remember { LocalDate.now() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val isSel = date == selectedDate
            val isTdy = date == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(date) }
                    )
            ) {
                Text(
                    shortDay(date.dayOfWeek),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSel) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSel) AppColors.CalorieGradient
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(
                            if (isTdy && !isSel) Modifier.border(1.5.dp, AppColors.Calorie.copy(alpha = 0.35f), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isSel -> Color.White
                            isTdy -> AppColors.Calorie
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private fun shortDay(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
}

// ── Calorie hero ─────────────────────────────────────────────────────

/**
 * Design-experiment flag for the resting (basal) burn band in the hero arc.
 * `false` = active shades only (estimate + live); `true` = a neutral resting
 * band also grows from the arc's left origin. Both variants are rendered for
 * comparison via release screenshots before one is picked.
 */
internal const val SHOW_RESTING_BURN_SHADE = false

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
     * Non-null only for ADD_ACTIVE + live measured/debug burn + the toggle on;
     * PAL-estimate-only days keep the legacy budget tail.
     */
    burnShade: ActiveBurnShade? = null,
    /** Resting (basal) burn so far, when known. Powers the optional resting shade. */
    restingBurn: Int? = null,
    /** When true, draw the resting/base shade band under the active shades. */
    showRestingShade: Boolean = false,
    freezeProgress: Boolean = false,
    /** True when Settings asks for ADD_ACTIVE but today’s burn is still 0. */
    awaitingActiveBurn: Boolean = false,
) {
    val ratio = HomeCalorieDisplay.progressRatio(displayMode, current, baseGoal, activeCalories)
    val remaining = HomeCalorieDisplay.remaining(displayMode, current, baseGoal, activeCalories)
    val effectiveGoal = HomeCalorieDisplay.effectiveGoal(displayMode, baseGoal, activeCalories)
    val integratesBurn = activeCalories > 0 && displayMode != HomeCalorieDisplayMode.STATIC
    val shade = burnShade
    val shadesActive = shade != null && shade.typical > 0 &&
        displayMode == HomeCalorieDisplayMode.ADD_ACTIVE
    // On the shade arc the eaten fill shares the burn scale (arc end = base + typical),
    // so eaten and burn read against the same denominator.
    val fillRatio = if (shadesActive) {
        HomeCalorieDisplay.burnShadeEatenFraction(current, baseGoal, shade!!.typical)
    } else {
        ratio
    }
    var showBudgetSheet by remember { mutableStateOf(false) }
    val goalLabel = when (displayMode) {
        HomeCalorieDisplayMode.ADD_ACTIVE -> effectiveGoal
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
    val restingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
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
        ) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2f
            // The resting (basal) rim rides the outer sub-stroke so it stays visible
            // even when the eaten fill covers the inner band.
            val restingVisible = shadesActive && showRestingShade && restingBurn != null
            val mainStroke = if (restingVisible) stroke * 0.62f else stroke
            val restingStroke = (stroke - mainStroke).coerceAtLeast(1.dp.toPx())
            val mainInset = inset + (stroke - mainStroke) / 2f
            val mainArcSize = Size(size.width - mainStroke, size.width - mainStroke)
            val mainTopLeft = Offset(mainInset, mainInset)
            val cx = size.width / 2f
            val cy = size.width / 2f
            val rMid = (size.width - mainStroke) / 2f
            val rIn = rMid - mainStroke / 2f + 2.dp.toPx()
            val rOut = rMid + mainStroke / 2f - 2.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = mainTopLeft,
                size = mainArcSize,
                style = Stroke(width = mainStroke, cap = StrokeCap.Round)
            )
            if (shadesActive) {
                // Single energy scale, one meaning per shade:
                //  - resting (basal) rim grows from the left on the outer band, neutral.
                //  - estimated-active zone = fixed dim segment [base → base+typical].
                //  - live-active shade = opaque, grows from the base boundary and
                //    extends toward/past the typical zone to the full ring when over.
                val typical = shade.typical
                val baseAngle = 180f * HomeCalorieDisplay.burnShadeBaseFraction(baseGoal, typical)
                val typicalSweep = 180f * HomeCalorieDisplay.burnShadeTypicalFraction(baseGoal, typical)
                if (restingVisible) {
                    val restSweep = 180f * HomeCalorieDisplay.burnShadeRestingFraction(restingBurn!!, baseGoal, typical)
                    if (restSweep > 0f) {
                        val restR = rMid + mainStroke / 2f + restingStroke / 2f
                        drawArc(
                            color = restingColor,
                            startAngle = 180f,
                            sweepAngle = restSweep.coerceAtMost(180f),
                            useCenter = false,
                            topLeft = Offset(cx - restR, cy - restR),
                            size = Size(restR * 2f, restR * 2f),
                            style = Stroke(width = restingStroke, cap = StrokeCap.Round)
                        )
                    }
                }
                if (typicalSweep > 0f) {
                    drawArc(
                        color = bonusColor,
                        startAngle = 180f + baseAngle,
                        sweepAngle = typicalSweep.coerceAtMost(180f - baseAngle),
                        useCenter = false,
                        topLeft = mainTopLeft,
                        size = mainArcSize,
                        style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                    )
                }
                val liveSweep = 180f * HomeCalorieDisplay.burnShadeLiveFraction(baseGoal, shade.live, typical)
                val liveMaxSweep = (180f - baseAngle).coerceAtLeast(0f)
                if (liveSweep > 0f) {
                    drawArc(
                        color = liveBurnColor,
                        startAngle = 180f + baseAngle,
                        sweepAngle = liveSweep.coerceAtMost(liveMaxSweep),
                        useCenter = false,
                        topLeft = mainTopLeft,
                        size = mainArcSize,
                        style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                    )
                }
                // Base-boundary notch: where your sedentary budget ends and the
                // activity-earned zone begins.
                val notchRad = Math.toRadians((180f + baseAngle).toDouble())
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
                if (HomeCalorieDisplay.isActiveBurnOverTypical(shade.live, typical)) {
                    val tipAngle = Math.toRadians(
                        (180f + baseAngle + liveSweep.coerceAtMost(liveMaxSweep)).toDouble(),
                    )
                    drawCircle(
                        color = successColor,
                        radius = 5.dp.toPx(),
                        center = Offset(
                            cx + (rMid * Math.cos(tipAngle)).toFloat(),
                            cy + (rMid * Math.sin(tipAngle)).toFloat(),
                        ),
                    )
                }
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
                        style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                    )
                    val notchRad = Math.toRadians((180f + baseSweep).toDouble())
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
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * (if (freezeProgress) fillRatio else animatedRatio.value),
                useCenter = false,
                topLeft = mainTopLeft,
                size = mainArcSize,
                style = Stroke(width = mainStroke, cap = StrokeCap.Round)
            )
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
                "$current",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // Decluttered goal line: "of <budget>". In ADD_ACTIVE the budget already includes
            // today's active burn, so the ⓘ affordance opens a sheet that explains the breakdown
            // instead of jamming the active figure into this one-line caption.
            val explainsBudget = integratesBurn || awaitingActiveBurn
            val goalDescription = when {
                awaitingActiveBurn -> stringResource(R.string.home_calorie_of_goal, goalLabel) +
                    ". " + stringResource(R.string.home_calorie_budget_sheet_awaiting)
                integratesBurn && activeCalorieSource == ActiveCalorieSource.ESTIMATED ->
                    stringResource(R.string.home_calorie_budget_a11y, goalLabel, baseGoal, activeCalories) +
                        ". " + stringResource(R.string.home_calorie_active_estimated_a11y)
                integratesBurn ->
                    stringResource(R.string.home_calorie_budget_a11y, goalLabel, baseGoal, activeCalories)
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_calorie_of_goal, goalLabel),
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
                    stringResource(R.string.home_calories_left, remaining),
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
            source = activeCalorieSource,
            awaiting = awaitingActiveBurn,
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
    source: ActiveCalorieSource?,
    awaiting: Boolean,
    burnedToday: Int? = null,
    onDismiss: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.home_calorie_budget_sheet_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (awaiting) {
                Text(
                    stringResource(R.string.home_calorie_budget_sheet_awaiting),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    stringResource(R.string.home_calories_goal_plus_active, goal, active),
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
