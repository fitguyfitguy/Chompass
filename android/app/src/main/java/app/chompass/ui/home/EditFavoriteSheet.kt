package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.MacroValueFormatter
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.ServingUnitOption
import app.chompass.models.OptionalNutrientGoals
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Edit page for a stored favorite (Codeberg #66) — favorites behave as a
 * personal saved-foods library, so edits here update the favorite itself,
 * permanently, instead of only the diary row it was hearted from.
 *
 * Visually mirrors [EditFoodEntrySheet] (same hero / name / serving /
 * nutrition / meal sections, same shared primitives), but a favorite is a
 * reusable template, not a diary row: no date/time pickers, no share link,
 * no Ask-AI reprocess, and the note is a plain stored field. Renaming is
 * guarded — the favorite's name is its identity key, so a name another food
 * (diary row or favorite) already uses blocks Save with an inline error.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditFavoriteSheet(
    container: AppContainer,
    entry: FoodEntry,
    /** #86: user's optional micro goals, shown as "(N%)" on ingredient micros. */
    optionalGoals: OptionalNutrientGoals? = null,
    onSave: (FoodEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var currentBaseEntry by remember(entry) { mutableStateOf(entry) }
    var noteText by remember(entry) { mutableStateOf(entry.customNote ?: "") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Rename guard (Codeberg #66): the name IS the identity key shared with
    // diary rows and other favorites. Loaded once on open — single-user data
    // cannot change underneath the sheet.
    var takenNames by remember(entry) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(entry.id) {
        takenNames = withContext(Dispatchers.Default) {
            container.foodRepository.favoriteRenameBlocklist(entry)
        }
    }

    // Serving-less entries with a unit quantity carry an honest portion base
    // (Codeberg #89); pure-gram entries keep null (Codeberg #10 follow-up).
    val recordedServing = ServingUnitOption.recordedPortionGrams(
        recordedServingGrams = currentBaseEntry.servingSizeGrams,
        selectedUnit = currentBaseEntry.selectedServingUnit,
        selectedQuantity = currentBaseEntry.selectedServingQuantity,
        options = currentBaseEntry.servingUnitOptions,
    )
    val entryBaseServing = recordedServing ?: 100.0
    var servingUnitOptions by remember(currentBaseEntry.servingUnitOptions, entryBaseServing) {
        mutableStateOf(ServingUnitOption.normalizedOptions(currentBaseEntry.servingUnitOptions, entryBaseServing))
    }
    var name by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.name) }
    var selectedServingUnitId by remember(currentBaseEntry) {
        mutableStateOf(ServingUnitOption.initialUnitId(currentBaseEntry.selectedServingUnit, servingUnitOptions))
    }
    var servingGrams by remember(currentBaseEntry, entryBaseServing) { mutableStateOf(entryBaseServing) }
    var baseServingGrams by remember(currentBaseEntry, entryBaseServing) { mutableStateOf(entryBaseServing) }
    var servingTouched by remember(currentBaseEntry, entryBaseServing) { mutableStateOf(false) }
    var editableConstituents by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.constituents) }
    var constituentsExpanded by remember(currentBaseEntry) {
        mutableStateOf(currentBaseEntry.constituents.isNotEmpty())
    }
    var servingQuantityText by remember(currentBaseEntry, servingUnitOptions) {
        mutableStateOf(
            ServingUnitOption.initialQuantityText(
                totalGrams = entryBaseServing,
                selectedUnitId = selectedServingUnitId,
                selectedQuantity = currentBaseEntry.selectedServingQuantity,
                options = servingUnitOptions
            )
        )
    }
    val selectedServingOption = ServingUnitOption.optionMatching(selectedServingUnitId, servingUnitOptions)
    val selectedServingQuantity = ServingUnitOption.parseQuantity(servingQuantityText)?.takeIf { it > 0 }
    val scale = ServingUnitOption.servingScale(recordedServing, servingGrams, baseServingGrams)
    var mealType by remember(entry) { mutableStateOf(currentBaseEntry.mealType) }
    var moreNutritionExpanded by remember { mutableStateOf(false) }
    var nutritionUnlocked by remember { mutableStateOf(false) }
    var editableCalories by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.calories) }
    var editableProtein by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.protein) }
    var editableCarbs by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.carbs) }
    var editableFat by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.fat) }
    var editableMicros by remember(currentBaseEntry) { mutableStateOf(MicronutrientValues.from(currentBaseEntry)) }
    var mealMenuExpanded by remember { mutableStateOf(false) }
    var servingMenuExpanded by remember { mutableStateOf(false) }
    var editableEmoji by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.emoji) }
    var editableImageFilename by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.imageFilename) }
    var showIconPicker by remember { mutableStateOf(false) }
    var iconPickError by remember { mutableStateOf<String?>(null) }
    val isDark = isDarkTheme()
    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // Photo-picker → storeBytes (sampled decode + JPEG + 320px thumbnail). The
    // file is `${favorite.id}.jpg` (favorite ids are stable), so re-picking
    // overwrites in place; the replaced file is pruned by updateFavorite once
    // no other row/favorite/draft references it.
    val imageStore = container.imageStore
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                val filename = if (bytes == null || bytes.isEmpty()) null else imageStore.storeBytes(bytes, entry.id)
                if (filename != null) {
                    editableImageFilename = filename
                    iconPickError = null
                } else {
                    iconPickError = context.getString(R.string.edit_icon_photo_failed)
                }
            }
        }
    }

    val emDashText = stringResource(R.string.nutrition_em_dash)
    val math = remember(scale, emDashText) { FoodEntryEditMath(scale, emDashText) }

    val nameTaken = name.trim().lowercase() in takenNames

    fun buildUpdated(): FoodEntry = editableMicros
        .scaled(scale)
        .applyTo(
            currentBaseEntry.copy(
                name = name.trim().ifEmpty { currentBaseEntry.name },
                calories = math.scaledInt(editableCalories),
                protein = math.scaledMacro(editableProtein),
                carbs = math.scaledMacro(editableCarbs),
                fat = math.scaledMacro(editableFat),
                mealType = mealType,
                customNote = noteText.trim().takeIf { it.isNotEmpty() },
                servingSizeGrams = ServingUnitOption.persistedServingGrams(recordedServing, servingTouched, servingGrams),
                servingUnitOptions = servingUnitOptions,
                selectedServingUnit = if (servingUnitOptions.isEmpty()) null else selectedServingOption.unit,
                selectedServingQuantity = if (servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                constituents = app.chompass.services.ai.ConstituentReconcile.scaleAll(
                    editableConstituents,
                    scale,
                ),
                emoji = editableEmoji,
                imageFilename = editableImageFilename,
            )
        )

    // Codeberg #30: block top-edge drag dismissal only while the user is
    // editing typed input, same gate as EditFoodEntrySheet.
    var inputFocused by remember { mutableStateOf(false) }
    val typedContentChanged = name.trim() != currentBaseEntry.name.trim() ||
        noteText != (currentBaseEntry.customNote ?: "")

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = rememberChompassSheetState(
            // Same raised thresholds as EditFoodEntrySheet (maintainer decision
            // 2026-08-18): a read-only scroll must spring back; dismissal needs
            // a deliberate pull or firm flick.
            positionalThreshold = 300.dp,
            velocityThreshold = 1200.dp,
        ),
        containerColor = sheetSurface,
        // Codeberg #6: zero the chrome insets (footer feedback loop) — same as
        // EditFoodEntrySheet.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.favorite_edit_title),
                onCancel = onDismiss,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                WithoutOverscroll {
                    ChompassSheetLazyColumn(
                        listState = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .onFocusChanged { inputFocused = it.isFocused },
                        blockTopEdge = inputFocused || typedContentChanged,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item {
                            EditFoodEntryHero(
                                emoji = editableEmoji,
                                imageFilename = editableImageFilename,
                                onClick = {
                                    dismissKeyboard()
                                    showIconPicker = true
                                },
                            )
                        }

                        item { SheetSectionHeader(stringResource(R.string.sheet_food_details)) }
                        item {
                            SheetPillRow {
                                Text(stringResource(R.string.sheet_name), fontSize = 17.sp, modifier = Modifier.padding(end = 8.dp))
                                Spacer(Modifier.weight(1f))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 17.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Calorie),
                                    modifier = Modifier.weight(2f)
                                )
                            }
                        }
                        if (nameTaken) {
                            item {
                                Text(
                                    stringResource(R.string.favorite_edit_name_taken),
                                    fontSize = 13.sp,
                                    color = AppColors.Calorie,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        item { SheetSectionHeader(stringResource(R.string.sheet_serving)) }
                        item {
                            ServingQuantityCard(
                                quantityText = servingQuantityText,
                                onQuantityChange = { newValue ->
                                    val currentQuantity = if (selectedServingOption.gramsPerUnit > 0) {
                                        servingGrams / selectedServingOption.gramsPerUnit
                                    } else {
                                        servingGrams
                                    }
                                    val parsed = ServingUnitOption.applyDeltaInput(newValue, currentQuantity)
                                    servingQuantityText = newValue
                                    if (parsed != null && parsed > 0) {
                                        servingGrams = parsed * selectedServingOption.gramsPerUnit
                                        servingTouched = true
                                        if (newValue.trim().startsWith("+") || newValue.trim().startsWith("-")) {
                                            servingQuantityText = ServingUnitOption.formatQuantity(parsed)
                                        }
                                    }
                                },
                                selectedUnitId = selectedServingUnitId,
                                onSelectedUnitChange = { optionId ->
                                    selectedServingUnitId = optionId
                                    val option = ServingUnitOption.optionMatching(optionId, servingUnitOptions)
                                    val quantity = if (option.gramsPerUnit > 0) servingGrams / option.gramsPerUnit else servingGrams
                                    servingQuantityText = ServingUnitOption.formatQuantity(quantity)
                                },
                                servingSizeGrams = servingGrams,
                                unitOptions = servingUnitOptions,
                                menuExpanded = servingMenuExpanded,
                                onMenuExpandedChange = { servingMenuExpanded = it },
                                gramUnit = stringResource(R.string.unit_g),
                                onUnitOptionsChange = { options, newId ->
                                    val gramsBefore = servingGrams
                                    servingUnitOptions = options
                                    selectedServingUnitId = newId
                                    val option = ServingUnitOption.optionMatching(newId, options)
                                    val quantity = if (option.gramsPerUnit > 0) {
                                        gramsBefore / option.gramsPerUnit
                                    } else {
                                        gramsBefore
                                    }
                                    servingQuantityText = ServingUnitOption.formatQuantity(quantity)
                                },
                            )
                        }

                        item {
                            SheetSectionHeaderWithLock(
                                title = stringResource(R.string.sheet_nutrition),
                                unlocked = nutritionUnlocked,
                                onToggle = {
                                    nutritionUnlocked = !nutritionUnlocked
                                    if (!nutritionUnlocked) dismissKeyboard()
                                }
                            )
                        }
                        item {
                            SheetPillCard {
                                ReviewNutritionValueRow(
                                    label = stringResource(R.string.nutrition_label_calories),
                                    displayValue = "${math.scaledInt(editableCalories)}",
                                    editValue = "${math.scaledInt(editableCalories)}",
                                    unit = stringResource(R.string.unit_kcal),
                                    unlocked = nutritionUnlocked,
                                    accentColor = AppColors.Calorie,
                                    onEdit = { editableCalories = math.baseDoubleFromText(it).roundToInt() }
                                )
                                SheetHairline()
                                ReviewNutritionValueRow(
                                    label = stringResource(R.string.nutrition_label_protein),
                                    displayValue = MacroValueFormatter.string(math.scaledMacro(editableProtein)),
                                    editValue = MacroValueFormatter.string(math.scaledMacro(editableProtein)),
                                    unit = stringResource(R.string.unit_g),
                                    unlocked = nutritionUnlocked,
                                    accentColor = AppColors.Protein,
                                    onEdit = { editableProtein = math.baseDoubleFromText(it) }
                                )
                                SheetHairline()
                                ReviewNutritionValueRow(
                                    label = stringResource(R.string.nutrition_label_carbs),
                                    displayValue = MacroValueFormatter.string(math.scaledMacro(editableCarbs)),
                                    editValue = MacroValueFormatter.string(math.scaledMacro(editableCarbs)),
                                    unit = stringResource(R.string.unit_g),
                                    unlocked = nutritionUnlocked,
                                    accentColor = AppColors.Carbs,
                                    onEdit = { editableCarbs = math.baseDoubleFromText(it) }
                                )
                                SheetHairline()
                                ReviewNutritionValueRow(
                                    label = stringResource(R.string.nutrition_label_fat),
                                    displayValue = MacroValueFormatter.string(math.scaledMacro(editableFat)),
                                    editValue = MacroValueFormatter.string(math.scaledMacro(editableFat)),
                                    unit = stringResource(R.string.unit_g),
                                    unlocked = nutritionUnlocked,
                                    accentColor = AppColors.Fat,
                                    onEdit = { editableFat = math.baseDoubleFromText(it) }
                                )
                                SheetHairline()
                                ReviewNutritionValueRow(
                                    label = stringResource(R.string.nutrition_label_fiber),
                                    displayValue = math.displayD(math.scaledD(editableMicros.fiber)),
                                    editValue = math.editD(math.scaledD(editableMicros.fiber)),
                                    unit = stringResource(R.string.unit_g),
                                    unlocked = nutritionUnlocked,
                                    accentColor = AppColors.Fiber,
                                    onEdit = {
                                        editableMicros = editableMicros.with(
                                            MicronutrientField.FIBER,
                                            math.baseOptionalFromText(it),
                                        )
                                    }
                                )
                            }
                        }

                        if (MicronutrientField.MoreNutrition.any { editableMicros[it] != null }) {
                            item {
                                SheetPillRow(onClick = { moreNutritionExpanded = !moreNutritionExpanded }) {
                                    Text(stringResource(R.string.sheet_more_nutrition), fontSize = 17.sp, modifier = Modifier.weight(1f))
                                    Icon(
                                        if (moreNutritionExpanded) Icons.Filled.KeyboardArrowDown
                                        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                                    )
                                }
                            }
                            if (moreNutritionExpanded) {
                                item {
                                    SheetPillCard {
                                        MicronutrientField.MoreNutrition.forEachIndexed { idx, field ->
                                            if (idx > 0) SheetHairline()
                                            val value = math.scaledD(editableMicros[field])
                                            ReviewNutritionValueRow(
                                                label = stringResource(field.labelRes),
                                                displayValue = math.displayD(value),
                                                editValue = math.editD(value),
                                                unit = stringResource(field.unitRes),
                                                unlocked = nutritionUnlocked,
                                                dim = true,
                                                onEdit = {
                                                    editableMicros = editableMicros.with(field, math.baseOptionalFromText(it))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { SheetSectionHeader(stringResource(R.string.sheet_meal)) }
                        item {
                            SheetPillRow(onClick = { mealMenuExpanded = true }) {
                                Text(stringResource(R.string.sheet_meal_type), fontSize = 17.sp, modifier = Modifier.weight(1f))
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            sheetMealIcon(mealType),
                                            contentDescription = null,
                                            tint = AppColors.Calorie,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            mealLabel(mealType),
                                            fontSize = 17.sp,
                                            color = AppColors.Calorie,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.UnfoldMore,
                                            contentDescription = null,
                                            tint = AppColors.Calorie
                                        )
                                    }
                                    SheetGlassDropdownMenu(
                                        expanded = mealMenuExpanded,
                                        onDismissRequest = { mealMenuExpanded = false },
                                        menuWidth = 184.dp
                                    ) {
                                        for (m in pickerMealIds()) {
                                            SheetGlassDropdownMenuItem(
                                                label = mealLabel(m),
                                                leadingIcon = sheetMealIcon(m),
                                                selected = m == mealType,
                                                onClick = {
                                                    mealType = m
                                                    mealMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            ConstituentsSection(
                                rows = app.chompass.services.ai.ConstituentReconcile.scaleAll(
                                    editableConstituents,
                                    scale,
                                ),
                                optionalGoals = optionalGoals,
                                expanded = constituentsExpanded,
                                onExpandedChange = { constituentsExpanded = it },
                                onRowsChange = { displayRows ->
                                    val (cleaned, agg, serving) = applyConstituentDisplayEdit(displayRows)
                                    editableConstituents = cleaned
                                    if (serving > 0) {
                                        baseServingGrams = serving
                                        servingGrams = serving
                                        servingTouched = true
                                        servingQuantityText = ServingUnitOption.formatQuantity(
                                            if (selectedServingOption.gramsPerUnit > 0) {
                                                serving / selectedServingOption.gramsPerUnit
                                            } else {
                                                serving
                                            },
                                        )
                                    }
                                    if (agg != null) {
                                        editableCalories = agg.calories
                                        editableProtein = agg.protein
                                        editableCarbs = agg.carbs
                                        editableFat = agg.fat
                                    }
                                },
                            )
                        }

                        item { SheetSectionHeader(stringResource(R.string.edit_note_section)) }
                        item {
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.edit_note_placeholder),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                            )
                        }
                    }
                }
            }

            SheetStickyPrimaryBar(
                primaryLabel = stringResource(R.string.action_save),
                primaryEnabled = !nameTaken,
                onPrimary = {
                    dismissKeyboard()
                    onSave(buildUpdated())
                },
            )
        }
    }

    if (showIconPicker) {
        EditFoodIconDialog(
            hasPhoto = editableImageFilename != null,
            errorMessage = iconPickError,
            onPickEmoji = { emoji ->
                editableEmoji = emoji
                iconPickError = null
                showIconPicker = false
            },
            onSetPhoto = {
                showIconPicker = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePhoto = {
                editableImageFilename = null
                iconPickError = null
            },
            onDismiss = { showIconPicker = false },
        )
    }
}
