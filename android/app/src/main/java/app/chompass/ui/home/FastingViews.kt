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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FastingSession

/**
 * Home progress row for the optional intermittent-fasting timer
 * (docs/local/PLAN_FASTING_TRACKER.md). Shows the elapsed time against the
 * optional goal with Start / Stop / Cancel. Local-only: nothing is synced or
 * exported. Placement: directly below the nicotine row in HomeScreen.
 */
@Composable
fun FastingProgressRow(
    active: Boolean,
    elapsedMillis: Long,
    goalHours: Int,
    goalReached: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasGoal = goalHours > 0
    val goalMillis = goalHours * FastingSession.MILLIS_PER_HOUR
    val progress = if (hasGoal) (elapsedMillis.toFloat() / goalMillis).coerceIn(0f, 1f) else 0f
    val statusLabel = when {
        !active -> stringResource(R.string.fasting_idle)
        goalReached -> stringResource(R.string.fasting_goal_reached)
        hasGoal -> stringResource(
            R.string.fasting_goal_progress,
            fastingDurationLabel(elapsedMillis),
            goalHours,
        )
        else -> fastingDurationLabel(elapsedMillis)
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
            Spacer(Modifier.weight(1f))
            Text(
                statusLabel,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                fontSize = 12.sp,
            )
        }
        if (hasGoal && active) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.fasting_stop), fontSize = 13.sp)
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.fasting_cancel), fontSize = 13.sp)
                }
            } else {
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
    active: Boolean,
    elapsedMillis: Long,
    goalHours: Int,
    goalReached: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        !active -> stringResource(R.string.fasting_idle)
        goalReached -> stringResource(R.string.fasting_goal_reached)
        goalHours > 0 -> stringResource(
            R.string.fasting_goal_progress,
            fastingDurationLabel(elapsedMillis),
            goalHours,
        )
        else -> fastingDurationLabel(elapsedMillis)
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
        if (active) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.fasting_stop), fontSize = 13.sp)
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.fasting_cancel), fontSize = 12.sp)
            }
        } else {
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
