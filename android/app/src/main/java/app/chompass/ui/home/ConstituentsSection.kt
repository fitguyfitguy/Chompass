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
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.ConstituentReconcile
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.MacroKind
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
        val grams = qty * selected.gramsPerUnit
        val factor = if (row.servingSizeGrams > 0) grams / row.servingSizeGrams else 1.0
        onChange(
            row.copy(
                servingSizeGrams = grams,
                calories = (row.calories * factor).roundToInt().coerceAtLeast(0),
                protein = row.protein * factor,
                carbs = row.carbs * factor,
                fat = row.fat * factor,
                selectedServingUnit = selected.unit,
                selectedServingQuantity = qty,
            ),
        )
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
                    unitId = newId
                    onChange(
                        row.copy(
                            servingUnitOptions = options,
                            selectedServingUnit = ServingUnitOption.optionMatching(newId, options).unit,
                        ),
                    )
                },
            )
            val kcalUnit = stringResource(R.string.unit_kcal)
            val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = AppColors.Calorie, fontWeight = FontWeight.Medium)) {
                        append("${row.calories} $kcalUnit")
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
            )
        }
    }
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
