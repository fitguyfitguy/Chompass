package org.codeberg.fitguy.nofud.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.services.ondevice.ModelCatalog
import org.codeberg.fitguy.nofud.services.ondevice.OnDeviceDownloadState
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import java.util.Locale

private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

/**
 * Model download/management sheet for [SettingsSheet.ON_DEVICE_MODEL]. Shows
 * the Hugging Face disclosure before the first download (not repeated once a
 * model is present), a storage pre-check, download progress, and
 * delete/unload actions. Pause/resume isn't implemented — [onCancelDownload]
 * discards the partial file and a retry starts over; the model is a one-time
 * ~2.4GB fetch, so simple restart was judged not worth range-resume complexity.
 */
@Composable
internal fun OnDeviceModelSheet(
    container: AppContainer,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSetOverWifiOnly: (Boolean) -> Unit,
) {
    val manager = container.onDeviceModelDownloadManager
    val state by manager.state().collectAsState(initial = OnDeviceDownloadState.NotDownloaded)
    val overWifiOnly by container.prefs.onDeviceDownloadOverWifiOnly.collectAsState(initial = true)
    val entry = ModelCatalog.current
    val isLoaded = container.onDeviceLlmGateway.isLoaded
    val freeBytes = remember {
        manager.modelsDir().mkdirs()
        StatFs(manager.modelsDir().path).availableBytes
    }
    val hasEnoughSpace = freeBytes >= entry.sizeBytes + 200L * 1024 * 1024

    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            "On-device model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "${entry.displayName} · ${gb(entry.sizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))

        when (val current = state) {
            is OnDeviceDownloadState.NotDownloaded -> {
                Text(
                    "Runs entirely on this device — nothing you type or photograph is sent to a server. " +
                        "Downloading fetches the model file directly from Hugging Face " +
                        "(${ModelCatalog.MODEL_CARD_URL}), a third-party host not otherwise used by this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tested on Pixel 9a (GrapheneOS). Other devices — especially lower-tier or " +
                        "non-Tensor hardware — haven't been validated yet and may be slower or " +
                        "unsupported; a cloud provider is a safer default there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(12.dp))
                if (!hasEnoughSpace) {
                    Text(
                        "Not enough free storage. Need at least ${gb(entry.sizeBytes)} free (have ${gb(freeBytes)}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ToggleRow(
                    label = "Download over Wi-Fi only",
                    checked = overWifiOnly,
                    onChange = onSetOverWifiOnly
                )
                Spacer(Modifier.height(8.dp))
                GradientSaveButton(text = "Download", enabled = hasEnoughSpace, onClick = onStartDownload)
            }
            is OnDeviceDownloadState.Downloading -> {
                val percent = current.progressPercent
                Text("Downloading… $percent%", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Calorie
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            is OnDeviceDownloadState.Verifying -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying download…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is OnDeviceDownloadState.Downloaded -> {
                Text("Downloaded and ready.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isLoaded) "Model loaded — ready for instant use." else "Not loaded — first request after app start may take up to a minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                if (isLoaded) {
                    TextButton(onClick = onUnload, modifier = Modifier.fillMaxWidth()) {
                        Text("Unload model (frees ~2GB memory)")
                    }
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete downloaded model", color = MaterialTheme.colorScheme.error)
                }
            }
            is OnDeviceDownloadState.Failed -> {
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                GradientSaveButton(text = "Retry download", onClick = onStartDownload)
            }
        }
    }
}
