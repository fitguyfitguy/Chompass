package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.AppContainer
import app.chompass.export.DiaryExporter
import app.chompass.export.DiaryFormat
import app.chompass.export.DiaryRange
import app.chompass.models.MealType
import app.chompass.models.UserProfile
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.theme.AppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportDiarySheet(
    container: AppContainer,
    profile: UserProfile?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var range by remember { mutableStateOf(DiaryRange.THIS_WEEK) }
    var format by remember { mutableStateOf(DiaryFormat.JSON) }
    var customStart by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
    var customEnd by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end" | null
    var status by remember { mutableStateOf<String?>(null) }

    val mealNames: Map<MealType, String> = MealType.values().associateWith { stringResource(it.displayNameRes) }
    val niceDate = DateTimeFormatter.ofPattern("d MMM yyyy")

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.export_diary_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)

            Text(stringResource(R.string.export_range), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DiaryRange.values().forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(stringResource(r.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Calorie.copy(alpha = 0.18f),
                            selectedLabelColor = AppColors.Calorie,
                        ),
                    )
                }
            }

            if (range == DiaryRange.CUSTOM) {
                DateRow(stringResource(R.string.export_from), customStart.format(niceDate)) { picking = "start" }
                DateRow(stringResource(R.string.export_to), customEnd.format(niceDate)) { picking = "end" }
            }

            Text(stringResource(R.string.export_format), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            ExportFormatChipRow(
                options = DiaryFormat.values(),
                selected = format,
                label = { it.label },
                onSelect = { format = it },
            )

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            ExportPrimaryButton {
                status = null
                scope.launch {
                    val entries = container.foodRepository.entries.first()
                    val (lo, hi) = DiaryExporter.resolveRange(range, customStart, customEnd, entries)
                    val result = DiaryExporter.build(
                        entries = entries, start = lo, end = hi, format = format,
                        profile = profile, mealDisplay = { mealNames[it] ?: it.name },
                    )
                    if (result == null) {
                        status = context.getString(R.string.export_no_meals)
                        return@launch
                    }
                    val (name, content) = result
                    try {
                        shareExportedFile(
                            context = context,
                            fileName = name,
                            content = content,
                            mimeType = format.mime,
                            chooserTitle = context.getString(R.string.export_diary_title),
                        )
                        onDismiss()
                    } catch (e: Exception) {
                        status = e.localizedMessage ?: context.getString(R.string.export_failed)
                    }
                }
            }
        }
    }

    if (picking != null) {
        val editingStart = picking == "start"
        val initial = (if (editingStart) customStart else customEnd)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (editingStart) customStart = picked else customEnd = picked
                    }
                    picking = null
                }) { Text(stringResource(R.string.action_ok), color = AppColors.Calorie) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = dpState)
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Calorie)
    }
}
