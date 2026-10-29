package app.chompass.ui.home

import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.Recipe
import app.chompass.models.RecipeIngredient
import app.chompass.models.fillMissingFrom
import app.chompass.models.gapReport
import app.chompass.services.FillGapsFeature
import app.chompass.services.ai.AiError
import app.chompass.services.ai.userMessage
import app.chompass.ui.components.MacroChip
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.energyText
import app.chompass.ui.theme.MacroKind
import kotlinx.coroutines.launch

/**
 * Bottom sheet for building/editing a [Recipe]: a name, a meal type, and an
 * ordered list of [RecipeIngredient]s each independently scalable. Ingredients
 * are added by reusing existing entry points (a Favorites picker or the
 * existing [ManualEntryDialog]) rather than a new AI/photo-analysis flow, so
 * this stays the smallest correct version of the feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBuilderSheet(
    container: AppContainer,
    recipe: Recipe,
    aiFeaturesEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (Recipe) -> Unit,
    onLogNow: (Recipe) -> Unit
) {
    val state = rememberChompassSheetState()
    var name by remember(recipe.id) { mutableStateOf(recipe.name) }
    var mealType by remember(recipe.id) { mutableStateOf(recipe.mealType) }
    var mealMenuExpanded by remember { mutableStateOf(false) }
    var ingredients by remember(recipe.id) { mutableStateOf(recipe.ingredients) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showFavoritesPicker by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }

    fun current(): Recipe = recipe.copy(name = name.trim(), mealType = mealType, ingredients = ingredients)

    val canSave = name.isNotBlank()
    val canLog = canSave && ingredients.isNotEmpty()
    val isNew = recipe.ingredients.isEmpty() && recipe.name.isBlank()

    // WP2 fill-missing: ingredients whose base (1x) macro values are gapped
    // (Mealie-style zeros — the manual-entry dialog refuses 0 kcal, so gaps
    // come from favorites-sourced or imported rows). Macro-only gate, same
    // rationale as the Home banner: per-ingredient micros would flag nearly
    // every row.
    val scope = rememberCoroutineScope()
    var fillingStep by remember { mutableStateOf<Int?>(null) }
    var fillTotal by remember { mutableStateOf(0) }
    var fillError by remember { mutableStateOf<String?>(null) }
    val gappedIngredients = remember(ingredients) {
        if (FillGapsFeature.ENABLED) ingredients.filter { it.toFillCandidate().gapReport().hasMacroGaps } else emptyList()
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Sequential text-only fill of every gapped ingredient; each result lands
    // in the ingredient list immediately (totals update live), Save commits,
    // dismiss discards. Stops at the first failure; already-filled rows stay.
    fun runFill() {
        if (fillingStep != null) return
        scope.launch {
            fillError = null
            try {
                val queue = ingredients.filter { it.toFillCandidate().gapReport().hasMacroGaps }
                fillTotal = queue.size
                for ((step, ingredient) in queue.withIndex()) {
                    fillingStep = step
                    val candidate = ingredient.toFillCandidate()
                    val analysis = container.foodAnalysis.fillEntry(candidate)
                    val merged = candidate.fillMissingFrom(analysis)
                    if (merged != candidate) {
                        ingredients = ingredients.map {
                            if (it.id == ingredient.id) it.filledFrom(merged) else it
                        }
                    }
                }
            } catch (e: Exception) {
                fillError = (e as? AiError)?.userMessage(context)
                    ?: context.getString(R.string.edit_reprocessing_failed)
            } finally {
                fillingStep = null
            }
        }
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        SheetReviewToolbar(
            title = stringResource(if (isNew) R.string.recipe_builder_title_new else R.string.recipe_builder_title_edit),
            primaryLabel = stringResource(R.string.recipe_builder_log_now),
            secondaryLabel = stringResource(R.string.recipe_builder_save),
            primaryEnabled = canLog,
            onCancel = onDismiss,
            onPrimary = { if (canLog) onLogNow(current()) },
            onSecondary = { if (canSave) onSave(current()) }
        )

        // #84-class hardening: nothing follows this list today; the weight
        // makes that explicit so a future trailing control cannot be pushed
        // past the sheet clip.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.recipe_builder_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    recipe.servingsPerRecipe?.takeIf { it > 0 }?.let { servings ->
                        Text(
                            stringResource(R.string.recipe_servings_format, formatServings(servings)),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SheetSectionHeader(stringResource(R.string.sheet_meal)) }
            item {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SheetPillRow(onClick = { mealMenuExpanded = true }) {
                        Text(stringResource(R.string.sheet_meal_type), fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    sheetMealIcon(mealType),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    mealLabel(mealType),
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
            }

            item { SheetSectionHeader(stringResource(R.string.recipe_builder_add_ingredient)) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showFavoritesPicker = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.recipe_builder_add_from_favorites)) }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showManualEntry = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.recipe_builder_add_manual)) }
                }
            }

            if (ingredients.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recipe_builder_no_ingredients),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                items(ingredients, key = { it.id }) { ingredient ->
                    IngredientRow(
                        ingredient = ingredient,
                        onScaleChange = { newScale ->
                            ingredients = ingredients.map {
                                if (it.id == ingredient.id) it.copy(quantityScale = newScale) else it
                            }
                        },
                        onRemove = {
                            ingredients = ingredients.filterNot { it.id == ingredient.id }
                        }
                    )
                }

                // WP2 fill-missing: same note family as MissingValuesNote —
                // muted scan line + primary action; Save commits, dismiss
                // discards (undo for free).
                if (aiFeaturesEnabled && gappedIngredients.isNotEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val step = fillingStep
                            if (step != null) {
                                Text(
                                    stringResource(R.string.recipe_builder_filling, step + 1, fillTotal),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    pluralStringResource(
                                        R.plurals.recipe_builder_fill_missing,
                                        gappedIngredients.size,
                                        gappedIngredients.size,
                                    ),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.edit_ai_missing_action),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { runFill() },
                                )
                            }
                            fillError?.let {
                                Text(it, color = Color.Red, fontSize = 13.sp)
                            }
                        }
                    }
                }

                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val total = current()
                        Text(
                            energyText(total.totalCalories),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MacroChip(MacroKind.PROTEIN, total.totalProtein)
                            MacroChip(MacroKind.CARBS, total.totalCarbs)
                            MacroChip(MacroKind.FAT, total.totalFat)
                        }
                    }
                }
            }
        }
    }

    if (showFavoritesPicker) {
        FavoritesIngredientPickerSheet(
            container = container,
            onPick = { entry ->
                ingredients = ingredients + RecipeIngredient.fromFoodEntry(entry)
                showFavoritesPicker = false
            },
            onDismiss = { showFavoritesPicker = false }
        )
    }

    if (showManualEntry) {
        val useSystemDateTimePickers by container.prefs.useSystemDateTimePickers.collectAsState(initial = false)
        ManualEntryDialog(
            onDismiss = { showManualEntry = false },
            useSystemDateTimePickers = useSystemDateTimePickers,
            // Recipe ingredients ignore meal/time — the log stamps its own.
            onSave = { ingredientName, kcal, p, c, f, _, _, _, _, _, _, _ ->
                ingredients = ingredients + RecipeIngredient(
                    name = ingredientName,
                    baseCalories = kcal,
                    baseProtein = p,
                    baseCarbs = c,
                    baseFat = f
                )
                showManualEntry = false
            }
        )
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredient,
    onScaleChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    val isDark = isDarkTheme()
    val rowFill = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(MaterialTheme.shapes.large)
            .background(rowFill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (ingredient.emoji != null) {
                Text(ingredient.emoji, fontSize = 20.sp)
            } else {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ingredient.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val amountUnit = listOfNotNull(ingredient.amountText, ingredient.unitText)
                .joinToString(" ")
                .takeIf { it.isNotEmpty() }
            Text(
                listOfNotNull(amountUnit, energyText(ingredient.calories)).joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = { onScaleChange((ingredient.quantityScale - 0.25).coerceAtLeast(0.25)) },
            modifier = Modifier.minimumInteractiveComponentSize().size(28.dp)
        ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_ingredient_decrease), modifier = Modifier.size(16.dp)) }

        Text(
            stringResource(R.string.recipe_builder_scale_format, ingredient.quantityScale),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        IconButton(
            onClick = { onScaleChange(ingredient.quantityScale + 0.25) },
            modifier = Modifier.minimumInteractiveComponentSize().size(28.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_ingredient_increase), modifier = Modifier.size(16.dp)) }

        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.recipe_builder_remove_ingredient),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Servings count for display: whole numbers without a trailing `.0`. */
private fun formatServings(value: Double): String {
    val asInt = value.toInt()
    return if (value == asInt.toDouble()) asInt.toString() else value.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesIngredientPickerSheet(
    container: AppContainer,
    onPick: (FoodEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val favorites by container.foodRepository.favorites.collectAsState(initial = emptyList())
    val state = rememberChompassSheetState()

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Text(
            stringResource(R.string.saved_meals_tab_favorites),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp, start = 20.dp, end = 20.dp)
        )
        if (favorites.isEmpty()) {
            Text(
                stringResource(R.string.saved_meals_no_favorites),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites, key = { it.id }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPick(entry) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(energyText(entry.calories), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
