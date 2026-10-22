package app.chompass.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.EnergyFormat
import app.chompass.models.HomeMacroCardLayout
import app.chompass.models.MacroValueFormatter
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.ui.navigation.LocalLaunchFillEpoch

/**
 * A single macro shown as a vertical fill bar (rounded tube that fills bottom-up toward the goal),
 * with the value above and the name + goal beneath.
 */
private data class MacroCardInput(
    val label: String,
    val shortLabel: String,
    val current: Double,
    val goal: Int,
    val unit: String,
    val modifier: Modifier,
    val accentColor: Color,
    val freezeProgress: Boolean,
    val planned: Double,
    val fatFloor: Boolean,
    val isEnergy: Boolean,
)

@Composable
fun MacroCard(
    label: String,
    shortLabel: String,
    current: Double,
    goal: Int,
    unit: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    freezeProgress: Boolean = false,
    /** Planned (not eaten) amount; faint fill under the logged bar. */
    planned: Double = 0.0,
    /** Fat is a floor target: at or above it reads "minimum met", never over. */
    fatFloor: Boolean = false,
    isEnergy: Boolean = false,
    layout: HomeMacroCardLayout? = null,
) {
    val input = MacroCardInput(
        label = label,
        shortLabel = shortLabel,
        current = current,
        goal = goal,
        unit = unit,
        modifier = modifier,
        accentColor = accentColor,
        freezeProgress = freezeProgress,
        planned = planned,
        fatFloor = fatFloor,
        isEnergy = isEnergy,
    )
    when (layout) {
        HomeMacroCardLayout.SLIM_LINE -> SlimLineMacroCard(input)
        HomeMacroCardLayout.CLASSIC -> ClassicMacroCard(input)
        null -> CompactBarsMacroCard(input)
    }
}

@Composable
private fun ClassicMacroCard(input: MacroCardInput) {
    with(input) {
    val energyUnit = LocalEnergyUnit.current
    fun formatted(value: Double): String = if (isEnergy) {
        EnergyFormat.quantity(value.toInt(), energyUnit).toString()
    } else {
        MacroValueFormatter.string(value)
    }
    val displayUnit = if (isEnergy) energyUnitLabel() else unit
    val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val goalValue = goal.toDouble()
    val animated = rememberMacroAnimation(progress, freezeProgress)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            formatted(current),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
        )

        Box(
            modifier = Modifier.size(width = 16.dp, height = 74.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
            )
            // Empty macros (0 g, or no goal) render as the bare track: the 16 dp
            // minimum fill only applies once there is progress to show, otherwise
            // an empty card would read as ~22 % full.
            val combined = if (goal > 0) {
                ((current + planned).toFloat() / goal).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (planned > 0.0 && combined > animated) {
                val plannedHeight = (74.dp * combined).coerceAtLeast(16.dp)
                Box(
                    Modifier
                        .width(16.dp)
                        .height(plannedHeight)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.38f)),
                )
            }
            val fillHeight = if (animated > 0f) (74.dp * animated).coerceAtLeast(16.dp) else 0.dp
            Box(
                Modifier
                    .width(16.dp)
                    .height(fillHeight)
                    .clip(CircleShape)
                    .background(accentColor),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                when {
                    goal <= 0 -> stringResource(R.string.macro_status_no_goal)
                    fatFloor && current >= goalValue -> stringResource(R.string.macro_status_floor_met)
                    current == goalValue -> stringResource(R.string.macro_status_goal_reached)
                    current < goalValue -> stringResource(
                        R.string.macro_status_left,
                        formatted(goalValue - current),
                        displayUnit,
                    )
                    else -> stringResource(
                        R.string.macro_status_over,
                        formatted(current - goalValue),
                        displayUnit,
                    )
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (goal > 0 && current > goalValue && !fatFloor) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                // Shrink to fit the card at max font scale: long values + long
                // suffixes (ru "91,5g übrig") clipped at 1.3× without this.
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            )
        }
    }
    }
}

/** Shared fill animation: launch-epoch restart + spring; frozen cards hold their value. */
@Composable
private fun rememberMacroAnimation(progress: Float, freezeProgress: Boolean): Float {
    val epoch = LocalLaunchFillEpoch.current
    var lastEpoch by rememberSaveable { mutableIntStateOf(0) }
    val animatable = remember { Animatable(if (lastEpoch == epoch) progress else 0f) }
    LaunchedEffect(epoch, progress) {
        if (freezeProgress) return@LaunchedEffect
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 55f)
        if (lastEpoch != epoch) {
            animatable.snapTo(0f)
            animatable.animateTo(progress, spec)
            lastEpoch = epoch
        } else {
            animatable.animateTo(progress, spec)
        }
    }
    return if (freezeProgress) progress else animatable.value
}

