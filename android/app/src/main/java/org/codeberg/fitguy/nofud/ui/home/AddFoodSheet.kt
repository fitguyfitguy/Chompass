package org.codeberg.fitguy.nofud.ui.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.ui.components.FudIconBubble
import org.codeberg.fitguy.nofud.ui.components.NoFudBottomSheet
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.models.WaterQuickPresets
import org.codeberg.fitguy.nofud.models.WaterAmountFormat

private enum class AddFoodTileSize {
    Hero,
    Compact,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    onPhoto: () -> Unit,
    onNote: () -> Unit,
    onSaved: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onCopyFromDay: () -> Unit,
    onDismiss: () -> Unit,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
) {
    NoFudBottomSheet(onDismiss = onDismiss) {
        AddFoodSheetContent(
            onPhoto = { onDismiss(); onPhoto() },
            onNote = { onNote(); onDismiss() },
            onSaved = { onDismiss(); onSaved() },
            onVoice = { onDismiss(); onVoice() },
            onBarcode = { onDismiss(); onBarcode() },
            onManual = { onDismiss(); onManual() },
            onCopyFromDay = { onDismiss(); onCopyFromDay() },
            waterTrackingEnabled = waterTrackingEnabled,
            waterQuickPresetsMl = waterQuickPresetsMl,
            waterUseMetric = waterUseMetric,
            onWater = { ml -> onDismiss(); onWater(ml) },
            onWaterCustom = { onDismiss(); onWaterCustom() },
        )
    }
}

/** Sheet body without ModalBottomSheet — used for JVM screenshot capture. */
@Composable
internal fun AddFoodSheetContent(
    onPhoto: () -> Unit = {},
    onNote: () -> Unit = {},
    onSaved: () -> Unit = {},
    onVoice: () -> Unit = {},
    onBarcode: () -> Unit = {},
    onManual: () -> Unit = {},
    onCopyFromDay: () -> Unit = {},
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp)
    ) {
        Text(
            stringResource(R.string.add_food_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AddFoodActionTile(
                label = stringResource(R.string.add_food_hero_photo),
                subtitle = stringResource(R.string.add_food_hero_photo_sub),
                icon = Icons.Filled.PhotoCamera,
                size = AddFoodTileSize.Hero,
                modifier = Modifier.weight(1f),
                onClick = onPhoto,
            )
            AddFoodActionTile(
                label = stringResource(R.string.add_food_hero_note),
                subtitle = stringResource(R.string.add_food_hero_note_sub),
                icon = Icons.Filled.Edit,
                size = AddFoodTileSize.Hero,
                modifier = Modifier.weight(1f),
                onClick = onNote,
            )
            AddFoodActionTile(
                label = stringResource(R.string.add_food_hero_saved),
                subtitle = stringResource(R.string.add_food_hero_saved_sub),
                icon = Icons.Filled.Bookmark,
                size = AddFoodTileSize.Hero,
                modifier = Modifier.weight(1f),
                onClick = onSaved,
            )
        }
        Spacer(Modifier.height(20.dp))
        SheetSectionHeader(stringResource(R.string.add_food_more_section))
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AddFoodActionTile(
                    label = stringResource(R.string.home_menu_voice),
                    icon = Icons.Filled.Mic,
                    size = AddFoodTileSize.Compact,
                    modifier = Modifier.weight(1f),
                    onClick = onVoice,
                )
                AddFoodActionTile(
                    label = stringResource(R.string.home_menu_barcode),
                    icon = Icons.Filled.QrCodeScanner,
                    size = AddFoodTileSize.Compact,
                    modifier = Modifier.weight(1f),
                    onClick = onBarcode,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AddFoodActionTile(
                    label = stringResource(R.string.home_menu_manual_entry),
                    icon = Icons.Filled.DriveFileRenameOutline,
                    size = AddFoodTileSize.Compact,
                    modifier = Modifier.weight(1f),
                    onClick = onManual,
                )
                AddFoodActionTile(
                    label = stringResource(R.string.home_menu_copy_from_day),
                    icon = Icons.Filled.CalendarMonth,
                    size = AddFoodTileSize.Compact,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyFromDay,
                )
            }
        }
        if (waterTrackingEnabled) {
            Spacer(Modifier.height(14.dp))
            AddFoodWaterQuickRow(
                presetsMl = waterQuickPresetsMl,
                useMetric = waterUseMetric,
                onWater = onWater,
                onWaterCustom = onWaterCustom,
            )
        }
    }
}

@Composable
private fun AddFoodWaterQuickRow(
    presetsMl: List<Int>,
    useMetric: Boolean,
    onWater: (Int) -> Unit,
    onWaterCustom: () -> Unit,
) {
    val presets = remember(presetsMl) {
        WaterQuickPresets(presetsMl).validatedOrDefault().amountsMl
    }
    var selectedIndex by remember(presets) {
        mutableIntStateOf((presets.size / 2).coerceAtMost(presets.lastIndex).coerceAtLeast(0))
    }
    LaunchedEffect(presets) {
        selectedIndex = selectedIndex.coerceIn(0, presets.lastIndex)
    }
    val selectedMl = presets[selectedIndex]

    SheetSectionHeader(stringResource(R.string.add_food_water_section))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.WaterDrop,
            contentDescription = null,
            tint = AppColors.Calorie.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
        if (presets.size > 1) {
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { selectedIndex = it.roundToInt().coerceIn(0, presets.lastIndex) },
                valueRange = 0f..presets.lastIndex.toFloat(),
                steps = (presets.size - 2).coerceAtLeast(0),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.Calorie,
                    activeTrackColor = AppColors.Calorie.copy(alpha = 0.75f),
                    inactiveTrackColor = AppColors.Calorie.copy(alpha = 0.16f),
                ),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            waterAmountLabel(selectedMl, useMetric),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        IconButton(
            onClick = { onWater(selectedMl) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_log_water),
                tint = AppColors.Calorie,
                modifier = Modifier.size(22.dp),
            )
        }
        TextButton(
            onClick = onWaterCustom,
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.widthIn(max = 64.dp),
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.water_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun waterAmountLabel(ml: Int, useMetric: Boolean): String =
    if (useMetric) {
        stringResource(R.string.water_amount_ml, ml)
    } else {
        stringResource(R.string.water_amount_fl_oz, WaterAmountFormat.flOzFromMl(ml))
    }

/** Home with a static add-food sheet overlay for release screenshots (no ModalBottomSheet). */
@Composable
internal fun HomeAddFoodScreenshotContent(
    ui: HomeUiState,
    weekStartsOnMonday: Boolean = true,
) {
    Box(Modifier.fillMaxSize()) {
        HomeScreenPreviewContent(ui = ui, weekStartsOnMonday = weekStartsOnMonday)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            AddFoodSheetContent()
        }
    }
}

@Composable
private fun AddFoodActionTile(
    label: String,
    icon: ImageVector,
    size: AddFoodTileSize,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val isHero = size == AddFoodTileSize.Hero
    val shape = if (isHero) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val bubbleSize = if (isHero) 22.dp else 20.dp
    val iconSize = if (isHero) 14.dp else 12.dp
    Column(
        modifier
            .heightIn(min = if (isHero) 96.dp else 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isHero) 10.dp else 8.dp,
                vertical = if (isHero) 14.dp else 12.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FudIconBubble(
            icon = icon,
            size = bubbleSize,
            iconSize = iconSize,
            tint = AppColors.Calorie
        )
        Spacer(Modifier.height(if (isHero) 8.dp else 6.dp))
        Text(
            label,
            style = if (isHero) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = if (isHero) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (isHero) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (isHero && !subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
