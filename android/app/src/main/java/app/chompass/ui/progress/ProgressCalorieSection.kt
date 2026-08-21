package app.chompass.ui.progress

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
import java.time.LocalDate
import app.chompass.ui.theme.AppTextOpacity

@Composable
internal fun CalorieSection(
    dailyCalories: List<Pair<LocalDate, Int>>,
    calorieGoal: Int,
    calorieAverage: Int? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_calories_section), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (calorieAverage != null) {
                Text(
                    stringResource(R.string.progress_avg_format, calorieAverage),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                )
            }
        }
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
            )
        }
    }
}
