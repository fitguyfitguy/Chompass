package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MacroValueFormatter
import app.chompass.models.LocaleFormat
import app.chompass.models.MealType
import app.chompass.models.MicronutrientField
import app.chompass.models.ServingUnitOption
import app.chompass.models.UserProfile
import app.chompass.services.FoodPhotoSession
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.PartialFoodAnalysis
import app.chompass.services.ai.applyTo
import app.chompass.services.ai.toMicronutrients
import app.chompass.ui.theme.AppColors
import kotlin.math.roundToInt
import java.time.Instant
import kotlinx.coroutines.launch
import app.chompass.ui.components.rememberDecodedBitmap
import app.chompass.ui.components.kcalText
import app.chompass.ui.components.FudGlassTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Exposed for JVM tests: every SNAP_FOOD photo gets the portion correction row. */
internal fun shouldOfferPortionClarify(
    source: FoodSource,
    portionPreConfirmed: Boolean = false,
): Boolean =
    source == FoodSource.SNAP_FOOD && !portionPreConfirmed

private val EmptyFoodAnalysisPlaceholder = FoodAnalysis(
    name = "",
    calories = 0,
    protein = 0.0,
    carbs = 0.0,
    fat = 0.0,
    servingSizeGrams = null,
)

/**
 * Progressive Log sheet: opens while AI analysis runs (partial fields stream in),
 * then unlocks editing and Log when [analysisReady]. Visually shares primitives
 * with [EditFoodEntrySheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodResultSheet(
    analysis: FoodAnalysis? = null,
    imageBytes: ByteArray? = null,
    preferGramsByDefault: Boolean = false,
    profile: UserProfile? = null,
    dayEntries: List<FoodEntry> = emptyList(),
    source: FoodSource = FoodSource.TEXT_INPUT,
    portionClarifyEnabled: Boolean = false,
    /** True when the user already entered exact grams on tip strip / prior note. */
    portionPreConfirmed: Boolean = false,
    /** True when a weigh-as-you-go draft already has ingredients. */
    progressiveMealActive: Boolean = false,
    analysisPhase: EntryAnalysisPhase? = null,
    partial: PartialFoodAnalysis? = null,
    /** False while AI (or unit inference) is in flight — fields and Log stay locked. */
    analysisReady: Boolean = analysis != null,
    imageCount: Int = if (imageBytes != null) 1 else 0,
    onReprocessPortion: (suspend (portionAnswer: String) -> Unit)? = null,
    onWhatIfSuggestion: (suspend (FoodEntry) -> String)? = null,
    onReanalyzeWithTip: ((note: String?, confirmedPortionGrams: Double?) -> Unit)? = null,
    /** Add another photo for re-analyze; receives current tip note/grams to preserve. */
    onAddPhoto: ((note: String?, confirmedPortionGrams: Double?) -> Unit)? = null,
    onSave: (
        name: String,
        servingGrams: Double?,
        scale: Double,
        mealType: MealType,
        selectedServingUnit: String?,
        selectedServingQuantity: Double?,
        editedAnalysis: FoodAnalysis
    ) -> Unit,
    /**
     * Commit this review into the progressive meal draft.
     * [resumeCapture] true → reopen camera; false → show the meal sheet.
     */
    onAddToProgressiveMeal: ((
        name: String,
        servingGrams: Double?,
        scale: Double,
        mealType: MealType,
        selectedServingUnit: String?,
        selectedServingQuantity: Double?,
        editedAnalysis: FoodAnalysis,
        resumeCapture: Boolean,
    ) -> Unit)? = null,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    inferringUnits: Boolean = false,
) {
    val placeholderName = stringResource(R.string.entry_analysis_placeholder_name)
    val effectiveAnalysis = analysis
        ?: partial?.toPreviewAnalysis()
        ?: EmptyFoodAnalysisPlaceholder
    val bitmap = rememberDecodedBitmap(imageBytes)
    // Codeberg #14: hoisted so the bottom-edge sheet-drag blocker can read it.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val portionClarifyFailedMessage = stringResource(R.string.sheet_portion_clarify_failed)
    // Keyed on imageBytes (stable across a reprocess call for the same photo), not analysis
    // (a new FoodAnalysis instance arrives after reprocessing) — so answering or skipping stays
    // sticky for this entry instead of re-showing once the refined estimate lands.
    var portionChipDismissed by remember(imageBytes) { mutableStateOf(false) }
    var isReprocessingPortion by remember(imageBytes) { mutableStateOf(false) }
    var portionClarifyError by remember(imageBytes) { mutableStateOf<String?>(null) }
    val showPortionClarify = analysisReady &&
        portionClarifyEnabled &&
        !portionChipDismissed &&
        shouldOfferPortionClarify(source, portionPreConfirmed)
    var name by remember { mutableStateOf(effectiveAnalysis.name) }
    // The entry's recorded serving, if any: null means macros are absolute
    // portion totals and weight edits must not scale them (Codeberg #10 follow-up).
    val recordedServing = effectiveAnalysis.servingSizeGrams
    // var so per-entry serving edits (custom unit name / grams) persist to save.
    var servingUnitOptions by remember(effectiveAnalysis.servingUnitOptions, effectiveAnalysis.servingSizeGrams) {
        mutableStateOf(ServingUnitOption.normalizedOptions(effectiveAnalysis.servingUnitOptions, effectiveAnalysis.servingSizeGrams ?: 100.0))
    }
    val initialServingUnit = if (preferGramsByDefault) {
        ServingUnitOption.grams.unit
    } else {
        effectiveAnalysis.selectedServingUnit
    }
    var selectedServingUnitId by remember(effectiveAnalysis, servingUnitOptions, preferGramsByDefault) {
        mutableStateOf(ServingUnitOption.initialUnitId(initialServingUnit, servingUnitOptions))
    }
    var servingGrams by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.servingSizeGrams ?: 100.0) }
    var baseServingGrams by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.servingSizeGrams ?: 100.0) }
    // True once the user changed the serving (weight/quantity): on an entry
    // without a recorded serving the corrected weight then records as the new
    // serving without scaling macros (Codeberg #10 follow-up).
    var servingTouched by remember(effectiveAnalysis) { mutableStateOf(false) }
    var editableConstituents by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.constituents) }
    var constituentsExpanded by remember(effectiveAnalysis) {
        mutableStateOf(effectiveAnalysis.constituents.isNotEmpty())
    }
    var servingQuantityText by remember(effectiveAnalysis, servingUnitOptions, preferGramsByDefault) {
        mutableStateOf(
            ServingUnitOption.initialQuantityText(
                totalGrams = effectiveAnalysis.servingSizeGrams ?: 100.0,
                selectedUnitId = selectedServingUnitId,
                selectedQuantity = effectiveAnalysis.selectedServingQuantity,
                options = servingUnitOptions
            )
        )
    }
    val selectedServingOption = ServingUnitOption.optionMatching(selectedServingUnitId, servingUnitOptions)
    val selectedServingQuantity = ServingUnitOption.parseQuantity(servingQuantityText)?.takeIf { it > 0 }
    val scale = ServingUnitOption.servingScale(recordedServing, servingGrams, baseServingGrams)
    var mealType by remember { mutableStateOf(MealType.currentMeal) }
    var moreNutritionExpanded by remember { mutableStateOf(false) }
    var nutritionUnlocked by remember { mutableStateOf(false) }
    var editableCalories by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.calories) }
    var editableProtein by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.protein) }
    var editableCarbs by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.carbs) }
    var editableFat by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.fat) }
    var editableMicros by remember(effectiveAnalysis) { mutableStateOf(effectiveAnalysis.toMicronutrients()) }
    var mealMenuExpanded by remember { mutableStateOf(false) }
    var servingMenuExpanded by remember { mutableStateOf(false) }
    var tipExpanded by remember { mutableStateOf(false) }
    var tipNote by remember(imageBytes) { mutableStateOf("") }
    var tipWeightText by remember(imageBytes) { mutableStateOf("") }
    var wasReady by remember { mutableStateOf(analysisReady) }

    // Sync streamed partials / completed analysis into editable fields.
    LaunchedEffect(analysis, partial) {
        val sourceAnalysis = analysis ?: partial?.toPreviewAnalysis() ?: return@LaunchedEffect
        name = sourceAnalysis.name
        servingGrams = sourceAnalysis.servingSizeGrams ?: 100.0
        baseServingGrams = sourceAnalysis.servingSizeGrams ?: 100.0
        editableConstituents = sourceAnalysis.constituents
        editableCalories = sourceAnalysis.calories
        editableProtein = sourceAnalysis.protein
        editableCarbs = sourceAnalysis.carbs
        editableFat = sourceAnalysis.fat
        editableMicros = sourceAnalysis.toMicronutrients()
        val options = ServingUnitOption.normalizedOptions(
            sourceAnalysis.servingUnitOptions,
            sourceAnalysis.servingSizeGrams ?: 100.0,
        )
        selectedServingUnitId = ServingUnitOption.initialUnitId(
            if (preferGramsByDefault) ServingUnitOption.grams.unit else sourceAnalysis.selectedServingUnit,
            options,
        )
        servingQuantityText = ServingUnitOption.initialQuantityText(
            totalGrams = sourceAnalysis.servingSizeGrams ?: 100.0,
            selectedUnitId = selectedServingUnitId,
            selectedQuantity = sourceAnalysis.selectedServingQuantity,
            options = options,
        )
    }

    LaunchedEffect(analysisReady) {
        if (analysisReady && !wasReady) {
            nutritionUnlocked = false
            tipExpanded = false
        }
        if (!analysisReady) {
            nutritionUnlocked = false
            mealMenuExpanded = false
            servingMenuExpanded = false
        }
        wasReady = analysisReady
    }

    LaunchedEffect(effectiveAnalysis.servingUnitOptions, inferringUnits) {
        if (!inferringUnits && effectiveAnalysis.servingUnitOptions.isNotEmpty()) {
            val options = ServingUnitOption.normalizedOptions(
                effectiveAnalysis.servingUnitOptions,
                effectiveAnalysis.servingSizeGrams ?: 100.0,
            )
            if (selectedServingUnitId !in options.map { it.id }) {
                selectedServingUnitId = ServingUnitOption.initialUnitId(effectiveAnalysis.selectedServingUnit, options)
                servingQuantityText = ServingUnitOption.initialQuantityText(
                    totalGrams = servingGrams,
                    selectedUnitId = selectedServingUnitId,
                    selectedQuantity = effectiveAnalysis.selectedServingQuantity,
                    options = options,
                )
            }
        }
    }

    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val emDashText = stringResource(R.string.nutrition_em_dash)
    val math = remember(scale, emDashText) { FoodEntryEditMath(scale, emDashText) }
    val canOfferTip = analysisReady && onReanalyzeWithTip != null && imageBytes != null
    val canAddPhoto = analysisReady && onAddPhoto != null && imageCount < FoodPhotoSession.MAX_IMAGES

    fun editedAnalysis() = editableMicros.scaled(scale).applyTo(
        effectiveAnalysis.copy(
            name = name.trim().ifEmpty { effectiveAnalysis.name.ifEmpty { placeholderName } },
            calories = math.scaledInt(editableCalories),
            protein = math.scaledMacro(editableProtein),
            carbs = math.scaledMacro(editableCarbs),
            fat = math.scaledMacro(editableFat),
            servingSizeGrams = ServingUnitOption.persistedServingGrams(recordedServing, servingTouched, servingGrams),
            servingUnitOptions = servingUnitOptions,
            grounding = effectiveAnalysis.grounding?.copy(userCorrected = true),
            constituents = app.chompass.services.ai.ConstituentReconcile.scaleAll(
                editableConstituents,
                scale,
            ),
        )
    )
    fun previewEntry() = editableMicros.scaled(scale).applyTo(
        FoodEntry(
            name = name.trim().ifEmpty { effectiveAnalysis.name.ifEmpty { placeholderName } },
            calories = math.scaledInt(editableCalories),
            protein = math.scaledMacro(editableProtein),
            carbs = math.scaledMacro(editableCarbs),
            fat = math.scaledMacro(editableFat),
            timestamp = Instant.now(),
            imageFilename = null,
            emoji = effectiveAnalysis.emoji,
            source = source,
            mealType = mealType,
            servingSizeGrams = ServingUnitOption.persistedServingGrams(recordedServing, servingTouched, servingGrams),
            servingUnitOptions = servingUnitOptions,
            selectedServingUnit = if (servingUnitOptions.isEmpty()) null else selectedServingOption.unit,
            selectedServingQuantity = if (servingUnitOptions.isEmpty()) null else selectedServingQuantity,
            constituents = app.chompass.services.ai.ConstituentReconcile.scaleAll(
                editableConstituents,
                scale,
            ),
        )
    )
    var whatIfEntry by remember { mutableStateOf<FoodEntry?>(null) }

    val busyPrimaryLabel = when {
        inferringUnits -> stringResource(R.string.entry_analysis_busy_units)
        analysisPhase == EntryAnalysisPhase.CallingAi && partial?.hasAnyField == true ->
            stringResource(R.string.entry_analysis_busy_filling)
        !analysisReady -> stringResource(R.string.entry_analysis_busy_analyzing)
        isSaving -> stringResource(R.string.action_logging)
        progressiveMealActive -> stringResource(R.string.progressive_meal_add_to_meal)
        else -> stringResource(R.string.action_log)
    }

    // Codeberg #30: block top-edge drag dismissal only while the user is
    // editing typed input: focus, or name/tip-note content that differs from
    // the analysis. Read-only scrolls keep drag-from-content dismissal, but
    // the sheet's raised thresholds (maintainer decision 2026-08-18) make
    // that a deliberate pull/flick, not a scroll gesture.
    var inputFocused by remember { mutableStateOf(false) }
    val typedContentChanged = name.trim() != effectiveAnalysis.name.trim() ||
        tipNote.isNotBlank()

    ChompassBottomSheet(
        onDismiss = { if (!isSaving) onDismiss() },
        sheetState = rememberChompassSheetState(
            busy = isSaving || !analysisReady,
            // Maintainer decision 2026-08-18: stock m3 1.4 thresholds dismiss
            // on a gentle swipe (velocity 125 dp/s). Raised twice on device:
            // a read-only scroll gesture must spring back; dismissal needs a
            // ~65% pull or a firm flick. Typing is still fully protected by
            // the gate.
            positionalThreshold = 300.dp,
            velocityThreshold = 1200.dp,
        ),
        containerColor = sheetSurface,
        // Codeberg #14: zero the chrome insets — the default contentWindowInsets
        // feeds the same layout feedback loop with the footer's
        // navigationBarsPadding/imePadding as #6 did on EditFoodEntrySheet
        // (M3 consumeWindowInsets(0,0,0,max(0,offset)) -> footer padding ->
        // content re-measure -> anchors move -> offset re-based). Removing it
        // kills the coupling; the footer still pads itself from the raw insets.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        fun commitLog() {
            if (isSaving || !analysisReady || analysis == null) return
            val persistedServing = ServingUnitOption.persistedServingGrams(recordedServing, servingTouched, servingGrams)
            if (progressiveMealActive && onAddToProgressiveMeal != null) {
                onAddToProgressiveMeal(
                    name.trim().ifEmpty { analysis.name },
                    persistedServing,
                    scale,
                    mealType,
                    if (servingUnitOptions.isEmpty()) null else selectedServingOption.unit,
                    if (servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                    editedAnalysis(),
                    false,
                )
            } else {
                onSave(
                    name.trim().ifEmpty { analysis.name },
                    persistedServing,
                    scale,
                    mealType,
                    if (servingUnitOptions.isEmpty()) null else selectedServingOption.unit,
                    if (servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                    editedAnalysis()
                )
            }
        }
        fun commitAddNext() {
            if (isSaving || !analysisReady || analysis == null || onAddToProgressiveMeal == null) return
            onAddToProgressiveMeal(
                name.trim().ifEmpty { analysis.name },
                ServingUnitOption.persistedServingGrams(recordedServing, servingTouched, servingGrams),
                scale,
                mealType,
                if (servingUnitOptions.isEmpty()) null else selectedServingOption.unit,
                if (servingUnitOptions.isEmpty()) null else selectedServingQuantity,
                editedAnalysis(),
                true,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.sheet_review_food),
                onCancel = { if (!isSaving) onDismiss() },
            )

            WithoutOverscroll {
                ChompassSheetLazyColumn(
                    listState = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .onFocusChanged { inputFocused = it.isFocused },
                    // Codeberg #14: block bottom-edge overscroll vs sheet
                    // drag-to-dismiss (the layer-3 shake); the top edge is
                    // gated (Codeberg #30): blocked while editing, else
                    // drag-from-content dismissal stays (raised thresholds).
                    blockTopEdge = inputFocused || typedContentChanged,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
            // Status strip while AI runs — also announces when editing unlocks.
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!analysisReady) {
                        analysisPhase?.let { phase ->
                            EntryAnalysisStepRow(currentPhase = phase)
                            Text(
                                entryAnalysisPhaseLabel(
                                    phase,
                                    fillingFields = partial?.hasAnyField == true,
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Calorie,
                                textAlign = TextAlign.Center,
                            )
                        } ?: run {
                            if (inferringUnits) {
                                Text(
                                    stringResource(R.string.entry_analysis_inferring_units),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Calorie,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (imageBytes != null) {
                            Text(
                                stringResource(R.string.entry_analysis_status_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (!wasReady) {
                        // One-shot announce when the gate opens (wasReady flips in LaunchedEffect).
                        Text(
                            stringResource(R.string.entry_analysis_ready),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
            }

            // Compact hero so name / serving / macros fit the first viewport.
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Text(effectiveAnalysis.emoji ?: "🍽", fontSize = 40.sp)
                    }
                }
            }

            // Early streaming spinner before any validated fields arrive.
            if (!analysisReady && analysis == null && (partial == null || !partial.hasAnyField)) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = AppColors.Calorie,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            if (canOfferTip || canAddPhoto) {
                item {
                    EntryAnalysisTipStrip(
                        expanded = tipExpanded,
                        onExpandedChange = { tipExpanded = it },
                        note = tipNote,
                        onNoteChange = { tipNote = it },
                        weightText = tipWeightText,
                        onWeightChange = { tipWeightText = it },
                        canOfferTip = canOfferTip,
                        canAddPhoto = canAddPhoto,
                        onApplyTip = {
                            val grams = parsePositiveGrams(tipWeightText)
                            onReanalyzeWithTip?.invoke(
                                tipNote.takeIf { it.isNotBlank() },
                                grams,
                            )
                            tipExpanded = false
                        },
                        onAddPhoto = {
                            val grams = parsePositiveGrams(tipWeightText)
                            val tip = tipNote.takeIf { it.isNotBlank() }
                                ?: effectiveAnalysis.customNote?.takeIf { it.isNotBlank() }
                            onAddPhoto?.invoke(tip, grams)
                        },
                    )
                }
            }

            if (analysisReady || analysis != null || (partial?.hasAnyField == true)) {
            item { SheetSectionHeader(stringResource(R.string.sheet_food_details)) }
            effectiveAnalysis.grounding?.let { grounding ->
                item {
                    Text(
                        text = when (grounding.sourceKind) {
                            app.chompass.models.NutrientSourceKind.USDA ->
                                stringResource(R.string.grounding_badge_usda)
                            app.chompass.models.NutrientSourceKind.OPEN_FOOD_FACTS ->
                                stringResource(R.string.grounding_badge_off)
                            app.chompass.models.NutrientSourceKind.SWISS ->
                                stringResource(R.string.grounding_badge_swiss)
                            app.chompass.models.NutrientSourceKind.HISTORY ->
                                stringResource(R.string.grounding_badge_history)
                            app.chompass.models.NutrientSourceKind.NUTRITION_LABEL ->
                                stringResource(R.string.grounding_badge_label)
                            app.chompass.models.NutrientSourceKind.MODEL_ESTIMATE ->
                                stringResource(R.string.grounding_badge_estimate)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.Calorie,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                effectiveAnalysis.groundingConfidence?.let { conf ->
                    item {
                        Text(
                            text = stringResource(
                                R.string.grounding_confidence_summary,
                                (conf.identity * 100).toInt(),
                                (conf.portion * 100).toInt(),
                                (conf.nutrientSource * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                if (grounding.validationNotes.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.grounding_validation_notes,
                                grounding.validationNotes.joinToString(" · "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            item {
                SheetPillRow {
                    Text(stringResource(R.string.sheet_name), fontSize = 17.sp, modifier = Modifier.padding(end = 8.dp))
                    Spacer(Modifier.weight(1f))
                    androidx.compose.foundation.text.BasicTextField(
                        value = name.ifEmpty { if (!analysisReady) placeholderName else "" },
                        onValueChange = { if (analysisReady) name = it },
                        singleLine = true,
                        enabled = analysisReady,
                        readOnly = !analysisReady,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (analysisReady) 1f else 0.55f,
                            ),
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
                        if (!analysisReady) return@ServingQuantityCard
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
                        if (!analysisReady) return@ServingQuantityCard
                        selectedServingUnitId = optionId
                        val option = ServingUnitOption.optionMatching(optionId, servingUnitOptions)
                        val quantity = if (option.gramsPerUnit > 0) servingGrams / option.gramsPerUnit else servingGrams
                        servingQuantityText = ServingUnitOption.formatQuantity(quantity)
                    },
                    servingSizeGrams = servingGrams,
                    unitOptions = servingUnitOptions,
                    menuExpanded = servingMenuExpanded && analysisReady,
                    onMenuExpandedChange = { if (analysisReady) servingMenuExpanded = it },
                    gramUnit = stringResource(R.string.unit_g),
                    isLoadingUnits = inferringUnits,
                    enabled = analysisReady,
                    onUnitOptionsChange = { options, newId ->
                        servingUnitOptions = options
                        selectedServingUnitId = newId
                    },
                )
            }

            item {
                SheetSectionHeaderWithLock(
                    title = stringResource(R.string.sheet_nutrition),
                    unlocked = nutritionUnlocked && analysisReady,
                    onToggle = {
                        if (!analysisReady) return@SheetSectionHeaderWithLock
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
                        unlocked = nutritionUnlocked && analysisReady,
                        accentColor = AppColors.Calorie,
                        onEdit = { editableCalories = math.baseDoubleFromText(it).roundToInt() }
                    )
                    SheetHairline()
                    ReviewNutritionValueRow(
                        label = stringResource(R.string.nutrition_label_protein),
                        displayValue = MacroValueFormatter.string(math.scaledMacro(editableProtein)),
                        editValue = MacroValueFormatter.string(math.scaledMacro(editableProtein)),
                        unit = stringResource(R.string.unit_g),
                        unlocked = nutritionUnlocked && analysisReady,
                        accentColor = AppColors.Protein,
                        onEdit = { editableProtein = math.baseDoubleFromText(it) }
                    )
                    SheetHairline()
                    ReviewNutritionValueRow(
                        label = stringResource(R.string.nutrition_label_carbs),
                        displayValue = MacroValueFormatter.string(math.scaledMacro(editableCarbs)),
                        editValue = MacroValueFormatter.string(math.scaledMacro(editableCarbs)),
                        unit = stringResource(R.string.unit_g),
                        unlocked = nutritionUnlocked && analysisReady,
                        accentColor = AppColors.Carbs,
                        onEdit = { editableCarbs = math.baseDoubleFromText(it) }
                    )
                    SheetHairline()
                    ReviewNutritionValueRow(
                        label = stringResource(R.string.nutrition_label_fat),
                        displayValue = MacroValueFormatter.string(math.scaledMacro(editableFat)),
                        editValue = MacroValueFormatter.string(math.scaledMacro(editableFat)),
                        unit = stringResource(R.string.unit_g),
                        unlocked = nutritionUnlocked && analysisReady,
                        accentColor = AppColors.Fat,
                        onEdit = { editableFat = math.baseDoubleFromText(it) }
                    )
                    SheetHairline()
                    ReviewNutritionValueRow(
                        label = stringResource(R.string.nutrition_label_fiber),
                        displayValue = math.displayD(math.scaledD(editableMicros.fiber)),
                        editValue = math.editD(math.scaledD(editableMicros.fiber)),
                        unit = stringResource(R.string.unit_g),
                        unlocked = nutritionUnlocked && analysisReady,
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

            item { SheetSectionHeader(stringResource(R.string.sheet_meal)) }
            item {
                SheetPillRow(onClick = { if (analysisReady) mealMenuExpanded = true }) {
                    Text(stringResource(R.string.sheet_meal_type), fontSize = 17.sp, modifier = Modifier.weight(1f))
                    // Anchor the DropdownMenu inside the right-side cluster so
                    // it pops open under the value, not the row's left edge.
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
                            expanded = mealMenuExpanded && analysisReady,
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

            // Secondary: portion check, ingredients, micros, what-if — after the Log path.
            if (showPortionClarify) {
                item {
                    PortionClarifyRow(
                        estimatedGrams = effectiveAnalysis.servingSizeGrams ?: 100.0,
                        isLoading = isReprocessingPortion,
                        error = portionClarifyError,
                        showQualitativeChips = onReprocessPortion != null,
                        onApplyExactGrams = { grams ->
                            servingGrams = grams
                            servingTouched = true
                            selectedServingUnitId = ServingUnitOption.grams.unit
                            servingQuantityText = ServingUnitOption.formatQuantity(grams)
                            portionChipDismissed = true
                            portionClarifyError = null
                        },
                        onSelect = { answer ->
                            val reprocess = onReprocessPortion ?: return@PortionClarifyRow
                            scope.launch {
                                isReprocessingPortion = true
                                portionClarifyError = null
                                try {
                                    reprocess(answer)
                                    portionChipDismissed = true
                                } catch (e: Exception) {
                                    portionClarifyError = e.localizedMessage ?: portionClarifyFailedMessage
                                } finally {
                                    isReprocessingPortion = false
                                }
                            }
                        },
                        onDismiss = { portionChipDismissed = true },
                    )
                }
            }

            item {
                ConstituentsSection(
                    rows = app.chompass.services.ai.ConstituentReconcile.scaleAll(
                        editableConstituents,
                        scale,
                    ),
                    expanded = constituentsExpanded,
                    onExpandedChange = { if (analysisReady) constituentsExpanded = it },
                    onRowsChange = { displayRows ->
                        if (!analysisReady) return@ConstituentsSection
                        val (cleaned, agg, serving) = applyConstituentDisplayEdit(displayRows)
                        editableConstituents = cleaned
                        if (serving > 0) {
                            baseServingGrams = serving
                            servingGrams = serving
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

            item {
                SheetPillRow(onClick = { if (analysisReady) moreNutritionExpanded = !moreNutritionExpanded }) {
                    Text(stringResource(R.string.sheet_more_nutrition), fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Icon(
                        if (moreNutritionExpanded) Icons.Filled.KeyboardArrowDown
                        else Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            if (moreNutritionExpanded && analysisReady) {
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
                                unlocked = nutritionUnlocked && analysisReady,
                                dim = true,
                                onEdit = {
                                    editableMicros = editableMicros.with(field, math.baseOptionalFromText(it))
                                }
                            )
                        }
                    }
                }
            }

            if (onWhatIfSuggestion != null && analysisReady) {
                item {
                    SheetPillRow(onClick = { whatIfEntry = previewEntry() }) {
                        Text(stringResource(R.string.action_what_if), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            } // analysisReady || analysis != null || partial has fields
            }
            } // WithoutOverscroll

            SheetStickyPrimaryBar(
                primaryLabel = busyPrimaryLabel,
                primaryEnabled = analysisReady && !isSaving && analysis != null,
                onPrimary = { commitLog() },
                textActionLabel = if (onAddToProgressiveMeal != null && analysisReady && !isSaving) {
                    stringResource(R.string.progressive_meal_add_next)
                } else {
                    null
                },
                onTextAction = if (onAddToProgressiveMeal != null && analysisReady) {
                    { commitAddNext() }
                } else {
                    null
                },
            )
        }
    }

    whatIfEntry?.let { entry ->
        WhatIfMealImpactDialog(
            entry = entry,
            dayEntries = dayEntries,
            profile = profile,
            onDismiss = { whatIfEntry = null },
            onSuggest = onWhatIfSuggestion
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryAnalysisTipStrip(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    weightText: String,
    onWeightChange: (String) -> Unit,
    canOfferTip: Boolean,
    canAddPhoto: Boolean,
    onApplyTip: () -> Unit,
    onAddPhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canOfferTip) {
                TextButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        stringResource(R.string.entry_analysis_tip_cta),
                        color = AppColors.Calorie,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canAddPhoto) {
                TextButton(
                    onClick = onAddPhoto,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        stringResource(R.string.entry_analysis_add_photo),
                        color = AppColors.Calorie,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (expanded && canOfferTip) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.context_note_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                var showGuide by remember { mutableStateOf(false) }
                PhotoAccuracyInfoButton(onClick = { showGuide = true })
                if (showGuide) {
                    PhotoAccuracyGuideDialog(onDismiss = { showGuide = false })
                }
            }
            FudGlassTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                placeholder = stringResource(R.string.context_note_placeholder),
            )
            Text(
                stringResource(R.string.context_note_weight_section),
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FudGlassTextField(
                    value = weightText,
                    onValueChange = { onWeightChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
                    placeholder = stringResource(R.string.context_note_weight_placeholder),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.context_note_weight_unit),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            TextButton(
                onClick = onApplyTip,
                enabled = note.isNotBlank() || parsePositiveGrams(weightText) != null,
            ) {
                Text(
                    stringResource(R.string.entry_analysis_tip_apply),
                    color = AppColors.Calorie,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun SheetSectionHeaderWithLock(
    title: String,
    unlocked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (unlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                contentDescription = stringResource(
                    if (unlocked) R.string.nutrition_lock_editing
                    else R.string.nutrition_unlock_editing
                ),
                tint = if (unlocked) AppColors.Calorie
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun ReviewNutritionValueRow(
    label: String,
    displayValue: String,
    editValue: String,
    unit: String,
    unlocked: Boolean,
    dim: Boolean = false,
    accentColor: Color? = null,
    onEdit: (String) -> Unit
) {
    var draft by remember { mutableStateOf(editValue) }
    LaunchedEffect(unlocked) {
        if (unlocked) draft = editValue
    }
    val labelColor = accentColor?.let {
        if (dim) it.copy(alpha = 0.72f) else it
    } ?: if (dim) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val valueColor = accentColor ?: MaterialTheme.colorScheme.onSurface
    val cursorColor = accentColor ?: AppColors.Calorie
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
        if (unlocked) {
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onEdit(it)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = valueColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(cursorColor),
                modifier = Modifier.width(92.dp)
            )
        } else {
            Text(
                displayValue,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            unit,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(36.dp)
        )
    }
}

private data class WhatIfTotals(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    operator fun plus(other: WhatIfTotals) = WhatIfTotals(
        calories = calories + other.calories,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat
    )
}

private fun List<FoodEntry>.whatIfTotals() = WhatIfTotals(
    calories = sumOf { it.calories },
    protein = sumOf { it.protein },
    carbs = sumOf { it.carbs },
    fat = sumOf { it.fat }
)

private fun FoodEntry.whatIfTotals() = WhatIfTotals(
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat
)

@Composable
internal fun WhatIfMealImpactDialog(
    entry: FoodEntry,
    dayEntries: List<FoodEntry>,
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onSuggest: (suspend (FoodEntry) -> String)?,
    initialSuggestion: String? = null,
) {
    val before = remember(dayEntries) { dayEntries.whatIfTotals() }
    val after = remember(before, entry) { before + entry.whatIfTotals() }
    var loading by remember(entry.id) { mutableStateOf(initialSuggestion == null) }
    var suggestion by remember(entry.id) { mutableStateOf(initialSuggestion) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }

    val onboardingFallback = stringResource(R.string.finish_onboarding_hint)
    val suggestionError = stringResource(R.string.error_ai_suggestion)
    LaunchedEffect(entry.id) {
        if (initialSuggestion != null) return@LaunchedEffect
        loading = true
        suggestion = null
        error = null
        runCatching { onSuggest?.invoke(entry) ?: onboardingFallback }
            .onSuccess { suggestion = it.ifBlank { null } }
            .onFailure { error = it.localizedMessage ?: suggestionError }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                stringResource(R.string.what_if_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                // Codeberg #17: cap the content height so the dialog fits small
                // screens (M3 AlertDialog never scrolls) — the content scrolls
                // internally when the AI text is long. 60% of the screen
                // height leaves room for title + buttons on any device.
                modifier = Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.what_if_subtitle),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    lineHeight = 19.sp
                )
                SheetPillCard {
                    WhatIfImpactRow(
                        label = stringResource(R.string.nutrition_label_calories),
                        added = "+${kcalText(entry.calories)}",
                        total = profile?.let {
                            "${LocaleFormat.integer(after.calories)} / ${LocaleFormat.integer(it.effectiveCalories)} ${stringResource(R.string.unit_kcal)}"
                        } ?: kcalText(after.calories),
                        accentColor = AppColors.Calorie
                    )
                    SheetHairline()
                    WhatIfImpactRow(
                        label = stringResource(R.string.nutrition_label_protein),
                        added = "+${MacroValueFormatter.withUnit(entry.protein)}",
                        total = profile?.let { "${MacroValueFormatter.withUnit(after.protein)} / ${it.effectiveProtein}g" }
                            ?: MacroValueFormatter.withUnit(after.protein),
                        accentColor = AppColors.Protein
                    )
                    SheetHairline()
                    WhatIfImpactRow(
                        label = stringResource(R.string.nutrition_label_carbs),
                        added = "+${MacroValueFormatter.withUnit(entry.carbs)}",
                        total = profile?.let { "${MacroValueFormatter.withUnit(after.carbs)} / ${it.effectiveCarbs}g" }
                            ?: MacroValueFormatter.withUnit(after.carbs),
                        accentColor = AppColors.Carbs
                    )
                    SheetHairline()
                    WhatIfImpactRow(
                        label = stringResource(R.string.nutrition_label_fat),
                        added = "+${MacroValueFormatter.withUnit(entry.fat)}",
                        total = profile?.let { "${MacroValueFormatter.withUnit(after.fat)} / ${it.effectiveFat}g" }
                            ?: MacroValueFormatter.withUnit(after.fat),
                        accentColor = AppColors.Fat
                    )
                }

                SheetPillCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.what_if_ai_suggestion),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        )
                        if (loading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.Calorie
                                )
                                Text(
                                    stringResource(R.string.what_if_loading),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                )
                            }
                        } else {
                            Text(
                                suggestion ?: error ?: stringResource(R.string.what_if_no_suggestion),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done), color = AppColors.Calorie)
            }
        }
    )
}

@Composable
private fun WhatIfImpactRow(
    label: String,
    added: String,
    total: String,
    accentColor: Color = AppColors.Calorie,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
            Text(
                added,
                fontSize = 13.sp,
                color = accentColor.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            total,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
    }
}

/** Portion correction row (docs/UNCERTAINTY_DRIVEN_ENTRY.md bet 1).
 *  Exact grams rescales locally via [servingGrams]; qualitative chips optionally
 *  re-analyze with the answer injected as extra context. */
@Composable
private fun PortionClarifyRow(
    estimatedGrams: Double,
    isLoading: Boolean,
    error: String?,
    showQualitativeChips: Boolean,
    onApplyExactGrams: (Double) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var weightText by remember(estimatedGrams) { mutableStateOf("") }
    var localError by remember(estimatedGrams) { mutableStateOf<String?>(null) }
    val invalidWeightMessage = stringResource(R.string.sheet_portion_clarify_weight_invalid)
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.sheet_portion_clarify_prompt),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.sheet_portion_clarify_dismiss), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sheet_portion_clarify_weight_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                    localError = null
                },
                enabled = !isLoading,
                singleLine = true,
                label = { Text(stringResource(R.string.sheet_portion_clarify_weight_label)) },
                placeholder = {
                    Text(
                        if (estimatedGrams > 0) {
                            ServingUnitOption.formatQuantity(estimatedGrams)
                        } else {
                            stringResource(R.string.sheet_portion_clarify_weight_placeholder)
                        }
                    )
                },
                suffix = { Text(stringResource(R.string.unit_g)) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = false,
                enabled = !isLoading,
                onClick = {
                    val grams = parsePositiveGrams(weightText)
                    if (grams == null) {
                        localError = invalidWeightMessage
                    } else {
                        onApplyExactGrams(grams)
                    }
                },
                label = { Text(stringResource(R.string.sheet_portion_clarify_weight_apply)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Calorie.copy(alpha = 0.18f),
                    selectedLabelColor = AppColors.Calorie,
                ),
            )
        }
        if (showQualitativeChips) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.sheet_portion_clarify_small,
                    R.string.sheet_portion_clarify_regular,
                    R.string.sheet_portion_clarify_large,
                    R.string.sheet_portion_clarify_restaurant,
                ).forEach { labelRes ->
                    val label = stringResource(labelRes)
                    FilterChip(
                        selected = false,
                        enabled = !isLoading,
                        onClick = { onSelect(label) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Calorie.copy(alpha = 0.18f),
                            selectedLabelColor = AppColors.Calorie,
                        ),
                    )
                }
            }
        }
        if (isLoading) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.sheet_portion_clarify_updating),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        (localError ?: error)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Stress layout for screenshot previews: the two side-by-side result-sheet
 * actions (tip CTA + add-photo) with long localized labels must ellipsize,
 * never squeeze the second button to zero width (letter-by-letter stacking).
 */
@Composable
internal fun ResultSheetTipStripStressPreviewContent() {
    Column(Modifier.padding(12.dp)) {
        EntryAnalysisTipStrip(
            expanded = false,
            onExpandedChange = {},
            note = "",
            onNoteChange = {},
            weightText = "",
            onWeightChange = {},
            canOfferTip = true,
            canAddPhoto = true,
            onApplyTip = {},
            onAddPhoto = {},
        )
    }
}
