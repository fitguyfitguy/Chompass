package app.chompass.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import app.chompass.R
import app.chompass.services.health.DailyActivity
import app.chompass.services.health.DailyWellness
import java.text.NumberFormat

@Composable
internal fun ActivitySection(days: List<DailyActivity>) {
    val integerFormat = remember { NumberFormat.getIntegerInstance() }
    val todayActivity = days.lastOrNull()
    val avgSteps = if (days.isEmpty()) 0L else days.sumOf { it.steps } / days.size
    val weekExerciseMinutes = days.sumOf { it.exerciseMinutes }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_activity_section), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.progress_activity_from_health),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        StatBadgeRow(
            listOf(
                stringResource(R.string.progress_activity_steps_today) to
                    integerFormat.format(todayActivity?.steps ?: 0L),
                stringResource(R.string.progress_activity_steps_avg) to
                    integerFormat.format(avgSteps),
                stringResource(R.string.progress_activity_exercise_week) to
                    stringResource(R.string.progress_activity_minutes_format, weekExerciseMinutes)
            )
        )
    }
}

@Composable
internal fun WellnessSection(days: List<DailyWellness>) {
    val integerFormat = remember { NumberFormat.getIntegerInstance() }
    val todaySleep = days.lastOrNull { it.sleepMinutes != null }?.sleepMinutes
    val restingValues = days.mapNotNull { it.restingHeartRateBpm }
    val avgResting = if (restingValues.isEmpty()) null else restingValues.average().roundToInt()
    val todayHydrationMl = days.lastOrNull { it.hydrationMl != null }?.hydrationMl

    val badges = buildList {
        if (todaySleep != null) {
            add(
                stringResource(R.string.progress_wellness_sleep) to
                    stringResource(R.string.progress_wellness_sleep_format, todaySleep / 60, todaySleep % 60)
            )
        }
        if (avgResting != null) {
            add(
                stringResource(R.string.progress_wellness_resting_hr) to
                    stringResource(R.string.progress_wellness_bpm_format, avgResting)
            )
        }
        if (todayHydrationMl != null) {
            add(
                stringResource(R.string.progress_wellness_hydration) to
                    stringResource(
                        R.string.progress_wellness_hydration_format,
                        integerFormat.format(todayHydrationMl.roundToInt())
                    )
            )
        }
    }
    if (badges.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_wellness_section), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.progress_activity_from_health),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        StatBadgeRow(badges)
    }
}
