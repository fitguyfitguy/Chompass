package app.chompass.ui.home

import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.res.stringResource
import app.chompass.R
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.data.FrequentFoodGroup
import app.chompass.models.FoodEntry
import app.chompass.models.Recipe
import app.chompass.services.FoodImageStore
import app.chompass.ui.components.MacroChip
import app.chompass.ui.components.kcalText
import app.chompass.ui.components.rememberFoodThumbnail
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.MacroKind
import app.chompass.ui.theme.AppRadii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class SavedTab { RECENTS, FREQUENT, FAVORITES, RECIPES }

/**
 * Verbatim port of `RecentsView` in
 * ios/calorietracker/Views/RecentsView.swift.
 *
 * Layout:
 *   - "Saved Meals" navigationTitle (Title Case, inline)
 *   - segmented Picker: Recents / Frequent / Favorites (pink-tinted selection)
 *   - per segment: List of `SavedMealRow` (56dp thumb · name + heart · pink kcal +
 *     optional subtitle · 3 macro tag pills · trailing plus.circle.fill log button)
 *   - per-segment empty state: 32sp pink-tinted icon + secondary message text
 *
 * Favorites segment additionally supports:
 *   - swipe-left to unfavorite
 *   - long-press the drag handle and slide vertically to reorder (mirrors iOS
 *     EditButton + .onMove). Drag delta is converted to an index offset using
 *     a fixed row pitch — favorites lists are short so an estimated pitch is
 *     accurate enough.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealsSheet(
    container: AppContainer,
    initialTab: SavedTab? = null,
    onDismiss: () -> Unit,
    onRelogEntry: (FoodEntry) -> Unit,
    onLogEntry: (FoodEntry) -> Unit,
    onLogRecipe: (Recipe) -> Unit = {},
    onEditRecipe: (Recipe) -> Unit = {},
    onCreateRecipe: () -> Unit = {}
) {
    val state = rememberChompassSheetState()
    val scope = rememberCoroutineScope()

    // Restore the last-selected segment from DataStore so reopening the sheet
    // remembers whether the user was on Recents / Frequent / Favorites — same
    // as iOS @AppStorage("lastRecentsSegment") in RecentsView.swift.
    val savedSegment by container.prefs.lastSavedMealsSegment.collectAsState(initial = SavedTab.RECENTS.name)
    var tab by remember(initialTab, savedSegment) {
        mutableStateOf(
            initialTab ?: runCatching { SavedTab.valueOf(savedSegment) }.getOrDefault(SavedTab.RECENTS)
        )
    }
    var recents by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }
    var frequent by remember { mutableStateOf<List<FrequentFoodGroup>>(emptyList()) }
    // All-time diary collapse for search — lets queries surface foods older
    // than the 30/90-day recents/frequent windows (and re-logging them keeps
    // the original name, so the identity merges instead of a "Name (2)").
    var historyTemplates by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }

    // Favorites are a reactive Flow now (ordered list of FoodEntry copies),
    // so the UI updates as soon as toggleFavorite/moveFavorite writes back.
    val favorites by container.foodRepository.favorites.collectAsState(initial = emptyList())
    val favKeys by container.foodRepository.favoriteKeys.collectAsState(initial = emptySet())
    val recipes by container.recipeRepository.recipes.collectAsState(initial = emptyList())

    // Per-tab search query — substring + case-insensitive match against
    // entry.name (or group.template.name for Frequent). Resets when the user
    // switches segments since the same word almost never matches across all
    // three contexts and the empty list reads as "your data vanished".
    var searchQuery by remember(tab) { mutableStateOf("") }
    val isSearching = searchQuery.isNotBlank()
    val filteredRecents = remember(recents, searchQuery) {
        if (searchQuery.isBlank()) recents
        else recents.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val filteredFrequent = remember(frequent, searchQuery) {
        if (searchQuery.isBlank()) frequent
        else frequent.filter { it.template.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val filteredFavorites = remember(favorites, searchQuery) {
        if (searchQuery.isBlank()) favorites
        else favorites.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val filteredRecipes = remember(recipes, searchQuery) {
        if (searchQuery.isBlank()) recipes
        else recipes.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    // Full-history matches for the current query, merged into the window lists
    // below (deduped by identity so a recent food isn't duplicated).
    val historyMatches = remember(historyTemplates, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) emptyList()
        else historyTemplates.filter { it.name.contains(q, ignoreCase = true) }
    }
    val mergedFilteredRecents = remember(filteredRecents, historyMatches) {
        if (historyMatches.isEmpty()) filteredRecents
        else {
            val seen = filteredRecents.mapTo(mutableSetOf()) { it.favoriteKey }
            filteredRecents + historyMatches.filter { seen.add(it.favoriteKey) }
        }
    }
    val mergedFilteredFrequent = remember(filteredFrequent, historyMatches) {
        if (historyMatches.isEmpty()) filteredFrequent
        else {
            val seen = filteredFrequent.mapTo(mutableSetOf()) { it.template.favoriteKey }
            // count 0 = match from all-time history, outside the 90-day window.
            filteredFrequent + historyMatches
                .filter { seen.add(it.favoriteKey) }
                .map { FrequentFoodGroup(template = it, count = 0) }
        }
    }

    // Run the legacy → ordered favorites migration once on mount so existing
    // users see their previous favorites in the new ordered list.
    LaunchedEffect(Unit) { container.foodRepository.migratedFavorites() }

    LaunchedEffect(tab, favKeys) {
        when (tab) {
            SavedTab.RECENTS -> {
                recents = container.foodRepository.recent()
                historyTemplates = withContext(Dispatchers.Default) {
                    container.foodRepository.historyTemplates()
                }
            }
            SavedTab.FREQUENT -> {
                frequent = container.foodRepository.frequent()
                historyTemplates = withContext(Dispatchers.Default) {
                    container.foodRepository.historyTemplates()
                }
            }
            SavedTab.FAVORITES -> Unit  // driven by `favorites` Flow above
            SavedTab.RECIPES -> Unit    // driven by `recipes` Flow above
        }
    }
    val isDark = isDarkTheme()
    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val searchSurface = if (isDark) Color.Transparent else Color(0xFFF2E9E3).copy(alpha = 0.78f)

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
        containerColor = sheetSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                when (tab) {
                    SavedTab.RECENTS -> stringResource(R.string.saved_meals_tab_recents)
                    SavedTab.FREQUENT -> stringResource(R.string.saved_meals_tab_frequent)
                    SavedTab.FAVORITES -> stringResource(R.string.saved_meals_tab_favorites)
                    SavedTab.RECIPES -> stringResource(R.string.saved_meals_tab_recipes)
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            SegmentedTabs(selected = tab, onSelect = { newTab ->
                tab = newTab
                scope.launch { container.prefs.setLastSavedMealsSegment(newTab.name) }
            })
            Spacer(Modifier.height(12.dp))

            // Search field — filters whichever tab is active. Substring match,
            // case-insensitive. Reset on tab switch via remember(tab) above.
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.saved_meals_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = if (isSearching) {
                    {
                        androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                        }
                    }
                } else null,
                shape = RoundedCornerShape(AppRadii.Field),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = searchSurface,
                    unfocusedContainerColor = searchSurface,
                    focusedBorderColor = AppColors.Calorie.copy(alpha = 0.34f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.16f else 0.12f)
                )
            )
            Spacer(Modifier.height(16.dp))

            when (tab) {
                SavedTab.RECENTS -> {
                    if (mergedFilteredRecents.isEmpty()) {
                        val msg = if (isSearching) stringResource(R.string.saved_meals_no_match)
                                  else stringResource(R.string.saved_meals_no_logs)
                        EmptyState(icon = if (isSearching) Icons.Outlined.Search else Icons.Outlined.Schedule, text = msg)
                    } else {
                        SavedList(items = mergedFilteredRecents) { entry ->
                            SavedMealRow(
                                entry = entry,
                                isFavorite = entry.favoriteKey in favKeys,
                                subtitle = null,
                                imageStore = container.imageStore,
                                onClick = { onRelogEntry(entry); onDismiss() },
                                onLog = { onLogEntry(entry); onDismiss() },
                            )
                        }
                    }
                }
                SavedTab.FREQUENT -> {
                    if (mergedFilteredFrequent.isEmpty()) {
                        val msg = if (isSearching) stringResource(R.string.saved_meals_no_match)
                                  else stringResource(R.string.saved_meals_no_logs)
                        EmptyState(icon = if (isSearching) Icons.Outlined.Search else Icons.Outlined.Refresh, text = msg)
                    } else {
                        SavedList(items = mergedFilteredFrequent) { group ->
                            SavedMealRow(
                                entry = group.template,
                                isFavorite = group.template.favoriteKey in favKeys,
                                // count 0 = all-time history match outside the 90-day window.
                                subtitle = if (group.count > 0) {
                                    stringResource(R.string.saved_meals_count_format, group.count)
                                } else {
                                    null
                                },
                                imageStore = container.imageStore,
                                onClick = { onRelogEntry(group.template); onDismiss() },
                                onLog = { onLogEntry(group.template); onDismiss() },
                            )
                        }
                    }
                }
                SavedTab.FAVORITES -> {
                    if (favorites.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Favorite,
                            text = stringResource(R.string.saved_meals_no_favorites)
                        )
                    } else if (filteredFavorites.isEmpty()) {
                        EmptyState(icon = Icons.Outlined.Search, text = stringResource(R.string.saved_meals_no_match))
                    } else if (isSearching) {
                        // Drag-to-reorder is hidden during search since the
                        // filtered indices don't map back to the unfiltered
                        // favorites array — letting reorder run on a filtered
                        // list would silently swap the wrong items.
                        SavedList(items = filteredFavorites) { entry ->
                            SavedMealRow(
                                entry = entry,
                                isFavorite = true,
                                subtitle = null,
                                imageStore = container.imageStore,
                                onClick = { onRelogEntry(entry); onDismiss() },
                                onLog = { onLogEntry(entry); onDismiss() },
                            )
                        }
                    } else {
                        FavoritesReorderableList(
                            favorites = favorites,
                            imageStore = container.imageStore,
                            onTap = { entry -> onRelogEntry(entry); onDismiss() },
                            onRemove = { entry ->
                                scope.launch { container.foodRepository.toggleFavorite(entry) }
                            },
                            onMove = { from, to ->
                                scope.launch { container.foodRepository.moveFavorite(from, to) }
                            }
                        )
                    }
                }
                SavedTab.RECIPES -> {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.compose.material3.IconButton(onClick = onCreateRecipe) {
                            Icon(
                                Icons.Filled.AddCircle,
                                contentDescription = stringResource(R.string.cd_new_recipe),
                                tint = AppColors.Calorie
                            )
                        }
                    }
                    if (filteredRecipes.isEmpty()) {
                        val msg = if (isSearching) stringResource(R.string.saved_meals_no_match)
                                  else stringResource(R.string.saved_meals_no_recipes)
                        EmptyState(icon = if (isSearching) Icons.Outlined.Search else Icons.Filled.Restaurant, text = msg)
                    } else {
                        SavedList(items = filteredRecipes) { recipe ->
                            RecipeRow(
                                recipe = recipe,
                                imageStore = container.imageStore,
                                onLog = { onLogRecipe(recipe); onDismiss() },
                                onEdit = { onEditRecipe(recipe) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(selected: SavedTab, onSelect: (SavedTab) -> Unit) {
    val isDark = isDarkTheme()
    val trackColor = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val trackBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .border(0.5.dp, trackBorder, RoundedCornerShape(10.dp))
            .padding(2.dp)
    ) {
        for (t in SavedTab.values()) {
            val isSel = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSel) Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(t) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (t) {
                        SavedTab.RECENTS -> stringResource(R.string.saved_meals_tab_recents)
                        SavedTab.FREQUENT -> stringResource(R.string.saved_meals_tab_frequent)
                        SavedTab.FAVORITES -> stringResource(R.string.saved_meals_tab_favorites)
                        SavedTab.RECIPES -> stringResource(R.string.saved_meals_tab_recipes)
                    },
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun <T> SavedList(items: List<T>, row: @Composable (T) -> Unit) {
    val listState = rememberLazyListState()
    ChompassSheetLazyColumn(
        listState = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightConstraint(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { row(it) }
    }
}

/**
 * Favorites-only list with swipe-left-to-unfavorite and tap-based ↑/↓ reorder.
 *
 * The original drag-to-reorder using long-press + pointerInput was unreliable
 * because the favorites list lives inside a ModalBottomSheet (vertical drag
 * to dismiss), so full-row drag handles can compete with parent gestures. The
 * native Android pattern
 * for manual list ordering (used by system Settings for default-app priority,
 * accessibility shortcut order, etc.) is per-row up/down arrow buttons; we
 * use that here.
 */
