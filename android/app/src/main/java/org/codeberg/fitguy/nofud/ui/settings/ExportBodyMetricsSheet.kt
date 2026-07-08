package org.codeberg.fitguy.nofud.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBodyMetricsSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    var status by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.92f else 0.96f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.export_body_metrics_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.export_body_metrics_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )

            status?.let {
                Text(it, color = Color(0xFFFF3B30), fontSize = 13.sp)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.CalorieGradient)
                    .clickable {
                        status = null
                        scope.launch {
                            val weights = container.weightRepository.entries.first()
                            val bodyFats = container.bodyFatRepository.entries.first()
                            if (weights.isEmpty() && bodyFats.isEmpty()) {
                                status = context.getString(R.string.export_body_metrics_empty)
                                return@launch
                            }
                            try {
                                val csv = buildMetricsCsv(weights, bodyFats)
                                val dir = File(context.cacheDir, "capture").apply { mkdirs() }
                                val file = File(dir, "NoFUD-Body-Metrics.csv")
                                file.writeText(csv)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, context.getString(R.string.export_body_metrics_title))
                                )
                                onDismiss()
                            } catch (e: Exception) {
                                status = e.localizedMessage ?: context.getString(R.string.export_failed)
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.export_action),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

private fun buildMetricsCsv(
    weights: List<org.codeberg.fitguy.nofud.models.WeightEntry>,
    bodyFats: List<org.codeberg.fitguy.nofud.models.BodyFatEntry>
): String {
    val timeFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    val rows = StringBuilder()
    rows.append("metric,timestamp,value,unit\n")
    weights.sortedBy { it.date }.forEach { entry ->
        rows.append("weight,")
            .append(csvEscape(timeFmt.format(entry.date)))
            .append(",")
            .append(csvEscape(String.format(java.util.Locale.US, "%.2f", entry.weightKg)))
            .append(",kg\n")
    }
    bodyFats.sortedBy { it.date }.forEach { entry ->
        rows.append("body_fat,")
            .append(csvEscape(timeFmt.format(entry.date)))
            .append(",")
            .append(csvEscape(String.format(java.util.Locale.US, "%.2f", entry.bodyFatPercent)))
            .append(",percent\n")
    }
    return rows.toString()
}

private fun csvEscape(field: String): String =
    if (field.contains(',') || field.contains('"') || field.contains('\n')) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else field
