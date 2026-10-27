package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.data.SavedFoodIndexEntry
import app.chompass.models.FoodEntry
import app.chompass.models.LocaleFormat
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.home.EditFavoriteSheet
import app.chompass.ui.theme.AppTextOpacity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Sort orders for the library browser. Local UI state, not persisted. */
enum class LibrarySort { LAST_LOGGED, NAME, LOGS }

/**
 * Codeberg #142: browse every saved food — the newest diary row per identity
 * key plus favorites — with search, sort, template edit, favorite toggle,
 * hide/unhide, and delete-all-logged-rows. Hidden foods live in their own
 * section; unhiding puts them back into Add Food suggestions.
 *
 * No ViewModel: settings sub-screens take the [AppContainer] directly, and
 * mutations reload via a [refreshTick] bump (same pattern as the tracker
 * presets screen).
 */
@Composable
fun SavedFoodsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.foodRepository
    val scope = rememberCoroutineScope()

    var refreshTick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.LAST_LOGGED) }
    var menuForKey by remember { mutableStateOf<String?>(null) }
    var editingTemplate by remember { mutableStateOf<FoodEntry?>(null) }
    /** Delete confirm target: template plus its all-time logged-row count. */
    var pendingDelete by remember { mutableStateOf<Pair<FoodEntry, Int>?>(null) }

    var index by remember { mutableStateOf<List<SavedFoodIndexEntry>>(emptyList()) }
    var hiddenKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(refreshTick) {
        hiddenKeys = repo.hiddenFoodKeys.first()
        index = repo.savedFoodIndex(includeHidden = true)
    }

    val favKeys by repo.favoriteKeys.collectAsState(initial = emptySet())

    val trimmedQuery = query.trim().lowercase()
    fun matches(entry: SavedFoodIndexEntry): Boolean =
        trimmedQuery.isEmpty() || entry.normalizedName.contains(trimmedQuery)

    val visible = index
        .filter { it.template.favoriteKey !in hiddenKeys && matches(it) }
        .let { list ->
            when (sort) {
                LibrarySort.LAST_LOGGED -> list.sortedByDescending { it.lastLogged }
                LibrarySort.NAME -> list.sortedBy { it.normalizedName }
                LibrarySort.LOGS -> list.sortedByDescending { it.logCount }
            }
        }
    val hidden = index.filter { it.template.favoriteKey in hiddenKeys && matches(it) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_saved_foods),
        onBack = onBack,
    ) {
        // Search — same substring semantics as the Add Food hub (the index
        // precomputes the normalized name).
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.saved_meals_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    label = {
                        Text(
                            when (option) {
                                LibrarySort.LAST_LOGGED -> stringResource(R.string.library_sort_last_logged)
                                LibrarySort.NAME -> stringResource(R.string.library_sort_name)
                                LibrarySort.LOGS -> stringResource(R.string.library_sort_logs)
                            }
                        )
                    },
                )
            }
        }

        if (visible.isEmpty() && hidden.isEmpty()) {
            Text(
                stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }

        if (visible.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.settings_saved_foods)) {
                visible.forEachIndexed { i, entry ->
                    val key = entry.template.favoriteKey
                    if (i > 0) HorizontalDivider()
                    LibraryRow(
                        entry = entry,
                        isFavorite = key in favKeys,
                        isHidden = false,
                        menuExpanded = menuForKey == key,
                        onMenuToggle = { menuForKey = if (menuForKey == key) null else key },
                        onMenuDismiss = { if (menuForKey == key) menuForKey = null },
                        onEdit = {
                            menuForKey = null
                            editingTemplate = entry.template
                        },
                        onToggleFavorite = {
                            menuForKey = null
                            scope.launch {
                                repo.toggleFavorite(entry.template)
                                // Tick inside the coroutine: the reload must
                                // observe the committed edit, not race it.
                                refreshTick++
                            }
                        },
                        onHide = {
                            menuForKey = null
                            scope.launch {
                                repo.hideFood(key)
                                refreshTick++
                            }
                        },
                        onDeleteRows = {
                            menuForKey = null
                            scope.launch {
                                val count = repo.loggedRowCount(key)
                                pendingDelete = entry.template to count
                                refreshTick++
                            }
                        },
                    )
                }
            }
        }

        if (hidden.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.library_hidden_section)) {
                hidden.forEachIndexed { i, entry ->
                    val key = entry.template.favoriteKey
                    if (i > 0) HorizontalDivider()
                    LibraryRow(
                        entry = entry,
                        isFavorite = key in favKeys,
                        isHidden = true,
                        menuExpanded = menuForKey == key,
                        onMenuToggle = { menuForKey = if (menuForKey == key) null else key },
                        onMenuDismiss = { if (menuForKey == key) menuForKey = null },
                        onEdit = {
                            menuForKey = null
                            editingTemplate = entry.template
                        },
                        onToggleFavorite = {
                            menuForKey = null
                            scope.launch {
                                repo.toggleFavorite(entry.template)
                                refreshTick++
                            }
                        },
                        onHide = {
                            menuForKey = null
                            scope.launch {
                                repo.unhideFood(key)
                                refreshTick++
                            }
                        },
                        onDeleteRows = {
                            menuForKey = null
                            scope.launch {
                                val count = repo.loggedRowCount(key)
                                pendingDelete = entry.template to count
                                refreshTick++
                            }
                        },
                    )
                }
            }
        }
    }

    editingTemplate?.let { template ->
        EditFavoriteSheet(
            container = container,
            entry = template,
            aiFeaturesEnabled = false,
            // Unreachable: the sheet hides its AI actions when
            // aiFeaturesEnabled is off (and double-guards its own handlers).
            onReprocess = { _, _, _ -> error("AI actions are off in the library browser") },
            onFill = { _ -> error("AI actions are off in the library browser") },
            titleOverride = stringResource(R.string.sheet_edit_food),
            onSave = { updated ->
                scope.launch {
                    repo.updateSavedTemplate(template, updated)
                    refreshTick++
                }
            },
            onDismiss = { editingTemplate = null },
        )
    }

    pendingDelete?.let { (template, count) ->
        ChompassDialog(onDismissRequest = { pendingDelete = null }) {
            Text(
                stringResource(R.string.library_delete_rows_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.library_delete_rows_message,
                    count,
                    template.name,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary),
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_delete),
                onPrimary = {
                    pendingDelete = null
                    scope.launch {
                        repo.deleteLoggedRows(template.favoriteKey)
                        refreshTick++
                    }
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { pendingDelete = null },
                destructive = true,
            )
        }
    }
}

/** One library row: name + summary, tap to edit, trailing action menu. */
@Composable
private fun LibraryRow(
    entry: SavedFoodIndexEntry,
    isFavorite: Boolean,
    isHidden: Boolean,
    menuExpanded: Boolean,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onDeleteRows: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.template.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.logCount == 0) {
                    stringResource(R.string.library_never_logged)
                } else {
                    stringResource(
                        R.string.library_row_summary,
                        entry.logCount,
                        LocaleFormat.mediumDateZoned().format(entry.lastLogged),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        if (isHidden) {
            TextButton(onClick = onHide) {
                Text(stringResource(R.string.action_unhide))
            }
        }
        Box {
            IconButton(onClick = onMenuToggle) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isFavorite) R.string.home_swipe_unfavorite else R.string.home_swipe_favorite
                            )
                        )
                    },
                    onClick = onToggleFavorite,
                )
                if (!isHidden) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_hide_from_suggestions)) },
                        onClick = onHide,
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = onDeleteRows,
                )
            }
        }
    }
}
