package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.AIProvider
import app.chompass.models.FoodEntry
import app.chompass.models.GapGroup
import app.chompass.models.GapMacroField
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroValueFormatter
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.gapReport
import app.chompass.services.FillQueueItem
import app.chompass.services.FillStatus
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.rememberChompassSheetState
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * AI fill-missing gaps sheet (Batch 2): the gapped favorites/diary rows of the
 * current scan as a queue. Per-row or run-all AI fills run in place (nothing
 * is written until Apply), each FILLED favorite offers a today-cascade
 * checkbox (its logged copies are filled locally from the same analysis), and
 * Apply publishes the engine's undo snapshot for the Home snackbar. Session
 * state lives in the singleton [app.chompass.services.FillGapsEngine]; every
 * close path resets it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillGapsSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val engine = container.fillGapsEngine
    val queue by engine.queue.collectAsState()
    val selectedAI by container.prefs.selectedAIProvider.collectAsState(initial = AIProvider.GEMINI)
    val scope = rememberCoroutineScope()
    val cascadeChecks = remember { mutableStateMapOf<UUID, Boolean>() }
    var confirmRunAll by remember { mutableStateOf(false) }

    // First scan arrival loads the session; an existing session is never
    // clobbered mid-run (apply writes would otherwise re-enter with a new
    // signature and wipe the APPLIED view before Done).
    LaunchedEffect(engine) {
        engine.scan.collect { scan ->
            if (engine.queue.value == null) engine.loadQueue(scan)
        }
    }

    val items = queue.orEmpty()
    val pending = items.filter { it.status == FillStatus.PENDING }
    val running = items.any { it.status == FillStatus.RUNNING }
    val anyFilled = items.any { it.status == FillStatus.FILLED }
    val allApplied = items.isNotEmpty() && items.all { it.status == FillStatus.APPLIED }

    val close: () -> Unit = {
        engine.resetQueue()
        onDismiss()
    }

    ChompassBottomSheet(
        onDismiss = close,
        sheetState = rememberChompassSheetState(busy = running),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Utility-sheet pattern: the progressive action (Fill all → Apply →
            // Done) lives in the toolbar, above the scroll body.
            var primaryLabel: String? = null
            var onPrimary: (() -> Unit)? = null
            when {
                // Hidden (null) while the confirm dialog is up so the dialog's
                // primary "Fill all" is the only tap target with that label.
                pending.isNotEmpty() && !confirmRunAll -> {
                    primaryLabel = stringResource(R.string.fill_gaps_run_all)
                    onPrimary = { confirmRunAll = true }
                }
                anyFilled -> {
                    primaryLabel = stringResource(R.string.fill_gaps_apply)
                    onPrimary = {
                        scope.launch {
                            // Stay enabled while fills run: a tap during the run
                            // waits out the in-flight rows and applies the whole
                            // batch (a dropped tap would look like a dead button).
                            while (engine.isRunning()) kotlinx.coroutines.delay(150)
                            engine.applyFilled(cascadeChecks.filterValues { it }.keys)
                        }
                    }
                }
                allApplied -> {
                    primaryLabel = stringResource(R.string.action_done)
                    onPrimary = close
                }
            }
            SheetReviewToolbar(
                title = stringResource(R.string.fill_gaps_title),
                onCancel = close,
                primaryLabel = primaryLabel,
                onPrimary = onPrimary,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                if (items.isEmpty()) {
                    GapEmptyState(text = stringResource(R.string.fill_gaps_empty))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(items, key = { it.target.entry.id }) { item ->
                            var expanded by remember(item.target.entry.id) { mutableStateOf(false) }
                            GapRow(
                                item = item,
                                engine = engine,
                                cascadeChecks = cascadeChecks,
                                onRun = { focus -> scope.launch { engine.runOne(item.copy(focus = focus)) } },
                                expanded = expanded,
                                onToggleExpand = { expanded = !expanded },
                            )
                        }
                    }
                }

                // Forever opt-out (step 4): kills the Home banner without
                // touching per-list X dismissals; the sheet stays open.
                if (items.isNotEmpty()) {
                    TextButton(
                        onClick = { scope.launch { container.prefs.setFillMissingBannerDisabled(true) } },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            stringResource(R.string.fill_gaps_never_remind),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmRunAll) {
        // UX-7: the only place the request count is shown. Per-row runs skip it.
        val confirmRes = if (selectedAI == AIProvider.ON_DEVICE) {
            R.plurals.fill_gaps_confirm_ondevice
        } else {
            R.plurals.fill_gaps_confirm_remote
        }
        ChompassDialog(onDismissRequest = { confirmRunAll = false }) {
            Text(
                pluralStringResource(confirmRes, pending.size, pending.size),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.fill_gaps_run_all),
                onPrimary = {
                    confirmRunAll = false
                    scope.launch { engine.runAll() }
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { confirmRunAll = false },
            )
        }
    }
}

@Composable
private fun GapEmptyState(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GapRow(
    item: FillQueueItem,
    engine: app.chompass.services.FillGapsEngine,
    cascadeChecks: MutableMap<UUID, Boolean>,
    onRun: (Set<GapGroup>) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    // Opt-in diff (UX-5): only fields the fill actually wrote, never a forced
    // review step — rows stay collapsed until tapped.
    val diffRows = if (item.status == FillStatus.FILLED && item.merged != null && item.merged != item.target.entry) {
        buildFillDiff(before = item.target.entry, after = item.merged!!)
    } else {
        emptyList()
    }
    // Per-nutrient fill focus (WP6): defaults to the full ask; the dropdown
    // next to the run button narrows the run to one missing group.
    var rowFocus by remember(item.target.entry.id) { mutableStateOf(GapGroup.entries.toSet()) }
    GapRowSurface(onClick = if (diffRows.isEmpty()) null else onToggleExpand) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                item.target.entry.name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GapCaption(item)
            if (expanded && diffRows.isNotEmpty()) {
                diffRows.forEach { row ->
                    GapMutedText(stringResource(R.string.edit_reprocess_diff_row, row.label, row.before, row.after))
                }
            }
            if (item.target.favorite && item.status == FillStatus.FILLED) {
                GapCascadeRow(item, engine, cascadeChecks)
            }
        }
        when (item.status) {
            FillStatus.PENDING, FillStatus.FAILED -> {
                var focusMenu by remember(item.target.entry.id) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onRun(rowFocus) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.analysis_queue_run_now),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box {
                        IconButton(onClick = { focusMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.fill_gaps_focus_pick),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        SheetGlassDropdownMenu(
                            expanded = focusMenu,
                            onDismissRequest = { focusMenu = false },
                            menuWidth = 200.dp,
                        ) {
                            val allGroups = GapGroup.entries.toSet()
                            SheetGlassDropdownMenuItem(
                                label = stringResource(R.string.fill_gaps_focus_all),
                                selected = rowFocus == allGroups,
                                onClick = {
                                    rowFocus = allGroups
                                    focusMenu = false
                                },
                            )
                            for (group in item.target.entry.gapReport().missingGroups()) {
                                SheetGlassDropdownMenuItem(
                                    label = focusLabel(group),
                                    selected = rowFocus == setOf(group),
                                    onClick = {
                                        rowFocus = setOf(group)
                                        focusMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            FillStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
            FillStatus.APPLIED -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            FillStatus.FILLED -> if (diffRows.isNotEmpty()) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

/**
 * Fill-only diff for an expanded gap row: exactly the fields the fill wrote
 * — zero→value macros, null→value micros, all-zero→valued constituent rows
 * (paired by index; the merge never reorders). Unlike [buildReprocessDiff]
 * there is no name/serving line (a fill never touches them) and unchanged
 * fields never appear; before renders as the diary's zero em dash.
 */
@Composable
private fun buildFillDiff(before: FoodEntry, after: FoodEntry): List<ReprocessDiffRow> {
    val res: @Composable (Int) -> String = { stringResource(it) }
    val kcalUnit = stringResource(R.string.unit_kcal)
    val gUnit = stringResource(R.string.unit_g)
    val rows = mutableListOf<ReprocessDiffRow>()
    fun macro(v: Double) = "${MacroValueFormatter.string(v)}$gUnit"
    if (before.calories == 0 && after.calories != 0) {
        rows += ReprocessDiffRow(res(GapMacroField.CALORIES.labelRes), "—", "${LocaleFormat.integer(after.calories)} $kcalUnit")
    }
    if (before.protein == 0.0 && after.protein != 0.0) {
        rows += ReprocessDiffRow(res(GapMacroField.PROTEIN.labelRes), "—", macro(after.protein))
    }
    if (before.carbs == 0.0 && after.carbs != 0.0) {
        rows += ReprocessDiffRow(res(GapMacroField.CARBS.labelRes), "—", macro(after.carbs))
    }
    if (before.fat == 0.0 && after.fat != 0.0) {
        rows += ReprocessDiffRow(res(GapMacroField.FAT.labelRes), "—", macro(after.fat))
    }
    val beforeMicros = MicronutrientValues.from(before)
    val afterMicros = MicronutrientValues.from(after)
    for (field in MicronutrientField.entries) {
        val newValue = afterMicros[field]
        if (beforeMicros[field] == null && newValue != null) {
            rows += ReprocessDiffRow(res(field.labelRes), "—", "${MacroValueFormatter.string(newValue)} ${res(field.unitRes)}")
        }
    }
    before.constituents.forEachIndexed { index, row ->
        val filled = after.constituents.getOrNull(index) ?: return@forEachIndexed
        val wasZero = row.calories == 0 && row.protein == 0.0 && row.carbs == 0.0 && row.fat == 0.0
        val hasValues = filled.calories != 0 || filled.protein != 0.0 || filled.carbs != 0.0 || filled.fat != 0.0
        if (wasZero && hasValues) {
            rows += ReprocessDiffRow(row.name, "—", "${LocaleFormat.integer(filled.calories)} $kcalUnit")
        }
    }
    return rows
}

/** Existing-vocabulary labels for the per-nutrient focus dropdown (WP6). */
@Composable
private fun focusLabel(group: GapGroup): String = stringResource(
    when (group) {
        GapGroup.CALORIES -> R.string.nutrition_label_calories
        GapGroup.PROTEIN -> R.string.nutrition_label_protein
        GapGroup.CARBS -> R.string.nutrition_label_carbs
        GapGroup.FAT -> R.string.nutrition_label_fat
        GapGroup.MICROS -> R.string.nutrition_section_detailed
    },
)

@Composable
private fun GapCaption(item: FillQueueItem) {
    when (item.status) {
        FillStatus.PENDING -> {
            // Same missing-list semantics the edit sheet renders (macros first).
            val gap = item.target.entry.gapReport()
            val missingLabels = (
                gap.missingMacros.map { stringResource(it.labelRes) } +
                    gap.missingMicros.map { stringResource(it.labelRes) }
                ).ifEmpty {
                    // Constituents-only rows (cocktails, Mealie imports) would
                    // render a blank "Missing:" — name the gapped rows instead.
                    gap.zeroMacroConstituents.map { it.name.trim() }.joinToString(", ")
                }
            GapMutedText(stringResource(R.string.edit_ai_missing_line, missingLabels))
        }
        FillStatus.FILLED -> {
            if (item.filledLabels.isEmpty()) {
                GapMutedText(stringResource(R.string.fill_gaps_no_values))
            } else {
                val labelStrings = item.filledLabels.map { stringResource(it) }
                // Result feedback, not a caption: the edit sheet's diff rows
                // set the emphasized 13sp convention.
                Text(
                    stringResource(R.string.fill_gaps_adds_line, labelStrings.joinToString(", ")),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FillStatus.FAILED -> item.errorText?.let { error ->
            Text(
                error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        else -> {}
    }
}

@Composable
private fun GapMutedText(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** UX-6: opt-in cascade for a filled favorite — fill its today-copies at Apply, no extra AI calls. */
@Composable
private fun GapCascadeRow(
    item: FillQueueItem,
    engine: app.chompass.services.FillGapsEngine,
    cascadeChecks: MutableMap<UUID, Boolean>,
) {
    val cascadeCount by produceState(initialValue = -1, item.target.entry.id) {
        value = engine.cascadeCandidates(item.target.entry).size
    }
    if (cascadeCount <= 0) return
    val checked = cascadeChecks[item.target.entry.id] == true
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { cascadeChecks[item.target.entry.id] = !checked },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { cascadeChecks[item.target.entry.id] = it },
        )
        Text(
            pluralStringResource(R.plurals.fill_gaps_cascade, cascadeCount, cascadeCount),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GapRowSurface(
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val isDark = isDarkTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f))
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.large,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
