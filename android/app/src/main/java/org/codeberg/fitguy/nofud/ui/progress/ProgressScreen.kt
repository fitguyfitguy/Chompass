package org.codeberg.fitguy.nofud.ui.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.services.health.DailyActivity
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialog
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialogActions
import org.codeberg.fitguy.nofud.ui.navigation.BottomNavScrollPadding

/**
 * Verbatim port of ios/calorietracker/ContentView.swift > struct ProgressTabView,
 * including the per-section components in ProgressComponents.swift.
 *
 * Layout (top -> bottom):
 *   1. Segmented TimeRange picker — 1W / 1M / 3M / 6M / 1Y / All
 *   2. WeightChartSection — Weight title + Log Weight pill + StatBadges
 *      (Current, Goal, Net Change, Average) + line chart with green dashed goal rule
 *   3. WeightHistoryLink — only shown if any weight entries exist; shows
 *      count + chevron, opens AllWeightHistorySheet. BodyFatHistoryLink
 *      mirrors it for body-fat entries, opening AllBodyFatHistorySheet
 *   4. CalorieChartSection — Calories title + Avg badge + bar chart of
 *      per-day calories with calorieGradient bars (dimmed below goal,
 *      pink above goal — same as iOS)
 *   5. MacroAveragesSection — averages over the selected time range,
 *      one MacroProgressRow per macro
 */
enum class TimeRange(@StringRes val labelRes: Int, val days: Int) {
    WEEK(R.string.progress_range_week, 7),
    MONTH(R.string.progress_range_month, 30),
    THREE_MONTHS(R.string.progress_range_3m, 90),
    SIX_MONTHS(R.string.progress_range_6m, 180),
    YEAR(R.string.progress_range_year, 365),
    ALL_TIME(R.string.progress_range_all, 3650);

    fun dateRange(today: java.time.LocalDate = java.time.LocalDate.now()): Pair<java.time.LocalDate, java.time.LocalDate> {
        val start = today.minusDays((days - 1).toLong())
        return start to today
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(container: AppContainer) {
    val vm: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val activity by vm.activity.collectAsState()
    val wellness by vm.wellness.collectAsState()
    val weightMetric = ui.weightUnit == "kg"

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddBodyFatDialog by remember { mutableStateOf(false) }
    var showAllWeights by remember { mutableStateOf(false) }
    var showAllBodyFats by remember { mutableStateOf(false) }
    var bodyMetric by remember { mutableStateOf(BodyMetric.WEIGHT) }
    var heavySectionsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        heavySectionsReady = true
    }
    val bodyFatAvailable = ui.bodyFatEntries.isNotEmpty()
        || ui.profile?.bodyFatPercentage != null
        || ui.profile?.goalBodyFatPercentage != null

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = BottomNavScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { TimeRangePicker(selected = ui.timeRange, onSelect = vm::setTimeRange) }

            item {
                if (bodyFatAvailable) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BodyMetricToggle(selected = bodyMetric, onSelect = { bodyMetric = it })
                        CardSection {
                            when (bodyMetric) {
                                BodyMetric.WEIGHT -> WeightSection(
                                    entries = ui.filteredWeights,
                                    stats = ui.weightStats,
                                    goalKg = ui.profile?.goalWeightKg,
                                    useMetric = weightMetric,
                                    onLogWeight = { showAddDialog = true }
                                )
                                BodyMetric.BODY_FAT -> BodyFatSection(
                                    entries = ui.filteredBodyFats,
                                    stats = ui.bodyFatStats,
                                    profileBodyFatFraction = ui.profile?.bodyFatPercentage,
                                    goalFraction = ui.profile?.goalBodyFatPercentage,
                                    onLogBodyFat = { showAddBodyFatDialog = true }
                                )
                            }
                        }
                    }
                } else {
                    CardSection {
                        WeightSection(
                            entries = ui.filteredWeights,
                            stats = ui.weightStats,
                            goalKg = ui.profile?.goalWeightKg,
                            useMetric = weightMetric,
                            onLogWeight = { showAddDialog = true }
                        )
                    }
                }
            }

            if (ui.entries.isNotEmpty()) {
                item {
                    WeightHistoryLink(count = ui.entries.size) { showAllWeights = true }
                }
            }

            if (ui.bodyFatEntries.isNotEmpty()) {
                item {
                    BodyFatHistoryLink(count = ui.bodyFatEntries.size) { showAllBodyFats = true }
                }
            }

            if (heavySectionsReady) {
                item {
                    CardSection {
                        CalorieSection(
                            dailyCalories = ui.dailyCalories,
                            calorieGoal = ui.profile?.effectiveCalories ?: 2000
                        )
                    }
                }
            } else {
                item { CardSection { ChartPlaceholder() } }
            }

            if (heavySectionsReady) {
                ui.profile?.let { p ->
                    item {
                        CardSection {
                            MacroAveragesSection(
                                avgProtein = ui.macroAverages.first,
                                avgCarbs = ui.macroAverages.second,
                                avgFat = ui.macroAverages.third,
                                proteinGoal = p.effectiveProtein,
                                carbsGoal = p.effectiveCarbs,
                                fatGoal = p.effectiveFat
                            )
                        }
                    }
                }
            } else {
                item { CardSection { ChartPlaceholder(height = 120.dp) } }
            }

            if (activity.isNotEmpty()) {
                item {
                    CardSection { ActivitySection(days = activity) }
                }
            }

            if (wellness.any { it.sleepMinutes != null || it.restingHeartRateBpm != null || it.hydrationMl != null }) {
                item {
                    CardSection { WellnessSection(days = wellness) }
                }
            }
        }
    }

    if (showAddDialog) {
        val seedKg = ui.entries.maxByOrNull { it.date }?.weightKg
            ?: ui.profile?.weightKg
            ?: 70.0
        val scope = rememberCoroutineScope()
        AddWeightDialog(
            useMetric = weightMetric,
            initialKg = seedKg,
            onUnitChange = { metric ->
                scope.launch { container.prefs.setWeightUnit(if (metric) "kg" else "lbs") }
            },
            onDismiss = { showAddDialog = false }
        ) { kg, whenLogged ->
            vm.addWeightAt(kg, whenLogged); showAddDialog = false
        }
    }
    if (showAddBodyFatDialog) {
        val seedFraction = ui.bodyFatEntries.maxByOrNull { it.date }?.bodyFatFraction
            ?: ui.profile?.bodyFatPercentage
            ?: 0.20
        AddBodyFatDialog(initialFraction = seedFraction, onDismiss = { showAddBodyFatDialog = false }) { fraction, whenLogged ->
            vm.addBodyFatAt(fraction, whenLogged); showAddBodyFatDialog = false
        }
    }
    if (showAllWeights) {
        AllWeightHistorySheet(
            entries = ui.entries.sortedByDescending { it.date },
            useMetric = weightMetric,
            onDelete = vm::deleteWeight,
            onDismiss = { showAllWeights = false }
        )
    }
    if (showAllBodyFats) {
        AllBodyFatHistorySheet(
            entries = ui.bodyFatEntries.sortedByDescending { it.date },
            onDelete = vm::deleteBodyFat,
            onDismiss = { showAllBodyFats = false }
        )
    }
    if (ui.goalReached) {
        FudGlassDialog(onDismissRequest = { vm.dismissGoalReached() }) {
            Text(stringResource(R.string.progress_goal_reached_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.progress_goal_reached_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_keep_going),
                onPrimary = { vm.dismissGoalReached() }
            )
        }
    }
}

