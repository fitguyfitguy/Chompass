package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.ServingUnitOption
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.components.rememberFoodThumbnail
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.energyText
import app.chompass.ui.components.gramsText
import app.chompass.ui.components.culinaryUnitLabels
import app.chompass.ui.util.clockTimePattern
import app.chompass.ui.theme.AppSpacing
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.semantics.Role
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ── Section headers / cards / rows ──────────────────────────────────

@Composable
internal fun SectionHeader(title: String) {
    // iOS Section header in .insetGrouped List renders the title in sentence case
    // (no uppercase transform), bold, ~22sp on the iOS calorie/food page. Match that.
    Text(
        title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = AppSpacing.Xxl, top = AppSpacing.Md, bottom = AppSpacing.Sm)
    )
}

@Composable
internal fun MealSectionHeader(
    meal: String,
    totalCalories: Int? = null,
    totalProtein: Double = 0.0,
    totalCarbs: Double = 0.0,
    totalFat: Double = 0.0,
    totalFiber: Double = 0.0,
    totalSugar: Double = 0.0,
    macroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
    onClick: (() -> Unit)? = null,
) {
    // iOS layout: small dim icon + sentence-case label, regular weight ~17sp.
    val nutritionCd = stringResource(R.string.nutrition_details_title)
    val calories = totalCalories
    val summaryOnOwnLine = calories != null && macroChips.size >= 4
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = nutritionCd, onClick = onClick)
                } else {
                    Modifier
                },
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 24.dp, top = 12.dp, bottom = if (summaryOnOwnLine) 0.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                sheetMealIcon(meal),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                mealLabel(meal),
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Combined nutrients for this meal (issue #103: chicken + pasta + sauce = one total)
            if (calories != null && !summaryOnOwnLine) {
                Text(
                    mealMacroSummary(calories, macroChips, totalProtein, totalCarbs, totalFat, totalFiber, totalSugar),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (calories != null && macroChips.size >= 4) {
            Text(
                mealMacroSummary(calories, macroChips, totalProtein, totalCarbs, totalFat, totalFiber, totalSugar),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, end = 24.dp, bottom = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun mealMacroSummary(
    totalCalories: Int,
    macroChips: List<FoodLogMacroChip>,
    totalProtein: Double,
    totalCarbs: Double,
    totalFat: Double,
    totalFiber: Double,
    totalSugar: Double,
): androidx.compose.ui.text.AnnotatedString {
    val chipGlyphs = macroChips.associateWith { stringResource(it.glyphRes()) }
    return buildAnnotatedString {
        append(energyText(totalCalories))
        if (macroChips.isNotEmpty()) {
            append(" · ")
            macroChips.forEachIndexed { index, chip ->
                if (index > 0) append(' ')
                val value = chip.valueFromMeal(
                    totalProtein,
                    totalCarbs,
                    totalFat,
                    totalFiber,
                    totalSugar,
                ).roundToInt()
                val color = chip.macroKind()?.color() ?: MaterialTheme.colorScheme.primary
                withStyle(SpanStyle(color = color)) { append("${value}${chipGlyphs[chip]}") }
            }
        }
    }
}

@Composable
internal fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChompassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        padding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = selectedCount.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_entries),
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onShare, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.cd_share_meal),
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

internal data class FoodLogMealGroup(
    val id: String,
    val meal: String,
    val entries: List<FoodEntry>
) {
    // Combined nutrients for this meal group (issue #103: chicken + pasta + sauce = one total).
    // Computed once at construction — per-access sumOf re-ran six list scans
    // on every recomposition of every meal header.
    val totalCalories: Int = entries.sumOf { it.calories }
    val totalProtein: Double = entries.sumOf { it.protein }
    val totalCarbs: Double = entries.sumOf { it.carbs }
    val totalFat: Double = entries.sumOf { it.fat }
    val totalFiber: Double = entries.sumOf { it.fiber ?: 0.0 }
    val totalSugar: Double = entries.sumOf { it.sugar ?: 0.0 }
}

/**
 * Groups diary rows for the home food log.
 *
 * - [FoodLogSortOrder.STANDARD]: meal slots Breakfast → Lunch → Dinner → Snack → Other,
 *   with oldest entries first inside each slot (latest meals last).
 * - [FoodLogSortOrder.LATEST_MEALS_FIRST]: contiguous meal-type runs after sorting by
 *   timestamp descending (newest meal group and newest items within a run first).
 *
 * Day entries from [app.chompass.data.FoodRepository.entriesForDate] arrive latest-first;
 * STANDARD must re-sort within each meal so the two modes stay opposites, not mixed.
 */
internal fun foodLogMealGroups(
    entries: List<FoodEntry>,
    sortOrder: FoodLogSortOrder
): List<FoodLogMealGroup> = when (sortOrder) {
    FoodLogSortOrder.STANDARD -> {
        val grouped = entries.groupBy { it.mealType }
        val order = app.chompass.models.CurrentMealCatalog.value.displayOrderIds()
        val known = order.mapNotNull { meal ->
                val mealEntries = grouped[meal].orEmpty()
                    .sortedWith(compareBy<FoodEntry> { it.timestamp }.thenBy { it.id })
                if (mealEntries.isEmpty()) null else FoodLogMealGroup(
                    id = "standard-$meal",
                    meal = meal,
                    entries = mealEntries
                )
            }
        val orphans = grouped.keys.filter { it !in order }.sorted().mapNotNull { meal ->
            val mealEntries = grouped[meal].orEmpty()
                .sortedWith(compareBy<FoodEntry> { it.timestamp }.thenBy { it.id })
            if (mealEntries.isEmpty()) null else FoodLogMealGroup(
                id = "standard-$meal",
                meal = meal,
                entries = mealEntries,
            )
        }
        known + orphans
    }
    FoodLogSortOrder.LATEST_MEALS_FIRST -> latestMealRuns(entries)
}

private fun latestMealRuns(entries: List<FoodEntry>): List<FoodLogMealGroup> {
    val sortedEntries = entries.sortedWith(
        compareByDescending<FoodEntry> { it.timestamp }.thenByDescending { it.id }
    )
    val groups = mutableListOf<FoodLogMealGroup>()
    var currentMeal: String? = null
    val currentEntries = mutableListOf<FoodEntry>()

    fun appendCurrentGroup() {
        val meal = currentMeal ?: return
        if (currentEntries.isEmpty()) return
        groups += FoodLogMealGroup(
            // Stable identity: anchored on the run's meal and newest entry id
            // (Codeberg #56). The old scheme embedded the run index
            // (`groups.size`), so ANY add/edit/delete renamed every run after
            // the change point and the LazyColumn tore down and re-parented
            // headers/rows on every meal-type edit — the exact reshuffle that
            // could drop a card from the composition on a slow device.
            // Unaffected runs now keep their key; a single entry moving between
            // meals only renames the source run (if it loses its newest entry)
            // and the destination run (if the moved entry becomes its newest).
            id = "latest-${meal}-${currentEntries.first().id}",
            meal = meal,
            entries = currentEntries.toList()
        )
    }

    for (entry in sortedEntries) {
        if (entry.mealType == currentMeal) {
            currentEntries += entry
        } else {
            appendCurrentGroup()
            currentMeal = entry.mealType
            currentEntries.clear()
            currentEntries += entry
        }
    }

    appendCurrentGroup()
    return groups
}

internal fun sectionCardShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    // Grouped-list rounding: the meal card sits at the nested M3 large slot (16dp).
    return when {
        isFirst && isLast -> RoundedCornerShape(16.dp)
        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(0.dp)
    }
}

@Composable
internal fun SectionCardWrapper(
    isFirst: Boolean,
    isLast: Boolean,
    transparent: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = sectionCardShape(isFirst, isLast)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)
    ) { content() }
}

