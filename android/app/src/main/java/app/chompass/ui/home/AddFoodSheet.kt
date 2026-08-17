package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import app.chompass.services.grounding.GroundedEntryFeature
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WaterAmountFormat

private enum class AddFoodTileSize {
    Hero,
    Compact,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    onPhoto: () -> Unit,
    onNote: () -> Unit,
    onSavedRecents: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onCopyFromDay: () -> Unit,
    onManualActive: () -> Unit = {},
    onGrounded: () -> Unit = {},
    onSearch: () -> Unit = {},
    onDismiss: () -> Unit,
    aiFeaturesEnabled: Boolean = true,
    barcodeEnabled: Boolean = true,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
    recentMeals: List<FoodEntry> = emptyList(),
    onRelogRecent: (FoodEntry) -> Unit = {},
    onReviewRecent: (FoodEntry) -> Unit = {},
) {
    ChompassBottomSheet(onDismiss = onDismiss) {
        AddFoodSheetContent(
            onPhoto = { onDismiss(); onPhoto() },
            onNote = { onNote(); onDismiss() },
            onSavedRecents = { onDismiss(); onSavedRecents() },
            onVoice = { onDismiss(); onVoice() },
            onBarcode = { onDismiss(); onBarcode() },
            onManual = { onDismiss(); onManual() },
            onCopyFromDay = { onDismiss(); onCopyFromDay() },
            onManualActive = { onDismiss(); onManualActive() },
            onGrounded = { onDismiss(); onGrounded() },
            onSearch = { onDismiss(); onSearch() },
            aiFeaturesEnabled = aiFeaturesEnabled,
            barcodeEnabled = barcodeEnabled,
            waterTrackingEnabled = waterTrackingEnabled,
            waterQuickPresetsMl = waterQuickPresetsMl,
            waterUseMetric = waterUseMetric,
            onWater = { ml -> onDismiss(); onWater(ml) },
            onWaterCustom = { onDismiss(); onWaterCustom() },
            recentMeals = recentMeals,
            onRelogRecent = { entry -> onDismiss(); onRelogRecent(entry) },
            onReviewRecent = { entry -> onDismiss(); onReviewRecent(entry) },
        )
    }
}

/** Sheet body without ModalBottomSheet — used for JVM screenshot capture. */
@Composable
internal fun AddFoodSheetContent(
    onPhoto: () -> Unit = {},
    onNote: () -> Unit = {},
    onSavedRecents: () -> Unit = {},
    onVoice: () -> Unit = {},
    onBarcode: () -> Unit = {},
    onManual: () -> Unit = {},
    onCopyFromDay: () -> Unit = {},
    onManualActive: () -> Unit = {},
    onGrounded: () -> Unit = {},
    onSearch: () -> Unit = {},
    /** Codeberg #20 phase 2: false hides the AI logging tiles (photo/note/voice);
     *  barcode, search, manual, recents, copy-from-day and water all stay. */
    aiFeaturesEnabled: Boolean = true,
    barcodeEnabled: Boolean = true,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
    recentMeals: List<FoodEntry> = emptyList(),
    onRelogRecent: (FoodEntry) -> Unit = {},
    onReviewRecent: (FoodEntry) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 20.dp)
    ) {
        Text(
            stringResource(R.string.add_food_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (aiFeaturesEnabled) {
                AddFoodActionTile(
                    label = stringResource(R.string.add_food_hero_photo),
                    subtitle = stringResource(R.string.add_food_hero_photo_sub),
                    icon = Icons.Filled.PhotoCamera,
                    size = AddFoodTileSize.Hero,
                    emphasis = true,
                    modifier = Modifier.weight(1.2f),
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
            }
            AddFoodActionTile(
                label = stringResource(R.string.saved_meals_tab_recents),
                subtitle = stringResource(R.string.add_food_hero_saved_sub),
                icon = Icons.Filled.History,
                size = AddFoodTileSize.Hero,
                modifier = Modifier.weight(1f),
                onClick = onSavedRecents,
            )
        }
        if (recentMeals.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.add_food_quick_relog),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.add_food_quick_relog_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recentMeals.forEach { entry ->
                    AddFoodRelogChip(
                        entry = entry,
                        onRelog = { onRelogRecent(entry) },
                        onReview = { onReviewRecent(entry) },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.add_food_quick_relog_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
            )
        }
        Spacer(Modifier.height(16.dp))
        SheetSectionHeader(stringResource(R.string.add_food_more_section))
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (aiFeaturesEnabled) {
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_voice),
                        icon = Icons.Filled.Mic,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onVoice,
                    )
                }
                if (barcodeEnabled) {
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_barcode),
                        icon = Icons.Filled.QrCodeScanner,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onBarcode,
                    )
                }
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
            if (GroundedEntryFeature.ENABLED) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_grounded),
                        icon = Icons.Filled.Science,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onGrounded,
                    )
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_search_food),
                        icon = Icons.Filled.Search,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onSearch,
                    )
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_manual_active),
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onManualActive,
                    )
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_search_food),
                        icon = Icons.Filled.Search,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onSearch,
                    )
                    AddFoodActionTile(
                        label = stringResource(R.string.home_menu_manual_active),
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        size = AddFoodTileSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = onManualActive,
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (waterTrackingEnabled) {
            Spacer(Modifier.height(12.dp))
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
private fun AddFoodRelogChip(
    entry: FoodEntry,
    onRelog: () -> Unit,
    onReview: () -> Unit,
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(20.dp)
    val fill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val border = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Row(
        Modifier
            .clip(shape)
            .background(fill)
            .border(0.5.dp, border, shape)
            .pointerInput(entry.id.toString()) {
                detectTapGestures(
                    onTap = { onRelog() },
                    onLongPress = { onReview() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            entry.emoji ?: "🍽",
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(Modifier.widthIn(max = 140.dp)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.add_food_relog_kcal, entry.calories),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Calorie,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.cd_relog_meal, entry.name),
            tint = AppColors.Calorie,
            modifier = Modifier.size(16.dp),
        )
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
    emphasis: Boolean = false,
    onClick: () -> Unit
) {
    val isHero = size == AddFoodTileSize.Hero
    val shape = if (isHero) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val bubbleSize = when {
        isHero && emphasis -> 26.dp
        isHero -> 22.dp
        else -> 20.dp
    }
    val iconSize = when {
        isHero && emphasis -> 16.dp
        isHero -> 14.dp
        else -> 12.dp
    }
    Column(
        modifier
            .heightIn(min = if (isHero) 96.dp else 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isHero) 10.dp else 8.dp,
                vertical = if (isHero) 14.dp else 10.dp
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
