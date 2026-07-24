package app.chompass.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.chompass.models.MacroValueFormatter
import app.chompass.ui.theme.AppColors

@Composable
internal fun MacroAveragesSection(
    avgProtein: Double, avgCarbs: Double, avgFat: Double,
    proteinGoal: Int, carbsGoal: Int, fatGoal: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.progress_macro_averages), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        MacroProgressRow(stringResource(R.string.macro_protein), avgProtein, proteinGoal, AppColors.Protein)
        MacroProgressRow(stringResource(R.string.macro_carbs), avgCarbs, carbsGoal, AppColors.Carbs)
        MacroProgressRow(stringResource(R.string.macro_fat), avgFat, fatGoal, AppColors.Fat)
    }
}

@Composable
internal fun MacroProgressRow(label: String, current: Double, goal: Int, accentColor: Color) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = accentColor)
            Spacer(Modifier.weight(1f))
            Text(
                "${MacroValueFormatter.string(current)}g / ${goal}g",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
            val w = maxWidth
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.16f))
            )
            val barWidth = (w * progress).coerceAtLeast(6.dp)
            Box(
                Modifier
                    .width(barWidth)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
        }
    }
}
