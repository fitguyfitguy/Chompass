package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FastingPhase
import app.chompass.models.FastingSession
import app.chompass.ui.util.clockTimePattern

/**
 * Fasting cycle bar on Home (docs/local/PLAN_FASTING_TRACKER.md §5). Tells the
 * user how long until they can eat (fasting phase) or until the next fast
 * starts (eating phase), with Start / Stop / Cancel. Local-only: nothing is
 * synced or exported. Placement: directly below the calorie hero.
 */
@Composable
fun FastingProgressRow(
    phase: FastingPhase,
    fastHours: Int,
    eatHours: Int,
    fastElapsedMillis: Long,
    eatElapsedMillis: Long,
    nextFastStartMillis: Long?,
    nowMillis: Long,
    goalReached: Boolean,
    autoStarted: Boolean,
    autoMode: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasGoal = fastHours > 0
    val fastWindowMillis = fastHours * FastingSession.MILLIS_PER_HOUR
    val eatWindowMillis = eatHours * FastingSession.MILLIS_PER_HOUR
    val clockFormatter = rememberClockFormatter()

    val statusLabel: String
    val progress: Float
    val countdownHint: String?
    when (phase) {
        FastingPhase.FASTING -> {
            statusLabel = if (goalReached) {
                stringResource(R.string.fasting_goal_reached)
            } else if (hasGoal) {
                stringResource(R.string.fasting_goal_progress, fastingDurationLabel(fastElapsedMillis), fastHours)
            } else {
                fastingDurationLabel(fastElapsedMillis)
            }
            progress = if (hasGoal) (fastElapsedMillis.toFloat() / fastWindowMillis).coerceIn(0f, 1f) else 0f
            countdownHint = if (hasGoal && !goalReached) {
                stringResource(R.string.fasting_window_opens_in, fastingDurationLabel(fastWindowMillis - fastElapsedMillis))
            } else {
                null
            }
        }
        FastingPhase.EATING -> {
            val remaining = (nextFastStartMillis ?: (nowMillis + eatWindowMillis)).let { (it - nowMillis).coerceAtLeast(0L) }
            statusLabel = stringResource(
                R.string.fasting_eating_progress,
                fastingDurationLabel(eatElapsedMillis),
                eatHours,
            )
            val denom = if (autoMode && nextFastStartMillis != null) {
                (nextFastStartMillis - (nowMillis - eatElapsedMillis)).coerceAtLeast(1L)
            } else {
                eatWindowMillis
            }
            progress = (eatElapsedMillis.toFloat() / denom).coerceIn(0f, 1f)
            countdownHint = if (autoMode && nextFastStartMillis != null) {
                stringResource(
                    R.string.fasting_fast_starts_at,
                    clockFormatter.format(java.time.Instant.ofEpochMilli(nextFastStartMillis).atZone(java.time.ZoneId.systemDefault())),
                    fastingDurationLabel(remaining),
                )
            } else {
                stringResource(R.string.fasting_fast_starts_in, fastingDurationLabel(remaining))
            }
        }
        FastingPhase.IDLE -> {
            statusLabel = if (autoMode && nextFastStartMillis != null) {
                stringResource(R.string.fasting_next_fast_at, clockFormatter.format(java.time.Instant.ofEpochMilli(nextFastStartMillis).atZone(java.time.ZoneId.systemDefault())))
            } else {
                stringResource(R.string.fasting_idle)
            }
            progress = 0f
            countdownHint = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(17.dp),
            )
            Spacer(Modifier.padding(start = 6.dp))
            Text(
                stringResource(R.string.fasting),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            if (autoStarted) {
                Spacer(Modifier.padding(start = 6.dp))
                Text(
                    stringResource(R.string.fasting_auto_tag),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                statusLabel,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                fontSize = 12.sp,
            )
        }
        if ((phase == FastingPhase.FASTING && hasGoal) || phase == FastingPhase.EATING) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            )
            if (countdownHint != null) {
                Text(
                    countdownHint,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                )
            }
        }
        // Auto mode is self-driving: no Start while idle/eating, no Stop on an
        // auto-started fast. A fast the user started manually (launcher) still
        // shows Stop so it can be ended.
        if (phase == FastingPhase.FASTING) {
            if (!autoStarted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.fasting_stop), fontSize = 13.sp)
                    }
                }
            }
        } else if (!autoMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.fasting_start), fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Compact fasting control for the Add Food hub sheet — one status line plus
 * Start/Stop (Cancel alongside when running). Mirrors the water quick row's
 * placement: the hub is the "I'm about to eat" moment, so stopping there is
 * ergonomically right.
 */
@Composable
fun FastingHubControl(
    phase: FastingPhase,
    fastHours: Int,
    eatHours: Int,
    fastElapsedMillis: Long,
    eatElapsedMillis: Long,
    nextFastStartMillis: Long?,
    nowMillis: Long,
    goalReached: Boolean,
    autoStarted: Boolean,
    autoMode: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fastWindowMillis = fastHours * FastingSession.MILLIS_PER_HOUR
    val clockFormatter = rememberClockFormatter()
    val status = when (phase) {
        FastingPhase.FASTING ->
            if (goalReached) {
                stringResource(R.string.fasting_goal_reached)
            } else if (fastHours > 0) {
                stringResource(R.string.fasting_goal_progress, fastingDurationLabel(fastElapsedMillis), fastHours)
            } else {
                fastingDurationLabel(fastElapsedMillis)
            }
        FastingPhase.EATING -> {
            val remaining = (nextFastStartMillis ?: nowMillis).let { (it - nowMillis).coerceAtLeast(0L) }
            if (autoMode && nextFastStartMillis != null) {
                stringResource(
                    R.string.fasting_fast_starts_at,
                    clockFormatter.format(java.time.Instant.ofEpochMilli(nextFastStartMillis).atZone(java.time.ZoneId.systemDefault())),
                    fastingDurationLabel(remaining),
                )
            } else {
                stringResource(R.string.fasting_fast_starts_in, fastingDurationLabel(remaining))
            }
        }
        FastingPhase.IDLE -> if (autoMode && nextFastStartMillis != null) {
            stringResource(R.string.fasting_next_fast_at, clockFormatter.format(java.time.Instant.ofEpochMilli(nextFastStartMillis).atZone(java.time.ZoneId.systemDefault())))
        } else {
            stringResource(R.string.fasting_idle)
        }
    }
    Column(modifier) {
        SheetSectionHeader(stringResource(R.string.add_food_fasting_section))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(18.dp),
            )
            Spacer(Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.fasting),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    status,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                )
            }
            // Auto mode is self-driving: Start only in manual mode; Stop only
            // for a fast the user started manually (launcher), never an
            // auto-started one.
            if (phase == FastingPhase.FASTING) {
                if (!autoStarted) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.fasting_stop), fontSize = 13.sp)
                    }
                }
            } else if (!autoMode) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.fasting_start), fontSize = 13.sp)
                }
            }
        }
    }
}

/** Localized "12 h 34 m" / "45 m" / "2 h". */
@Composable
private fun fastingDurationLabel(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0L && minutes > 0L -> stringResource(R.string.fasting_duration_h_m, hours, minutes)
        hours > 0L -> stringResource(R.string.fasting_duration_h, hours)
        else -> stringResource(R.string.fasting_duration_m, minutes)
    }
}

/** Local clock format ("20:00" / "8:00 PM") for the next-fast-start labels. */
@Composable
private fun rememberClockFormatter(): java.time.format.DateTimeFormatter {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        java.time.format.DateTimeFormatter.ofPattern(
            clockTimePattern(context),
            java.util.Locale.getDefault(),
        )
    }
}
