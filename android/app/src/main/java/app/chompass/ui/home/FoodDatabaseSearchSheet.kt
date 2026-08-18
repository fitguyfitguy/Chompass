package app.chompass.ui.home

import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.NutrientSourceKind
import app.chompass.services.grounding.DatabaseSearchResult
import app.chompass.services.grounding.FoodDatabaseSearch
import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.kcalText
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.theme.AppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Add Food "Search food" sheet: live Open Food Facts + offline USDA / Swiss
 * databases with per-source chips and provenance badges on each hit. Tapping a
 * row prefills [FoodResultSheet] via [onSelect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDatabaseSearchSheet(
    container: AppContainer,
    onSelect: (DatabaseSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val searchFailedMsg = stringResource(R.string.error_search_food_failed)
    var query by remember { mutableStateOf("") }
    var selectedSources by remember {
        mutableStateOf(
            setOf(
                FoodDatabaseSearch.Source.OPEN_FOOD_FACTS,
                FoodDatabaseSearch.Source.USDA,
                FoodDatabaseSearch.Source.SWISS,
            )
        )
    }
    var results by remember { mutableStateOf<List<DatabaseSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, selectedSources) {
        val q = query.trim()
        if (q.isEmpty()) {
            // Invalidate any in-flight search so its late result/error can't
            // land after the field was cleared.
            generation++
            results = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        delay(300)
        val gen = ++generation
        val outcome = try {
            Result.success(container.foodDatabaseSearch.search(q, selectedSources))
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Query changed or the sheet closed: never swallow cancellation —
            // writing state from a cancelled effect surfaces as
            // "The coroutine scope left the composition".
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        if (gen != generation) return@LaunchedEffect
        outcome.onSuccess { results = it }
            .onFailure { error = it.localizedMessage ?: searchFailedMsg }
        loading = false
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = rememberChompassSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            Text(
                stringResource(R.string.food_search_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            FudGlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.food_search_placeholder),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatabaseSourceChip(
                    label = stringResource(R.string.food_search_source_off),
                    selected = FoodDatabaseSearch.Source.OPEN_FOOD_FACTS in selectedSources,
                    onToggle = {
                        selectedSources = toggleSource(FoodDatabaseSearch.Source.OPEN_FOOD_FACTS, selectedSources)
                    },
                )
                DatabaseSourceChip(
                    label = stringResource(R.string.food_search_source_usda),
                    selected = FoodDatabaseSearch.Source.USDA in selectedSources,
                    onToggle = {
                        selectedSources = toggleSource(FoodDatabaseSearch.Source.USDA, selectedSources)
                    },
                )
                DatabaseSourceChip(
                    label = stringResource(R.string.food_search_source_swiss),
                    selected = FoodDatabaseSearch.Source.SWISS in selectedSources,
                    onToggle = {
                        selectedSources = toggleSource(FoodDatabaseSearch.Source.SWISS, selectedSources)
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.food_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            when {
                loading && results.isEmpty() -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = AppColors.Calorie,
                            strokeWidth = 3.dp,
                        )
                    }
                }

                query.isBlank() -> {
                    EmptyHint(stringResource(R.string.food_search_empty))
                }

                error != null && results.isEmpty() -> {
                    EmptyHint(error ?: "")
                }

                results.isEmpty() -> {
                    EmptyHint(stringResource(R.string.food_search_no_results))
                }

                else -> {
                    ChompassSheetLazyColumn(
                        listState = listState,
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { "${it.sourceKind}:${it.sourceId}" }) { result ->
                            SearchResultRow(
                                result = result,
                                onClick = { onSelect(result) },
                            )
                        }
                        if (loading) {
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun toggleSource(
    source: FoodDatabaseSearch.Source,
    current: Set<FoodDatabaseSearch.Source>,
): Set<FoodDatabaseSearch.Source> {
    val next = if (source in current) current - source else current + source
    return next.ifEmpty { setOf(source) }
}

@Composable
private fun DatabaseSourceChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 8.dp),
    )
}

@Composable
private fun SearchResultRow(
    result: DatabaseSearchResult,
    onClick: () -> Unit,
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(16.dp)
    val fill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                result.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                resultSourceSubtitle(result),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                kcalText(result.displayCalories),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie,
            )
            Text(
                resultMacroLine(result),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun resultSourceSubtitle(result: DatabaseSearchResult): String {
    val source = when (result.sourceKind) {
        NutrientSourceKind.OPEN_FOOD_FACTS -> stringResource(R.string.food_search_source_off)
        NutrientSourceKind.USDA -> stringResource(R.string.food_search_source_usda)
        NutrientSourceKind.SWISS -> stringResource(R.string.food_search_source_swiss)
        else -> result.sourceKind.name
    }
    return listOfNotNull(
        result.brand,
        source,
        result.lang?.takeIf { it != "en" }?.uppercase(Locale.ROOT),
    ).joinToString(" · ")
}

internal fun resultMacroLine(result: DatabaseSearchResult): String {
    fun v(x: Double?) = x?.let { it.roundToInt().toString() } ?: "—"
    val macros = listOf(
        v(result.proteinPerServing),
        v(result.carbsPerServing),
        v(result.fatPerServing),
    )
    // Always a 3-element list: OFF hits routinely lack one or two macros
    // (incompleteEnergy), and a fixed `macros[2]` on a shorter list crashed
    // the sheet with IndexOutOfBoundsException while rendering (Codeberg #26).
    return if (macros.all { it == "—" }) "P · C · F" else "P ${macros[0]} · C ${macros[1]} · F ${macros[2]}"
}
