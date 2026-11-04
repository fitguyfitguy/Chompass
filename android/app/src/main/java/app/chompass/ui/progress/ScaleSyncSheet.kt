package app.chompass.ui.progress

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.scale.ScaleSyncState
import app.chompass.ui.components.ChompassBottomSheet
import com.health.openscale.core.utils.LogManager

/**
 * Bottom sheet driving one native BLE scale sync run (Progress → Scale metrics →
 * Sync from scale). Permission flow is intentionally a standard
 * RequestMultiplePermissions contract here — unlike the Health Connect contract,
 * which must sit on MainActivity for its session lifecycle. The feature is gated
 * to Android 12+ by the caller (ProgressScreen), so only the BLUETOOTH_SCAN /
 * BLUETOOTH_CONNECT pair is requested; no pre-31 location fallback exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleSyncSheet(
    syncState: ScaleSyncState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            onStart()
        } else {
            LogManager.w("ChompassScaleSync", "nearby-devices permission denied")
        }
    }

    ChompassBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.progress_sync_scale_metrics),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when (val state = syncState) {
                ScaleSyncState.Idle -> {
                    Text(
                        stringResource(R.string.progress_sync_scale_rationale),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 31) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.progress_sync_scale_grant))
                    }
                }

                ScaleSyncState.Scanning -> {
                    SyncBusyRow(text = stringResource(R.string.progress_sync_scale_scanning), onCancel = onCancel)
                }

                is ScaleSyncState.Connecting -> SyncBusyRow(
                    text = stringResource(R.string.progress_sync_scale_reading, state.deviceName),
                    onCancel = onCancel,
                )

                is ScaleSyncState.Reading -> SyncBusyRow(
                    text = stringResource(R.string.progress_sync_scale_reading, state.deviceName),
                    onCancel = onCancel,
                )

                is ScaleSyncState.Done -> {
                    Text(
                        stringResource(R.string.progress_sync_scale_done, state.importedCount),
                        fontSize = 14.sp,
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }

                is ScaleSyncState.Failed -> {
                    Text(
                        stringResource(R.string.progress_sync_scale_failed, state.message),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                onCancel()
                                onStart()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncBusyRow(text: String, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(text, fontSize = 14.sp)
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_cancel))
    }
}
