package org.codeberg.fitguy.nofud.ui.home

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
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.MacroValueFormatter
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.models.ServingUnitOption
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialog
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialogActions
import org.codeberg.fitguy.nofud.ui.components.FudGlassPrimaryButton
import org.codeberg.fitguy.nofud.ui.components.FudGlassSurface
import org.codeberg.fitguy.nofud.ui.components.FudGlassTextField
import org.codeberg.fitguy.nofud.ui.theme.AppColors

// ── Dialogs (unchanged styling polish) ──────────────────────────────

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
private fun rememberDecodedBitmap(bytes: ByteArray?): android.graphics.Bitmap? {
    val state = produceState<android.graphics.Bitmap?>(initialValue = null, key1 = bytes) {
        value = if (bytes == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }
    return state.value
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
    analysis: org.codeberg.fitguy.nofud.services.ai.FoodAnalysis,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text("${analysis.emoji ?: "🍽"}  ${analysis.name}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${analysis.calories} kcal", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AppColors.Calorie)
                MacroLine(stringResource(R.string.macro_protein_format, MacroValueFormatter.withUnit(analysis.protein)), AppColors.Protein)
                MacroLine(stringResource(R.string.macro_carbs_format, MacroValueFormatter.withUnit(analysis.carbs)), AppColors.Carbs)
                MacroLine(stringResource(R.string.macro_fat_format, MacroValueFormatter.withUnit(analysis.fat)), AppColors.Fat)
                if (analysis.fiber != null || analysis.sugar != null || analysis.sodium != null) {
                    Spacer(Modifier.height(2.dp))
                    analysis.fiber?.let { MacroLine(stringResource(R.string.nutrient_fiber_format, it.toString()), AppColors.Fiber, fontSize = 12.sp) }
                    analysis.sugar?.let { Text(stringResource(R.string.nutrient_sugar_format, it.toString()), fontSize = 12.sp) }
                    analysis.saturatedFat?.let { Text(stringResource(R.string.nutrient_sat_fat_format, it.toString()), fontSize = 12.sp) }
                    analysis.sodium?.let { Text(stringResource(R.string.nutrient_sodium_format, it.toString()), fontSize = 12.sp) }
                    analysis.potassium?.let { Text(stringResource(R.string.nutrient_potassium_format, it.toString()), fontSize = 12.sp) }
                    analysis.cholesterol?.let { Text(stringResource(R.string.nutrient_cholesterol_format, it.toString()), fontSize = 12.sp) }
                }
                Text(
                    stringResource(R.string.home_serving_format, analysis.servingSizeGrams.toInt()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
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
    onSave: (name: String, calories: Int, protein: Double, carbs: Double, fat: Double, mealType: MealType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
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
