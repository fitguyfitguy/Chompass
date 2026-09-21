package app.chompass.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.ChompassBottomSheet
import java.time.LocalDate

/** Mark the viewed diary day as not tracked (#106). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UntrackedDaySheet(
    date: LocalDate,
    untracked: Boolean,
    kcal: Int?,
    onSave: (untracked: Boolean, kcal: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var flagged by rememberSaveable { mutableStateOf(untracked) }
    var kcalText by rememberSaveable { mutableStateOf(kcal?.toString().orEmpty()) }

    ChompassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                stringResource(R.string.untracked_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.untracked_sheet_body, date.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.untracked_sheet_toggle),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = flagged, onCheckedChange = { flagged = it })
            }
            if (flagged) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.untracked_sheet_kcal_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    val parsed = kcalText.toIntOrNull()
                    onSave(flagged, parsed)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
