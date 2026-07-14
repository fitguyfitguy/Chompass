package org.codeberg.fitguy.nofud.ui.home

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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.ActiveCalorieSource
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplay
import org.codeberg.fitguy.nofud.models.HomeCalorieDisplayMode
import org.codeberg.fitguy.nofud.ui.navigation.LocalLaunchFillEpoch
import org.codeberg.fitguy.nofud.ui.theme.AppColors
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
    freezeProgress: Boolean = false,
) {
    val ratio = HomeCalorieDisplay.progressRatio(displayMode, current, baseGoal, activeCalories)
    val remaining = HomeCalorieDisplay.remaining(displayMode, current, baseGoal, activeCalories)
    val effectiveGoal = HomeCalorieDisplay.effectiveGoal(displayMode, baseGoal, activeCalories)
    val integratesBurn = activeCalories > 0 && displayMode != HomeCalorieDisplayMode.STATIC
    val goalLabel = when (displayMode) {
        HomeCalorieDisplayMode.ADD_ACTIVE -> effectiveGoal
        else -> baseGoal
    }
    val epoch = LocalLaunchFillEpoch.current
    var lastEpoch by rememberSaveable { mutableIntStateOf(0) }
    val animatedRatio = remember { Animatable(if (lastEpoch == epoch) ratio else 0f) }
    LaunchedEffect(epoch, ratio) {
        if (freezeProgress) {
            animatedRatio.snapTo(ratio)
            return@LaunchedEffect
        }
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 55f)
        if (lastEpoch != epoch) {
            animatedRatio.snapTo(0f)
            animatedRatio.animateTo(ratio, spec)
            lastEpoch = epoch
        } else {
            animatedRatio.animateTo(ratio, spec)
        }
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val bonusColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val baseTrackColor = progressColor.copy(alpha = 0.28f)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val tertiary = MaterialTheme.colorScheme.tertiary

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
            val arcSize = Size(size.width - stroke, size.width - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (displayMode == HomeCalorieDisplayMode.ADD_ACTIVE && activeCalories > 0 && effectiveGoal > 0) {
                val baseSweep = 180f * (baseGoal.toFloat() / effectiveGoal.toFloat()).coerceIn(0f, 1f)
                val bonusSweep = (180f - baseSweep).coerceAtLeast(0f)
                if (baseSweep > 0f) {
                    drawArc(
                        color = baseTrackColor,
                        startAngle = 180f,
                        sweepAngle = baseSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                if (bonusSweep > 0f) {
                    drawArc(
                        color = bonusColor,
                        startAngle = 180f + baseSweep,
                        sweepAngle = bonusSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            if (displayMode == HomeCalorieDisplayMode.DUAL && activeCalories > 0 && baseGoal > 0) {
                val burnSweep = (180f * (activeCalories.toFloat() / baseGoal.toFloat())).coerceIn(4f, 36f)
                drawArc(
                    color = tertiary.copy(alpha = 0.35f),
                    startAngle = 180f,
                    sweepAngle = burnSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 0.55f, cap = StrokeCap.Round)
                )
            }
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * (if (freezeProgress) ratio else animatedRatio.value),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
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
            Text(
                stringResource(R.string.home_calorie_of_goal, goalLabel),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = muted,
            )
            if (integratesBurn) {
                val bonusRes = when (activeCalorieSource) {
                    ActiveCalorieSource.MEASURED -> R.string.home_calorie_active_bonus_measured
                    ActiveCalorieSource.ESTIMATED -> R.string.home_calorie_active_bonus_estimated
                    ActiveCalorieSource.UNAVAILABLE, null -> R.string.home_calorie_active_bonus
                }
                val bonusText = stringResource(bonusRes, activeCalories)
                val estimatedA11y = stringResource(R.string.home_calorie_active_estimated_a11y)
                Text(
                    bonusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tertiary,
                    modifier = if (activeCalorieSource == ActiveCalorieSource.ESTIMATED) {
                        Modifier.semantics { contentDescription = "$estimatedA11y: $bonusText" }
                    } else {
                        Modifier
                    },
                )
                if (displayMode == HomeCalorieDisplayMode.ADD_ACTIVE) {
                    val breakdownRes = when (activeCalorieSource) {
                        ActiveCalorieSource.MEASURED -> R.string.home_calorie_goal_breakdown_measured
                        ActiveCalorieSource.ESTIMATED -> R.string.home_calorie_goal_breakdown_estimated
                        ActiveCalorieSource.UNAVAILABLE, null -> R.string.home_calorie_goal_breakdown
                    }
                    val breakdownText = stringResource(breakdownRes, baseGoal, activeCalories)
                    Text(
                        breakdownText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = muted,
                        modifier = if (activeCalorieSource == ActiveCalorieSource.ESTIMATED) {
                            Modifier.semantics { contentDescription = "$estimatedA11y: $breakdownText" }
                        } else {
                            Modifier
                        },
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
                    when (displayMode) {
                        HomeCalorieDisplayMode.NET ->
                            stringResource(R.string.home_calories_net_left, remaining)
                        else -> stringResource(R.string.home_calories_left, remaining)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
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
