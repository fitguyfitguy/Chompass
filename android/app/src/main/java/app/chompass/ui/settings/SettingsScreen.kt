package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.about.AboutSettingsRows
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
@Composable
fun SettingsScreen(container: AppContainer, nav: NavHostController) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.nav_settings),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            SettingsSearchField(
                query = query,
                onQueryChange = { query = it },
            )

            if (query.isBlank()) {
                if (ui.suggestions.isNotEmpty()) {
                    SuggestionsCard(
                        suggestions = ui.suggestions,
                        onAction = { nav.navigate(it.targetRoute) },
                        onDismiss = vm::dismissSuggestion,
                    )
                }

                FudGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = AppRadii.Container,
                    padding = 0.dp,
                                    ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SettingsHubRow(
                            label = stringResource(R.string.settings_section_personal),
                            summary = stringResource(R.string.settings_group_personal_summary),
                            icon = Icons.Outlined.Person,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_PERSONAL) },
                        )
                        HorizontalDivider()
                        SettingsHubRow(
                            label = stringResource(R.string.settings_section_goals),
                            summary = stringResource(R.string.settings_group_goals_summary),
                            icon = Icons.Outlined.Equalizer,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_GOALS) },
                        )
                        HorizontalDivider()
                        SettingsHubRow(
                            label = stringResource(R.string.settings_group_food),
                            summary = stringResource(R.string.settings_group_food_summary),
                            icon = Icons.Outlined.Restaurant,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_FOOD) },
                        )
                        HorizontalDivider()
                        SettingsHubRow(
                            label = stringResource(R.string.settings_group_app_display),
                            summary = stringResource(R.string.settings_group_app_summary),
                            icon = Icons.Outlined.Settings,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_APP) },
                        )
                        HorizontalDivider()
                        SettingsHubRow(
                            label = stringResource(R.string.settings_group_trackers),
                            summary = stringResource(R.string.settings_group_trackers_summary),
                            icon = Icons.Outlined.TrackChanges,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_TRACKERS) },
                        )
                        HorizontalDivider()
                        SettingsHubRow(
                            label = stringResource(R.string.settings_group_ai),
                            summary = stringResource(R.string.settings_group_ai_summary),
                            icon = Icons.Outlined.SmartToy,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_AI) },
                        )
                        HorizontalDivider()
                        SettingsHubRow(
                            label = stringResource(R.string.settings_group_data),
                            summary = stringResource(R.string.settings_group_data_summary),
                            icon = Icons.Outlined.FolderOpen,
                            onClick = { nav.navigate(ChompassRoutes.SETTINGS_DATA) },
                        )
                    }
                }

                SectionCard(title = stringResource(R.string.nav_about)) {
                    AboutSettingsRows(container)
                }
            } else {
                SettingsSearchResults(query = query, nav = nav)
            }
            Spacer(Modifier.height(BottomNavScrollPadding))
        }
    }
}

/** Glass search field: magnifier, inline clear button, no label. */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppRadii.Container,
        padding = 0.dp,
            ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(AppColors.Calorie),
            ) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    )
                }
                it()
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_suggestions_dismiss),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Offline index match against [query]; each result shows its group as context. */
@Composable
private fun SettingsSearchResults(query: String, nav: NavHostController) {
    // F perf (device walk 2026-08-24): fold the whole index once per locale
    // ([resolveSettingsIndex]); per-keystroke matching then folds the query
    // exactly once and scans, instead of re-folding labels/keywords and the
    // query for every entry on every recomposition.
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val context = LocalContext.current
    val foldedIndex = remember(locale, context) { resolveSettingsIndex(context) }
    val foldedQuery = settingsSearchFold(query)
    val matched = remember(query, foldedIndex) {
        buildList {
            for (folded in foldedIndex) {
                if (settingsSearchMatchesFolded(folded, foldedQuery)) {
                    add(folded.entry to folded.label)
                }
            }
        }
    }
    if (matched.isEmpty()) {
        Text(
            stringResource(R.string.settings_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        return
    }
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppRadii.Container,
        padding = 0.dp,
            ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            matched.forEachIndexed { index, (entry, label) ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate(entry.route) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FudIconBubble(icon = entry.icon, size = 28.dp, iconSize = 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(entry.groupRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
                    )
                }
            }
        }
    }
}

/** Dismissible hub card proposing beneficial-but-optional setups (max 3 rows). */
@Composable
private fun SuggestionsCard(
    suggestions: List<SettingsSuggestion>,
    onAction: (SettingsSuggestion) -> Unit,
    onDismiss: (String) -> Unit,
) {
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppRadii.Container,
        padding = 0.dp,
            ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                stringResource(R.string.settings_suggestions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
            )
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        suggestion.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onAction(suggestion) }) {
                        Text(
                            suggestion.actionLabel,
                            color = AppColors.Calorie,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(onClick = { onDismiss(suggestion.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.settings_suggestions_dismiss),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
