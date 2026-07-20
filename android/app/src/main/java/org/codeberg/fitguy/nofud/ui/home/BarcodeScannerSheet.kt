package org.codeberg.fitguy.nofud.ui.home

import androidx.compose.runtime.Composable

@Composable
fun BarcodeScannerSheet(
    onBarcode: (String) -> Unit,
    onDismiss: () -> Unit
) = BarcodeScannerContent(onBarcode, onDismiss)
