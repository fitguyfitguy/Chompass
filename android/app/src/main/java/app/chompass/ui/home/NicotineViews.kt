package app.chompass.ui.home
import app.chompass.ui.settings.GradientSaveButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.theme.warning
import app.chompass.ui.util.clockTimePattern
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Optional nicotine tracker row (docs/local/PLAN_NICOTINE_TRACKER.md).
 * Mirrors [WaterProgressRow]: count against an optional daily limit. When the
 * limit is 0 (no limit set) the label shows the bare count and the bar is
 * hidden. Tap opens the day's history sheet.
 */
@Composable
fun NicotineProgressRow(
    current: Int,
    limit: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hasLimit = limit > 0
    val progress = if (hasLimit) (current.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    } else {
        modifier.fillMaxWidth().padding(vertical = 4.dp)
    }
    Column(
        modifier = rowModifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.nicotine),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasLimit) {
                    stringResource(R.string.nicotine_progress, current, limit)
                } else {
                    stringResource(R.string.nicotine_count_today, current)
                },
                color = MaterialTheme.colorScheme.warning,
                fontSize = 12.sp,
            )
        }
        if (hasLimit) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            )
        }
    }
}

/**
 * Custom nicotine log sheet: kind chips + count wheel + optional mg wheel
 * (0 = no mg recorded). Mirrors [WaterCustomAmountSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicotineCustomCountSheet(
    onDismiss: () -> Unit,
    onAdd: (NicotineKind, Int, Double?) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var kind by remember { mutableStateOf(NicotineKind.CIGARETTE) }
    var count by remember { mutableStateOf(1) }
    var mg by remember { mutableStateOf(0) }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.nicotine_log_title),
                onCancel = onDismiss,
            )

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NicotineKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(stringResource(option.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.18f),
                        ),
                    )
                }
            }

            Text(
                stringResource(R.string.nicotine_count),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            NumericWheelPicker(
                value = count,
                onValueChange = { count = it },
                min = 1,
                max = 20,
                step = 1,
            )

            Text(
                stringResource(R.string.nicotine_mg_optional),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            NumericWheelPicker(
                value = mg,
                onValueChange = { mg = it },
                min = 0,
                max = 30,
                step = 1,
                unit = stringResource(R.string.unit_mg),
            )

            GradientSaveButton(
                text = stringResource(R.string.nicotine_add),
                enabled = count > 0,
                onClick = {
                    onAdd(kind, count, mg.takeIf { it > 0 }?.toDouble())
                    onDismiss()
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Day-scoped nicotine history (mirrors [WaterHistorySheet]): the selected
 * day's logs with their time, newest first. Tap a row to edit kind/count/mg,
 * use the trailing delete button to remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicotineHistorySheet(
    day: LocalDate,
    entries: List<NicotineEntry>,
    onDismiss: () -> Unit,
    onEdit: (NicotineEntry) -> Unit,
    onDelete: (NicotineEntry) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    val ctx = LocalContext.current
    val timeFmt = remember(ctx) {
        DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val sorted = remember(entries) { entries.sortedByDescending { it.date } }
    val total = entries.sumOf { it.count }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.nicotine_history_title),
                onCancel = onDismiss,
            )

            val dayLabel = remember(ctx) {
                DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault())
                    .format(day)
            }
            if (sorted.isNotEmpty()) {
                Text(
                    stringResource(R.string.nicotine_history_total, dayLabel, total),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }

            if (sorted.isEmpty()) {
                Text(
                    stringResource(R.string.nicotine_no_entries),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sorted.forEach { entry ->
                        NicotineHistoryRow(
                            entry = entry,
                            timeFmt = timeFmt,
                            onEdit = { onEdit(entry) },
                            onDelete = { onDelete(entry) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NicotineHistoryRow(
    entry: NicotineEntry,
    timeFmt: DateTimeFormatter,
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
        Text(
            timeFmt.format(entry.date),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(entry.kind.labelRes),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (entry.mg != null) {
                stringResource(R.string.nicotine_count_with_mg, entry.count, entry.mg)
            } else {
                entry.count.toString()
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.warning,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_nicotine),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

/**
 * Editor for one existing nicotine log (mirrors [WaterEditAmountSheet]): kind
 * chips + count + optional mg, prefilled with the current values. Saving keeps
 * the log's id and time of day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicotineEditSheet(
    entry: NicotineEntry,
    onDismiss: () -> Unit,
    onSave: (NicotineKind, Int, Double?) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var kind by remember { mutableStateOf(entry.kind) }
    var count by remember { mutableStateOf(entry.count) }
    var mg by remember { mutableStateOf(entry.mg?.toInt() ?: 0) }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.nicotine_edit_title),
                onCancel = onDismiss,
            )

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NicotineKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(stringResource(option.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.18f),
                        ),
                    )
                }
            }

            Text(
                stringResource(R.string.nicotine_count),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            NumericWheelPicker(
                value = count,
                onValueChange = { count = it },
                min = 1,
                max = 20,
                step = 1,
            )

            Text(
                stringResource(R.string.nicotine_mg_optional),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            NumericWheelPicker(
                value = mg,
                onValueChange = { mg = it },
                min = 0,
                max = 30,
                step = 1,
                unit = stringResource(R.string.unit_mg),
            )

            GradientSaveButton(
                text = stringResource(R.string.action_save),
                enabled = count > 0,
                onClick = {
                    onSave(kind, count, mg.takeIf { it > 0 }?.toDouble())
                    onDismiss()
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
