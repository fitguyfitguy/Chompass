package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.FoodReviewPositionalThreshold
import app.chompass.ui.components.FoodReviewVelocityThreshold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import app.chompass.models.fillMissingFrom
import app.chompass.models.gapReport
import app.chompass.models.microsStaleFor
import app.chompass.services.FillGapsFeature
import app.chompass.services.ai.AiError
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.toMicronutrients
import app.chompass.services.ai.userMessage
import app.chompass.ui.components.ChompassSegmentedTabs
import app.chompass.models.MacroValueFormatter
import app.chompass.models.EnergyFormat
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.components.MagnitudeDrafts
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.ServingUnitOption
import app.chompass.models.OptionalNutrientGoals
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.AppRadii
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
 * reusable template, not a diary row: no date/time pickers and no share link,
 * and the note is a plain stored field. Renaming is guarded — the favorite's
 * name is its identity key, so a name another food (diary row or favorite)
 * already uses blocks Save with an inline error.
 *
 * WP2 fill-missing: the same Ask-AI block as the diary edit sheet (mode
 * pills, scan line, MissingValuesNote). Fill merges into empty fields of the
 * draft; Reanalyze/Recalculate overwrite in place — Save commits, dismiss
 * discards.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditFavoriteSheet(
    container: AppContainer,
    entry: FoodEntry,
    /** #86: user's optional micro goals, shown as "(N%)" on ingredient micros. */
    optionalGoals: OptionalNutrientGoals? = null,
    aiFeaturesEnabled: Boolean = true,
    /** Reanalyze/Recalculate: full AI overwrite of the draft fields. */
    onReprocess: suspend (
        updatedNote: String,
        /** RECALCULATE only: the sheet's current edited mix the estimate runs over; null = Reanalyze (stored entry + note). */
        editedMix: FoodEntry?,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ) -> FoodAnalysis,
    /** Fill-missing analysis: text-only; result merges into empty fields only. */
    onFill: suspend (
        onProgress: (FoodAnalysisProgress) -> Unit,
    ) -> FoodAnalysis,
    /** Sub-screen reuse (Codeberg #142): the Settings library shows its own title. */
    titleOverride: String? = null,
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
    // Quantity changes resolve live: each picker value rewrites the draft and
    // immediately converts it to grams, so the Total row and locked macros
    // track the wheel while scrolling. Typed mode still commits only on Done,
    // so intermediate digits never reach the draft; the MagnitudeDrafts
    // registration and the collapse / unit-switch flushes remain as safety
    // nets for anything that lands unresolved.
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

    // WP2 fill-missing: same Ask-AI state machine as EditFoodEntrySheet —
    // smart-default mode (gaps → Fill, photo → Reanalyze, composition edited
    // this session → Recalculate), pill taps pin the choice.
    var aiCorrectExpanded by remember { mutableStateOf(false) }
    val entryGapReport = remember(currentBaseEntry) { currentBaseEntry.gapReport() }
    var aiModeOverride by remember(entry) { mutableStateOf<AiActionMode?>(null) }
    var isReprocessing by remember { mutableStateOf(false) }
    var reprocessPhase by remember { mutableStateOf<EntryAnalysisPhase?>(null) }
    var reprocessPartial by remember { mutableStateOf<app.chompass.services.ai.PartialFoodAnalysis?>(null) }
    var changedFields by remember { mutableStateOf<List<ReprocessDiffRow>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val constituentsEdited = editableConstituents != currentBaseEntry.constituents
    val aiActionMode = aiModeOverride ?: when {
        FillGapsFeature.ENABLED && entryGapReport.hasGaps -> AiActionMode.FILL
        currentBaseEntry.imageFilename != null -> AiActionMode.REANALYZE
        constituentsEdited -> AiActionMode.RECALCULATE
        else -> AiActionMode.REANALYZE
    }
    // Hoisted out of the AI coroutines so diff labels come from stringResource.
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

    // Reanalyze/Recalculate: run the AI and overwrite the draft fields in
    // place; Save commits, dismiss discards (same contract as the diary sheet).
    fun reprocess() {
        // Unreachable via UI when the master switch is off; belt-and-braces.
        if (!aiFeaturesEnabled) return
        scope.launch {
            isReprocessing = true
            errorText = null
            changedFields = emptyList()
            reprocessPhase = EntryAnalysisPhase.Preparing
            reprocessPartial = null
            val before = currentBaseEntry
            try {
                val editedMix = if (aiActionMode == AiActionMode.RECALCULATE) buildUpdated() else null
                val newAnalysis = onReprocess(noteText, editedMix) { progress ->
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
                currentBaseEntry = if (aiActionMode == AiActionMode.RECALCULATE) {
                    // Totals-only overwrite over the EDITED snapshot the
                    // estimate ran from. Building on the stored base would
                    // reset every edited field (name, constituents, serving,
                    // emoji) — each is remember(currentBaseEntry)-keyed.
                    buildUpdated().copy(
                        calories = newAnalysis.calories,
                        protein = newAnalysis.protein,
                        carbs = newAnalysis.carbs,
                        fat = newAnalysis.fat,
                        customNote = noteText.trim().takeIf { it.isNotEmpty() },
                    ).let { newAnalysis.toMicronutrients().applyTo(it) }
                } else {
                    currentBaseEntry.copy(
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
                }
                editableCalories = newAnalysis.calories
                editableProtein = newAnalysis.protein
                editableCarbs = newAnalysis.carbs
                editableFat = newAnalysis.fat
                editableMicros = newAnalysis.toMicronutrients()
                if (aiActionMode != AiActionMode.RECALCULATE) {
                    name = newAnalysis.name
                    servingGrams = newAnalysis.servingSizeGrams ?: 100.0
                    baseServingGrams = newAnalysis.servingSizeGrams ?: 100.0
                    editableConstituents = newAnalysis.constituents
                    constituentsExpanded = newAnalysis.constituents.isNotEmpty()
                    editableEmoji = newAnalysis.emoji
                }
                val structuralDiff = buildReprocessDiff(
                    before,
                    currentBaseEntry,
                    reprocessLabels,
                    reprocessEnergyLabel,
                    reprocessGUnit,
                ) { EnergyFormat.quantity(it, reprocessEnergyUnit) }
                changedFields = if (aiActionMode == AiActionMode.RECALCULATE) {
                    // The re-estimate rewrites every micro; show those rows too
                    // or a micro-only change looks like a no-op.
                    structuralDiff + buildMicroChangeDiff(
                        before,
                        currentBaseEntry,
                        labelFor = { context.getString(it.labelRes) },
                        unitFor = { context.getString(it.unitRes) },
                    )
                } else {
                    structuralDiff
                }
            } catch (e: Exception) {
                errorText = (e as? AiError)?.userMessage(context) ?: context.getString(R.string.edit_reprocessing_failed)
            } finally {
                isReprocessing = false
                reprocessPhase = null
                reprocessPartial = null
            }
        }
    }

    // Fill-missing run: text-only analysis whose result merges into EMPTY
    // fields only. Name, serving, note, emoji, photo are untouched; the diff
    // card shows exactly what the fill wrote.
    fun fill() {
        // Unreachable via UI when the master switch is off; belt-and-braces.
        if (!aiFeaturesEnabled) return
        scope.launch {
            isReprocessing = true
            errorText = null
            changedFields = emptyList()
            reprocessPhase = EntryAnalysisPhase.Preparing
            reprocessPartial = null
            val before = currentBaseEntry
            try {
                val newAnalysis = onFill { progress ->
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
                val merged = before.fillMissingFrom(newAnalysis)
                if (merged != before) {
                    currentBaseEntry = merged
                    editableCalories = merged.calories
                    editableProtein = merged.protein
                    editableCarbs = merged.carbs
                    editableFat = merged.fat
                    editableMicros = MicronutrientValues.from(merged)
                    editableConstituents = merged.constituents
                    constituentsExpanded = merged.constituents.isNotEmpty()
                    changedFields = buildReprocessDiff(
                        before,
                        currentBaseEntry,
                        reprocessLabels,
                        reprocessEnergyLabel,
                        reprocessGUnit,
                    ) { EnergyFormat.quantity(it, reprocessEnergyUnit) }
                }
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
            positionalThreshold = FoodReviewPositionalThreshold,
            velocityThreshold = FoodReviewVelocityThreshold,
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
                title = titleOverride ?: stringResource(R.string.favorite_edit_title),
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
                                onRemovePhoto = { editableImageFilename = null; iconPickError = null },
                            )
                        }

                        item { SheetSectionHeader(stringResource(R.string.sheet_food_details)) }
                        item {
                            SheetPillRow {
                                Text(stringResource(R.string.sheet_name), fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Spacer(Modifier.weight(1f))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
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
                        item { SheetSectionHeader(stringResource(R.string.sheet_meal)) }
                        item {
                            SheetPillRow(onClick = { mealMenuExpanded = true }) {
                                Text(stringResource(R.string.sheet_meal_type), fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                                            fontSize = 16.sp,
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

                        item { SheetSectionHeader(stringResource(R.string.sheet_serving)) }
                        item {
                            ServingQuantityCard(
                                quantityText = servingQuantityText,
                                onQuantityChange = { newValue ->
                                    servingQuantityText = newValue
                                    resolveServingDraft()
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
                            // WP2 fill-missing: unconditional slot right after the
                            // macros card (NOT inside More Nutrition — an all-null
                            // Mealie template renders no More Nutrition card at all).
                            if (FillGapsFeature.ENABLED && aiFeaturesEnabled && entryGapReport.hasGaps) {
                                MissingValuesNote(
                                    missingCount = entryGapReport.missingMacros.size +
                                        entryGapReport.missingMicros.size +
                                        entryGapReport.zeroMacroConstituents.size,
                                    onFill = {
                                        aiModeOverride = AiActionMode.FILL
                                        aiCorrectExpanded = true
                                    },
                                )
                            }
                        }

                        if (MicronutrientField.MoreNutrition.any { editableMicros[it] != null }) {
                            item {
                                SheetPillRow(onClick = { moreNutritionExpanded = !moreNutritionExpanded }) {
                                    Text(stringResource(R.string.sheet_more_nutrition), fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                                            StaleCompositionNote()
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

                        if (aiFeaturesEnabled) {
                            val showAiCorrectBody = aiCorrectExpanded ||
                                isReprocessing ||
                                changedFields.isNotEmpty() ||
                                errorText != null
                            // Cost guard (settled decision 6): the Fill pill only
                            // exists while the scan found gaps; Reanalyze /
                            // Recalculate are always available. Fill falls back to
                            // Reanalyze if gaps got filled while the override still
                            // points at Fill.
                            val aiModes = buildList {
                                if (FillGapsFeature.ENABLED && entryGapReport.hasGaps) add(AiActionMode.FILL)
                                add(AiActionMode.REANALYZE)
                                add(AiActionMode.RECALCULATE)
                            }
                            val selectedAiMode = if (aiActionMode in aiModes) aiActionMode else AiActionMode.REANALYZE
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ChompassSegmentedTabs(
                                        options = aiModes,
                                        selected = selectedAiMode,
                                        onSelect = { mode ->
                                            aiModeOverride = mode
                                            if (!showAiCorrectBody) aiCorrectExpanded = true
                                        },
                                        label = { mode ->
                                            stringResource(
                                                when (mode) {
                                                    AiActionMode.FILL -> R.string.edit_ai_mode_fill
                                                    AiActionMode.REANALYZE -> R.string.edit_ai_mode_reanalyze
                                                    AiActionMode.RECALCULATE -> R.string.edit_ai_mode_recalculate
                                                },
                                            )
                                        },
                                    )
                                    if (FillGapsFeature.ENABLED && entryGapReport.hasGaps) {
                                        val missingLabels = (
                                            entryGapReport.missingMacros.map { stringResource(it.labelRes) } +
                                                entryGapReport.missingMicros.map { stringResource(it.labelRes) }
                                            ).joinToString(", ")
                                        Text(
                                            stringResource(R.string.edit_ai_missing_line, missingLabels),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(horizontal = 18.dp),
                                        )
                                    }
                                }
                            }
                            if (showAiCorrectBody) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // The note steers only the full Reanalyze
                                        // (settled UX-1). The template's note field
                                        // already sits in its own section below, so
                                        // the diary sheet's duplicate in-block field
                                        // is not repeated here.
                                        if (aiActionMode == AiActionMode.REANALYZE && !isReprocessing) {
                                            Text(
                                                stringResource(R.string.edit_reprocess_explain),
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                                lineHeight = 18.sp,
                                            )
                                        } else if (aiActionMode != AiActionMode.REANALYZE && !isReprocessing) {
                                            Text(
                                                stringResource(
                                                    if (aiActionMode == AiActionMode.FILL) {
                                                        R.string.edit_ai_mode_fill
                                                    } else {
                                                        R.string.edit_ai_mode_recalculate
                                                    },
                                                ),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    if (aiActionMode == AiActionMode.FILL) fill() else reprocess()
                                                },
                                            )
                                        }

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
                                shape = RoundedCornerShape(AppRadii.Pill),
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
