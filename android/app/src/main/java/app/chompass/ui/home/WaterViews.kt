package app.chompass.ui.home

import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.ChompassBottomSheet
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.chompass.models.WaterAmountFormat
import app.chompass.models.WaterEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.water
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.util.clockTimePattern
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun waterProgressLabel(currentMl: Int, goalMl: Int, useMetric: Boolean): String =
    if (useMetric) {
        stringResource(R.string.water_progress, currentMl, goalMl)
    } else {
        stringResource(
            R.string.water_progress_fl_oz,
            WaterAmountFormat.flOzFromMl(currentMl),
            WaterAmountFormat.flOzFromMl(goalMl),
        )
    }

@Composable
fun WaterProgressRow(
    current: Int,
    goal: Int,
    useMetric: Boolean = true,
    auto: Boolean = false,
    onAutoClick: (() -> Unit)? = null,
    /** Preformatted "Next 300 ml · 15:24" hint under the bar; null hides it. */
    nextDrinkLabel: String? = null,
    /** When set, the whole row becomes tappable (opens the day's water history). */
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    }
    Column(
        modifier = rowModifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.water,
                modifier = Modifier.size(17.dp),
            )
            Text(
                stringResource(R.string.water),
                modifier = Modifier.padding(start = 6.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            if (auto) {
                val badgeModifier = if (onAutoClick != null) {
                    Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onAutoClick)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                } else {
                    Modifier.padding(start = 6.dp)
                }
                Text(
                    stringResource(R.string.settings_water_auto_badge),
                    modifier = badgeModifier,
                    color = AppColors.Calorie,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                waterProgressLabel(current, goal, useMetric),
                color = MaterialTheme.colorScheme.water,
                fontSize = 12.sp,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AppColors.Calorie,
            trackColor = AppColors.Calorie.copy(alpha = 0.16f),
        )
        if (nextDrinkLabel != null) {
            Text(
                nextDrinkLabel,
                color = AppColors.Calorie,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterCustomAmountSheet(
    useMetric: Boolean = true,
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var amountMl by remember { mutableStateOf(if (useMetric) 250 else 8) }
    val amountFlOz = WaterAmountFormat.flOzFromMl(amountMl)

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
                    stringResource(R.string.water_log_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.padding(horizontal = 31.dp))
            }

            Text(
                stringResource(R.string.water_how_much),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            if (useMetric) {
                NumericWheelPicker(
                    value = amountMl,
                    onValueChange = { amountMl = it },
                    min = 50,
                    max = 5000,
                    step = 50,
                    unit = stringResource(R.string.unit_ml),
                )
            } else {
                var flOz by remember(amountFlOz) { mutableStateOf(amountFlOz) }
                NumericWheelPicker(
                    value = flOz,
                    onValueChange = { flOz = it; amountMl = WaterAmountFormat.mlFromFlOz(it) },
                    min = 1,
                    max = 169,
                    step = 1,
                    unit = stringResource(R.string.unit_fl_oz),
                )
            }

            Button(
                onClick = {
                    onAdd(amountMl)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Calorie),
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null)
                Text(
                    stringResource(R.string.water_add),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Day-scoped water history (Codeberg #58b): the selected day's sips with their
 * time of day, newest first. Tap a row to edit its amount, use the trailing
 * delete button to remove it. Works for today and any past day the user has
 * switched to — that is the "edit history" gap the issue describes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterHistorySheet(
    day: LocalDate,
    entries: List<WaterEntry>,
    useMetric: Boolean,
    onDismiss: () -> Unit,
    onEdit: (WaterEntry) -> Unit,
    onDelete: (WaterEntry) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    val ctx = LocalContext.current
    val timeFmt = remember(ctx) {
        DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val sorted = remember(entries) { entries.sortedByDescending { it.date } }
    val total = entries.sumOf { it.milliliters }

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
                    stringResource(R.string.water_history_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.water,
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
                    stringResource(
                        R.string.water_history_total,
                        dayLabel,
                        waterHistoryAmountLabel(total, useMetric),
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }

            if (sorted.isEmpty()) {
                Text(
                    stringResource(R.string.water_no_entries),
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
                        WaterHistoryRow(
                            entry = entry,
                            useMetric = useMetric,
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
private fun WaterHistoryRow(
    entry: WaterEntry,
    useMetric: Boolean,
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
        Icon(
            Icons.Filled.WaterDrop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.water.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            timeFmt.format(entry.date),
            modifier = Modifier.padding(start = 10.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            waterHistoryAmountLabel(entry.milliliters, useMetric),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.water,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_water),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun waterHistoryAmountLabel(ml: Int, useMetric: Boolean): String =
    if (useMetric) {
        stringResource(R.string.water_amount_ml, ml)
    } else {
        stringResource(R.string.water_amount_fl_oz, WaterAmountFormat.flOzFromMl(ml))
    }

/**
 * Amount editor for one existing sip (Codeberg #58b): same wheel picker as the
 * add sheet, prefilled with the current amount. Saving keeps the sip's id and
 * time of day — only the amount changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterEditAmountSheet(
    entry: WaterEntry,
    useMetric: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var amountMl by remember { mutableStateOf(entry.milliliters) }
    val amountFlOz = WaterAmountFormat.flOzFromMl(amountMl)

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
                    stringResource(R.string.water_edit_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.padding(horizontal = 31.dp))
            }

            Text(
                stringResource(R.string.water_how_much),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            if (useMetric) {
                NumericWheelPicker(
                    value = amountMl,
                    onValueChange = { amountMl = it },
                    min = 50,
                    max = 5000,
                    step = 50,
                    unit = stringResource(R.string.unit_ml),
                )
            } else {
                var flOz by remember(amountFlOz) { mutableStateOf(amountFlOz) }
                NumericWheelPicker(
                    value = flOz,
                    onValueChange = { flOz = it; amountMl = WaterAmountFormat.mlFromFlOz(it) },
                    min = 1,
                    max = 169,
                    step = 1,
                    unit = stringResource(R.string.unit_fl_oz),
                )
            }

            Button(
                onClick = {
                    onSave(amountMl)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Calorie),
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null)
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
