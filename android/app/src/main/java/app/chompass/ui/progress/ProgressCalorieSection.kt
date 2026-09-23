package app.chompass.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.EnergyFormat
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.navigation.LocalEnergyUnit
import java.time.LocalDate
import app.chompass.ui.theme.AppTextOpacity

@Composable
internal fun CalorieSection(
    dailyCalories: List<Pair<LocalDate, Int>>,
    calorieGoal: Int,
    calorieAverage: Int? = null,
    dailyCalorieGoals: Map<LocalDate, Int> = emptyMap(),
    /** Day-type/untracked marker lane inputs (UI-UX §10). */
    dayTypeByDay: Map<String, String> = emptyMap(),
    untrackedDays: Set<String> = emptySet(),
    typeColorOf: (String) -> androidx.compose.ui.graphics.Color = { androidx.compose.ui.graphics.Color.Transparent },
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.progressCollapseHeader(expanded) { expanded = !expanded },
        ) {
            Text(stringResource(R.string.progress_calories_section), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (calorieAverage != null) {
                Text(
                    stringResource(R.string.progress_avg_format, EnergyFormat.quantity(calorieAverage, LocalEnergyUnit.current), energyUnitLabel()),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                )
            }
            Spacer(Modifier.width(8.dp))
            CollapseChevron(expanded)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dailyCalories.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.progress_no_food),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                        )
                    }
                } else {
                    CalorieBarChart(
                        dailyCalories = downsampleCalorieBars(dailyCalories),
                        goal = calorieGoal,
                        dailyGoals = dailyCalorieGoals,
                        dayTypeByDay = dayTypeByDay,
                        untrackedDays = untrackedDays,
                        typeColorOf = typeColorOf,
                    )
                }
            }
        }
    }
}