@Composable
internal fun Divider() {
    Box(
        Modifier
            .padding(start = 102.dp, end = 14.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}

/** Shared Planned label for diary rows and the week-plan canvas. */
@Composable
internal fun DiaryPlannedChip() {
    Text(
        stringResource(R.string.diary_planned).uppercase(Locale.getDefault()),
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Swipe-to-action wrapper around FoodRow.
 *
 * - Swipe right-to-left (trailing) past threshold → delete (mirrors iOS swipeActions
 *   trailing destructive button).
 * - Swipe left-to-right (leading) past threshold → toggle favorite (mirrors iOS
 *   .swipeActions secondary heart button).
 * - Tap → open EditFoodEntrySheet (matches iOS .onTapGesture).
 *
 * The dismiss state is reset on a no-confirm swing-back so partial swipes don't
 * leave the row stuck mid-flight when the user releases short of the threshold.
 */
private class SwipeAction(val label: String, val action: () -> Unit)

@Composable
internal fun SwipeableFoodRow(
    entry: FoodEntry,
    isFavorite: Boolean,
    rowShape: RoundedCornerShape,
    macroChips: List<FoodLogMacroChip>,
    compact: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    /** Meal planning mode: planned rows show the chip and the Log-now action. */
    planningMode: Boolean = false,
    onLogNow: ((FoodEntry) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var offsetPx by remember(entry.id) { mutableFloatStateOf(0f) }
    val deleteLabel = stringResource(R.string.home_swipe_delete)
    val favoriteLabel = stringResource(
        if (isFavorite) R.string.home_swipe_unfavorite else R.string.home_swipe_favorite
    )
    val logNowLabel = stringResource(R.string.entry_log_now)

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val rowWidthPx = with(density) { maxWidth.toPx() }
        val maxSwipePx = rowWidthPx * 0.72f
        // Triggers are fractions of the row width so they stay reachable inside
        // maxSwipePx on every screen size (a fixed dp threshold can exceed the clamp
        // on narrow phones and make the action impossible to fire).
        val favoriteTriggerPx = rowWidthPx * 0.30f
        val deleteTriggerPx = rowWidthPx * 0.55f
        Box(Modifier.fillMaxWidth()) {
            SwipeBackground(offsetPx = offsetPx, isFavorite = isFavorite, rowShape = rowShape)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
                    .pointerInput(entry.id, maxSwipePx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetPx = (offsetPx + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                            },
                            onDragEnd = {
                                val finalOffset = offsetPx
                                offsetPx = 0f
                                when {
                                    finalOffset <= -deleteTriggerPx -> onDelete()
                                    finalOffset >= favoriteTriggerPx -> onToggleFavorite()
                                }
                            },
                            onDragCancel = {
                                offsetPx = 0f
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = onLongPress
                    )
                    .semantics {
                        // TalkBack fallback for the swipe gestures (UI-audit 2.7):
                        // expose delete/favorite as custom actions so screen-reader
                        // users can trigger them without swiping.
                        val actions = buildList {
                            add(SwipeAction(deleteLabel, onDelete))
                            add(SwipeAction(favoriteLabel, onToggleFavorite))
                            if (planningMode && entry.planned && onLogNow != null) {
                                add(SwipeAction(logNowLabel) { onLogNow(entry) })
                            }
                        }
                        customActions = actions.map { action ->
                            CustomAccessibilityAction(action.label) {
                                action.action()
                                true
                            }
                        }
                    }
            ) {
                FoodRow(
                    entry = entry,
                    isFavorite = isFavorite,
                    rowShape = rowShape,
                    macroChips = macroChips,
                    compact = compact,
                    planningMode = planningMode,
                    onLogNow = onLogNow,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SwipeBackground(
    offsetPx: Float,
    isFavorite: Boolean,
    rowShape: RoundedCornerShape,
) {
    if (offsetPx == 0f) {
        Box(Modifier.matchParentSize())
        return
    }
    val (bg, icon, label) = if (offsetPx < 0f) {
        Triple(
            MaterialTheme.colorScheme.error,
            Icons.Filled.Delete,
            stringResource(R.string.home_swipe_delete)
        )
    } else {
        Triple(
            MaterialTheme.colorScheme.primary,
            if (isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
            if (isFavorite) stringResource(R.string.home_swipe_unfavorite) else stringResource(R.string.home_swipe_favorite)
        )
    }
    // iOS Mail-style trailing reveal, painted as the card's own background:
    // one full-row silhouette in the row's shape, the icon centered in the
    // strip the foreground has slid off of (maintainer finding M1 2026-10-08:
    // a separate reveal-width tile read as a second card next to the row).
    val widthPx = kotlin.math.abs(offsetPx)
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    val alignment = if (offsetPx < 0f) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        Modifier
            .matchParentSize()
            .background(bg, rowShape)
    ) {
        Box(
            Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(widthDp),
            contentAlignment = Alignment.Center
        ) {
            if (widthPx > 24f) {
                Icon(icon, contentDescription = label, tint = Color.White)
            }
        }
    }
}

@Composable
internal fun FoodRow(
    entry: FoodEntry,
    isFavorite: Boolean = false,
    rowShape: RoundedCornerShape = RoundedCornerShape(16.dp),
    isSelected: Boolean = false,
    macroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
    compact: Boolean = false,
    /** Meal planning mode: planned rows show the chip and the Log-now action. */
    planningMode: Boolean = false,
    onLogNow: ((FoodEntry) -> Unit)? = null,
) {
    val isDark = isDarkTheme()
    val ctx = LocalContext.current
    val timeFmt = remember(ctx) {
        DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val container = (ctx.applicationContext as? app.chompass.ChompassApp)?.container
    val bitmap = rememberFoodThumbnail(entry.imageFilename, container?.imageStore)
    // iOS layout: large 76dp square thumb · column with (Name + heart on left,
    // time on right) · pink kcal · serving · macro tag pills row.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
            )
            .border(
                if (isSelected) 1.dp else 0.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.outlineVariant,
                rowShape
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Box(
            Modifier
                .size(if (compact) 57.dp else 76.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = entry.name,
                    contentScale = if (entry.source == FoodSource.BARCODE) {
                        androidx.compose.ui.layout.ContentScale.Fit
                    } else {
                        androidx.compose.ui.layout.ContentScale.Crop
                    },
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                )
                entry.emoji != null -> Text(entry.emoji ?: "", fontSize = if (compact) 26.sp else 36.sp)
                else -> Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 22.dp else 28.dp)
                )
            }
        }

        Column(
            Modifier.weight(1f).padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
        ) {
            // Name (+ heart) on the left, time on the top-right.
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        entry.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.cd_favorited),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (planningMode && entry.planned) {
                        DiaryPlannedChip()
                    }
                    Text(
                        timeFmt.format(entry.timestamp).lowercase(Locale.getDefault()),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (planningMode && entry.planned && onLogNow != null) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.entry_log_now),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(16.dp)
                                .clickable { onLogNow(entry) },
                        )
                    }
                }
            }

            // Pink kcal · gray serving size. Compact macros sit on this same
            // line, content-sized, pinned to the row's trailing edge. Chips
            // measure first (unweighted); the serving echo gets whatever width
            // is left and fades out at the tail, so 3 chips never wrap.
            val echo = ServingUnitOption.homeDisplaySelection(
                entry.selectedServingUnit,
                entry.selectedServingQuantity,
                entry.servingSizeGrams?.takeIf { it > 0 },
                entry.servingUnitOptions,
            )
            val servingText = echo?.let { (qty, option) ->
                "${ServingUnitOption.formatQuantity(qty)} " + option.displayUnit(
                    qty,
                    stringResource(R.string.unit_serving),
                    stringResource(R.string.unit_serving_plural),
                    stringResource(R.string.unit_package),
                    stringResource(R.string.unit_package_plural),
                    culinaryUnitLabels(),
                )
            } ?: entry.servingSizeGrams?.takeIf { it > 0 }?.let { gramsText(it) }
            var servingLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    energyText(entry.calories),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
                if (servingText != null) {
                    Text("·", color = MaterialTheme.colorScheme.outline)
                    Text(
                        servingText,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val fadePx = 18.dp.toPx()
                                if (servingLayout?.hasVisualOverflow == true && size.width > fadePx) {
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startX = size.width - fadePx,
                                            endX = size.width,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            },
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onTextLayout = { servingLayout = it },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (compact && macroChips.size in 1..3) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        macroChips.forEach { chip ->
                            FoodLogMacroChipView(chip, chip.valueFrom(entry))
                        }
                    }
                }
            }

            if (compact && macroChips.size >= 4) {
                FoodLogMacroChipLine(macroChips, entry, spacedBy = 4.dp)
            }

            if (!compact && macroChips.isNotEmpty()) {
                FoodLogMacroChipLine(macroChips, entry, spacedBy = 6.dp)
            }
        }
    }
}