/** Signed-delta status: "−84" under goal, "+12" over, "✓" met. Null hides the status (no goal). */
private data class MacroStatusGlyph(val text: String, val met: Boolean, val over: Boolean, val a11y: String)

@Composable
private fun macroStatusGlyph(input: MacroCardInput, format: (Double) -> String): MacroStatusGlyph? = with(input) {
    val goalValue = goal.toDouble()
    val displayUnit = if (isEnergy) energyUnitLabel() else unit
    when {
        goal <= 0 -> null
        fatFloor && current >= goalValue -> MacroStatusGlyph("✓", met = true, over = false,
            a11y = stringResource(R.string.macro_status_floor_met))
        current == goalValue -> MacroStatusGlyph("✓", met = true, over = false,
            a11y = stringResource(R.string.macro_status_goal_reached))
        current < goalValue -> MacroStatusGlyph("−" + format(goalValue - current), met = false, over = false,
            a11y = stringResource(R.string.macro_status_left, format(goalValue - current), displayUnit))
        else -> MacroStatusGlyph("+" + format(current - goal), met = false, over = true,
            a11y = stringResource(R.string.macro_status_over, format(current - goal), displayUnit))
    }
}

/** 6dp progress bar shared by the band and slim-line layouts: ghost + animated fill. */
@Composable
private fun MacroSlimBar(
    animated: Float,
    combined: Float,
    planned: Double,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (planned > 0.0 && combined > animated) {
            Box(
                Modifier
                    .fillMaxWidth(combined)
                    .height(6.dp)
                    .background(accentColor.copy(alpha = 0.38f)),
            )
        }
        // 16dp minimum nub matches the classic tube's visible-fill rule.
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .widthIn(min = 16.dp)
                    .height(6.dp)
                    .background(accentColor),
            )
        }
    }
}

/** Default band card: label / 6dp bar / value + status line. */
@Composable
private fun CompactBarsMacroCard(input: MacroCardInput) {
    with(input) {
        val energyUnit = LocalEnergyUnit.current
        fun formatted(value: Double): String = if (isEnergy) {
            EnergyFormat.quantity(value.toInt(), energyUnit).toString()
        } else {
            MacroValueFormatter.string(value)
        }
        val displayUnit = if (isEnergy) energyUnitLabel() else unit
        val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
        val combined = if (goal > 0) ((current + planned).toFloat() / goal).coerceIn(0f, 1f) else 0f
        val animated = rememberMacroAnimation(progress, freezeProgress)
        val status = macroStatusGlyph(input) { formatted(it) }

        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    shortLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    displayUnit,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            MacroSlimBar(
                animated = animated,
                combined = combined,
                planned = planned,
                accentColor = accentColor,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    formatted(current),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline(),
                )
                if (status != null) {
                    Text(
                        status.text,
                        fontSize = 11.sp,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                        color = when {
                            status.over -> accentColor
                            status.met -> accentColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .alignByBaseline()
                            .semantics { contentDescription = status.a11y },
                    )
                }
            }
        }
    }
}

/** Two-line slim card: label + value + status on one line, the bar under it. */
@Composable
private fun SlimLineMacroCard(input: MacroCardInput) {
    with(input) {
        val energyUnit = LocalEnergyUnit.current
        fun formatted(value: Double): String = if (isEnergy) {
            EnergyFormat.quantity(value.toInt(), energyUnit).toString()
        } else {
            MacroValueFormatter.string(value)
        }
        val displayUnit = if (isEnergy) energyUnitLabel() else unit
        val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
        val combined = if (goal > 0) ((current + planned).toFloat() / goal).coerceIn(0f, 1f) else 0f
        val animated = rememberMacroAnimation(progress, freezeProgress)
        val status = macroStatusGlyph(input) { formatted(it) }

        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        shortLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        displayUnit,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        formatted(current),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                    )
                }
                if (status != null) {
                    Text(
                        status.text,
                        fontSize = 10.sp,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
                        color = when {
                            status.over -> accentColor
                            status.met -> accentColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.semantics { contentDescription = status.a11y },
                    )
                }
            }
            MacroSlimBar(
                animated = animated,
                combined = combined,
                planned = planned,
                accentColor = accentColor,
            )
        }
    }
}
