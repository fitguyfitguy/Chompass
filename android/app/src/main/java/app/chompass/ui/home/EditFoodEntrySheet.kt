package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.toMicronutrients
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import app.chompass.ui.util.clockTimePattern
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.FoodEntry
import app.chompass.services.MealShare
import app.chompass.models.MacroValueFormatter
import app.chompass.models.MealType
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.ServingUnitOption
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.kcalText
import app.chompass.ui.components.macroGramsText
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import app.chompass.ui.components.rememberFoodImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets

/**
 * Edit page for an existing FoodEntry. Visually identical to [FoodResultSheet]
 * (the first-time review page), so the edit experience matches the logging
 * experience. Differences from FoodResultSheet:
 *   - Sticky primary action says "Save" instead of "Log".
 *   - Initial values come from the existing entry; save mutates it via onSave.
 * Deletion is handled by swipe-to-delete on the Home food log list.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditFoodEntrySheet(
    entry: FoodEntry,
    preferGramsByDefault: Boolean = false,
    /** Codeberg #20 phase 2: with the master AI switch off, the Ask-AI-to-correct
     *  section is hidden and the stored note is a plain editable field (Save
     *  only — never Reprocess). */
    aiFeaturesEnabled: Boolean = true,
    onReprocess: suspend (
        updatedNote: String,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ) -> FoodAnalysis,
    onSave: (FoodEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var currentBaseEntry by remember(entry) { mutableStateOf(entry) }
    var noteText by remember(entry) { mutableStateOf(entry.customNote ?: "") }
    var isReprocessing by remember { mutableStateOf(false) }
    var reprocessPhase by remember { mutableStateOf<EntryAnalysisPhase?>(null) }
    var reprocessPartial by remember { mutableStateOf<app.chompass.services.ai.PartialFoodAnalysis?>(null) }
    var changedFields by remember { mutableStateOf<List<ReprocessDiffRow>>(emptyList()) }
    // Dismissible by downward drag; only block while reprocessing (matches the
    // touch-consuming overlay below). Never permanently reject Hidden.
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Long list: block overscroll at the BOTTOM edge always (the bottom edge
    // must not fight the sheet's drag-to-dismiss, visible as a shake when
    // scrolled to the bottom). The top edge is gated (Codeberg #30): blocked
    // while typing, else drag-from-content dismissal stays — but with raised
    // sheet thresholds (maintainer decision 2026-08-18) so a read-only scroll
    // gesture cannot dismiss; only a decisive pull/flick can.
    val listState = rememberLazyListState()

    val recordedServing = currentBaseEntry.servingSizeGrams
    val entryBaseServing = recordedServing ?: 100.0
    // var so per-entry serving edits (custom unit name / grams) persist to save.
    var servingUnitOptions by remember(currentBaseEntry.servingUnitOptions, entryBaseServing) {
        mutableStateOf(ServingUnitOption.normalizedOptions(currentBaseEntry.servingUnitOptions, entryBaseServing))
    }
    var name by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.name) }
    val initialServingUnit = if (preferGramsByDefault) {
        ServingUnitOption.grams.unit
    } else {
        currentBaseEntry.selectedServingUnit
    }
    var selectedServingUnitId by remember(currentBaseEntry, servingUnitOptions, preferGramsByDefault) {
        mutableStateOf(ServingUnitOption.initialUnitId(initialServingUnit, servingUnitOptions))
    }
    var servingGrams by remember(currentBaseEntry, entryBaseServing) { mutableStateOf(entryBaseServing) }
    var baseServingGrams by remember(currentBaseEntry, entryBaseServing) { mutableStateOf(entryBaseServing) }
    // True once the user changed the serving (weight/quantity) on an entry that
    // had no recorded serving: the corrected weight then records as the new
    // serving without scaling macros (Codeberg #10 follow-up).
    var servingTouched by remember(currentBaseEntry, entryBaseServing) { mutableStateOf(false) }
    var editableConstituents by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.constituents) }
    var constituentsExpanded by remember(currentBaseEntry) {
        mutableStateOf(currentBaseEntry.constituents.isNotEmpty())
    }
    var servingQuantityText by remember(currentBaseEntry, servingUnitOptions, preferGramsByDefault) {
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
    val zone = remember { ZoneId.systemDefault() }
    val initialLoggedAt = remember(entry.id, entry.timestamp) { entry.timestamp.atZone(zone) }
    var loggedDate by remember(entry.id, entry.timestamp) { mutableStateOf(initialLoggedAt.toLocalDate()) }
    var loggedTime by remember(entry.id, entry.timestamp) { mutableStateOf(initialLoggedAt.toLocalTime().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Entry icon: pickable emoji and/or photo (see EditFoodIconDialog).
    var editableEmoji by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.emoji) }
    var editableImageFilename by remember(currentBaseEntry) { mutableStateOf(currentBaseEntry.imageFilename) }
    var showIconPicker by remember { mutableStateOf(false) }
    var iconPickError by remember { mutableStateOf<String?>(null) }
    val isDark = isDarkTheme()
    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val context = LocalContext.current
    val dateFormatter = remember { LocaleFormat.mediumDate() }
    val timeFormatter = remember(context) { DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // Photo-picker → storeBytes (sampled decode + JPEG + 320px thumbnail). The
    // file is `${entry.id}.jpg`, so re-picking overwrites in place — no orphans.
    val imageStore = remember { (context.applicationContext as app.chompass.ChompassApp).container.imageStore }
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

    fun buildUpdated(): FoodEntry = editableMicros
        .scaled(scale)
        .applyTo(
            currentBaseEntry.copy(
                name = name.trim().ifEmpty { currentBaseEntry.name },
                calories = math.scaledInt(editableCalories),
                protein = math.scaledMacro(editableProtein),
                carbs = math.scaledMacro(editableCarbs),
                fat = math.scaledMacro(editableFat),
                timestamp = loggedDate.atTime(loggedTime).atZone(zone).toInstant(),
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

    // Re-run the AI on this entry with the edited note and overwrite the fields in
    // place; marking customNote as the current note flips the primary button back to Save.
    fun reprocess() {
        // Unreachable via UI when the master switch is off (button says Save,
        // section is hidden); belt-and-braces against a stale callback.
        if (!aiFeaturesEnabled) return
        scope.launch {
            isReprocessing = true
            errorText = null
            changedFields = emptyList()
            reprocessPhase = EntryAnalysisPhase.Preparing
            reprocessPartial = null
            val before = currentBaseEntry
            try {
                val newAnalysis = onReprocess(noteText) { progress ->
                    when (progress) {
                        is FoodAnalysisProgress.Phase -> reprocessPhase = progress.phase
                        is FoodAnalysisProgress.Partial -> {
                            reprocessPartial = progress.partial
                        }
                        is FoodAnalysisProgress.Parsed -> {
                            reprocessPartial = app.chompass.services.ai.PartialFoodAnalysis.fromComplete(
                                progress.analysis,
                                streaming = false,
                            )
                        }
                        is FoodAnalysisProgress.Complete -> {
                            reprocessPartial = app.chompass.services.ai.PartialFoodAnalysis.fromComplete(
                                progress.analysis,
                                streaming = false,
                            )
                        }
                    }
                }
                currentBaseEntry = currentBaseEntry.copy(
                    name = newAnalysis.name,
                    calories = newAnalysis.calories,
                    protein = newAnalysis.protein,
                    carbs = newAnalysis.carbs,
                    fat = newAnalysis.fat,
                    servingSizeGrams = newAnalysis.servingSizeGrams,
                    servingUnitOptions = newAnalysis.servingUnitOptions,
                    selectedServingUnit = newAnalysis.selectedServingUnit,
                    selectedServingQuantity = newAnalysis.selectedServingQuantity,
                    customNote = noteText.trim().takeIf { it.isNotEmpty() },
                    emoji = newAnalysis.emoji,
                    constituents = newAnalysis.constituents,
                ).let { newAnalysis.toMicronutrients().applyTo(it) }
                editableCalories = newAnalysis.calories
                editableProtein = newAnalysis.protein
                editableCarbs = newAnalysis.carbs
                editableFat = newAnalysis.fat
                editableMicros = newAnalysis.toMicronutrients()
                name = newAnalysis.name
                servingGrams = newAnalysis.servingSizeGrams ?: 100.0
                baseServingGrams = newAnalysis.servingSizeGrams ?: 100.0
                editableConstituents = newAnalysis.constituents
                constituentsExpanded = newAnalysis.constituents.isNotEmpty()
                editableEmoji = newAnalysis.emoji
                changedFields = buildReprocessDiff(
                    before,
                    currentBaseEntry,
                    ReprocessDiffLabels(
                        name = context.getString(R.string.manual_name),
                        calories = context.getString(R.string.macro_calories),
                        protein = context.getString(R.string.macro_protein),
                        carbs = context.getString(R.string.macro_carbs),
                        fat = context.getString(R.string.macro_fat),
                        serving = context.getString(R.string.sheet_serving),
                    ),
                    context.getString(R.string.unit_kcal),
                    context.getString(R.string.unit_g),
                )
            } catch (e: Exception) {
                errorText = e.localizedMessage ?: context.getString(R.string.edit_reprocessing_failed)
            } finally {
                isReprocessing = false
                reprocessPhase = null
                reprocessPartial = null
            }
        }
    }

    // Codeberg #30: block top-edge drag dismissal only while the user is
    // editing typed input. Focus covers active typing; the content diff
    // covers typed-but-blurred fields (name/note). Read-only scrolls keep
    // drag-from-content dismissal, but the sheet's raised thresholds
    // (maintainer decision 2026-08-18) make that a deliberate pull/flick,
    // not a scroll gesture.
    var inputFocused by remember { mutableStateOf(false) }
    val typedContentChanged = name.trim() != currentBaseEntry.name.trim() ||
        noteText != (currentBaseEntry.customNote ?: "")

    ChompassBottomSheet(
        onDismiss = { if (!isReprocessing) onDismiss() },
        sheetState = rememberChompassSheetState(
            busy = isReprocessing,
            // Maintainer decision 2026-08-18: stock m3 1.4 thresholds dismiss
            // on a gentle swipe (velocity 125 dp/s). Raised twice on device:
            // a read-only scroll gesture must spring back; dismissal needs a
            // ~65% pull or a firm flick. Typing is still fully protected by
            // the gate.
            positionalThreshold = 300.dp,
            velocityThreshold = 1200.dp,
        ),
        containerColor = sheetSurface,
        // Codeberg #6: zero the chrome insets — the default contentWindowInsets
        // feeds a layout feedback loop with the footer's navigationBarsPadding/
        // imePadding (M3 consumeWindowInsets(0,0,0,max(0,offset)) changes the
        // consumed insets as the sheet moves -> footer padding -> content
        // re-measure -> anchors move -> offset re-based). Removing it kills the
        // coupling; the footer still pads itself from the raw insets.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // While the note differs from what's saved, the primary button becomes
        // "Reprocess"; once reprocessed (or unchanged) it reverts to "Save".
        // With the master AI switch off the note is a plain stored field, so the
        // button is always "Save" (no AI path exists to flip it).
        val noteChanged = shouldOfferReprocess(aiFeaturesEnabled, noteText, currentBaseEntry.customNote)
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.sheet_edit_food),
                onCancel = { if (!isReprocessing) onDismiss() },
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
            // Compact hero so name / serving / macros fit the first viewport.
            // Tap it to change the emoji or photo shown for this entry.
            item {
                EditFoodEntryHero(
                    emoji = editableEmoji,
                    imageFilename = editableImageFilename,
                    enabled = !isReprocessing,
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
                        servingUnitOptions = options
                        selectedServingUnitId = newId
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

            // "More Nutrition" — own pill row with chevron-right that flips to
            // chevron-down when expanded; matches iOS DisclosureGroup behavior.
            if (MicronutrientField.MoreNutrition.any { editableMicros[it] != null }) {
                item {
                    SheetPillRow(onClick = { moreNutritionExpanded = !moreNutritionExpanded }) {
                        Text(stringResource(R.string.sheet_more_nutrition), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(
                            if (moreNutritionExpanded) Icons.Filled.KeyboardArrowDown
                            else Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                    // Wrap only the right cluster in a Box so the DropdownMenu
                    // anchors on the right side of the row (under the value),
                    // not at the row's left edge.
                    Box {
                        androidx.compose.foundation.layout.Row(
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
                                stringResource(mealType.displayNameRes),
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
                            for (m in MealType.values()) {
                                SheetGlassDropdownMenuItem(
                                    label = stringResource(m.displayNameRes),
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

            if (aiFeaturesEnabled) {
                item { SheetSectionHeader(stringResource(R.string.edit_reprocess_section)) }
            } else {
                // Master AI switch off (Codeberg #20): no Ask-AI-to-correct section.
                // The stored note stays editable as a plain field so notes on
                // logged entries remain visible/editable without any AI.
                item { SheetSectionHeader(stringResource(R.string.edit_note_section)) }
                item {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.edit_note_placeholder),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                    )
                }
            }
            if (aiFeaturesEnabled) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.edit_reprocess_context),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    SheetPillCard {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "${currentBaseEntry.emoji ?: "🍽"}  ${currentBaseEntry.name}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${kcalText(currentBaseEntry.calories)} · " +
                                    macroGramsText(currentBaseEntry.protein) + " P · " +
                                    macroGramsText(currentBaseEntry.carbs) + " C · " +
                                    macroGramsText(currentBaseEntry.fat) + " F",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                            currentBaseEntry.servingSizeGrams?.takeIf { it > 0 }?.let { grams ->
                                Text(
                                    stringResource(R.string.home_serving_format, grams.toInt()),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.edit_reprocess_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.edit_reprocess_explain),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 18.sp,
                    )

                    val chipLabels = listOf(
                        stringResource(R.string.edit_reprocess_chip_portion),
                        stringResource(R.string.edit_reprocess_chip_larger),
                        stringResource(R.string.edit_reprocess_chip_oil),
                        stringResource(R.string.edit_reprocess_chip_brand),
                        stringResource(R.string.edit_reprocess_chip_prep),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chipLabels.forEach { label ->
                            androidx.compose.material3.FilterChip(
                                selected = noteText.contains(label, ignoreCase = true),
                                enabled = !isReprocessing,
                                onClick = {
                                    noteText = if (noteText.isBlank()) label
                                    else if (noteText.contains(label, ignoreCase = true)) noteText
                                    else "$noteText, $label"
                                },
                                label = { Text(label, fontSize = 13.sp) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Calorie.copy(alpha = 0.18f),
                                    selectedLabelColor = AppColors.Calorie,
                                ),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            changedFields = emptyList()
                        },
                        enabled = !isReprocessing,
                        placeholder = {
                            Text(
                                stringResource(R.string.edit_reprocess_hint),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                    )

                    if (isReprocessing) {
                        val phase = reprocessPhase
                        if (phase != null && reprocessPartial?.hasAnyField == true) {
                            ProgressiveAnalysisCard(partial = reprocessPartial!!)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.Calorie,
                                )
                                Text(
                                    when (phase) {
                                        EntryAnalysisPhase.Preparing ->
                                            stringResource(R.string.entry_analysis_phase_preparing)
                                        EntryAnalysisPhase.LookingUpBarcode ->
                                            stringResource(R.string.entry_analysis_phase_looking_up_barcode)
                                        EntryAnalysisPhase.CallingAi ->
                                            stringResource(R.string.entry_analysis_phase_calling_ai)
                                        EntryAnalysisPhase.Parsing ->
                                            stringResource(R.string.entry_analysis_phase_parsing)
                                        else -> stringResource(R.string.edit_reprocessing)
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    if (!isReprocessing && changedFields.isNotEmpty()) {
                        SheetPillCard {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stringResource(R.string.edit_reprocess_diff_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Calorie,
                                )
                                changedFields.forEach { row ->
                                    Text(
                                        stringResource(
                                            R.string.edit_reprocess_diff_row,
                                            row.label,
                                            row.before,
                                            row.after,
                                        ),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                    )
                                }
                                Text(
                                    stringResource(R.string.edit_reprocess_review_hint),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                            }
                        }
                    } else if (!isReprocessing && noteText.trim() == (currentBaseEntry.customNote ?: "") &&
                        changedFields.isEmpty() && errorText == null
                    ) {
                        // idle
                    }

                    errorText?.let {
                        Text(it, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            } // aiFeaturesEnabled

            item { SheetSectionHeader(stringResource(R.string.section_date_time)) }
            item {
                SheetPillCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                dismissKeyboard()
                                showDatePicker = true
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.label_date), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(
                            loggedDate.format(dateFormatter),
                            fontSize = 17.sp,
                            color = AppColors.Calorie,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    SheetHairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                dismissKeyboard()
                                showTimePicker = true
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.label_time), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(
                            loggedTime.format(timeFormatter),
                            fontSize = 17.sp,
                            color = AppColors.Calorie,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Share this meal as a fudai://add-meal link (issue #107)
            item { SheetSectionHeader(stringResource(R.string.section_share)) }
            item {
                SheetPillCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { MealShare.share(context, listOf(currentBaseEntry)) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = null,
                            tint = AppColors.Calorie,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.share_meal), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        }  // WithoutOverscroll
                if (isReprocessing) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures { /* Consume touches while reprocessing */ }
                            }
                    )
                }
            }

            SheetStickyPrimaryBar(
                primaryLabel = when {
                    isReprocessing -> stringResource(R.string.edit_reprocessing)
                    noteChanged -> stringResource(R.string.edit_reprocess)
                    else -> stringResource(R.string.action_save)
                },
                primaryEnabled = !isReprocessing,
                onPrimary = {
                    if (!isReprocessing) {
                        if (noteChanged) reprocess() else onSave(buildUpdated())
                    }
                },
            )
        }
    }

    if (showDatePicker) {
        var pickedDate by remember(loggedDate) { mutableStateOf(loggedDate) }
        FudGlassDialog(onDismissRequest = { showDatePicker = false }) {
            Text(stringResource(R.string.label_date), fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
                    loggedDate = pickedDate
                    showDatePicker = false
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { showDatePicker = false }
            )
        }
    }

    if (showTimePicker) {
        EditFoodTimeDialog(
            initialTime = loggedTime,
            onConfirm = {
                loggedTime = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
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

/**
 * Primary-button decision for the edit sheet: a note edit flips Save →
 * "Correct with AI" only while AI features are on. With the master switch
 * off (Codeberg #20) the note is a plain stored field — Save is the only
 * primary action, so the AI path is unreachable from this sheet.
 */
internal fun shouldOfferReprocess(
    aiFeaturesEnabled: Boolean,
    noteText: String,
    savedNote: String?,
): Boolean = aiFeaturesEnabled && noteText.trim() != (savedNote ?: "")

@Composable
private fun EditFoodTimeDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    var hourText by remember(initialTime) { mutableStateOf(initialTime.hour.toString().padStart(2, '0')) }
    var minuteText by remember(initialTime) { mutableStateOf(initialTime.minute.toString().padStart(2, '0')) }

    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.label_time), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FudGlassTextField(
                value = hourText,
                onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                placeholder = stringResource(R.string.placeholder_hour),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FudGlassTextField(
                value = minuteText,
                onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                placeholder = stringResource(R.string.placeholder_minute),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = {
                val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: initialTime.hour
                val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: initialTime.minute
                onConfirm(LocalTime.of(hour, minute))
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}

/**
 * Pickable food emojis for the entry icon. Single-codepoint so they render
 * uniformly across platforms.
 */
internal val FOOD_ENTRY_EMOJIS: List<String> = listOf(
    "🍽", "🍎", "🍌", "🍇", "🍉", "🍓", "🍒", "🍑", "🥭", "🍍", "🍋", "🥝",
    "🍅", "🥑", "🥦", "🥕", "🌽", "🍞", "🥐", "🥖", "🥞", "🧇", "🍳", "🥚",
    "🥓", "🍗", "🍖", "🥩", "🍔", "🍟", "🍕", "🌮", "🥗", "🍜", "🍝", "🍣",
    "🍦", "🍪", "🍩", "🍫", "🍰", "🥛", "☕", "🧃", "🥤", "🍺",
)

/** Hero shown at the top of the edit sheet; tap to change the emoji / photo. */
@Composable
internal fun EditFoodEntryHero(
    emoji: String?,
    imageFilename: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val ctx = LocalContext.current
    // Safe-cast so previews (no ChompassApp application) render the emoji fallback.
    val container = (ctx.applicationContext as? app.chompass.ChompassApp)?.container
    val bitmap = if (container != null) {
        rememberFoodImage(imageFilename, container.imageStore)
    } else {
        null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(AppRadii.Field))
            )
        } else {
            Text(emoji ?: "🍽", fontSize = 40.sp)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun EditFoodIconDialog(
    hasPhoto: Boolean,
    errorMessage: String? = null,
    onPickEmoji: (String) -> Unit,
    onSetPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.edit_icon_picker_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Column(
            Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FOOD_ENTRY_EMOJIS.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPickEmoji(emoji) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FudGlassPrimaryButton(
                text = stringResource(R.string.edit_icon_set_photo),
                onClick = onSetPhoto,
                modifier = Modifier.weight(1f),
                height = 44.dp,
            )
            if (hasPhoto) {
                FudGlassPrimaryButton(
                    text = stringResource(R.string.edit_icon_remove_photo),
                    onClick = onRemovePhoto,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
            }
        }
        errorMessage?.let {
            Text(it, color = Color.Red, fontSize = 13.sp)
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )
    }
}

internal data class ReprocessDiffLabels(
    val name: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val serving: String,
)

internal data class ReprocessDiffRow(
    val label: String,
    val before: String,
    val after: String,
)

internal fun buildReprocessDiff(
    before: FoodEntry,
    after: FoodEntry,
    labels: ReprocessDiffLabels,
    kcalUnit: String,
    gUnit: String,
): List<ReprocessDiffRow> {
    val rows = mutableListOf<ReprocessDiffRow>()
    fun add(label: String, a: String, b: String) {
        if (a != b) rows += ReprocessDiffRow(label, a, b)
    }
    fun macro(v: Double) = "${MacroValueFormatter.string(v)}$gUnit"
    add(labels.name, before.name, after.name)
    add(labels.calories, "${LocaleFormat.integer(before.calories)} $kcalUnit", "${LocaleFormat.integer(after.calories)} $kcalUnit")
    add(labels.protein, macro(before.protein), macro(after.protein))
    add(labels.carbs, macro(before.carbs), macro(after.carbs))
    add(labels.fat, macro(before.fat), macro(after.fat))
    val beforeG = before.servingSizeGrams?.let { "${LocaleFormat.integer(it.toInt())} $gUnit" } ?: "—"
    val afterG = after.servingSizeGrams?.let { "${LocaleFormat.integer(it.toInt())} $gUnit" } ?: "—"
    add(labels.serving, beforeG, afterG)
    return rows
}
