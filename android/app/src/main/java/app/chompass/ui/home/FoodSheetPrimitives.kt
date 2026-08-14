package app.chompass.ui.home

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import app.chompass.R
import app.chompass.models.MealType
import app.chompass.models.ServingUnitOption
import app.chompass.ui.theme.AppColors
import app.chompass.ui.components.isDarkTheme
import app.chompass.models.MacroValueFormatter

// Shared visual primitives for the food review/edit sheets. Names are
// `Sheet*`-prefixed so they don't collide with the look-alike privates in
// HomeScreen.kt and NutritionDetailSheet.kt.

/**
 * Top chrome for review/edit sheets. Pass [primaryLabel]/[onPrimary] to keep
 * the primary action in the toolbar (utility sheets). Food review/edit sheets
 * omit primary here and use [SheetStickyPrimaryBar] instead.
 */
@Composable
internal fun SheetReviewToolbar(
    title: String,
    onCancel: () -> Unit,
    primaryLabel: String? = null,
    secondaryLabel: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null
) {
    val compact = LocalConfiguration.current.screenWidthDp < 380
    val outerPadding = if (compact) 8.dp else 14.dp
    val itemGap = if (compact) 6.dp else 8.dp
    val showPrimary = primaryLabel != null && onPrimary != null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = outerPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetToolbarPill(stringResource(R.string.action_cancel), compact = compact, onClick = onCancel)
        Spacer(Modifier.width(itemGap))
        Text(
            title,
            fontSize = if (compact) 16.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(itemGap))
        if (secondaryLabel != null && onSecondary != null) {
            SheetToolbarPill(secondaryLabel, compact = compact, onClick = onSecondary)
            if (showPrimary) Spacer(Modifier.width(itemGap))
        }
        if (showPrimary) {
            SheetToolbarPill(
                primaryLabel,
                bold = true,
                compact = compact,
                enabled = primaryEnabled,
                onClick = onPrimary,
            )
        }
    }
}

/**
 * Sticky primary CTA for [FoodResultSheet] / [EditFoodEntrySheet].
 * Sits below the scroll body so Log/Save stays visible with the IME open.
 */
