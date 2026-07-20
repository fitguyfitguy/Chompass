package org.codeberg.fitguy.nofud.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.codeberg.fitguy.nofud.R

@Composable
internal fun BarcodeScannerContent(
    @Suppress("UNUSED_PARAMETER") onBarcode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.barcode_fdroid_unavailable_title)) },
        text = { Text(stringResource(R.string.barcode_fdroid_unavailable_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}
