package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.theme.nutrientAccentColor
import androidx.compose.foundation.shape.CircleShape
import app.chompass.models.MacroValueFormatter
import app.chompass.ui.components.isDarkTheme

@Composable
fun HomeTopNutrientPickerDialog(
    selected: List<HomeTopNutrient>,
    onSave: (List<HomeTopNutrient>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(selected) {
        mutableStateOf(HomeTopNutrient.normalized(selected))
    }
    val maxCards = HomeDisplayPreferences.MAX_NUTRIENT_CARD_COUNT
    var flashMax by remember { mutableStateOf(false) }
    LaunchedEffect(flashMax) {
        if (flashMax) {
            kotlinx.coroutines.delay(600)
            flashMax = false
        }
    }

    fun toggle(nutrient: HomeTopNutrient) {
        draft = when {
            nutrient in draft -> if (draft.size <= 1) draft else draft - nutrient
            draft.size >= maxCards -> {
                // 5th pick is refused: flash the cap line so the limit reads
                // as intentional instead of a dead tap.
                flashMax = true
                draft
            }
            else -> draft + nutrient
        }
    }

    ChompassDialog(onDismissRequest = onDismiss) {
        Text(
            if (flashMax) stringResource(R.string.home_nutrients_pick_max, maxCards)
            else stringResource(R.string.home_nutrients_pick_count, maxCards),
            fontSize = 14.sp,
            color = if (flashMax) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HomeTopNutrient.values().toList()) { nutrient ->
                NutrientPickerRow(
                    label = stringResource(nutrient.displayNameRes),
                    unit = stringResource(nutrient.unitRes),
                    checked = nutrient in draft,
                    accentColor = nutrientAccentColor(nutrient),
                    onClick = { toggle(nutrient) }
                )
            }
        }
        ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = {
                onSave(HomeTopNutrient.normalized(draft))
                onDismiss()
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}

@Composable
fun FoodLogMacroChipPickerDialog(
    selected: List<FoodLogMacroChip>,
    onSave: (List<FoodLogMacroChip>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(selected) { mutableStateOf(FoodLogMacroChip.normalized(selected)) }

    fun toggle(chip: FoodLogMacroChip) {
        draft = if (chip in draft) {
            if (draft.size <= 1) draft else draft - chip
        } else {
            draft + chip
        }
    }

    ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_food_log_chips), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FoodLogMacroChip.entries) { chip ->
                val kind = chip.macroKind()
                val accent = kind?.color() ?: MaterialTheme.colorScheme.primary
                NutrientPickerRow(
                    label = stringResource(chip.kindLabelRes()),
                    unit = stringResource(chip.glyphRes()),
                    checked = chip in draft,
                    accentColor = accent,
                    onClick = { toggle(chip) }
                )
            }
        }
        ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = {
                onSave(FoodLogMacroChip.normalized(draft))
                onDismiss()
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun NutrientPickerRow(
    label: String,
    unit: String,
    checked: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val isDark = isDarkTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (checked) accentColor.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
            )
            .border(
                0.5.dp,
                if (checked) accentColor.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (checked) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    1.dp,
                    if (checked) accentColor.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                    MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                unit,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FoodLogMacroChipView(chip: FoodLogMacroChip, value: Double, dense: Boolean = false) {
    val color = chip.macroKind()?.color() ?: MaterialTheme.colorScheme.primary
    val glyph = stringResource(chip.glyphRes())
    val valueText = MacroValueFormatter.string(value)
    val unit = stringResource(R.string.unit_g)
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(
                horizontal = if (dense) 6.dp else 8.dp,
                vertical = if (dense) 2.dp else 3.dp,
            )
    ) {
        Text(
            buildAnnotatedString {
                append("$glyph $valueText")
                if (dense) {
                    addStyle(
                        SpanStyle(color = color.copy(alpha = 0.6f)),
                        "$glyph $valueText".length,
                        "$glyph $valueText$unit".length,
                    )
                }
                append(unit)
            },
            fontSize = if (dense) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