/** Static progress layout for release screenshot previews (no ViewModel). */
@Composable
internal fun ProgressScreenPreviewContent(
    ui: ProgressUiState,
    activity: List<DailyActivity> = emptyList(),
    bodyMetric: BodyMetric = BodyMetric.WEIGHT,
    chartsImmediate: Boolean = true,
) {
    val weightMetric = ui.weightUnit == "kg"
    val bodyFatAvailable = ui.bodyFatEntries.isNotEmpty()
        || ui.profile?.bodyFatPercentage != null
        || ui.profile?.goalBodyFatPercentage != null

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = BottomNavScrollPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TimeRangePicker(selected = ui.timeRange, onSelect = {}) }
            item {
                if (bodyFatAvailable) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BodyMetricToggle(selected = bodyMetric, onSelect = {})
                        CardSection {
                            when (bodyMetric) {
                                BodyMetric.WEIGHT -> WeightSection(
                                    entries = ui.filteredWeights,
                                    stats = ui.weightStats,
                                    goalKg = ui.profile?.goalWeightKg,
                                    useMetric = weightMetric,
                                    onLogWeight = {},
                                    chartsImmediate = chartsImmediate,
                                )
                                BodyMetric.BODY_FAT -> BodyFatSection(
                                    entries = ui.filteredBodyFats,
                                    stats = ui.bodyFatStats,
                                    profileBodyFatFraction = ui.profile?.bodyFatPercentage,
                                    goalFraction = ui.profile?.goalBodyFatPercentage,
                                    onLogBodyFat = {},
                                    chartsImmediate = chartsImmediate,
                                )
                            }
                        }
                    }
                } else {
                    CardSection {
                        WeightSection(
                            entries = ui.filteredWeights,
                            stats = ui.weightStats,
                            goalKg = ui.profile?.goalWeightKg,
                            useMetric = weightMetric,
                            onLogWeight = {},
                            chartsImmediate = chartsImmediate,
                        )
                    }
                }
            }
            if (ui.entries.isNotEmpty()) {
                item { WeightHistoryLink(count = ui.entries.size) {} }
            }
            if (ui.bodyFatEntries.isNotEmpty()) {
                item { BodyFatHistoryLink(count = ui.bodyFatEntries.size) {} }
            }
            item {
                CardSection {
                    CalorieSection(
                        dailyCalories = ui.dailyCalories,
                        calorieGoal = ui.profile?.effectiveCalories ?: 2000,
                    )
                }
            }
            ui.profile?.let { p ->
                item {
                    CardSection {
                        MacroAveragesSection(
                            avgProtein = ui.macroAverages.first,
                            avgCarbs = ui.macroAverages.second,
                            avgFat = ui.macroAverages.third,
                            proteinGoal = p.effectiveProtein,
                            carbsGoal = p.effectiveCarbs,
                            fatGoal = p.effectiveFat,
                        )
                    }
                }
            }
            if (activity.isNotEmpty()) {
                item {
                    CardSection { ActivitySection(days = activity) }
                }
            }
        }
    }
}
