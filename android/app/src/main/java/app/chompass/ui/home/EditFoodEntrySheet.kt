package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.FoodReviewPositionalThreshold
import app.chompass.ui.components.FoodReviewVelocityThreshold
import app.chompass.ui.components.MagnitudeDrafts
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.toMicronutrients
import app.chompass.services.ai.AiError
import app.chompass.services.ai.userMessage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.app.DatePickerDialog
import android.app.TimePickerDialog

import android.graphics.Typeface
import android.text.TextPaint
import android.text.format.DateFormat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.style.TextOverflow
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.EnergyFormat
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.microsStaleFor
import app.chompass.services.MealShare
import app.chompass.models.MacroValueFormatter
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.ServingUnitOption
import app.chompass.models.OptionalNutrientGoals
import app.chompass.ui.components.ClockTimeWheelPicker
import app.chompass.ui.components.parseClockDigits
import app.chompass.ui.components.DateWheelPicker

import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import java.time.LocalDate
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
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

import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.navigation.LocalEnergyUnit
/**
 * Edit page for an existing FoodEntry. Visually identical to [FoodResultSheet]
 * (the first-time review page), so the edit experience matches the logging
 * experience. Differences from FoodResultSheet:
 *   - Sticky primary action says "Save" instead of "Log".
 *   - Initial values come from the existing entry; save mutates it via onSave.
 * Deletion is handled by swipe-to-delete on the Home food log list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodEntrySheet(
    entry: FoodEntry,
    /** #86: user's optional micro goals, shown as "(N%)" on ingredient micros. */
    optionalGoals: OptionalNutrientGoals? = null,
    preferGramsByDefault: Boolean = false,
    /** Codeberg #20 phase 2: with the master AI switch off, the Ask-AI-to-correct
     *  section is hidden and the stored note is a plain editable field (Save
     *  only — never Reprocess). */
    aiFeaturesEnabled: Boolean = true,
    useSystemDateTimePickers: Boolean = false,
    dayEntries: List<FoodEntry> = emptyList(),
    mealTimesEnabled: Boolean = true,
    onReprocess: suspend (
        updatedNote: String,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ) -> FoodAnalysis,
    onSave: (FoodEntry, applyTimeToMeal: Boolean) -> Unit,
    onLogNow: ((FoodEntry) -> Unit)? = null,
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
    // Hoisted out of the reprocess coroutine so the diff labels come from
    // stringResource, not context.getString (lint LocalContextGetResourceValueCall).
    val reprocessLabels = ReprocessDiffLabels(
        name = stringResource(R.string.manual_name),
        calories = stringResource(R.string.macro_calories),
        protein = stringResource(R.string.macro_protein),
        carbs = stringResource(R.string.macro_carbs),
        fat = stringResource(R.string.macro_fat),
        serving = stringResource(R.string.sheet_serving),
    )
    val reprocessGUnit = stringResource(R.string.unit_g)
    val reprocessEnergyUnit = LocalEnergyUnit.current
    val reprocessEnergyLabel = energyUnitLabel()
    // Long list: block overscroll at the BOTTOM edge always (the bottom edge
    // must not fight the sheet's drag-to-dismiss, visible as a shake when
    // scrolled to the bottom). The top edge is gated (Codeberg #30): blocked
    // while typing, else drag-from-content dismissal stays — but with raised
    // sheet thresholds (maintainer decision 2026-08-18) so a read-only scroll
    // gesture cannot dismiss; only a decisive pull/flick can.
    val listState = rememberLazyListState()

    // Serving-less entries with a unit quantity carry an honest portion base
    // (Codeberg #89); pure-gram entries keep null (Codeberg #10 follow-up).
    val recordedServing = ServingUnitOption.recordedPortionGrams(
        recordedServingGrams = currentBaseEntry.servingSizeGrams,
        selectedUnit = currentBaseEntry.selectedServingUnit,
        selectedQuantity = currentBaseEntry.selectedServingQuantity,
        options = currentBaseEntry.servingUnitOptions,
    )
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
    var selectedServingUnitId by remember(currentBaseEntry, preferGramsByDefault) {
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
    // Draft-while-typing (one commit model with the magnitude pickers): the
    // quantity field only moves the visible draft; the grams conversion —
    // deltas and expressions included — resolves on Save / collapse / unit
    // switch, so macros don't rescale through intermediate digits.
    val resolveServingDraft = {
        val option = ServingUnitOption.optionMatching(selectedServingUnitId, servingUnitOptions)
        val currentQuantity = if (option.gramsPerUnit > 0) servingGrams / option.gramsPerUnit else servingGrams
        val parsed = ServingUnitOption.applyDeltaInput(servingQuantityText, currentQuantity)
        if (parsed != null && parsed > 0) {
            servingGrams = parsed * option.gramsPerUnit
            servingTouched = true
            if (servingQuantityText.trim().startsWith("+") || servingQuantityText.trim().startsWith("-")) {
                servingQuantityText = ServingUnitOption.formatQuantity(parsed)
            }
        }
        Unit
    }
    DisposableEffect(currentBaseEntry) {
        val unregister = MagnitudeDrafts.register(resolveServingDraft)
        onDispose { unregister() }
    }
    var nutritionUnlocked by remember { mutableStateOf(false) }
    val scale = ServingUnitOption.servingScale(
        servingGrams = servingGrams,
        baseServingGrams = baseServingGrams,
        scaleWithAmount = !nutritionUnlocked,
    )
    var mealType by remember(entry) { mutableStateOf(currentBaseEntry.mealType) }
    var moreNutritionExpanded by remember { mutableStateOf(false) }
    var aiCorrectExpanded by remember { mutableStateOf(false) }
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
    var applyTimeToMeal by remember(entry.id) { mutableStateOf(false) }
    val siblingCount = remember(entry.id, entry.mealType, entry.timestamp, dayEntries) {
        siblingEntriesForTimeApply(dayEntries, entry, zone).size
    }
    val timeChanged = loggedDate != initialLoggedAt.toLocalDate() ||
        loggedTime != initialLoggedAt.toLocalTime().withSecond(0).withNano(0)
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

    // Commit paths recompute the scale derivatives from the live grams: the
    // sticky bar flushes a pending serving draft (MagnitudeDrafts.commitAll)
    // in the same frame, after the last composition computed `scale`/`math`.
    fun saveScale(): Double = ServingUnitOption.servingScale(
        servingGrams = servingGrams,
        baseServingGrams = baseServingGrams,
        scaleWithAmount = !nutritionUnlocked,
    )
    fun buildUpdated(): FoodEntry {
        val saveMath = FoodEntryEditMath(saveScale(), emDashText)
        val saveQuantity = ServingUnitOption.parseQuantity(servingQuantityText)?.takeIf { it > 0 }
        return editableMicros
            .scaled(saveScale())
            .applyTo(
                currentBaseEntry.copy(
                    name = name.trim().ifEmpty { currentBaseEntry.name },
                    calories = saveMath.scaledInt(editableCalories),
                    protein = saveMath.scaledMacro(editableProtein),
                    carbs = saveMath.scaledMacro(editableCarbs),
                    fat = saveMath.scaledMacro(editableFat),
                    timestamp = loggedDate.atTime(loggedTime).atZone(zone).toInstant(),
                    mealType = mealType,
                    customNote = noteText.trim().takeIf { it.isNotEmpty() },
                    servingSizeGrams = ServingUnitOption.persistedServingGrams(recordedServing, servingTouched, servingGrams),
                    servingUnitOptions = servingUnitOptions,
                    selectedServingUnit = if (servingUnitOptions.isEmpty()) null else selectedServingOption.unit,
                    selectedServingQuantity = if (servingUnitOptions.isEmpty()) null else saveQuantity,
                    constituents = app.chompass.services.ai.ConstituentReconcile.scaleAll(
                        editableConstituents,
                        saveScale(),
                    ),
                    emoji = editableEmoji,
                    imageFilename = editableImageFilename,
                )
            )
    }

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
                    microsCompositionSignature = app.chompass.models.microsCompositionSignature(newAnalysis.constituents),
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
                    reprocessLabels,
                    reprocessEnergyLabel,
                    reprocessGUnit,
                ) { EnergyFormat.quantity(it, reprocessEnergyUnit) }
            } catch (e: Exception) {
                errorText = (e as? AiError)?.userMessage(context) ?: context.getString(R.string.edit_reprocessing_failed)
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
            positionalThreshold = FoodReviewPositionalThreshold,
            velocityThreshold = FoodReviewVelocityThreshold,
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
                    packShot = entry.source == FoodSource.BARCODE,
                    enabled = !isReprocessing,
                    onClick = {
                        dismissKeyboard()
                        showIconPicker = true
                    },
                    onRemovePhoto = { editableImageFilename = null; iconPickError = null },
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
                            for (m in pickerMealIds(includeOther = !mealTimesEnabled)) {
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
                    if (siblingCount > 0 && timeChanged) {
                        SheetHairline()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { applyTimeToMeal = !applyTimeToMeal }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = applyTimeToMeal,
                                onCheckedChange = { applyTimeToMeal = it },
                            )
                            Text(
                                stringResource(R.string.edit_food_apply_time, mealLabel(entry.mealType)),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Strong),
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }

            item { SheetSectionHeader(stringResource(R.string.sheet_serving)) }
            item {
                ServingQuantityCard(
                    quantityText = servingQuantityText,
                    onQuantityChange = { newValue ->
                        servingQuantityText = newValue
                    },
                    selectedUnitId = selectedServingUnitId,
                    onSelectedUnitChange = { optionId ->
                        resolveServingDraft()
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
                        resolveServingDraft()
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
                    onQuantityEditingDone = resolveServingDraft,
                )
            }
            if (nutritionUnlocked) {
                item {
                    Text(
                        stringResource(R.string.sheet_serving_unlocked_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                if (recordedServing == null) {
                    item {
                        Text(
                            stringResource(R.string.sheet_serving_baseless_hint),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                }
            }

            item {
                SheetSectionHeaderWithLock(
                    title = stringResource(R.string.sheet_nutrition),
                    unlocked = nutritionUnlocked,
                    onToggle = {
                        resolveServingDraft()
                        if (!nutritionUnlocked) {
                            val baked = FoodEntryEditMath(saveScale(), emDashText).bakeScale(
                                editableCalories,
                                editableProtein,
                                editableCarbs,
                                editableFat,
                                editableMicros,
                                editableConstituents,
                                servingGrams,
                            )
                            editableCalories = baked.calories
                            editableProtein = baked.protein
                            editableCarbs = baked.carbs
                            editableFat = baked.fat
                            editableMicros = baked.micros
                            editableConstituents = baked.constituents
                            baseServingGrams = baked.baseServingGrams
                            nutritionUnlocked = true
                        } else {
                            baseServingGrams = servingGrams
                            nutritionUnlocked = false
                            dismissKeyboard()
                        }
                    }
                )
            }
            item {
                SheetPillCard {
                    val unit = LocalEnergyUnit.current
                    ReviewNutritionValueRow(
                        label = stringResource(R.string.nutrition_label_calories),
                        displayValue = "${math.scaledInt(EnergyFormat.quantity(editableCalories, unit))}",
                        editValue = "${math.scaledInt(EnergyFormat.quantity(editableCalories, unit))}",
                        unit = energyUnitLabel(),
                        unlocked = nutritionUnlocked,
                        accentColor = AppColors.Calorie,
                        onEdit = { editableCalories = EnergyFormat.toKcal(math.baseDoubleFromText(it).roundToInt(), unit) }
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
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                        )
                    }
                }
                if (moreNutritionExpanded) {
                    item {
                        SheetPillCard {
                            val stale = microsStaleFor(
                                currentBaseEntry.microsCompositionSignature,
                                editableConstituents,
                            )
                            if (stale) {
                                StaleCompositionNote(onReestimate = { reprocess() })
                            }
                            MicronutrientField.MoreNutrition.forEachIndexed { idx, field ->
                                if (idx > 0 || stale) SheetHairline()
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
                        val commit = commitConstituentDisplayEdit(displayRows, scale)
                        editableConstituents = commit.bases
                        if (commit.baseAggregate != null) {
                            editableCalories = commit.baseAggregate.calories
                            editableProtein = commit.baseAggregate.protein
                            editableCarbs = commit.baseAggregate.carbs
                            editableFat = commit.baseAggregate.fat
                        }
                        if (commit.baseSum > 0) {
                            baseServingGrams = commit.baseSum
                            servingGrams = commit.displaySum
                            servingTouched = true
                            servingQuantityText = ServingUnitOption.formatQuantity(
                                if (selectedServingOption.gramsPerUnit > 0) {
                                    commit.displaySum / selectedServingOption.gramsPerUnit
                                } else {
                                    commit.displaySum
                                },
                            )
                        }
                    },
                )
            }
            currentBaseEntry.productMetadata?.takeIf { it.hasDisplayDetails }?.let { metadata ->
                item { SheetSectionHeader(stringResource(R.string.product_information)) }
                item { FoodProductMetadataCard(metadata) }
            }

            if (aiFeaturesEnabled) {
                val showAiCorrectBody = aiCorrectExpanded ||
                    isReprocessing ||
                    changedFields.isNotEmpty() ||
                    errorText != null
                item {
                    SheetPillRow(
                        onClick = {
                            if (!isReprocessing) aiCorrectExpanded = !aiCorrectExpanded
                        },
                    ) {
                        Text(
                            stringResource(R.string.edit_reprocess_section),
                            fontSize = 17.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (!showAiCorrectBody && noteText.isNotBlank()) {
                            Text(
                                noteText,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                            )
                        }
                        Icon(
                            if (showAiCorrectBody) Icons.Filled.KeyboardArrowDown
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                }
                if (showAiCorrectBody) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.edit_reprocess_explain),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                lineHeight = 18.sp,
                            )
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled)
                                    )
                                },
                                shape = RoundedCornerShape(AppRadii.Pill),
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
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary),
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
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Emphasized),
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.edit_reprocess_review_hint),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                        )
                                    }
                                }
                            }

                            errorText?.let {
                                Text(it, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled)
                            )
                        },
                        shape = RoundedCornerShape(AppRadii.Pill),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                    )
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
                            Icons.Filled.Share,
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
                        if (noteChanged) reprocess() else onSave(buildUpdated(), applyTimeToMeal && timeChanged)
                    }
                },
                textActionLabel = if (entry.planned && onLogNow != null && !isReprocessing) {
                    stringResource(R.string.entry_log_now)
                } else {
                    null
                },
                onTextAction = if (entry.planned && onLogNow != null) {
                    {
                        onLogNow(buildUpdated())
                    }
                } else {
                    null
                },
            )
        }
    }

    if (showDatePicker) {
        if (useSystemDateTimePickers) {
            val ctx = LocalContext.current
            LaunchedEffect(Unit) {
                val theme = if (isDark) {
                    android.R.style.Theme_DeviceDefault_Dialog
                } else {
                    android.R.style.Theme_DeviceDefault_Light_Dialog
                }
                DatePickerDialog(
                    ctx,
                    theme,
                    { _, y, m, d ->
                        loggedDate = LocalDate.of(y, m + 1, d)
                        showDatePicker = false
                    },
                    loggedDate.year,
                    loggedDate.monthValue - 1,
                    loggedDate.dayOfMonth,
                ).apply {
                    setOnCancelListener { showDatePicker = false }
                    setOnDismissListener { showDatePicker = false }
                    show()
                }
            }

        } else {

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
    }

    if (showTimePicker) {
        FoodLogTimePicker(
            initialTime = loggedTime,
            useSystem = useSystemDateTimePickers,
            onConfirm = {
                loggedTime = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
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
internal fun FoodLogTimePicker(
    initialTime: LocalTime,
    useSystem: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    if (useSystem) {
        // The Activity context is required for the dialog window token; a
        // createConfigurationContext wrapper has no token and crashes (BadTokenException).
        val ctx = LocalContext.current
        val dark = isDarkTheme()
        LaunchedEffect(Unit) {
            val theme = if (dark) {
                android.R.style.Theme_DeviceDefault_Dialog
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog
            }
            TimePickerDialog(
                ctx,
                theme,
                { _, h, min -> onConfirm(LocalTime.of(h, min)) },
                initialTime.hour,
                initialTime.minute,
                DateFormat.is24HourFormat(ctx),
            ).apply {
                setOnCancelListener { onDismiss() }
                show()
            }
        }
    } else {
        EditFoodTimeDialog(
            initialTime = initialTime,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun EditFoodTimeDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember(initialTime) { mutableStateOf(initialTime) }
    var typedDigits by remember { mutableStateOf<String?>(null) }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.label_time), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        ClockTimeWheelPicker(
            time = picked,
            onChange = { picked = it },
            onTypedDraftChange = { typedDigits = it },
            modifier = Modifier.fillMaxWidth(),
        )
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = {
                val digits = typedDigits?.filter { it.isDigit() }
                val time = if (digits != null && digits.length == 4) {
                    parseClockDigits(digits) ?: picked
                } else {
                    picked
                }
                onConfirm(time)
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}

/**
 * Pickable food emojis for the entry icon. Single-codepoint so they render
 * uniformly across platforms. The dialog shows only glyphs the device can
 * draw; see [foodEntryEmojisSupported].
 */
internal val FOOD_ENTRY_EMOJIS: List<String> = listOf(
    "🍽", "🍎", "🍏", "🍐", "🍊", "🍋",
    "🍌", "🍉", "🍇", "🍓", "🍒", "🍑",
    "🥭", "🍍", "🥝", "🍈", "🥥", "🫐",
    "🍅", "🥑", "🥦", "🥬", "🥒", "🥕",
    "🌽", "🥔", "🍠", "🧄", "🧅", "🍆",
    "🫑", "🫒", "🍄", "🥜", "🫘", "🫛",
    "🫚", "🧀", "🧈", "🍞", "🥐", "🥖",
    "🥨", "🥯", "🫓", "🥞", "🧇", "🥣",
    "🍳", "🥚", "🥓", "🍗", "🍖", "🥩",
    "🍔", "🍟", "🍕", "🌭", "🥪", "🌮",
    "🌯", "🥙", "🧆", "🫔", "🥗", "🍜",
    "🍝", "🍲", "🥘", "🫕", "🍛", "🍱",
    "🍙", "🍚", "🍣", "🍤", "🥟", "🐟",
    "🦐", "🦀", "🦞", "🦪", "🍦", "🍪",
    "🍩", "🍫", "🍰", "🧁", "🥧", "🍿",
    "🍯", "🥛", "☕", "🍵", "🫖", "🧋",
    "🧃", "🥤", "🍺", "🍷", "🥃", "🍶",
)

/**
 * Device-supported subset of [FOOD_ENTRY_EMOJIS]. If [hasGlyph] rejects most
 * of the catalog (broken paint / missing emoji fallback), return the full
 * list rather than an empty grid.
 */
internal fun foodEntryEmojisSupported(hasGlyph: (String) -> Boolean): List<String> {
    val supported = FOOD_ENTRY_EMOJIS.filter(hasGlyph)
    return if (supported.size >= FOOD_ENTRY_EMOJIS.size / 2) supported else FOOD_ENTRY_EMOJIS
}

/** Hero shown at the top of the edit sheet; tap to change the emoji / photo.
 *  Pass [onRemovePhoto] to render a direct × on the photo (#220) — it only
 *  clears the pending edit state; Save persists, Cancel keeps the photo. */
@Composable
internal fun EditFoodEntryHero(
    emoji: String?,
    imageFilename: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    packShot: Boolean = false,
    onRemovePhoto: (() -> Unit)? = null,
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
            Box {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = if (packShot) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .then(
                            if (packShot) Modifier.fillMaxWidth().height(160.dp)
                            else Modifier.size(96.dp),
                        )
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                )
                // #220: analysis-queue-style direct remove; pending state only.
                if (onRemovePhoto != null) {
                    IconButton(
                        onClick = onRemovePhoto,
                        enabled = enabled,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = AppTextOpacity.Subtle), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
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
            val emojis = remember {
                val paint = TextPaint().apply { typeface = Typeface.DEFAULT }
                foodEntryEmojisSupported(paint::hasGlyph)
            }
            emojis.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppRadii.Track))
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
    kcalToDisplay: (Int) -> Int = { it },
): List<ReprocessDiffRow> {
    val rows = mutableListOf<ReprocessDiffRow>()
    fun add(label: String, a: String, b: String) {
        if (a != b) rows += ReprocessDiffRow(label, a, b)
    }
    fun macro(v: Double) = "${MacroValueFormatter.string(v)}$gUnit"
    add(labels.name, before.name, after.name)
    add(labels.calories, "${LocaleFormat.integer(kcalToDisplay(before.calories))} $kcalUnit", "${LocaleFormat.integer(kcalToDisplay(after.calories))} $kcalUnit")
    add(labels.protein, macro(before.protein), macro(after.protein))
    add(labels.carbs, macro(before.carbs), macro(after.carbs))
    add(labels.fat, macro(before.fat), macro(after.fat))
    val beforeG = before.servingSizeGrams?.let { "${LocaleFormat.integer(it.toInt())} $gUnit" } ?: "—"
    val afterG = after.servingSizeGrams?.let { "${LocaleFormat.integer(it.toInt())} $gUnit" } ?: "—"
    add(labels.serving, beforeG, afterG)
    return rows
}
