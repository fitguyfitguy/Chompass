package org.codeberg.fitguy.nofud.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodLogMacroChip
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.ui.components.FudGlassSurface
import org.codeberg.fitguy.nofud.ui.components.rememberFoodThumbnail
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.MacroKind
import org.codeberg.fitguy.nofud.ui.util.clockTimePattern
import java.time.ZoneId
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
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
internal fun MealSectionHeader(
    meal: MealType,
    totalCalories: Int? = null,
    totalProtein: Double = 0.0,
    totalCarbs: Double = 0.0,
    totalFat: Double = 0.0,
    macroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
) {
    // iOS layout: small dim icon + sentence-case label, regular weight ~17sp.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            mealIcon(meal),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(meal.displayNameRes),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
        // Combined nutrients for this meal (issue #103: chicken + pasta + sauce = one total)
        if (totalCalories != null) {
            Spacer(Modifier.weight(1f))
            val summary = buildAnnotatedString {
                append("$totalCalories kcal")
                if (macroChips.isNotEmpty()) {
                    append(" · ")
                    macroChips.forEachIndexed { index, chip ->
                        if (index > 0) append(' ')
                        val value = when (chip) {
                            FoodLogMacroChip.PROTEIN -> totalProtein.roundToInt()
                            FoodLogMacroChip.CARBS -> totalCarbs.roundToInt()
                            FoodLogMacroChip.FAT -> totalFat.roundToInt()
                            else -> 0
                        }
                        val color = chip.macroKind()?.color() ?: AppColors.Calorie
                        withStyle(SpanStyle(color = color)) { append("${value}${chip.glyph}") }
                    }
                }
            }
            Text(
                summary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
internal fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    FudGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
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
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
            Text(
                text = selectedCount.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShare, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = stringResource(R.string.cd_share_meal),
                    tint = if (selectedCount > 0) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

internal data class FoodLogMealGroup(
    val id: String,
    val meal: MealType,
    val entries: List<FoodEntry>
) {
    // Combined nutrients for this meal group (issue #103: chicken + pasta + sauce = one total).
    val totalCalories: Int get() = entries.sumOf { it.calories }
    val totalProtein: Double get() = entries.sumOf { it.protein }
    val totalCarbs: Double get() = entries.sumOf { it.carbs }
    val totalFat: Double get() = entries.sumOf { it.fat }
}

internal fun foodLogMealGroups(
    entries: List<FoodEntry>,
    sortOrder: FoodLogSortOrder
): List<FoodLogMealGroup> = when (sortOrder) {
    FoodLogSortOrder.STANDARD -> {
        val grouped = entries.groupBy { it.mealType }
        listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK, MealType.OTHER)
            .mapNotNull { meal ->
                val mealEntries = grouped[meal].orEmpty()
                if (mealEntries.isEmpty()) null else FoodLogMealGroup(
                    id = "standard-${meal.name}",
                    meal = meal,
                    entries = mealEntries
                )
            }
    }
    FoodLogSortOrder.LATEST_MEALS_FIRST -> latestMealRuns(entries)
}

private fun latestMealRuns(entries: List<FoodEntry>): List<FoodLogMealGroup> {
    val sortedEntries = entries.sortedByDescending { it.timestamp }
    val groups = mutableListOf<FoodLogMealGroup>()
    var currentMeal: MealType? = null
    val currentEntries = mutableListOf<FoodEntry>()

    fun appendCurrentGroup() {
        val meal = currentMeal ?: return
        if (currentEntries.isEmpty()) return
        groups += FoodLogMealGroup(
            id = "latest-${groups.size}-${meal.name}-${currentEntries.first().id}",
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

private fun mealIcon(meal: MealType): ImageVector = when (meal) {
    MealType.BREAKFAST -> Icons.Filled.WbTwilight
    MealType.LUNCH -> Icons.Filled.WbSunny
    MealType.DINNER -> Icons.Filled.Bedtime
    MealType.SNACK -> Icons.Filled.Coffee
    MealType.OTHER -> Icons.Filled.Restaurant
}

internal fun sectionCardShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    // 22dp corners on the meal card matches the softer iOS look (was 14dp).
    return when {
        isFirst && isLast -> RoundedCornerShape(22.dp)
        isFirst -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        isLast -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
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
@Composable
internal fun SwipeableFoodRow(
    entry: FoodEntry,
    isFavorite: Boolean,
    rowShape: RoundedCornerShape,
    macroChips: List<FoodLogMacroChip>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val density = LocalDensity.current
    val favoriteTriggerPx = with(density) { 150.dp.toPx() }
    val deleteTriggerPx = with(density) { 280.dp.toPx() }
    var offsetPx by remember(entry.id) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val maxSwipePx = with(density) { maxWidth.toPx() * 0.72f }
        Box(Modifier.fillMaxWidth()) {
            SwipeBackground(offsetPx = offsetPx, isFavorite = isFavorite)
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
            ) {
                FoodRow(entry = entry, isFavorite = isFavorite, rowShape = rowShape, macroChips = macroChips)
            }
        }
    }
}

@Composable
private fun BoxScope.SwipeBackground(offsetPx: Float, isFavorite: Boolean) {
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
            AppColors.Calorie,
            if (isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
            if (isFavorite) stringResource(R.string.home_swipe_unfavorite) else stringResource(R.string.home_swipe_favorite)
        )
    }
    // iOS Mail-style trailing reveal: paint only the area the foreground has
    // moved out of, pinned to the matching edge. Width = absolute offset.
    val widthPx = kotlin.math.abs(offsetPx)
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    val alignment = if (offsetPx < 0f) Alignment.CenterEnd else Alignment.CenterStart

    Box(Modifier.matchParentSize()) {
        Box(
            Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(widthDp)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            if (widthPx > 24f) {
                Icon(icon, contentDescription = label, tint = Color.White)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
internal fun FoodRow(
    entry: FoodEntry,
    isFavorite: Boolean = false,
    rowShape: RoundedCornerShape = RoundedCornerShape(22.dp),
    isSelected: Boolean = false,
    macroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ctx = LocalContext.current
    val timeFmt = DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.US).withZone(ZoneId.systemDefault())
    val container = (ctx.applicationContext as? org.codeberg.fitguy.nofud.NoFUDApp)?.container
    val bitmap = rememberFoodThumbnail(entry.imageFilename, container?.imageStore)
    // iOS layout: large 76dp square thumb · column with (Name + heart on left,
    // time on right) · pink kcal · serving · macro tag pills row.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isSelected) AppColors.Calorie.copy(alpha = if (isDark) 0.25f else 0.12f)
                else if (isDark) AppColors.TranslucentSurfaceDark
                else AppColors.TranslucentSurfaceLight
            )
            .border(
                if (isSelected) 1.dp else 0.5.dp,
                if (isSelected) AppColors.Calorie.copy(alpha = 0.65f)
                else if (isDark) AppColors.HairlineBorderDark
                else AppColors.HairlineBorderLight,
                rowShape
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = entry.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
                entry.emoji != null -> Text(entry.emoji ?: "", fontSize = 36.sp)
                else -> Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(
            Modifier.weight(1f).padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        maxLines = 2,
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
                Text(
                    timeFmt.format(entry.timestamp).lowercase(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            // Pink kcal · gray serving size.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${entry.calories} kcal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                entry.servingSizeGrams?.takeIf { it > 0 }?.let { grams ->
                    Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    val gramsText = if (grams == grams.toInt().toDouble()) "${grams.toInt()}g"
                                    else String.format("%.1fg", grams)
                    Text(
                        gramsText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Macro pills (P / C / F) — tinted dark capsules with gray text.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                macroChips.forEach { chip ->
                    FoodLogMacroChipView(chip, chip.valueFrom(entry))
                }
            }
        }
    }
}
