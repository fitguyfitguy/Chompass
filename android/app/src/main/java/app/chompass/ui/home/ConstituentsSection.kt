package app.chompass.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodConstituent
import app.chompass.models.MacroValueFormatter
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.OptionalNutrient
import app.chompass.models.ServingUnitOption
import app.chompass.models.OptionalNutrientGoals
import app.chompass.services.ai.ConstituentReconcile
import app.chompass.ui.components.kcalText
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.MacroKind
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Grouped editable constituent rows for a composite meal review sheet.
 * [rows] are display-space values (already scaled to the current serving).
 */
@Composable
internal fun ConstituentsSection(
    rows: List<FoodConstituent>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRowsChange: (List<FoodConstituent>) -> Unit,
    /** User's optional daily goals for the "(N%)" suffixes; null hides percents. */
    optionalGoals: OptionalNutrientGoals? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.sheet_constituents_count, rows.size),
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (!expanded) return
        rows.forEachIndexed { index, row ->
            ConstituentRowCard(
                row = row,
                optionalGoals = optionalGoals,
                onChange = { updated ->
                    onRowsChange(rows.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onRowsChange(rows.toMutableList().also { it.removeAt(index) })
                },
            )
        }
        TextButton(
            onClick = {
                onRowsChange(
                    rows + FoodConstituent(
                        name = "",
                        calories = 0,
                        protein = 0.0,
                        carbs = 0.0,
                        fat = 0.0,
                        servingSizeGrams = 50.0,
                    ),
                )
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.sheet_constituents_add))
        }
    }
}

@Composable
private fun ConstituentRowCard(
    row: FoodConstituent,
    optionalGoals: OptionalNutrientGoals?,
    onChange: (FoodConstituent) -> Unit,
    onRemove: () -> Unit,
) {
    var unitId by remember(row) {
        mutableStateOf(ServingUnitOption.initialUnitId(row.selectedServingUnit, row.servingUnitOptions))
    }
    var quantityText by remember(row, unitId) {
        mutableStateOf(
            ServingUnitOption.initialQuantityText(
                totalGrams = row.servingSizeGrams,
                selectedUnitId = unitId,
                selectedQuantity = row.selectedServingQuantity,
                options = row.servingUnitOptions,
            ),
        )
    }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    val selected = ServingUnitOption.optionMatching(unitId, row.servingUnitOptions)

    fun applyQuantity(text: String) {
        quantityText = text
        val qty = ServingUnitOption.parseQuantity(text)?.takeIf { it > 0 } ?: return
        onChange(applyConstituentQuantity(row, qty, selected))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!row.emoji.isNullOrBlank()) {
                    Text(row.emoji!!, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                }
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { onChange(row.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.sheet_name)) },
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.sheet_constituents_remove),
                    )
                }
            }
            ServingQuantityCard(
                quantityText = quantityText,
                onQuantityChange = { applyQuantity(it) },
                showQuantityCalc = false,
                selectedUnitId = unitId,
                onSelectedUnitChange = { id ->
                    unitId = id
                    val option = ServingUnitOption.optionMatching(id, row.servingUnitOptions)
                    val qty = if (option.gramsPerUnit > 0) {
                        row.servingSizeGrams / option.gramsPerUnit
                    } else {
                        row.servingSizeGrams
                    }
                    quantityText = ServingUnitOption.formatQuantity(qty)
                    onChange(
                        row.copy(
                            selectedServingUnit = option.unit,
                            selectedServingQuantity = qty,
                        ),
                    )
                },
                servingSizeGrams = row.servingSizeGrams,
                unitOptions = row.servingUnitOptions,
                menuExpanded = unitMenuExpanded,
                onMenuExpandedChange = { unitMenuExpanded = it },
                gramUnit = stringResource(R.string.unit_g),
                onUnitOptionsChange = { options, newId ->
                    val gramsBefore = row.servingSizeGrams
                    val option = ServingUnitOption.optionMatching(newId, options)
                    val qty = if (option.gramsPerUnit > 0) {
                        gramsBefore / option.gramsPerUnit
                    } else {
                        gramsBefore
                    }
                    unitId = newId
                    quantityText = ServingUnitOption.formatQuantity(qty)
                    onChange(
                        row.copy(
                            servingUnitOptions = options,
                            selectedServingUnit = option.unit,
                            selectedServingQuantity = qty,
                        ),
                    )
                },
            )
            ConstituentMacroLine(row)
            ConstituentMicrosDisclosure(row, optionalGoals)
        }
    }
}

