package app.chompass.ui.home

import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.FudGlassTextField
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.theme.AppTextOpacity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualActiveSheet(
    onSave: (name: String, calories: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf(100) }
    val canSave = name.isNotBlank() && calories > 0

    ChompassBottomSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.manual_active_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.manual_active_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(14.dp))
        FudGlassTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.manual_active_name_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        NumericWheelPicker(
            value = calories,
            onValueChange = { calories = it },
            min = 10,
            max = 2000,
            step = 10,
            unit = stringResource(R.string.unit_kcal),
        )
        Spacer(Modifier.height(16.dp))
        SheetStickyPrimaryBar(
            primaryLabel = stringResource(R.string.manual_active_save),
            primaryEnabled = canSave,
            onPrimary = {
                if (!canSave) return@SheetStickyPrimaryBar
                onSave(name, calories)
                onDismiss()
            },
        )
    }
}