/** Classic chips, content-sized and pinned to the trailing edge of the row. */
@Composable
private fun FoodLogMacroChipLine(
    chips: List<FoodLogMacroChip>,
    entry: FoodEntry,
    spacedBy: androidx.compose.ui.unit.Dp,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacedBy, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { chip ->
            FoodLogMacroChipView(chip, chip.valueFrom(entry), dense = chips.size > 3)
        }
    }
}

/** Static swipe-reveal layout for release screenshot previews (no gesture state). */
@Composable
internal fun SwipeRevealScreenshotContent() {
    val entry = FoodEntry(
        name = "Greek yogurt with berries",
        calories = 280,
        protein = 22.0,
        carbs = 32.0,
        fat = 6.0,
        timestamp = Instant.parse("2026-06-15T12:00:00Z"),
        source = FoodSource.MANUAL,
        emoji = "\uD83E\uDD63",
    )
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Single-row group: all four card corners rounded — delete reveal.
        SwipeRevealRow(entry, offsetPx = -400f, isFavorite = false, isFirst = true, isLast = true)
        // First row of a group (top corners rounded) — delete reveal.
        SwipeRevealRow(entry, offsetPx = -320f, isFavorite = false, isFirst = true, isLast = false)
        // Last row of a group (bottom corners rounded) — favorite reveal.
        SwipeRevealRow(entry, offsetPx = 320f, isFavorite = false, isFirst = false, isLast = true)
    }
}

/** Mirrors the SwipeableFoodRow structure (SectionCardWrapper clip + offset card). */
@Composable
private fun SwipeRevealRow(
    entry: FoodEntry,
    offsetPx: Float,
    isFavorite: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val rowShape = sectionCardShape(isFirst, isLast)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(rowShape)
    ) {
        SwipeBackground(offsetPx = offsetPx, isFavorite = isFavorite, rowShape = rowShape)
        Box(Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) }) {
            FoodRow(entry = entry, isFavorite = isFavorite, rowShape = rowShape)
        }
    }
}