/** kcal · P · C · F summary line shared by the editable row and the read-only ingredient row. */
@Composable
private fun ConstituentMacroLine(row: FoodConstituent, modifier: Modifier = Modifier) {
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = AppColors.Calorie, fontWeight = FontWeight.Medium)) {
                append(kcalText(row.calories))
            }
            withStyle(SpanStyle(color = separatorColor)) { append(" · ") }
            withStyle(SpanStyle(color = MacroKind.PROTEIN.color(), fontWeight = FontWeight.Medium)) {
                append("${MacroKind.PROTEIN.glyph} ${MacroValueFormatter.string(row.protein)}")
            }
            withStyle(SpanStyle(color = separatorColor)) { append(" · ") }
            withStyle(SpanStyle(color = MacroKind.CARBS.color(), fontWeight = FontWeight.Medium)) {
                append("${MacroKind.CARBS.glyph} ${MacroValueFormatter.string(row.carbs)}")
            }
            withStyle(SpanStyle(color = separatorColor)) { append(" · ") }
            withStyle(SpanStyle(color = MacroKind.FAT.color(), fontWeight = FontWeight.Medium)) {
                append("${MacroKind.FAT.glyph} ${MacroValueFormatter.string(row.fat)}")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/**
 * Expandable read-only "Detailed Nutrition" block for a constituent row (#86):
 * one line per present micro — "Label value unit (N%)" — with the percent
 * against the user's optional daily goal. Zeroed / absent goal → no percent.
 */
@Composable
internal fun ConstituentMicrosDisclosure(
    row: FoodConstituent,
    optionalGoals: OptionalNutrientGoals?,
    modifier: Modifier = Modifier,
) {
    val present = constituentMicros(row)
    if (present.isEmpty()) return
    // Deliberately not keyed on [row]: quantity/name edits rebuild the row
    // object on every keystroke and must not collapse an open disclosure
    // (rows never reorder in place, so slot identity is stable).
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.nutrition_section_detailed),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (expanded) {
            present.forEach { (field, value) ->
                val label = stringResource(field.labelRes)
                val unit = stringResource(field.unitRes)
                val percent = microGoal(field, optionalGoals)
                    ?.let { nutritionGoalPercent(value, it.toDouble()) }
                Text(
                    text = buildString {
                        append(label)
                        append(' ')
                        append(String.format(Locale.getDefault(), "%.1f", value))
                        append(' ')
                        append(unit)
                        if (percent != null) append(" ($percent%)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Read-only ingredient summary for NutritionDetailSheet's Ingredients section:
 * emoji + name + kcal, the shared macro line, then the micros block when the
 * row carries any.
 */
@Composable
internal fun ConstituentSummaryRow(
    row: FoodConstituent,
    optionalGoals: OptionalNutrientGoals?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!row.emoji.isNullOrBlank()) {
                Text(row.emoji!!, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(row.name, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(
                text = kcalText(row.calories),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie,
            )
        }
        ConstituentMacroLine(row, modifier = Modifier.padding(top = 4.dp))
        ConstituentMicrosDisclosure(row, optionalGoals)
    }
}

/** Present (non-null) micros on a constituent row, in catalog order. */
private fun constituentMicros(row: FoodConstituent): List<Pair<MicronutrientField, Double>> {
    val values = MicronutrientValues.from(row)
    return MicronutrientField.entries.mapNotNull { field -> values[field]?.let { field to it } }
}

/** Daily goal for a constituent micro; mono/poly fats have no optional goal → no percent. */
private fun microGoal(field: MicronutrientField, goals: OptionalNutrientGoals?): Int? {
    val nutrient = when (field) {
        MicronutrientField.MONOUNSATURATED_FAT, MicronutrientField.POLYUNSATURATED_FAT -> return null
        MicronutrientField.SUGAR -> OptionalNutrient.SUGAR
        MicronutrientField.ADDED_SUGAR -> OptionalNutrient.ADDED_SUGAR
        MicronutrientField.FIBER -> OptionalNutrient.FIBER
        MicronutrientField.SATURATED_FAT -> OptionalNutrient.SATURATED_FAT
        MicronutrientField.CHOLESTEROL -> OptionalNutrient.CHOLESTEROL
        MicronutrientField.SODIUM -> OptionalNutrient.SODIUM
        MicronutrientField.POTASSIUM -> OptionalNutrient.POTASSIUM
        MicronutrientField.TRANS_FAT -> OptionalNutrient.TRANS_FAT
        MicronutrientField.CALCIUM -> OptionalNutrient.CALCIUM
        MicronutrientField.IRON -> OptionalNutrient.IRON
        MicronutrientField.MAGNESIUM -> OptionalNutrient.MAGNESIUM
        MicronutrientField.ZINC -> OptionalNutrient.ZINC
        MicronutrientField.VITAMIN_A -> OptionalNutrient.VITAMIN_A
        MicronutrientField.VITAMIN_C -> OptionalNutrient.VITAMIN_C
        MicronutrientField.VITAMIN_D -> OptionalNutrient.VITAMIN_D
        MicronutrientField.VITAMIN_B12 -> OptionalNutrient.VITAMIN_B12
        MicronutrientField.VITAMIN_E -> OptionalNutrient.VITAMIN_E
        MicronutrientField.VITAMIN_K -> OptionalNutrient.VITAMIN_K
        MicronutrientField.FOLATE -> OptionalNutrient.FOLATE
        MicronutrientField.OMEGA3 -> OptionalNutrient.OMEGA3
        MicronutrientField.CAFFEINE -> OptionalNutrient.CAFFEINE
    }
    return goals?.valueFor(nutrient)
}

/**
 * Quantity-edit scaling for a constituent row: grams, macros, AND micros all
 * follow the grams factor ([FoodConstituent.microsScaled]) — the same mass
 * semantics as every other scaling path (#86 review fix: micros used to stay
 * stale here while macros scaled).
 */
internal fun applyConstituentQuantity(
    row: FoodConstituent,
    qty: Double,
    option: ServingUnitOption,
): FoodConstituent {
    val grams = qty * option.gramsPerUnit
    val factor = if (row.servingSizeGrams > 0) grams / row.servingSizeGrams else 1.0
    return row.copy(
        servingSizeGrams = grams,
        calories = (row.calories * factor).roundToInt().coerceAtLeast(0),
        protein = row.protein * factor,
        carbs = row.carbs * factor,
        fat = row.fat * factor,
        selectedServingUnit = option.unit,
        selectedServingQuantity = qty,
    ).microsScaled(factor)
}

/** Apply display-space constituent edits: rebase bases and recompute meal totals. */
internal fun applyConstituentDisplayEdit(
    displayRows: List<FoodConstituent>,
): Triple<List<FoodConstituent>, ConstituentReconcile.Aggregate?, Double> {
    val cleaned = displayRows.filter { it.name.isNotBlank() || it.servingSizeGrams > 0 }
    val agg = ConstituentReconcile.aggregatesFrom(
        cleaned.filter { it.name.isNotBlank() && it.servingSizeGrams > 0 },
    )
    val serving = agg?.servingSizeGrams ?: cleaned.sumOf { it.servingSizeGrams }
    return Triple(cleaned, agg, serving)
}

internal data class ConstituentEditCommit(
    val bases: List<FoodConstituent>,
    val baseAggregate: ConstituentReconcile.Aggregate?,
    val displaySum: Double,
    val baseSum: Double,
)

internal fun commitConstituentDisplayEdit(
    displayRows: List<FoodConstituent>,
    scale: Double,
): ConstituentEditCommit {
    val (cleaned, _, displaySum) = applyConstituentDisplayEdit(displayRows)
    val bases = if (scale == 0.0 || kotlin.math.abs(scale - 1.0) < 1e-9) {
        cleaned
    } else {
        ConstituentReconcile.scaleAll(cleaned, 1.0 / scale)
    }
    val named = bases.filter { it.name.isNotBlank() && it.servingSizeGrams > 0 }
    val baseAgg = ConstituentReconcile.aggregatesFrom(named)
    val baseSum = baseAgg?.servingSizeGrams ?: bases.sumOf { it.servingSizeGrams }
    return ConstituentEditCommit(bases, baseAgg, displaySum, baseSum)
}

