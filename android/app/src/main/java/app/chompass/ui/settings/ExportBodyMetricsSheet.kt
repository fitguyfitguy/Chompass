package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.export.BodyMetricsExporter
import app.chompass.export.BodyMetricsFormat
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.ChompassBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportBodyMetricsSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberChompassSheetState()
    var format by remember { mutableStateOf(BodyMetricsFormat.CSV) }
    var status by remember { mutableStateOf<String?>(null) }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
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
            ExportFormatChipRow(
                options = BodyMetricsFormat.values(),
                selected = format,
                label = { it.label },
                onSelect = { format = it },
            )

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            ExportPrimaryButton {
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
                        shareExportedFile(
                            context = context,
                            fileName = name,
                            content = content,
                            mimeType = format.mime,
                            chooserTitle = context.getString(R.string.export_body_metrics_title),
                        )
                        onDismiss()
                    } catch (e: Exception) {
                        status = e.localizedMessage ?: context.getString(R.string.export_failed)
                    }
                }
            }
        }
    }
}
