package app.chompass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors

@Composable
fun StepsCard(
    steps: Long,
    goal: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = AppColors.Calorie,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.Container))
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.home_steps_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = LocaleFormat.integer(steps),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = stringResource(R.string.home_steps_goal_format, LocaleFormat.integer(goal)),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ActiveCaloriesCard(
    activeCalories: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            LocaleFormat.integer(activeCalories),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
        Text(
            stringResource(R.string.home_active_calories_label),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 2,
        )
    }
}
