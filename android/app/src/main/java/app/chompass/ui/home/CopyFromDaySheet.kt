package app.chompass.ui.home

import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.FoodEntry
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.theme.AppColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CopyFromDaySheet(
    targetDate: LocalDate,
    allEntries: List<FoodEntry>,
    isSaving: Boolean = false,
    onCopy: (List<FoodEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    // Dismissible by downward drag; only block while copying entries.
    val state = rememberChompassSheetState(busy = isSaving)
    var sourceDate by remember(targetDate) { mutableStateOf(targetDate.minusDays(1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dateFmt = remember { LocaleFormat.shortDate() }
    val sourceEntries = remember(allEntries, sourceDate) {
        allEntries
            .filter { it.timestamp.atZone(zone).toLocalDate() == sourceDate }
            .sortedByDescending { it.timestamp }
    }
    val groups = remember(sourceEntries) {
        foodLogMealGroups(sourceEntries, FoodLogSortOrder.STANDARD)
    }
    val targetText = if (targetDate == LocalDate.now()) "today" else targetDate.format(dateFmt)

    ChompassBottomSheet(
        onDismiss = { if (!isSaving) onDismiss() },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        SheetReviewToolbar(
            title = stringResource(R.string.home_menu_copy_from_day),
            primaryLabel = if (sourceEntries.isEmpty()) {
                stringResource(R.string.action_done)
            } else if (isSaving) {
                stringResource(R.string.action_logging)
            } else {
                stringResource(R.string.copy_all)
            },
            primaryEnabled = !isSaving,
            onCancel = { if (!isSaving) onDismiss() },
            onPrimary = {
                if (!isSaving) {
                    if (sourceEntries.isEmpty()) onDismiss() else onCopy(sourceEntries)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SheetSectionHeader(stringResource(R.string.section_source))
                    SheetPillRow(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.copy_from), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(
                            sourceDate.format(dateFmt),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Foods will be copied to $targetText. Original entries stay unchanged.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
            }

            if (sourceEntries.isEmpty()) {
                item {
                    SectionCardWrapper(isFirst = true, isLast = true) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = AppColors.Calorie.copy(alpha = 0.45f),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                stringResource(R.string.copy_no_foods_on_day),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                item {
                    FudGlassPrimaryButton(
                        text = pluralStringResource(R.plurals.copy_foods_to, sourceEntries.size, sourceEntries.size, targetText),
                        onClick = { if (!isSaving) onCopy(sourceEntries) },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }

                groups.forEach { group ->
                    item(key = "copy-header-${group.id}") {
                        MealSectionHeader(meal = group.meal)
                    }
                    item(key = "copy-meal-${group.id}") {
                        FudGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            cornerRadius = 18.dp,
                            padding = 0.dp
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSaving) { if (!isSaving) onCopy(group.entries) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    stringResource(R.string.copy_meal_format, stringResource(group.meal.displayNameRes)),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    itemsIndexed(group.entries, key = { _, entry -> "copy-entry-${entry.id}" }) { index, entry ->
                        val isFirst = index == 0
                        val isLast = index == group.entries.lastIndex
                        val rowShape = sectionCardShape(isFirst, isLast)
                        SectionCardWrapper(isFirst = isFirst, isLast = isLast, transparent = true) {
                            Box(Modifier.clickable(enabled = !isSaving) { if (!isSaving) onCopy(listOf(entry)) }) {
                                FoodRow(entry = entry, rowShape = rowShape)
                            }
                            if (index != group.entries.lastIndex) Divider()
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        var pickedDate by remember(sourceDate) { mutableStateOf(sourceDate) }
        FudGlassDialog(onDismissRequest = { showDatePicker = false }) {
            Text(stringResource(R.string.copy_from), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            DateWheelPicker(
                selected = pickedDate,
                onSelect = { pickedDate = it },
                minYear = LocalDate.now().year - 10,
                maxYear = LocalDate.now().year,
                modifier = Modifier.fillMaxWidth()
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_done),
                onPrimary = {
                    sourceDate = pickedDate
                    showDatePicker = false
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { showDatePicker = false }
            )
        }
    }
}
