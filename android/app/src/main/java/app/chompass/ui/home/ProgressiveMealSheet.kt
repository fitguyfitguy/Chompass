package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.MealType
import app.chompass.models.ProgressiveMealDraft
import app.chompass.models.ProgressiveMealItem
import app.chompass.ui.components.MacroChip
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.MacroKind

/**
 * Review sheet for an in-progress weigh-as-you-go meal: list of reviewed
 * ingredients, running totals, Add another (camera), Log meal, Discard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressiveMealSheet(
    draft: ProgressiveMealDraft,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onRemoveItem: (java.util.UUID) -> Unit,
    onAddAnother: () -> Unit,
    onLogMeal: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberChompassSheetState(busy = isSaving)
    val listState = rememberLazyListState()
    var mealMenuExpanded by remember { mutableStateOf(false) }
    val canLog = draft.items.isNotEmpty() && !isSaving

    ChompassBottomSheet(
        onDismiss = { if (!isSaving) onDismiss() },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        SheetReviewToolbar(
            title = stringResource(R.string.progressive_meal_title),
            primaryLabel = if (isSaving) {
                stringResource(R.string.action_logging)
            } else {
                stringResource(R.string.progressive_meal_log)
            },
            secondaryLabel = stringResource(R.string.progressive_meal_add_another),
            primaryEnabled = canLog,
            onCancel = { if (!isSaving) onDismiss() },
            onPrimary = { if (canLog) onLogMeal() },
            onSecondary = { if (!isSaving) onAddAnother() },
        )

        ChompassSheetLazyColumn(
            listState = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = onNameChange,
                        placeholder = {
                            Text(stringResource(R.string.progressive_meal_name_placeholder))
                        },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text(
                        stringResource(R.string.progressive_meal_ingredient_count, draft.items.size),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }

            item { SheetSectionHeader(stringResource(R.string.sheet_meal)) }
            item {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SheetPillRow(onClick = { if (!isSaving) mealMenuExpanded = true }) {
                        Text(
                            stringResource(R.string.sheet_meal_type),
                            fontSize = 17.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    sheetMealIcon(draft.mealType),
                                    contentDescription = null,
                                    tint = AppColors.Calorie,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(draft.mealType.displayNameRes),
                                    fontSize = 17.sp,
                                    color = AppColors.Calorie,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            SheetGlassDropdownMenu(
                                expanded = mealMenuExpanded,
                                onDismissRequest = { mealMenuExpanded = false },
                                menuWidth = 184.dp,
                            ) {
                                for (m in MealType.values()) {
                                    SheetGlassDropdownMenuItem(
                                        label = stringResource(m.displayNameRes),
                                        leadingIcon = sheetMealIcon(m),
                                        selected = m == draft.mealType,
                                        onClick = {
                                            onMealTypeChange(m)
                                            mealMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (draft.items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.progressive_meal_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            } else {
                items(draft.items, key = { it.id }) { item ->
                    ProgressiveIngredientRow(
                        item = item,
                        enabled = !isSaving,
                        onRemove = { onRemoveItem(item.id) },
                    )
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.progressive_meal_totals),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                        Text(
                            "${draft.totalCalories} kcal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Calorie,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MacroChip(MacroKind.PROTEIN, draft.totalProtein)
                            MacroChip(MacroKind.CARBS, draft.totalCarbs)
                            MacroChip(MacroKind.FAT, draft.totalFat)
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = { if (!isSaving) onDiscard() },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(stringResource(R.string.progressive_meal_discard))
                }
            }
        }
    }
}

@Composable
private fun ProgressiveIngredientRow(
    item: ProgressiveMealItem,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val isDark = isDarkTheme()
    val rowFill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val analysis = item.analysis
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowFill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (analysis.emoji != null) {
                Text(analysis.emoji, fontSize = 20.sp)
            } else {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = AppColors.Calorie,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(analysis.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${analysis.calories} kcal · ${(analysis.servingSizeGrams ?: 0.0).roundToIntSafe()} g",
                fontSize = 13.sp,
                color = AppColors.Calorie,
            )
        }

        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.progressive_meal_remove),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
