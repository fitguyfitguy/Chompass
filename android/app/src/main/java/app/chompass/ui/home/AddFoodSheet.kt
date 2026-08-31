package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Close
import app.chompass.ui.util.clockTimePattern
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import app.chompass.R
import app.chompass.data.QuickRelogRows
import app.chompass.models.FoodEntry
import app.chompass.models.CaffeineKind
import app.chompass.models.FastingPhase
import app.chompass.models.NicotineKind
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.blockSheetDragAtScrollEdges
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.warning
import app.chompass.ui.theme.water
import app.chompass.ui.theme.caffeine
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
    onQueue: () -> Unit = {},
    queuePendingCount: Int = 0,
    onDismiss: () -> Unit,
    aiFeaturesEnabled: Boolean = true,
    barcodeEnabled: Boolean = true,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
    nicotineTrackingEnabled: Boolean = false,
    nicotineQuickKinds: List<NicotineKind> = NicotineKind.DefaultQuickKinds,
    onNicotine: (NicotineKind) -> Unit = {},
    onNicotineCustom: () -> Unit = {},
    caffeineTrackingEnabled: Boolean = false,
    caffeineQuickKinds: List<CaffeineKind> = CaffeineKind.DefaultQuickKinds,
    onCaffeine: (CaffeineKind) -> Unit = {},
    onCaffeineCustom: () -> Unit = {},
    fastingEnabled: Boolean = false,
    fastingPhase: FastingPhase = FastingPhase.IDLE,
    fastingElapsedMillis: Long = 0L,
    fastingEatingElapsedMillis: Long = 0L,
    fastingGoalHours: Int = 0,
    fastingEatHours: Int = 0,
    fastingGoalReached: Boolean = false,
    fastingAutoStarted: Boolean = false,
    fastingNextFastStartMillis: Long? = null,
    fastingNowMillis: Long = 0L,
    fastingAutoWindows: Boolean = false,
    onStartFast: () -> Unit = {},
    onStopFast: () -> Unit = {},
    relogRows: QuickRelogRows = QuickRelogRows.Empty,
    relogLoading: Boolean = false,
    onRelogRecent: (FoodEntry) -> Unit = {},
    onReviewRecent: (FoodEntry) -> Unit = {},
    logTimeOverride: LocalTime? = null,
    useSystemDateTimePickers: Boolean = false,
    onLogTimeOverride: (LocalTime?) -> Unit = {},
) {
    ChompassBottomSheet(onDismiss = onDismiss) {
        AddFoodSheetContent(
            onPhoto = { onDismiss(); onPhoto() },
            onNote = { onDismiss(); onNote() },
            onSavedRecents = { onDismiss(); onSavedRecents() },
            onVoice = { onDismiss(); onVoice() },
            onBarcode = { onDismiss(); onBarcode() },
            onManual = { onDismiss(); onManual() },
            onCopyFromDay = { onDismiss(); onCopyFromDay() },
            onManualActive = { onDismiss(); onManualActive() },
            onGrounded = { onDismiss(); onGrounded() },
            onSearch = { onDismiss(); onSearch() },
            onQueue = { onDismiss(); onQueue() },
            queuePendingCount = queuePendingCount,
            aiFeaturesEnabled = aiFeaturesEnabled,
            barcodeEnabled = barcodeEnabled,
            waterTrackingEnabled = waterTrackingEnabled,
            waterQuickPresetsMl = waterQuickPresetsMl,
            waterUseMetric = waterUseMetric,
            onWater = { ml -> onDismiss(); onWater(ml) },
            onWaterCustom = { onDismiss(); onWaterCustom() },
            nicotineTrackingEnabled = nicotineTrackingEnabled,
            nicotineQuickKinds = nicotineQuickKinds,
            onNicotine = { kind -> onDismiss(); onNicotine(kind) },
            onNicotineCustom = { onDismiss(); onNicotineCustom() },
            caffeineTrackingEnabled = caffeineTrackingEnabled,
            caffeineQuickKinds = caffeineQuickKinds,
            onCaffeine = { kind -> onDismiss(); onCaffeine(kind) },
            onCaffeineCustom = { onDismiss(); onCaffeineCustom() },
            fastingEnabled = fastingEnabled,
            fastingPhase = fastingPhase,
            fastingElapsedMillis = fastingElapsedMillis,
            fastingEatingElapsedMillis = fastingEatingElapsedMillis,
            fastingGoalHours = fastingGoalHours,
            fastingEatHours = fastingEatHours,
            fastingGoalReached = fastingGoalReached,
            fastingAutoStarted = fastingAutoStarted,
            fastingNextFastStartMillis = fastingNextFastStartMillis,
            fastingNowMillis = fastingNowMillis,
            fastingAutoWindows = fastingAutoWindows,
            onStartFast = { onDismiss(); onStartFast() },
            onStopFast = { onDismiss(); onStopFast() },
            relogRows = relogRows,
            relogLoading = relogLoading,
            onRelogRecent = { entry -> onDismiss(); onRelogRecent(entry) },
            onReviewRecent = { entry -> onDismiss(); onReviewRecent(entry) },
            logTimeOverride = logTimeOverride,
            useSystemDateTimePickers = useSystemDateTimePickers,
            onLogTimeOverride = onLogTimeOverride,
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
    /** Codeberg #53: open the analysis queue (pending badge in the label). */
    onQueue: () -> Unit = {},
    queuePendingCount: Int = 0,
    /** Codeberg #20 phase 2: false hides the AI logging tiles (photo/note/voice);
     *  barcode, search, manual, recents, copy-from-day and water all stay. */
    aiFeaturesEnabled: Boolean = true,
    barcodeEnabled: Boolean = true,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
    nicotineTrackingEnabled: Boolean = false,
    nicotineQuickKinds: List<NicotineKind> = NicotineKind.DefaultQuickKinds,
    onNicotine: (NicotineKind) -> Unit = {},
    onNicotineCustom: () -> Unit = {},
    caffeineTrackingEnabled: Boolean = false,
    caffeineQuickKinds: List<CaffeineKind> = CaffeineKind.DefaultQuickKinds,
    onCaffeine: (CaffeineKind) -> Unit = {},
    onCaffeineCustom: () -> Unit = {},
    fastingEnabled: Boolean = false,
    fastingPhase: FastingPhase = FastingPhase.IDLE,
    fastingElapsedMillis: Long = 0L,
    fastingEatingElapsedMillis: Long = 0L,
    fastingGoalHours: Int = 0,
    fastingEatHours: Int = 0,
    fastingGoalReached: Boolean = false,
    fastingAutoStarted: Boolean = false,
    fastingNextFastStartMillis: Long? = null,
    fastingNowMillis: Long = 0L,
    fastingAutoWindows: Boolean = false,
    onStartFast: () -> Unit = {},
    onStopFast: () -> Unit = {},
    relogRows: QuickRelogRows = QuickRelogRows.Empty,
    relogLoading: Boolean = false,
    onRelogRecent: (FoodEntry) -> Unit = {},
    onReviewRecent: (FoodEntry) -> Unit = {},
    logTimeOverride: LocalTime? = null,
    useSystemDateTimePickers: Boolean = false,
    onLogTimeOverride: (LocalTime?) -> Unit = {},
) {
    val context = LocalContext.current
    val timeFormatter = remember(context) {
        DateTimeFormatter.ofPattern(clockTimePattern(context))
    }
    var showLogTimePicker by remember { mutableStateOf(false) }
    val logTimeLabel = logTimeOverride?.format(timeFormatter)
        ?: stringResource(R.string.add_food_log_time_now)
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            // Device pass #2 (2026-08-24): the content outgrew the sheet on
            // shorter screens (water + nicotine + caffeine + fasting rows), so
            // the bottom rows were clipped below the sheet edge. Scrolling the
            // column keeps every row reachable; like TextInputSheet, block
            // drag-from-content dismissal (handle/scrim still dismiss).
            .blockSheetDragAtScrollEdges(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 20.dp)
    ) {
        Text(
            stringResource(R.string.add_food_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showLogTimePicker = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.add_food_log_time),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Text(
                logTimeLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Calorie,
            )
            if (logTimeOverride != null) {
                IconButton(
                    onClick = { onLogTimeOverride(null) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_clear_log_time),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
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
        Spacer(Modifier.height(16.dp))
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
                    if (aiFeaturesEnabled) {
                        AddFoodActionTile(
                            label = if (queuePendingCount > 0) {
                                stringResource(R.string.analysis_queue_tile_label_count, queuePendingCount)
                            } else {
                                stringResource(R.string.analysis_queue_tile_label)
                            },
                            icon = Icons.Filled.Schedule,
                            size = AddFoodTileSize.Compact,
                            modifier = Modifier.weight(1f),
                            onClick = onQueue,
                        )
                    }
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
                    if (aiFeaturesEnabled) {
                        AddFoodActionTile(
                            label = if (queuePendingCount > 0) {
                                stringResource(R.string.analysis_queue_tile_label_count, queuePendingCount)
                            } else {
                                stringResource(R.string.analysis_queue_tile_label)
                            },
                            icon = Icons.Filled.Schedule,
                            size = AddFoodTileSize.Compact,
                            modifier = Modifier.weight(1f),
                            onClick = onQueue,
                        )
                    }
                }
            }
        }
        if (!relogRows.isEmpty) {
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (relogRows.recents.isNotEmpty()) {
                    AddFoodRelogRow(
                        entries = relogRows.recents,
                        onRelog = onRelogRecent,
                        onReview = onReviewRecent,
                    )
                }
                if (relogRows.frequents.isNotEmpty()) {
                    AddFoodRelogRow(
                        entries = relogRows.frequents,
                        onRelog = onRelogRecent,
                        onReview = onReviewRecent,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        } else if (relogLoading) {
            Spacer(Modifier.height(22.dp))
            AddFoodRelogPlaceholder()
            Spacer(Modifier.height(14.dp))
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
        if (nicotineTrackingEnabled) {
            Spacer(Modifier.height(12.dp))
            AddFoodNicotineQuickRow(
                quickKinds = nicotineQuickKinds,
                onNicotine = onNicotine,
                onNicotineCustom = onNicotineCustom,
            )
        }
        if (caffeineTrackingEnabled) {
            Spacer(Modifier.height(12.dp))
            AddFoodCaffeineQuickRow(
                quickKinds = caffeineQuickKinds,
                onCaffeine = onCaffeine,
                onCaffeineCustom = onCaffeineCustom,
            )
        }
        if (fastingEnabled) {
            Spacer(Modifier.height(12.dp))
            FastingHubControl(
                phase = fastingPhase,
                fastHours = fastingGoalHours,
                eatHours = fastingEatHours,
                fastElapsedMillis = fastingElapsedMillis,
                eatElapsedMillis = fastingEatingElapsedMillis,
                goalReached = fastingGoalReached,
                autoStarted = fastingAutoStarted,
                nextFastStartMillis = fastingNextFastStartMillis,
                nowMillis = fastingNowMillis,
                autoMode = fastingAutoWindows,
                onStart = onStartFast,
                onStop = onStopFast,
            )
        }
    }
    if (showLogTimePicker) {
        FoodLogTimePicker(
            initialTime = logTimeOverride ?: LocalTime.now().withSecond(0).withNano(0),
            useSystem = useSystemDateTimePickers,
            onConfirm = {
                onLogTimeOverride(it)
                showLogTimePicker = false
            },
            onDismiss = { showLogTimePicker = false },
        )
    }
}

@Composable
private fun AddFoodRelogPlaceholder() {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(fill)
                            .size(width = 96.dp, height = 36.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFoodRelogRow(
    entries: List<FoodEntry>,
    onRelog: (FoodEntry) -> Unit,
    onReview: (FoodEntry) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            AddFoodRelogChip(
                entry = entry,
                onRelog = { onRelog(entry) },
                onReview = { onReview(entry) },
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
            .combinedClickable(
                // No ripple (the chip is a tinted surface, not a button) but
                // click + long-press semantics so TalkBack can activate and
                // discover the review action.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRelog,
                onLongClick = onReview,
            )
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
            tint = MaterialTheme.colorScheme.water.copy(alpha = 0.85f),
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
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.water_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
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

/**
 * Nicotine quick-log row (optional tracker): one chip per quick kind (+1) and
 * a Custom button opening [NicotineCustomCountSheet]. Mirrors the water row.
 */
@Composable
private fun AddFoodNicotineQuickRow(
    quickKinds: List<NicotineKind>,
    onNicotine: (NicotineKind) -> Unit,
    onNicotineCustom: () -> Unit,
) {
    val kinds = remember(quickKinds) { quickKinds.distinct().ifEmpty { NicotineKind.DefaultQuickKinds } }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { kind ->
            AssistChip(
                onClick = { onNicotine(kind) },
                label = {
                    Text(
                        stringResource(R.string.nicotine_quick_plus_one, stringResource(kind.labelRes)),
                        maxLines = 1,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.12f),
                ),
            )
        }
        TextButton(
            onClick = onNicotineCustom,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.nicotine_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Caffeine quick-log row (optional tracker): one chip per quick kind (+1
 * with the kind's default mg) and a Custom button opening
 * [CaffeineCustomSheet]. Mirrors the nicotine row.
 */
@Composable
private fun AddFoodCaffeineQuickRow(
    quickKinds: List<CaffeineKind>,
    onCaffeine: (CaffeineKind) -> Unit,
    onCaffeineCustom: () -> Unit,
) {
    val kinds = remember(quickKinds) { quickKinds.distinct().ifEmpty { CaffeineKind.DefaultQuickKinds } }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { kind ->
            AssistChip(
                onClick = { onCaffeine(kind) },
                label = {
                    Text(
                        stringResource(R.string.caffeine_quick_plus_one, stringResource(kind.labelRes)),
                        maxLines = 1,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.caffeine.copy(alpha = 0.12f),
                ),
            )
        }
        TextButton(
            onClick = onCaffeineCustom,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.caffeine_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
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
            .height(if (isHero) 108.dp else 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isHero) 10.dp else 8.dp,
                vertical = if (isHero) 10.dp else 8.dp
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
        Spacer(Modifier.height(if (isHero) 8.dp else 4.dp))
        if (isHero) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
