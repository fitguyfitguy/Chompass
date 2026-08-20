package app.chompass.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.WheelPicker
import app.chompass.ui.util.clockTimePattern
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DailySummaryTimeSheet(
    hour: Int,
    minute: Int,
    onSave: (hour: Int, minute: Int) -> Unit,
) {
    val currentMinutes = (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59))
    val options = remember(currentMinutes) {
        val grid = (0 until 24 * 60 step 15).toList()
        if (currentMinutes in grid) grid else (grid + currentMinutes).sorted()
    }
    var selectedMinutes by remember(currentMinutes) { mutableIntStateOf(currentMinutes) }
    val context = LocalContext.current
    val formatter = remember(context) {
        DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
    }
    Text(
        stringResource(R.string.settings_notif_daily_summary_time),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(16.dp))
    WheelPicker(
        items = options,
        selected = selectedMinutes,
        onSelect = { selectedMinutes = it },
        label = { LocalTime.of(it / 60, it % 60).format(formatter) },
    )
    Spacer(Modifier.height(16.dp))
    GradientSaveButton { onSave(selectedMinutes / 60, selectedMinutes % 60) }
    Spacer(Modifier.height(8.dp))
}
