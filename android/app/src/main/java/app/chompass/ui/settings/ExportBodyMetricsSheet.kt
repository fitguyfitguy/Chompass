package app.chompass.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.export.BodyMetricsExporter
import app.chompass.export.BodyMetricsFormat
import app.chompass.ui.theme.AppColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportBodyMetricsSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var format by remember { mutableStateOf(BodyMetricsFormat.CSV) }
    var status by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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

            Text(
                stringResource(R.string.export_body_metrics_format),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyMetricsFormat.values().forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = { format = f },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Calorie.copy(alpha = 0.18f),
                            selectedLabelColor = AppColors.Calorie,
                        ),
                    )
                }
            }

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                            val measurements = container.bodyMeasurementRepository.entries.first()
                            val result = BodyMetricsExporter.build(weights, bodyFats, measurements, format)
                            if (result == null) {
                                status = context.getString(R.string.export_body_metrics_empty)
                                return@launch
                            }
                            val (name, content) = result
                            try {
                                val dir = File(context.cacheDir, "capture").apply { mkdirs() }
                                val file = File(dir, name)
                                file.writeText(content)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = format.mime
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
