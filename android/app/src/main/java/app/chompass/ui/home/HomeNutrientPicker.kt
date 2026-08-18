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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeTopNutrient
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.theme.AppColors
import androidx.compose.foundation.shape.CircleShape
import app.chompass.models.MacroValueFormatter
import app.chompass.ui.components.MacroChip
import app.chompass.ui.components.isDarkTheme
import java.util.Locale

@Composable
fun HomeTopNutrientPickerDialog(
    selected: List<HomeTopNutrient>,
    cardCount: Int = HomeDisplayPreferences.DEFAULT_NUTRIENT_CARD_COUNT,
    onSave: (List<HomeTopNutrient>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(selected, cardCount) {
        mutableStateOf(HomeTopNutrient.normalized(selected, cardCount))
    }
    val maxCards = cardCount.coerceIn(
        HomeDisplayPreferences.MIN_NUTRIENT_CARD_COUNT,
        HomeDisplayPreferences.MAX_NUTRIENT_CARD_COUNT
    )

    fun toggle(nutrient: HomeTopNutrient) {
        draft = if (nutrient in draft) {
            if (draft.size <= 1) draft else draft - nutrient
        } else {
            if (draft.size >= maxCards) draft.dropLast(1) + nutrient else draft + nutrient
        }
    }

    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_nutrients), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.home_nutrients_pick_count, maxCards),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
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
                    accentColor = AppColors.nutrientColor(nutrient),
                    onClick = { toggle(nutrient) }
                )
            }
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = {
                onSave(HomeTopNutrient.normalized(draft, cardCount))
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

    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_food_log_chips), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FoodLogMacroChip.entries) { chip ->
                val kind = chip.macroKind()
                val accent = kind?.color() ?: AppColors.Calorie
                NutrientPickerRow(
                    label = chip.glyph,
                    unit = kind?.name?.lowercase(Locale.ROOT)?.replaceFirstChar { it.titlecase() } ?: "Sugar",
                    checked = chip in draft,
                    accentColor = accent,
                    onClick = { toggle(chip) }
                )
            }
        }
        FudGlassDialogActions(
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
    val shape = RoundedCornerShape(16.dp)
    val isDark = isDarkTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (checked) accentColor.copy(alpha = 0.14f)
                else if (isDark) AppColors.TranslucentSurfaceDark
                else AppColors.TranslucentSurfaceLight
            )
            .border(
                0.5.dp,
                if (checked) accentColor.copy(alpha = 0.28f)
                else if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    1.dp,
                    if (checked) accentColor.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                    RoundedCornerShape(8.dp)
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
fun FoodLogMacroChipView(chip: FoodLogMacroChip, value: Double) {
    val kind = chip.macroKind()
    if (kind != null) {
        MacroChip(kind, value)
    } else {
        val color = AppColors.Calorie
        Box(
            Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "${chip.glyph} ${MacroValueFormatter.withUnit(value)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}
