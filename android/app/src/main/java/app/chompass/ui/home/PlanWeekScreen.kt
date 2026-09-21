package app.chompass.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chompass.AppContainer
import app.chompass.R
import androidx.compose.material3.minimumInteractiveComponentSize
import app.chompass.models.FoodEntry
import app.chompass.models.LocaleFormat
import app.chompass.services.grounding.FoodSuggestion
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.services.grounding.SuggestionKind
import app.chompass.ui.components.energyText
import app.chompass.ui.settings.SettingsSubScreen
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class PlanWeekDay(val date: LocalDate, val entries: List<FoodEntry>)

internal class PlanWeekViewModel(private val container: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    private val _windowStart = MutableStateFlow(LocalDate.now())
    val windowStart: StateFlow<LocalDate> = _windowStart.asStateFlow()
    private val _days = MutableStateFlow<List<PlanWeekDay>>(emptyList())
    val days: StateFlow<List<PlanWeekDay>> = _days.asStateFlow()

    private val _tab = MutableStateFlow(SavedTab.RECENTS)
    val tab: StateFlow<SavedTab> = _tab.asStateFlow()

    private val _rows = MutableStateFlow<List<FoodSuggestion>>(emptyList())
    val rows: StateFlow<List<FoodSuggestion>> = _rows.asStateFlow()

    private val _pickerDay = MutableStateFlow<LocalDate?>(null)
    val pickerDay: StateFlow<LocalDate?> = _pickerDay.asStateFlow()

    private var refreshGeneration = 0

    init {
        selectTab(SavedTab.RECENTS)
        refresh()
    }

    /**
     * Prev/next shift the window by a week. Prev clamps so the window never
     * starts before today; next clamps so its end never passes the diary's
     * 8-week forward window (#96).
     */
    fun shift(delta: Int) {
        val today = LocalDate.now()
        val maxStart = maxDiaryNavDate(today).minusDays(6)
        val next = _windowStart.value.plusDays((delta * 7).toLong()).coerceIn(today, maxStart)
        if (next != _windowStart.value) {
            _windowStart.value = next
            refresh()
        }
    }

    fun selectTab(tab: SavedTab) {
        _tab.value = tab
        viewModelScope.launch {
            _rows.value = withContext(Dispatchers.Default) {
                runCatching {
                    when (tab) {
                        SavedTab.RECENTS -> container.foodRepository.recent().map {
                            FoodSuggestion.SavedFood(
                                template = it, kind = SuggestionKind.RECENT,
                                logCount = 0, daysSince = 0, score = 0.0,
                            )
                        }
                        SavedTab.FREQUENT -> container.foodRepository.frequent().map {
                            FoodSuggestion.SavedFood(
                                template = it.template, kind = SuggestionKind.FREQUENT,
                                logCount = it.count, daysSince = 0, score = 0.0,
                            )
                        }
                        SavedTab.FAVORITES -> container.foodRepository.migratedFavorites().map {
                            FoodSuggestion.SavedFood(
                                template = it, kind = SuggestionKind.FAVORITE,
                                logCount = 0, daysSince = 0, score = 0.0,
                            )
                        }
                        SavedTab.RECIPES -> container.recipeRepository.recipes.first()
                            .map { FoodSuggestion.SavedRecipe(it, score = 0.0) }
                    }
                }.getOrDefault(emptyList())
            }
        }
    }

    fun openPicker(day: LocalDate) {
        _pickerDay.value = day
    }

    fun closePicker() {
        _pickerDay.value = null
    }

    /** Row tap: planSavedMeal / planRecipe semantics for the section's day. */
    fun plan(suggestion: FoodSuggestion, date: LocalDate) {
        viewModelScope.launch {
            when (suggestion) {
                is FoodSuggestion.SavedFood -> container.foodRepository.addEntry(
                    suggestion.template.duplicatedForLogging(
                        timestampForLogging(date, Instant.now(), zone, timeOverride = null),
                        mealType = suggestion.template.mealType,
                    ).copy(planned = true),
                    writeHealth = false,
                )
                is FoodSuggestion.SavedRecipe -> container.recipeRepository.logRecipe(
                    suggestion.recipe,
                    timestampForLogging(date, Instant.now(), zone, timeOverride = null),
                    mealType = suggestion.recipe.mealType,
                    planned = true,
                )
                else -> return@launch
            }
            refresh()
        }
    }

    /** Chip long-press: remove the row (v1 without the Home undo snackbar). */
    fun remove(entry: FoodEntry) {
        viewModelScope.launch {
            container.foodRepository.deleteEntry(entry)
            refresh()
        }
    }

    fun refresh() {
        val generation = ++refreshGeneration
        val start = _windowStart.value
        viewModelScope.launch {
            val days = withContext(Dispatchers.Default) {
                (0..6).map { offset ->
                    val day = start.plusDays(offset.toLong())
                    PlanWeekDay(day, container.foodRepository.entriesForDate(day).first())
                }
            }
            // A shift may have restarted the read; only the newest window wins.
            if (generation == refreshGeneration) _days.value = days
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlanWeekViewModel(container) as T
    }
}

/**
 * Plan week canvas (meal planning mode): seven day sections prefilled from
 * Saved Meals / Recipes. V1 lists entries per day; a slot-structured grid is
 * a later refinement.
 */
@Composable
internal fun PlanWeekScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
) {
    val vm: PlanWeekViewModel = viewModel(factory = PlanWeekViewModel.Factory(container))
    val windowStart by vm.windowStart.collectAsState()
    val days by vm.days.collectAsState()
    val tab by vm.tab.collectAsState()
    val rows by vm.rows.collectAsState()
    val pickerDay by vm.pickerDay.collectAsState()
    val dateFmt = remember { LocaleFormat.shortDate() }

    SettingsSubScreen(
        title = stringResource(R.string.plan_week_title),
        onBack = { nav.popBackStack() },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { vm.shift(-1) },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_plan_week_prev),
                )
            }
            Text(
                stringResource(
                    R.string.plan_week_range,
                    windowStart.format(dateFmt),
                    windowStart.plusDays(6).format(dateFmt),
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = { vm.shift(1) },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_plan_week_next),
                )
            }
        }

        days.forEach { day ->
            PlanWeekDaySection(
                day = day,
                onAdd = { vm.openPicker(day.date) },
                onChipTap = { entry ->
                    // Return to Home on the tapped chip's day.
                    container.planWeekReturnDay.value = day.date
                    nav.popBackStack()
                },
                onChipRemove = { vm.remove(it) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    pickerDay?.let { day ->
        PlanWeekPickerDialog(
            tab = tab,
            rows = rows,
            day = day,
            onSelectTab = vm::selectTab,
            onPlan = { suggestion ->
                vm.plan(suggestion, day)
                vm.closePicker()
            },
            onDismiss = vm::closePicker,
        )
    }
}

@Composable
private fun PlanWeekDaySection(
    day: PlanWeekDay,
    onAdd: () -> Unit,
    onChipTap: (FoodEntry) -> Unit,
    onChipRemove: (FoodEntry) -> Unit,
) {
    val dateFmt = remember { LocaleFormat.shortDate() }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.date.format(dateFmt).replaceFirstChar { it.uppercase(Locale.getDefault()) },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plan_week_add_meal),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (day.entries.isEmpty()) {
            Text(
                stringResource(
                    if (day.date.isAfter(today)) R.string.home_no_foods_planned
                    else R.string.home_no_foods_logged
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        } else {
            day.entries.forEach { entry ->
                PlanWeekEntryChip(
                    entry = entry,
                    onTap = { onChipTap(entry) },
                    onRemove = { onChipRemove(entry) },
                )
            }
        }
    }
}

@Composable
private fun PlanWeekEntryChip(
    entry: FoodEntry,
    onTap: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(AppRadii.Field)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                shape,
            )
            .combinedClickable(onClick = onTap, onLongClick = onRemove)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            mealLabel(entry.mealType),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "·",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
        )
        Text(
            entry.name,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.planned) {
            DiaryPlannedChip()
        }
    }
}

@Composable
private fun PlanWeekPickerDialog(
    tab: SavedTab,
    rows: List<FoodSuggestion>,
    day: LocalDate,
    onSelectTab: (SavedTab) -> Unit,
    onPlan: (FoodSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFmt = remember { LocaleFormat.shortDate() }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.plan_week_add_meal) + " · " + day.format(dateFmt),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        SegmentedTabs(selected = tab, onSelect = onSelectTab)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            items(rows, key = { it.key }) { suggestion ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onPlan(suggestion) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        suggestion.name,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        energyText(suggestionCalories(suggestion)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (rows.isEmpty()) {
                item { Text(
                    stringResource(R.string.saved_meals_no_match),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                ) }
            }
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
        )
    }
}
