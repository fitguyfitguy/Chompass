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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.util.clockTimePattern
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Renders a mg value compactly: whole numbers without decimals. */
private fun formatMg(mg: Double): String =
    if (mg % 1.0 == 0.0) mg.toInt().toString() else String.format(Locale.US, "%.1f", mg)

/**
 * Optional caffeine tracker row (device-pass revision of the caffeine plan).
 * Mirrors [NicotineProgressRow]: today's mg (tracker logs + food-entry
 * caffeine) against an optional daily mg limit. When the limit is 0 (no limit
 * set) the label shows the bare total and the bar is hidden. Tap opens the
 * day's history sheet.
 */
@Composable
fun CaffeineProgressRow(
    currentMg: Double,
    limit: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hasLimit = limit > 0
    val progress = if (hasLimit) (currentMg.toFloat() / limit).coerceIn(0f, 1f) else 0f
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
                stringResource(R.string.caffeine),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasLimit) {
                    stringResource(R.string.caffeine_progress, formatMg(currentMg), limit)
                } else {
                    stringResource(R.string.caffeine_total_today, formatMg(currentMg))
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
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
 * Custom caffeine log sheet: kind chips + mg wheel. The wheel starts at the
 * kind's default mg so "Custom" is a tweak of the quick chip. Mirrors
 * [NicotineCustomCountSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeineCustomSheet(
    onDismiss: () -> Unit,
    onAdd: (CaffeineKind, Double) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var kind by remember { mutableStateOf(CaffeineKind.COFFEE) }
    var mg by remember { mutableStateOf((CaffeineKind.COFFEE.defaultMg ?: 0.0).toInt()) }

    fun switchKind(next: CaffeineKind) {
        kind = next
        mg = (next.defaultMg ?: 0.0).toInt().coerceAtLeast(0)
    }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.caffeine_log_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.padding(horizontal = 31.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaffeineKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { switchKind(option) },
                        label = { Text(stringResource(option.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }

            Text(
                stringResource(R.string.caffeine_mg),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            NumericWheelPicker(
                value = mg,
                onValueChange = { mg = it },
                min = 0,
                max = 500,
                step = 5,
                unit = stringResource(R.string.unit_mg),
            )

            Button(
                onClick = {
                    onAdd(kind, mg.toDouble())
                    onDismiss()
                },
                enabled = mg > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    stringResource(R.string.caffeine_add),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Day-scoped caffeine history (mirrors [NicotineHistorySheet]): the selected
 * day's logs with their time, newest first. Tap a row to edit kind/mg, use
 * the trailing delete button to remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeineHistorySheet(
    day: LocalDate,
    entries: List<CaffeineEntry>,
    onDismiss: () -> Unit,
    onEdit: (CaffeineEntry) -> Unit,
    onDelete: (CaffeineEntry) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    val ctx = LocalContext.current
    val timeFmt = remember(ctx) {
        DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val sorted = remember(entries) { entries.sortedByDescending { it.date } }
    val totalMg = entries.sumOf { it.mg }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.caffeine_history_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.padding(horizontal = 31.dp))
            }

            val dayLabel = remember(ctx) {
                DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault())
                    .format(day)
            }
            if (sorted.isNotEmpty()) {
                Text(
                    stringResource(R.string.caffeine_history_total, dayLabel, formatMg(totalMg)),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }

            if (sorted.isEmpty()) {
                Text(
                    stringResource(R.string.caffeine_no_entries),
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
                        CaffeineHistoryRow(
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
private fun CaffeineHistoryRow(
    entry: CaffeineEntry,
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
            stringResource(R.string.caffeine_mg_value, formatMg(entry.mg)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_caffeine),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

/**
 * Editor for one existing caffeine log (mirrors [NicotineEditSheet]): kind
 * chips + mg wheel, prefilled with the current values. Saving keeps the log's
 * id and time of day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeineEditSheet(
    entry: CaffeineEntry,
    onDismiss: () -> Unit,
    onSave: (CaffeineKind, Double) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var kind by remember { mutableStateOf(entry.kind) }
    var mg by remember { mutableStateOf(entry.mg.toInt().coerceAtLeast(0)) }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.caffeine_edit_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.padding(horizontal = 31.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaffeineKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(stringResource(option.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }

            Text(
                stringResource(R.string.caffeine_mg),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            NumericWheelPicker(
                value = mg,
                onValueChange = { mg = it },
                min = 0,
                max = 500,
                step = 5,
                unit = stringResource(R.string.unit_mg),
            )

            Button(
                onClick = {
                    onSave(kind, mg.toDouble())
                    onDismiss()
                },
                enabled = mg > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    stringResource(R.string.action_save),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
