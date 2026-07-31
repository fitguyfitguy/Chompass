package app.chompass.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.MacroValueFormatter
import app.chompass.models.MealType
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.PartialFoodAnalysis
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.theme.AppColors
import app.chompass.ui.components.rememberDecodedBitmap

// ── Dialogs (unchanged styling polish) ──────────────────────────────

@Composable
internal fun EntryAnalysisOverlay(
    phase: EntryAnalysisPhase,
    preview: FoodAnalysis? = null,
    partial: PartialFoodAnalysis? = null,
    imageBytes: ByteArray? = null,
) {
    val bitmap = rememberDecodedBitmap(imageBytes)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Icon(
                    Icons.Filled.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }

            EntryAnalysisStepRow(currentPhase = phase)

            val phaseLabel = when (phase) {
                EntryAnalysisPhase.Preparing -> stringResource(R.string.entry_analysis_phase_preparing)
                EntryAnalysisPhase.LookingUpBarcode -> stringResource(R.string.entry_analysis_phase_looking_up_barcode)
                EntryAnalysisPhase.CallingAi -> {
                    if (partial?.hasAnyField == true) {
                        stringResource(R.string.entry_analysis_phase_filling_fields)
                    } else {
                        stringResource(R.string.entry_analysis_phase_calling_ai)
                    }
                }
                EntryAnalysisPhase.Parsing -> stringResource(R.string.entry_analysis_phase_parsing)
                EntryAnalysisPhase.Recognizing -> stringResource(R.string.entry_analysis_phase_recognizing)
                EntryAnalysisPhase.SearchingHistory -> stringResource(R.string.entry_analysis_phase_searching_history)
                EntryAnalysisPhase.SearchingUsda -> stringResource(R.string.entry_analysis_phase_searching_usda)
                EntryAnalysisPhase.Resolving -> stringResource(R.string.entry_analysis_phase_resolving)
            }
            Text(
                text = phaseLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            when {
                partial != null && partial.hasAnyField -> {
                    ProgressiveAnalysisCard(partial = partial)
                }
                preview != null -> {
                    AnalysisPreviewCard(analysis = preview)
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            color = AppColors.Calorie,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        if (phase == EntryAnalysisPhase.CallingAi) {
                            Text(
                                stringResource(R.string.entry_analysis_waiting_response),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryAnalysisStepRow(currentPhase: EntryAnalysisPhase) {
    val phases = phasesForOverlay(currentPhase)
    val currentIndex = phases.indexOf(currentPhase).coerceAtLeast(0)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        phases.forEachIndexed { index, step ->
            val done = index < currentIndex
            val active = index == currentIndex
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> AppColors.Calorie.copy(alpha = 0.85f)
                            active -> AppColors.Calorie.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                } else if (active) {
                    CircularProgressIndicator(
                        color = AppColors.Calorie,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (index < phases.lastIndex) {
                Box(
                    Modifier
                        .size(width = 12.dp, height = 2.dp)
                        .background(
                            if (index < currentIndex) AppColors.Calorie.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                )
            }
        }
    }
}

private fun phasesForOverlay(current: EntryAnalysisPhase): List<EntryAnalysisPhase> =
    when (current) {
        EntryAnalysisPhase.Recognizing,
        EntryAnalysisPhase.SearchingHistory,
        EntryAnalysisPhase.SearchingUsda,
        EntryAnalysisPhase.Resolving -> listOf(
            EntryAnalysisPhase.Recognizing,
            EntryAnalysisPhase.SearchingHistory,
            EntryAnalysisPhase.SearchingUsda,
            EntryAnalysisPhase.Resolving,
        )
        else -> listOf(
            EntryAnalysisPhase.Preparing,
            EntryAnalysisPhase.LookingUpBarcode,
            EntryAnalysisPhase.CallingAi,
            EntryAnalysisPhase.Parsing,
        )
    }

@Composable
internal fun ProgressiveAnalysisCard(
    partial: PartialFoodAnalysis,
    animate: Boolean = true,
) {
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val nameAlpha by animateFloatAsState(
                targetValue = if (partial.name != null) 1f else 0.35f,
                animationSpec = spring(stiffness = if (animate) Spring.StiffnessMediumLow else Spring.StiffnessHigh),
                label = "nameAlpha",
            )
            Text(
                "${partial.emoji ?: "🍽"}  ${partial.name ?: "····"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = nameAlpha),
                modifier = Modifier.graphicsLayer { alpha = nameAlpha },
            )
            val calAlpha by animateFloatAsState(
                targetValue = if (partial.calories != null) 1f else 0.35f,
                animationSpec = spring(stiffness = if (animate) Spring.StiffnessMediumLow else Spring.StiffnessHigh),
                label = "calAlpha",
            )
            Text(
                partial.calories?.let { "$it kcal" } ?: "··· kcal",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Calorie.copy(alpha = calAlpha),
            )
            ProgressiveMacroLine(
                label = stringResource(R.string.nutrition_label_protein),
                value = partial.protein,
                color = AppColors.Protein,
                animate = animate,
            )
            ProgressiveMacroLine(
                label = stringResource(R.string.nutrition_label_carbs),
                value = partial.carbs,
                color = AppColors.Carbs,
                animate = animate,
            )
            ProgressiveMacroLine(
                label = stringResource(R.string.nutrition_label_fat),
                value = partial.fat,
                color = AppColors.Fat,
                animate = animate,
            )
            Text(
                partial.servingSizeGrams?.let {
                    stringResource(R.string.home_serving_format, it.toInt())
                } ?: stringResource(R.string.entry_analysis_field_pending),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (partial.servingSizeGrams != null) 0.55f else 0.28f
                ),
            )
            if (partial.micronutrientCount > 0) {
                Text(
                    stringResource(R.string.entry_analysis_micros_found, partial.micronutrientCount),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            if (partial.hasUnitOptions) {
                Text(
                    stringResource(R.string.entry_analysis_units_found),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            if (partial.streaming) {
                Text(
                    stringResource(R.string.entry_analysis_streaming_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun ProgressiveMacroLine(
    label: String,
    value: Double?,
    color: Color,
    animate: Boolean,
) {
    val alpha by animateFloatAsState(
        targetValue = if (value != null) 1f else 0.35f,
        animationSpec = spring(stiffness = if (animate) Spring.StiffnessMediumLow else Spring.StiffnessHigh),
        label = "macroAlpha-$label",
    )
    Text(
        text = if (value != null) {
            "$label ${MacroValueFormatter.withUnit(value)}"
        } else {
            "$label ···"
        },
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = color.copy(alpha = alpha),
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    )
}

@Composable
internal fun AnalysisPreviewCard(analysis: FoodAnalysis) {
    ProgressiveAnalysisCard(
        partial = PartialFoodAnalysis.fromComplete(analysis, streaming = false),
        animate = true,
    )
}

@Composable
internal fun AnalyzingOverlay(imageBytes: ByteArray? = null) {
    // Verbatim port of ios/calorietracker/Views/AnalyzingView.swift:
    //   VStack { (image | text.magnifyingglass) → ProgressView(.large) → "Analyzing your food..." }
    //   filling the screen, opaque background, calorie-pink accents.
    val bitmap = rememberDecodedBitmap(imageBytes)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Icon(
                    Icons.Filled.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
            CircularProgressIndicator(
                color = AppColors.Calorie,
                strokeWidth = 4.dp,
                modifier = Modifier.size(40.dp)
            )
            // iOS uses two different copies depending on the input mode — photo flows
            // say "Analyzing your food..." while text/voice flows say
            // "Looking up nutrition..." (see ContentView.swift cases .analyzing /
            // .analyzingText). pendingImageBytes is the discriminator.
            Text(
                if (bitmap != null) stringResource(R.string.home_analyzing_food) else stringResource(R.string.home_looking_up_nutrition),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie
            )
        }
    }
}

@Composable
internal fun CameraPairTransitionOverlay() {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "cameraPairTransitionScale"
    )

    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        FudGlassSurface(
            modifier = Modifier
                .width(250.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            cornerRadius = 28.dp,
            padding = 22.dp,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(AppColors.CalorieGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    stringResource(R.string.home_first_photo_saved),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.home_take_second_shot),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
        }
    }
}

@Composable
internal fun AnalysisResultDialog(
    analysis: app.chompass.services.ai.FoodAnalysis,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    FudGlassDialog(onDismissRequest = onDismiss) {
        AnalysisPreviewCard(analysis = analysis)
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_save),
            onPrimary = onSave,
            dismissText = stringResource(R.string.action_discard),
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun ManualEntryDialog(
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, calories: Int, protein: Double, carbs: Double, fat: Double, fiber: Double?, mealType: MealType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealType.currentMeal) }
    var mealMenuExpanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && calories.toIntOrNull() != null && !isSaving

    FudGlassDialog(onDismissRequest = onDismiss) {
                Text(stringResource(R.string.manual_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

                FudGlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.manual_name_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.manual_calories), calories, { calories = it.filter(Char::isDigit) }, Modifier.weight(1f), accentColor = AppColors.Calorie)
                    NumberField(stringResource(R.string.manual_protein), protein, { protein = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Protein)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.manual_carbs), carbs, { carbs = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Carbs)
                    NumberField(stringResource(R.string.manual_fat), fat, { fat = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Fat)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.manual_fiber), fiber, { fiber = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Fiber)
                }

                // Meal Type — DropdownMenu styled to match the FoodResultSheet /
                // EditFoodEntrySheet meal pickers (icon + label, pink, anchored
                // to the right cluster).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { mealMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.sheet_meal_type), fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                sheetMealIcon(mealType),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(mealType.displayNameRes),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
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

                FudGlassPrimaryButton(
                    text = if (isSaving) {
                        stringResource(R.string.action_logging)
                    } else {
                        stringResource(R.string.action_save)
                    },
                    onClick = {
                        if (!isSaving) {
                            onSave(
                                name.trim(),
                                calories.toIntOrNull() ?: 0,
                                ServingUnitOption.parseQuantity(protein) ?: 0.0,
                                ServingUnitOption.parseQuantity(carbs) ?: 0.0,
                                ServingUnitOption.parseQuantity(fat) ?: 0.0,
                                fiber.trim().takeIf { it.isNotEmpty() }?.let { ServingUnitOption.parseQuantity(it) },
                                mealType
                            )
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    accentColor: Color = AppColors.Calorie,
) {
    FudGlassTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number
        ),
        accentColor = accentColor,
        modifier = modifier
    )
}

private fun filterDecimalInput(value: String): String =
    value.filter { it.isDigit() || it == '.' || it == ',' }

@Composable
private fun MacroLine(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 16.sp) {
    Text(text, fontSize = fontSize, color = color, fontWeight = FontWeight.Medium)
}
