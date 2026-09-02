package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassPinnedFooterColumn
import app.chompass.ui.components.ChompassPinnedFooterSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ProgressiveMealDraft
import app.chompass.models.ProgressiveMealItem
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.FoodAnalysis
import app.chompass.ui.components.MacroChip
import app.chompass.ui.components.kcalText
import app.chompass.ui.components.gramsText
import app.chompass.ui.components.culinaryUnitLabels
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.MacroKind
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

/**
 * Review sheet for an in-progress weigh-as-you-go meal: list of reviewed
 * ingredients, running totals, Add another (camera), Log meal, Discard.
 *
 * Codeberg #84: the ingredient list and the Add another / Log meal row live
 * in a [ChompassPinnedFooterSheet] so the CTA row is measured before the list
 * and stays pinned at the sheet bottom no matter how many ingredients
 * accumulate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressiveMealSheet(
    draft: ProgressiveMealDraft,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onMealTypeChange: (String) -> Unit,
    onRemoveItem: (java.util.UUID) -> Unit,
    onAddAnother: () -> Unit,
    onLogMeal: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberChompassSheetState(
        busy = isSaving,
        positionalThreshold = 300.dp,
        velocityThreshold = 1200.dp,
    )
    val canLog = draft.items.isNotEmpty() && !isSaving

    ChompassPinnedFooterSheet(
        onDismiss = { if (!isSaving) onDismiss() },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.background,
        toolbar = { ProgressiveMealToolbar(isSaving = isSaving, onDismiss = onDismiss) },
        body = {
            ProgressiveMealContentList(
                draft = draft,
                isSaving = isSaving,
                onNameChange = onNameChange,
                onMealTypeChange = onMealTypeChange,
                onRemoveItem = onRemoveItem,
                onDiscard = onDiscard,
            )
        },
        footer = {
            ProgressiveMealFooterRow(
                isSaving = isSaving,
                canLog = canLog,
                onAddAnother = onAddAnother,
                onLogMeal = onLogMeal,
            )
        },
    )
}

/**
 * Sheet body without ModalBottomSheet — used for JVM screenshot capture
 * (Codeberg #84 layout lock).
 */
@Composable
internal fun ProgressiveMealSheetBody(
    draft: ProgressiveMealDraft,
    isSaving: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onMealTypeChange: (String) -> Unit = {},
    onRemoveItem: (java.util.UUID) -> Unit = {},
    onAddAnother: () -> Unit = {},
    onLogMeal: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val canLog = draft.items.isNotEmpty() && !isSaving

    ChompassPinnedFooterColumn(
        toolbar = { ProgressiveMealToolbar(isSaving = isSaving, onDismiss = onDismiss) },
        body = {
            ProgressiveMealContentList(
                draft = draft,
                isSaving = isSaving,
                onNameChange = onNameChange,
                onMealTypeChange = onMealTypeChange,
                onRemoveItem = onRemoveItem,
                onDiscard = onDiscard,
            )
        },
        footer = {
            ProgressiveMealFooterRow(
                isSaving = isSaving,
                canLog = canLog,
                onAddAnother = onAddAnother,
                onLogMeal = onLogMeal,
            )
        },
    )
}

@Composable
private fun ProgressiveMealToolbar(isSaving: Boolean, onDismiss: () -> Unit) {
    SheetReviewToolbar(
        title = stringResource(R.string.progressive_meal_title),
        onCancel = { if (!isSaving) onDismiss() },
    )
}

@Composable
private fun ProgressiveMealContentList(
    draft: ProgressiveMealDraft,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onMealTypeChange: (String) -> Unit,
    onRemoveItem: (java.util.UUID) -> Unit,
    onDiscard: () -> Unit,
) {
    val listState = rememberLazyListState()
    var mealMenuExpanded by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }

    ChompassSheetLazyColumn(
        listState = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .onFocusChanged { inputFocused = it.isFocused },
        blockTopEdge = inputFocused || draft.name.isNotBlank(),
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
                    shape = RoundedCornerShape(AppRadii.Field),
                )
                Text(
                    stringResource(R.string.progressive_meal_ingredient_count, draft.items.size),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
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
                                mealLabel(draft.mealType),
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
                            for (m in pickerMealIds()) {
                                SheetGlassDropdownMenuItem(
                                    label = mealLabel(m),
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    )
                    Text(
                        kcalText(draft.totalCalories),
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

@Composable
private fun ProgressiveMealFooterRow(
    isSaving: Boolean,
    canLog: Boolean,
    onAddAnother: () -> Unit,
    onLogMeal: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Pinned footer pads itself: the sheet host zeroes
            // contentWindowInsets (#6/#14 feedback loop). imePadding because
            // the body's name field can hold focus.
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetToolbarPill(
            stringResource(R.string.progressive_meal_add_another),
            compact = true,
            enabled = !isSaving,
            maxLines = 2,
            onClick = { if (!isSaving) onAddAnother() },
            modifier = Modifier.weight(1f),
        )
        SheetToolbarPill(
            stringResource(R.string.progressive_meal_log),
            bold = true,
            compact = true,
            enabled = !isSaving && canLog,
            maxLines = 2,
            onClick = { if (canLog) onLogMeal() },
            modifier = Modifier.weight(1.2f),
        )
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
            // Same echo rule as saved rows (Codeberg #65): pending items keep
            // their analyzed unit when resolvable, else grams.
            val echo = ServingUnitOption.homeDisplaySelection(
                analysis.selectedServingUnit,
                analysis.selectedServingQuantity,
                analysis.servingSizeGrams?.takeIf { it > 0 },
                analysis.servingUnitOptions,
            )
            val servingText = echo?.let { (qty, option) ->
                "${ServingUnitOption.formatQuantity(qty)} " + option.displayUnit(
                    qty,
                    stringResource(R.string.unit_serving),
                    stringResource(R.string.unit_serving_plural),
                    culinaryUnitLabels(),
                )
            } ?: gramsText((analysis.servingSizeGrams ?: 0.0).roundToIntSafe().toDouble())
            Text(
                "${kcalText(analysis.calories)} · $servingText",
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

/** Six-ingredient demo draft for the #84 overflow screenshot. */
private val ScreenshotProgressiveDraft = ProgressiveMealDraft(
    items = (1..6).map { i ->
        ProgressiveMealItem(
            analysis = FoodAnalysis(
                name = "Demo ingredient $i",
                calories = 60 * i,
                protein = 4.0 * i,
                carbs = 5.0 * i,
                fat = 2.0 * i,
                servingSizeGrams = null,
                emoji = "🥗",
            ),
        )
    },
)

/**
 * Home with the seeded meal-builder sheet for release screenshots (no
 * ModalBottomSheet) — locks the Codeberg #84 pinned-footer layout: the
 * Add another / Log meal row must stay visible under a 6-ingredient list.
 */
@Composable
internal fun HomeProgressiveMealScreenshotContent(ui: HomeUiState) {
    Box(Modifier.fillMaxSize()) {
        HomeScreenPreviewContent(ui = ui)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ProgressiveMealSheetBody(draft = ScreenshotProgressiveDraft)
        }
    }
}