@Composable
internal fun SheetStickyPrimaryBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    textActionLabel: String? = null,
    onTextAction: (() -> Unit)? = null,
    textActionEnabled: Boolean = true,
) {
    val isDark = isDarkTheme()
    val hairline = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(hairline),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (textActionLabel != null && onTextAction != null) {
                TextButton(
                    onClick = onTextAction,
                    enabled = textActionEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(textActionLabel, fontWeight = FontWeight.Medium)
                }
            }
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd)))
                    .alpha(if (primaryEnabled) 1f else 0.45f)
                    .clickable(enabled = primaryEnabled, onClick = onPrimary)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    primaryLabel,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SheetToolbarPill(
    label: String,
    bold: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = CircleShape
    val isDark = isDarkTheme()
    val horizontalPadding = when {
        compact && bold -> 12.dp
        compact -> 10.dp
        else -> 16.dp
    }
    val modifier = (if (bold) {
        Modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd)))
    } else {
        Modifier
            .clip(shape)
            .background(if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight)
            .border(0.5.dp, if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight, shape)
    }).alpha(if (enabled) 1f else 0.45f)
    Box(
        modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (bold) Color.White else AppColors.Calorie,
            fontSize = if (compact) 15.sp else 16.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
internal fun SheetSectionHeader(title: String) {
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SheetPillRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val isDark = isDarkTheme()
    val rowFill = if (isDark) {
        AppColors.TranslucentSurfaceDark
    } else {
        AppColors.TranslucentSurfaceLight
    }
    val rowBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(rowFill)
        .border(0.5.dp, rowBorder, shape)
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        withClick.padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun SheetPillCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val isDark = isDarkTheme()
    val cardFill = if (isDark) {
        AppColors.TranslucentSurfaceDark
    } else {
        AppColors.TranslucentSurfaceLight
    }
    val cardBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardFill)
            .border(0.5.dp, cardBorder, shape)
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
internal fun ServingQuantityCard(
    quantityText: String,
    onQuantityChange: (String) -> Unit,
    selectedUnitId: String,
    onSelectedUnitChange: (String) -> Unit,
    servingSizeGrams: Double,
    unitOptions: List<ServingUnitOption>,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    gramUnit: String,
    isLoadingUnits: Boolean = false,
    enabled: Boolean = true,
    /** Set false for hosts that don't resolve deltas/expressions (constituents). */
    showQuantityCalc: Boolean = true,
    /**
     * Opt-in per-entry serving customization (Codeberg #10 follow-up): renames /
     * re-weights the selected unit right in the card. Receives the updated option
     * list plus the new selected unit id (the normalized id follows the name).
     * Hosts without editable options pass null to hide the affordance.
     */
    onUnitOptionsChange: ((List<ServingUnitOption>, String) -> Unit)? = null,
) {
    val pickerOptions = ServingUnitOption.pickerOptions(unitOptions)
    val selectedOption = ServingUnitOption.optionMatching(selectedUnitId, unitOptions)
    val parsedQuantity = ServingUnitOption.parseQuantity(quantityText)
    val selectedUnitLabel = selectedOption.displayUnit(parsedQuantity)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    // Base quantity for relative edits / expression previews: the committed
    // serving grams converted back into the selected unit (mirrors the
    // sheets' currentQuantity). Expressions ignore the base; only leading
    // +/- deltas use it.
    val currentQuantityForBase = if (selectedOption.gramsPerUnit > 0) {
        servingSizeGrams / selectedOption.gramsPerUnit
    } else {
        servingSizeGrams
    }
    val focusRequester = remember { FocusRequester() }
    var quantityFieldValue by remember {
        mutableStateOf(TextFieldValue(quantityText, selection = TextRange(quantityText.length)))
    }
    // Per-entry custom serving: rename / re-weight the selected unit inline.
    // Available on any unit — including plain grams — so a dish without a
    // heuristic or analyzed unit (homemade curry, a stew, ...) can still get a
    // named serving ("bowl" = 300 g). Drafts reset when the selected option
    // changes (its id is the key).
    var editingServing by remember { mutableStateOf(false) }
    val canCustomizeServing = onUnitOptionsChange != null
    var servingNameDraft by remember(selectedUnitId) { mutableStateOf(selectedOption.unit) }
    var servingGramsDraft by remember(selectedUnitId) {
        mutableStateOf(
            ServingUnitOption.formatQuantity(
                if (selectedOption.isGramUnit) {
                    // Starting from plain grams the new serving defaults to the
                    // whole dish's grams (one bowl of the logged amount); manual
                    // entries have no total yet, so fall back to 1.
                    servingSizeGrams.takeIf { it > 0 } ?: 1.0
                } else {
                    selectedOption.gramsPerUnit
                }
            )
        )
    }
    val pushServingEdit = {
        val grams = ServingUnitOption.parseQuantity(servingGramsDraft)
        if (grams != null) {
            ServingUnitOption.servingEdit(
                selectedUnitId = selectedUnitId,
                selectedOption = selectedOption,
                unitOptions = unitOptions,
                name = servingNameDraft,
                grams = grams,
            )?.let { result ->
                onUnitOptionsChange?.invoke(result.options, result.updated.id)
            }
        }
    }
    // Expressions stay visible while typing (with a live "=" preview); on
    // focus loss they commit to the resolved number, like +/- deltas do
    // immediately.
    val commitQuantity = {
        if (enabled && quantityText.isNotEmpty()) {
            val resolved = ServingUnitOption.applyDeltaInput(quantityText, currentQuantityForBase)
            if (ServingUnitOption.isQuantityExpression(quantityText) && resolved != null && resolved > 0) {
                val formatted = ServingUnitOption.formatQuantity(resolved)
                if (formatted != quantityText.trim()) {
                    quantityFieldValue = TextFieldValue(formatted, selection = TextRange(formatted.length))
                    onQuantityChange(formatted)
                }
            }
        }
    }

    LaunchedEffect(quantityText) {
        if (quantityText != quantityFieldValue.text) {
            quantityFieldValue = TextFieldValue(
                text = quantityText,
                selection = TextRange(quantityText.length)
            )
        }
    }

    SheetPillCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.sheet_quantity),
                fontSize = 17.sp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { dismissKeyboard() }
            )
            Spacer(
                Modifier
                    .weight(1f)
                    .clickable { dismissKeyboard() }
            )
            BasicTextField(
                value = quantityFieldValue,
                onValueChange = { newValue ->
                    if (!enabled) return@BasicTextField
                    quantityFieldValue = newValue.copy(
                        selection = TextRange(newValue.text.length)
                    )
                    onQuantityChange(newValue.text)
                },
                singleLine = true,
                enabled = enabled,
                readOnly = !enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.55f),
                    fontSize = 17.sp,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(AppColors.Calorie),
                modifier = Modifier
                    .width(80.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState -> if (!focusState.isFocused) commitQuantity() }
            )
            if (quantityText.isNotEmpty() && enabled) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = stringResource(R.string.cd_clear_quantity),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable {
                            quantityFieldValue = TextFieldValue("", selection = TextRange.Zero)
                            onQuantityChange("")
                            focusRequester.requestFocus()
                        }
                )
            }
            Spacer(Modifier.width(6.dp))
            if (pickerOptions.size > 1) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled && !isLoadingUnits) {
                                dismissKeyboard()
                                onMenuExpandedChange(true)
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedUnitLabel,
                            fontSize = 17.sp,
                            color = AppColors.Calorie,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(min = 32.dp, max = 88.dp)
                        )
                        Icon(
                            Icons.Filled.UnfoldMore,
                            contentDescription = null,
                            tint = AppColors.Calorie
                        )
                    }
                    SheetGlassDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        menuWidth = 150.dp
                    ) {
                        for (option in pickerOptions) {
                            val optionLabel = option.displayUnit(
                                if (option.id == selectedUnitId) parsedQuantity else null
                            )
                            SheetGlassDropdownMenuItem(
                                label = optionLabel,
                                selected = option.id == selectedUnitId,
                                reserveSelectionSlot = true,
                                onClick = {
                                    onSelectedUnitChange(option.id)
                                    onMenuExpandedChange(false)
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    gramUnit,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .width(24.dp)
                        .clickable { dismissKeyboard() }
                )
            }
            if (canCustomizeServing && !editingServing) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_serving),
                    tint = AppColors.Calorie,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable {
                            dismissKeyboard()
                            editingServing = true
                        }
                )
            }
        }

        if (enabled && !isLoadingUnits && showQuantityCalc) {
            SheetHairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (op in listOf("+", "-", "×", "÷")) {
                    QuantityOperatorChip(
                        operator = op,
                        contentDescription = stringResource(R.string.cd_quantity_operator, op),
                        enabled = enabled,
                        onClick = {
                            val next = quantityFieldValue.text + op
                            quantityFieldValue = TextFieldValue(next, selection = TextRange(next.length))
                            onQuantityChange(next)
                        }
                    )
                }
                val previewResolved = ServingUnitOption.applyDeltaInput(quantityText, currentQuantityForBase)
                if (ServingUnitOption.isQuantityExpression(quantityText) && previewResolved != null && previewResolved > 0) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "= ${ServingUnitOption.formatQuantity(previewResolved)}",
                        fontSize = 15.sp,
                        color = AppColors.Calorie,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isLoadingUnits && unitOptions.isEmpty()) {
            SheetHairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    color = AppColors.Calorie,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.entry_analysis_inferring_units),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        if (canCustomizeServing && editingServing) {
            SheetHairline()
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sheet_serving_custom_name),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.width(92.dp)
                    )
                    BasicTextField(
                        value = servingNameDraft,
                        onValueChange = {
                            servingNameDraft = it
                            pushServingEdit()
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        ),
                        cursorBrush = SolidColor(AppColors.Calorie),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sheet_serving_custom_grams),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.width(92.dp)
                    )
                    BasicTextField(
                        value = servingGramsDraft,
                        onValueChange = {
                            servingGramsDraft = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            pushServingEdit()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        ),
                        cursorBrush = SolidColor(AppColors.Calorie),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.unit_g),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cd_apply_serving_edit),
                        tint = AppColors.Calorie,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable {
                                dismissKeyboard()
                                editingServing = false
                            }
                    )
                }
            }
        } else if (!selectedOption.isGramUnit) {
            SheetHairline()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_total), fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(
                    "~${MacroValueFormatter.string(servingSizeGrams)} $gramUnit",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun QuantityOperatorChip(
    operator: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        operator,
        fontSize = 15.sp,
        color = if (enabled) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
internal fun SheetNutritionRow(
    label: String,
    value: String,
    unit: String,
    dim: Boolean = false,
    accentColor: Color? = null,
) {
    val labelColor = accentColor?.let {
        if (dim) it.copy(alpha = 0.72f) else it
    } ?: if (dim) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val valueColor = accentColor ?: MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 16.sp,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            unit,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
internal fun SheetHairline() {
    Box(
        Modifier
            .padding(start = 18.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}

@Composable
internal fun SheetGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    menuWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val sizedModifier = if (menuWidth != null) modifier.width(menuWidth) else modifier
    val isDark = isDarkTheme()
    val menuContainer = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val borderColor = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = shape,
        containerColor = menuContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = sizedModifier
            .border(0.5.dp, borderColor, shape)
            .padding(vertical = 5.dp),
        content = content
    )
}

@Composable
internal fun SheetGlassDropdownMenuItem(
    label: String,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
    reserveSelectionSlot: Boolean = false,
    onClick: () -> Unit
) {
    val isDark = isDarkTheme()
    val checkTint = if (isDark) Color.White else AppColors.Calorie
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            // ~48dp tap target per row (Material menu guidance), matching the
            // roomier iOS add-menu rows instead of the old cramped ~36dp.
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leadingIcon != null -> {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = AppColors.Calorie,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            reserveSelectionSlot -> {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = checkTint,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (selected && leadingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * No-op [OverscrollEffect]: no glow, no stretch, no animations. Used on long
 * sheet lists whose default stretch overscroll spring can get stuck oscillating
 * (endless bounce at the list edge after an overshooting fling until a tap
 * cancels it) when combined with the modal sheet's nested scroll handling.
 *
 * [applyToScroll] must forward the delta to [consume]: the callback performs
 * the actual scroll (nested-scroll pre/post dispatch included). Returning
 * `Offset.Zero` without calling it silently swallows every drag/fling delta —
 * the list never scrolls (foundation's own NoOp does the same forwarding).
 */
private val NoOpOverscrollEffect: OverscrollEffect = object : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        consume: (Offset) -> Offset,
    ): Offset = consume(delta)

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        performFling(velocity)
    }

    override val isInProgress: Boolean = false
}

/** Renders [content] with the stretch/glow overscroll effect disabled. */
@Composable
internal fun WithoutOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOverscrollFactory provides object : OverscrollFactory {
            override fun createOverscrollEffect(): OverscrollEffect = NoOpOverscrollEffect
            override fun equals(other: Any?): Boolean = this === other
            override fun hashCode(): Int = System.identityHashCode(this)
        },
        content = content,
    )
}

internal fun sheetMealIcon(meal: MealType): ImageVector = when (meal) {
    MealType.BREAKFAST -> Icons.Filled.WbTwilight
    MealType.LUNCH -> Icons.Filled.WbSunny
    MealType.DINNER -> Icons.Filled.Bedtime
    MealType.SNACK -> Icons.Filled.LocalCafe
    MealType.OTHER -> Icons.Filled.Restaurant
}
