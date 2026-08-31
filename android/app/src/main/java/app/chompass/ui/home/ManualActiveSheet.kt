package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ManualActiveEntry
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.theme.AppTextOpacity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualActiveSheet(
    onSave: (name: String, calories: Int) -> Unit,
    onDismiss: () -> Unit,
    initial: ManualActiveEntry? = null,
    todayEntries: List<ManualActiveEntry> = emptyList(),
    day: LocalDate = LocalDate.now(),
    onEdit: ((ManualActiveEntry) -> Unit)? = null,
    onDelete: ((ManualActiveEntry) -> Unit)? = null,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var calories by remember(initial?.id) { mutableStateOf(initial?.calories?.takeIf { it > 0 } ?: 100) }
    val canSave = calories > 0
    val editing = initial != null

    ChompassBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(if (editing) R.string.manual_active_edit_title else R.string.manual_active_title),
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
                primaryLabel = stringResource(
                    if (editing) R.string.action_save else R.string.manual_active_save,
                ),
                primaryEnabled = canSave,
                onPrimary = {
                    if (!canSave) return@SheetStickyPrimaryBar
                    onSave(name, calories)
                    onDismiss()
                },
            )
            if (!editing && todayEntries.isNotEmpty() && onEdit != null && onDelete != null) {
                Spacer(Modifier.height(20.dp))
                val dayLabel = remember(day) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault())
                        .format(day)
                }
                Text(
                    stringResource(
                        R.string.manual_active_history_total,
                        dayLabel,
                        todayEntries.sumOf { it.calories },
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todayEntries.forEach { entry ->
                        ManualActiveHistoryRow(
                            entry = entry,
                            onEdit = { onEdit(entry) },
                            onDelete = { onDelete(entry) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ManualActiveHistoryRow(
    entry: ManualActiveEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            entry.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
        Text(
            "${entry.calories} ${stringResource(R.string.unit_kcal)}",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.tertiary,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_manual_active),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}