@Composable
private fun FavoritesReorderableList(
    favorites: List<FoodEntry>,
    imageStore: FoodImageStore,
    onTap: (FoodEntry) -> Unit,
    onRemove: (FoodEntry) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val lastIndex = favorites.lastIndex
    ChompassSheetLazyColumn(
        listState = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightConstraint(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = favorites,
            key = { _, entry -> entry.favoriteKey }
        ) { idx, entry ->
            FavoriteSwipeToUnfavoriteRow(
                entry = entry,
                onUnfavorite = { onRemove(entry) }
            ) {
                SavedMealRow(
                    entry = entry,
                    isFavorite = true,
                    subtitle = null,
                    imageStore = imageStore,
                    onClick = { onTap(entry) },
                    trailing = {
                        MoveButtons(
                            canMoveUp = idx > 0,
                            canMoveDown = idx < lastIndex,
                            onMoveUp = { onMove(idx, idx - 1) },
                            onMoveDown = { onMove(idx, idx + 1) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoriteSwipeToUnfavoriteRow(
    entry: FoodEntry,
    onUnfavorite: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { 120.dp.toPx() }
    var offsetPx by remember(entry.favoriteKey) { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxSwipePx = with(density) { maxWidth.toPx() * 0.55f }
        Box(Modifier.fillMaxWidth()) {
            FavoriteUnfavoriteBackground(offsetPx)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
                    .pointerInput(entry.favoriteKey, maxSwipePx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetPx = (offsetPx + dragAmount).coerceIn(-maxSwipePx, 0f)
                            },
                            onDragEnd = {
                                val finalOffset = offsetPx
                                offsetPx = 0f
                                if (finalOffset <= -triggerPx) onUnfavorite()
                            },
                            onDragCancel = {
                                offsetPx = 0f
                            }
                        )
                    }
            ) {
                content()
            }
        }
    }
}

/**
 * Native Android pattern for manual list reorder — small ↑/↓ arrow buttons
 * stacked vertically. The arrow at the boundary (top row's ↑, bottom row's ↓)
 * is dimmed and non-clickable.
 */
@Composable
private fun MoveButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .size(width = 32.dp, height = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (canMoveUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    else Color.Transparent
                )
                .clickable(enabled = canMoveUp, onClick = onMoveUp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.cd_move_up),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canMoveUp) 0.75f else 0.18f),
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            Modifier
                .size(width = 32.dp, height = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (canMoveDown) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    else Color.Transparent
                )
                .clickable(enabled = canMoveDown, onClick = onMoveDown),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_move_down),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canMoveDown) 0.75f else 0.18f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * iOS Mail-style trailing reveal: the Unfavorite panel is pinned to the
 * right edge and its width tracks the swipe distance, so only the area
 * that's been "revealed" by the foreground sliding left is tinted — the
 * still-visible portion of the row stays its normal color.
 */
@Composable
private fun BoxScope.FavoriteUnfavoriteBackground(offsetPx: Float) {
    if (offsetPx == 0f) {
        Box(Modifier.matchParentSize())
        return
    }
    val revealWidthPx = (-offsetPx).coerceAtLeast(0f)
    val revealWidthDp = with(LocalDensity.current) { revealWidthPx.toDp() }

    Box(Modifier.matchParentSize()) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(revealWidthDp)
                .background(AppColors.Calorie),
            contentAlignment = Alignment.Center
        ) {
            if (revealWidthPx > 24f) {
                Icon(Icons.Outlined.Favorite, contentDescription = stringResource(R.string.cd_unfavorite), tint = Color.White)
            }
        }
    }
}

