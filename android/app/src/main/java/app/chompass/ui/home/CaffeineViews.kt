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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.chompass.models.HabitPreset
import app.chompass.models.HabitPresetDomain
import app.chompass.models.MilkKind
import app.chompass.models.builtinCaffeineDefaultMg
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.ChompassPinnedFooterSheet
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.theme.caffeine
import app.chompass.ui.util.clockTimePattern
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Renders a mg value compactly: whole numbers without decimals. */
internal fun formatMg(mg: Double): String =
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
                color = MaterialTheme.colorScheme.caffeine,
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
    presets: List<HabitPreset> = HabitPresetDomain.CAFFEINE.defaultCatalog.presets,
    onDismiss: () -> Unit,
    onAdd: (String, Double, MilkKind?, Int) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    val initial = presets.firstOrNull()
        ?: HabitPreset(CaffeineKind.COFFEE.storageKey, defaultMg = CaffeineKind.COFFEE.defaultMg)
    var kind by remember { mutableStateOf(initial.id) }
    var mg by remember { mutableStateOf((initial.defaultMg ?: builtinCaffeineDefaultMg(initial.id) ?: 0.0).toInt().coerceAtLeast(0)) }
    var milkKind by remember { mutableStateOf(MilkKind.fromStorage(initial.milkKind)) }
    var milkMl by remember { mutableStateOf(initial.milkMl.coerceIn(0, MilkKind.MAX_ML)) }

    fun switchKind(next: HabitPreset) {
        kind = next.id
        mg = (next.defaultMg ?: builtinCaffeineDefaultMg(next.id) ?: 0.0)
            .toInt()
            .coerceIn(0, 500)
        milkKind = MilkKind.fromStorage(next.milkKind)
        milkMl = next.milkMl.coerceIn(0, MilkKind.MAX_ML)
    }

    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        toolbar = {
            SheetReviewToolbar(
                title = stringResource(R.string.caffeine_log_title),
                onCancel = onDismiss,
            )
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { option ->
                        FilterChip(
                            selected = kind == option.id,
                            onClick = { switchKind(option) },
                            label = { Text(trackerPresetLabel(presets, option.id, HabitPresetDomain.CAFFEINE)) },
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

                CaffeineMilkSection(
                    milkKind = milkKind,
                    milkMl = milkMl,
                    onKindChange = { milkKind = it },
                    onMlChange = { milkMl = it },
                )
            }
        },
        footer = {
            GradientSaveButton(
                text = stringResource(R.string.caffeine_add),
                enabled = mg > 0,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                onClick = {
                    onAdd(kind, mg.toDouble(), milkKind.takeIf { milkMl > 0 }, milkMl)
                    onDismiss()
                },
            )
        },
    )
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
    presets: List<HabitPreset> = HabitPresetDomain.CAFFEINE.defaultCatalog.presets,
    onDismiss: () -> Unit,
    onEdit: (CaffeineEntry) -> Unit,
    onDelete: (CaffeineEntry) -> Unit,
    /** Custom-amount log: with every preset deleted this is the only path left (5.0.0 MUST 2). */
    onCustom: () -> Unit = {},
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
            SheetReviewToolbar(
                title = stringResource(R.string.caffeine_history_title),
                onCancel = onDismiss,
            )

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
                            presets = presets,
                            timeFmt = timeFmt,
                            onEdit = { onEdit(entry) },
                            onDelete = { onDelete(entry) },
                        )
                    }
                }
            }
            TextButton(
                onClick = onCustom,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.caffeine_custom_short))
            }
        }
    }
}

@Composable
private fun CaffeineHistoryRow(
    entry: CaffeineEntry,
    presets: List<HabitPreset>,
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
            trackerPresetLabel(presets, entry.kind, HabitPresetDomain.CAFFEINE),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.caffeine_mg_value, formatMg(entry.mg)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.caffeine,
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
    presets: List<HabitPreset> = HabitPresetDomain.CAFFEINE.defaultCatalog.presets,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var kind by remember { mutableStateOf(entry.kind) }
    var mg by remember { mutableStateOf(entry.mg.toInt().coerceAtLeast(0)) }
    // An entry whose preset was deleted keeps an "Other"-labeled chip so it
    // stays editable.
    val chips = remember(entry.kind, presets) {
        if (presets.any { it.id == entry.kind }) presets else presets + HabitPreset(entry.kind)
    }

    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        toolbar = {
            SheetReviewToolbar(
                title = stringResource(R.string.caffeine_edit_title),
                onCancel = onDismiss,
            )
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { option ->
                        FilterChip(
                            selected = kind == option.id,
                            onClick = { kind = option.id },
                            label = { Text(trackerPresetLabel(chips, option.id, HabitPresetDomain.CAFFEINE)) },
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
            }
        },
        footer = {
            GradientSaveButton(
                text = stringResource(R.string.action_save),
                enabled = mg > 0,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                onClick = {
                    onSave(kind, mg.toDouble())
                    onDismiss()
                },
            )
        },
    )
}

@Composable
internal fun CaffeineMilkSection(
    milkKind: MilkKind?,
    milkMl: Int,
    onKindChange: (MilkKind) -> Unit,
    onMlChange: (Int) -> Unit,
) {
    Text(
        stringResource(R.string.caffeine_milk_section),
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MilkKind.entries.forEach { option ->
            FilterChip(
                selected = milkKind == option,
                onClick = { onKindChange(option) },
                label = { Text(stringResource(option.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ),
            )
        }
    }
    Text(
        stringResource(R.string.caffeine_milk_ml),
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    NumericWheelPicker(
        value = milkMl,
        onValueChange = onMlChange,
        min = 0,
        max = MilkKind.MAX_ML,
        step = 10,
        unit = stringResource(R.string.unit_ml),
    )
}
