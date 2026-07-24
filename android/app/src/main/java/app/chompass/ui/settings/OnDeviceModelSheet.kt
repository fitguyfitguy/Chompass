package app.chompass.ui.settings

import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.services.ondevice.ModelCatalog
import app.chompass.services.ondevice.OnDeviceDownloadState
import app.chompass.ui.theme.AppColors
import java.util.Locale

private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

/**
 * Model download/management sheet for [SettingsSheet.ON_DEVICE_MODEL]. Shows
 * the Hugging Face disclosure before the first download (not repeated once a
 * model is present), a storage pre-check, download progress, and
 * delete/unload actions. Pause/resume isn't implemented — [onCancelDownload]
 * discards the partial file and a retry starts over; the model is a one-time
 * large fetch, so simple restart was judged not worth range-resume complexity.
 */
@Composable
internal fun OnDeviceModelSheet(
    container: AppContainer,
    selectedModelId: String,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSetOverWifiOnly: (Boolean) -> Unit,
) {
    val manager = container.onDeviceModelDownloadManager
    val entry = remember(selectedModelId) { ModelCatalog.forModelId(selectedModelId) }
    val state by manager.state(selectedModelId).collectAsState(initial = OnDeviceDownloadState.NotDownloaded)
    val overWifiOnly by container.prefs.onDeviceDownloadOverWifiOnly.collectAsState(initial = true)
    val isLoaded = container.onDeviceLlmGateway.isLoaded
    val freeBytes = remember(entry) {
        manager.modelsDir().mkdirs()
        StatFs(manager.modelsDir().path).availableBytes
    }
    val hasEnoughSpace = freeBytes >= entry.sizeBytes + 200L * 1024 * 1024

    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            stringResource(R.string.on_device_model_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "${entry.displayName} · ${gb(entry.sizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.on_device_model_accuracy_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (entry.modelId == ModelCatalog.E4B.modelId) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.on_device_model_e4b_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Spacer(Modifier.height(12.dp))

        when (val current = state) {
            is OnDeviceDownloadState.NotDownloaded -> {
                Text(
                    stringResource(R.string.on_device_model_privacy_notice, entry.modelCardUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.on_device_model_device_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(12.dp))
                if (!hasEnoughSpace) {
                    Text(
                        stringResource(
                            R.string.on_device_model_storage_short,
                            gb(entry.sizeBytes),
                            gb(freeBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ToggleRow(
                    label = stringResource(R.string.on_device_model_wifi_only),
                    checked = overWifiOnly,
                    onChange = onSetOverWifiOnly
                )
                Spacer(Modifier.height(8.dp))
                GradientSaveButton(
                    text = stringResource(R.string.on_device_model_download),
                    enabled = hasEnoughSpace,
                    onClick = onStartDownload
                )
            }
            is OnDeviceDownloadState.Downloading -> {
                Text(
                    stringResource(R.string.on_device_model_downloading, current.progressPercent),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { current.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Calorie
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
            is OnDeviceDownloadState.Verifying -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.on_device_model_verifying), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is OnDeviceDownloadState.Downloaded -> {
                Text(stringResource(R.string.on_device_model_ready), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isLoaded) {
                        stringResource(R.string.on_device_model_loaded)
                    } else {
                        stringResource(R.string.on_device_model_not_loaded)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                if (isLoaded) {
                    TextButton(onClick = onUnload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.on_device_model_unload))
                    }
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.on_device_model_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            is OnDeviceDownloadState.Failed -> {
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                GradientSaveButton(
                    text = stringResource(R.string.on_device_model_retry),
                    onClick = onStartDownload
                )
            }
        }
    }
}