/**
 * Verbatim port of `private struct SavedMealRow` in RecentsView.swift.
 * The optional [trailing] slot replaces the default "+ Log" button — the
 * Favorites tab uses it to inject a drag handle for reordering.
 */
@Composable
private fun SavedMealRow(
    entry: FoodEntry,
    isFavorite: Boolean,
    subtitle: String?,
    imageStore: FoodImageStore,
    onClick: () -> Unit,
    onLog: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val isDark = isDarkTheme()
    val rowFill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val rowBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.Container))
            .background(rowFill)
            .border(
                0.5.dp,
                rowBorder,
                RoundedCornerShape(AppRadii.Container)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Thumbnail(emoji = entry.emoji, imageFilename = entry.imageFilename, imageStore = imageStore)

        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    entry.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = AppColors.Calorie,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    kcalText(entry.calories),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Calorie
                )
                if (subtitle != null) {
                    Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroChip(MacroKind.PROTEIN, entry.protein)
                MacroChip(MacroKind.CARBS, entry.carbs)
                MacroChip(MacroKind.FAT, entry.fat)
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onLog != null) {
            IconButton(
                onClick = onLog,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = stringResource(R.string.cd_log),
                    tint = AppColors.Calorie,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Icon(
                Icons.Filled.AddCircle,
                contentDescription = stringResource(R.string.cd_log),
                tint = AppColors.Calorie,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Row for a saved [Recipe] — mirrors [SavedMealRow]'s shell, using the first
 * ingredient's emoji as the recipe's face and the summed macros as totals.
 * Tapping the row opens the recipe builder for editing; the trailing "+"
 * logs every ingredient immediately.
 */
@Composable
private fun RecipeRow(
    recipe: Recipe,
    imageStore: FoodImageStore,
    onLog: () -> Unit,
    onEdit: () -> Unit
) {
    val isDark = isDarkTheme()
    val rowFill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val rowBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.Container))
            .background(rowFill)
            .border(0.5.dp, rowBorder, RoundedCornerShape(AppRadii.Container))
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Thumbnail(emoji = recipe.emoji ?: recipe.ingredients.firstOrNull()?.emoji, imageFilename = null, imageStore = imageStore)

        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(recipe.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(kcalText(recipe.totalCalories), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Calorie)
                Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(
                    stringResource(R.string.recipe_ingredient_count_format, recipe.ingredients.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroChip(MacroKind.PROTEIN, recipe.totalProtein)
                MacroChip(MacroKind.CARBS, recipe.totalCarbs)
                MacroChip(MacroKind.FAT, recipe.totalFat)
            }
        }

        androidx.compose.material3.IconButton(onClick = onLog) {
            Icon(
                Icons.Filled.AddCircle,
                contentDescription = stringResource(R.string.cd_log),
                tint = AppColors.Calorie,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 56dp thumb. Prefers the saved food photo (via [imageStore]) over the emoji
 * fallback so logged entries with photos show their actual image — same as
 * iOS RecentsView's `entry.imageData` branch.
 */
@Composable
private fun Thumbnail(emoji: String?, imageFilename: String?, imageStore: FoodImageStore) {
    val shape = RoundedCornerShape(12.dp)
    val bitmap = rememberFoodThumbnail(imageFilename, imageStore)

    Box(
        Modifier
            .size(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .border(1.dp, AppColors.Calorie.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            emoji != null -> Text(emoji, fontSize = 28.sp)
            else -> Icon(
                Icons.Filled.Restaurant,
                contentDescription = null,
                tint = AppColors.Calorie,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Box(
        Modifier.fillMaxWidth().heightConstraint(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AppColors.Calorie.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun Modifier.heightConstraint(): Modifier = this.height(420.dp)
