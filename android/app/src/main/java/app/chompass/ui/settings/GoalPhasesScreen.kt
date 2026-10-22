package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.CalorieSafety
import app.chompass.models.DayTargets
import app.chompass.models.EnergyFormat
import app.chompass.models.GoalJournal
import app.chompass.models.GoalPhase
import app.chompass.models.LocaleFormat
import app.chompass.models.PhaseEditError
import app.chompass.models.PhaseIntent
import app.chompass.models.UnitFormat
import app.chompass.models.MacroPlanResolver
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.components.ChompassTextField
import app.chompass.ui.components.ChompassPrimaryButton
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.ChompassPinnedFooterSheet
import app.chompass.ui.components.energyUnitLabel
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.navigation.LocalEnergyUnit
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.success
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Settings → Goals → Goal phases (Progress v2): list of dated cut/maintain/
 * bulk windows with a per-phase adherence line, plus the add/edit sheet.
 * Writes go through [SettingsViewModel.saveGoalPhase] / [deleteGoalPhase],
 * which funnel into GoalPhaseEdit (pure day-type mechanics).
 */
@Composable
fun GoalPhasesScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    val phases = ui.profile?.goalPhases.orEmpty().sortedWith(
        compareBy({ it.startDate }, { it.createdAtMillis })
    )
    var sheetPhase by remember { mutableStateOf<GoalPhase?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    // Per-phase adherence lines (avg Δ kcal/day · net over the window), loaded
    // from the diary aggregates + journal-resolved goals.
    var adherenceLines by remember { mutableStateOf<Map<String, PhaseAdherenceLine>>(emptyMap()) }
    val adherenceKey = phases.joinToString("|") { "${it.id}:${it.startDate}:${it.endDate}:${it.calories}" }
    LaunchedEffect(adherenceKey) {
        for (phase in phases) {
            vm.phaseAdherence(phase)?.let { line ->
                adherenceLines = adherenceLines + (phase.id.toString() to line)
            }
        }
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_goal_phases_title),
        onBack = onBack,
    ) {
        Text(
            stringResource(R.string.settings_goal_phases_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        SectionCard(title = stringResource(R.string.settings_goal_phases_title)) {
            if (phases.isEmpty()) {
                Text(
                    stringResource(R.string.settings_goal_phases_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            phases.forEachIndexed { index, phase ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                }
                PhaseRow(
                    phase = phase,
                    adherence = adherenceLines[phase.id.toString()],
                    onClick = { sheetPhase = phase; showSheet = true },
                )
            }
            TextButton(
                onClick = { sheetPhase = null; showSheet = true },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_goal_phases_add))
            }
        }
    }

    if (showSheet) {
        val baseTargets = ui.profile?.let { MacroPlanResolver.baseTargets(it) }
        GoalPhaseEditSheet(
            editing = sheetPhase,
            base = baseTargets,
            calorieFloor = ui.profile?.let { CalorieSafety.floorKcal(it.bmr) }
                ?: CalorieSafety.ABSOLUTE_FLOOR_KCAL,
            calorieCeiling = ui.profile?.let {
                CalorieSafety.ceilingKcal(it.tdee, CalorieSafety.floorKcal(it.bmr))
            } ?: CalorieSafety.PARSER_CEILING_KCAL,
            onSave = { draft, onResult ->
                vm.saveGoalPhase(draft) { error ->
                    if (error == null) showSheet = false
                    onResult(error)
                }
            },
            onDelete = { phase ->
                vm.deleteGoalPhase(phase.id)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun PhaseRow(
    phase: GoalPhase,
    adherence: PhaseAdherenceLine?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(phase.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                IntentChip(phase.intent)
            }
            Spacer(Modifier.height(2.dp))
            val zone = ZoneId.systemDefault()
            val fmt = remember { LocaleFormat.shortDate() }
            val unit = LocalEnergyUnit.current
            val start = GoalJournal.parseDateOrNull(phase.startDate)
            val end = GoalJournal.parseDateOrNull(phase.endDate)
            val range = if (start != null && end != null) {
                "${fmt.format(start.atStartOfDay(zone))} – ${fmt.format(end.atStartOfDay(zone))} · ${LocaleFormat.integer(EnergyFormat.quantity(phase.calories, unit))} ${energyUnitLabel()}"
            } else {
                LocaleFormat.integer(EnergyFormat.quantity(phase.calories, unit)).toString()
            }
            Text(
                range,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
            if (adherence != null && adherence.avgDelta != null) {
                Text(
                    stringResource(
                        R.string.settings_goal_phases_adherence_format,
                        UnitFormat.signedDelta(EnergyFormat.quantity(adherence.avgDelta, unit).toDouble()) + " " + energyUnitLabel(),
                        UnitFormat.signedDelta(EnergyFormat.quantity(adherence.netKcal?.toInt() ?: 0, unit).toDouble()) + " " + energyUnitLabel(),
                    ) + " · " + stringResource(R.string.settings_goal_phases_overlap_days, adherence.overlapDays),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
            modifier = Modifier.width(18.dp),
        )
    }
}

/** CUT error tone, MAINTAIN neutral, BULK success — the plan's chip tones. */
@Composable
private fun IntentChip(intent: PhaseIntent) {
    val label = stringResource(
        when (intent) {
            PhaseIntent.CUT -> R.string.phase_intent_cut
            PhaseIntent.MAINTAIN -> R.string.phase_intent_maintain
            PhaseIntent.BULK -> R.string.phase_intent_bulk
        }
    )
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when (intent) {
        PhaseIntent.CUT -> scheme.errorContainer to scheme.error
        PhaseIntent.MAINTAIN -> scheme.surfaceContainerHigh to scheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
        PhaseIntent.BULK -> scheme.success.copy(alpha = 0.16f) to scheme.success
    }
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Add/edit sheet: name, intent chips, start/end date wheels, target wheels. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun GoalPhaseEditSheet(
    editing: GoalPhase?,
    base: DayTargets?,
    calorieFloor: Int,
    calorieCeiling: Int,
    onSave: (GoalPhase, onResult: (PhaseEditError?) -> Unit) -> Unit,
    onDelete: (GoalPhase) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
    var intent by remember(editing) { mutableStateOf(editing?.intent ?: PhaseIntent.CUT) }
    var start by remember(editing) { mutableStateOf(editing?.startDate ?: today.toString()) }
    var end by remember(editing) {
        mutableStateOf(editing?.endDate ?: today.plusDays(13).toString())
    }
    var calories by remember(editing) {
        mutableIntStateOf((editing?.calories ?: base?.calories ?: 2000).coerceIn(calorieFloor, calorieCeiling))
    }
    var protein by remember(editing) { mutableIntStateOf(editing?.proteinG ?: base?.proteinG ?: 150) }
    var carbs by remember(editing) { mutableIntStateOf(editing?.carbsG ?: base?.carbsG ?: 200) }
    var fat by remember(editing) { mutableIntStateOf(editing?.fatG ?: base?.fatG ?: 65) }
    var error by remember { mutableStateOf<PhaseEditError?>(null) }

    fun draft(): GoalPhase = GoalPhase(
        id = editing?.id ?: UUID.randomUUID(),
        name = name.trim(),
        intent = intent,
        startDate = start,
        endDate = end,
        calories = calories,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
        profileId = editing?.profileId,
        createdAtMillis = editing?.createdAtMillis ?: 0L,
    )

    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = rememberChompassSheetState(),
        toolbar = {
            Text(
                stringResource(
                    if (editing != null) R.string.settings_goal_phases_edit
                    else R.string.settings_goal_phases_add
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 12.dp),
            ) {
                ChompassTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) name = it },
                    placeholder = stringResource(R.string.settings_goal_phases_name_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhaseIntent.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == intent,
                            onClick = { intent = candidate },
                            label = {
                                Text(
                                    stringResource(
                                        when (candidate) {
                                            PhaseIntent.CUT -> R.string.phase_intent_cut
                                            PhaseIntent.MAINTAIN -> R.string.phase_intent_maintain
                                            PhaseIntent.BULK -> R.string.phase_intent_bulk
                                        }
                                    ),
                                    fontSize = 12.sp,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_goal_phases_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                DateWheelPicker(
                    selected = GoalJournal.parseDateOrNull(start) ?: today,
                    onSelect = { start = it.toString() },
                    maxYear = today.year + 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_goal_phases_end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                DateWheelPicker(
                    selected = GoalJournal.parseDateOrNull(end) ?: today.plusDays(13),
                    onSelect = { end = it.toString() },
                    maxYear = today.year + 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.settings_goal_phases_range_clip_hint, app.chompass.models.MacroPlan.ASSIGNMENT_KEEP_DAYS.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_goal_phases_targets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                Spacer(Modifier.height(6.dp))
                val unit = LocalEnergyUnit.current
                EditorWheelLabel(stringResource(R.string.macro_calories), unit = energyUnitLabel())
                NumericWheelPicker(
                    value = EnergyFormat.quantity(calories, unit),
                    onValueChange = { calories = EnergyFormat.toKcal(it, unit) },
                    min = EnergyFormat.quantity(calorieFloor, unit),
                    max = EnergyFormat.quantity(calorieCeiling, unit),
                    unit = energyUnitLabel(),
                    step = EnergyFormat.wheelStep(25, unit),
                )
                if (calories <= calorieFloor) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_day_types_floor_warning, EnergyFormat.quantity(calorieFloor, unit), energyUnitLabel()),
                        color = AppColors.Calorie,
                    )
                }
                Spacer(Modifier.height(12.dp))
                EditorWheelLabel(stringResource(R.string.macro_protein), unit = stringResource(R.string.unit_g))
                NumericWheelPicker(
                    value = protein,
                    onValueChange = { protein = it },
                    min = 0,
                    max = 400,
                    unit = stringResource(R.string.unit_g),
                    step = 5,
                )
                Spacer(Modifier.height(12.dp))
                EditorWheelLabel(stringResource(R.string.macro_carbs), unit = stringResource(R.string.unit_g))
                NumericWheelPicker(
                    value = carbs,
                    onValueChange = { carbs = it },
                    min = 0,
                    max = 800,
                    unit = stringResource(R.string.unit_g),
                    step = 5,
                )
                Spacer(Modifier.height(12.dp))
                EditorWheelLabel(stringResource(R.string.macro_fat), unit = stringResource(R.string.unit_g))
                NumericWheelPicker(
                    value = fat,
                    onValueChange = { fat = it },
                    min = 10,
                    max = 300,
                    unit = stringResource(R.string.unit_g),
                    step = 5,
                )
                Spacer(Modifier.height(8.dp))
                error?.let { err ->
                    Text(
                        text = when (err) {
                            PhaseEditError.BlankName -> stringResource(R.string.settings_goal_phases_error_blank_name)
                            PhaseEditError.BadRange -> stringResource(R.string.settings_goal_phases_error_bad_range)
                            is PhaseEditError.TooLong -> stringResource(R.string.settings_goal_phases_error_too_long, app.chompass.models.MacroPlan.ASSIGNMENT_KEEP_DAYS.toInt())
                            PhaseEditError.NoProfileSlot -> stringResource(R.string.settings_goal_phases_error_no_slot)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        footer = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                ChompassPrimaryButton(
                    flushDrafts = true,
                    enabled = name.isNotBlank(),
                    onClick = {
                        error = null
                        onSave(draft()) { result -> error = result }
                    },
                )
                if (editing != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.settings_goal_phases_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )

    if (confirmDelete && editing != null) {
        ChompassDialog(onDismissRequest = { confirmDelete = false }) {
            Text(
                stringResource(R.string.settings_goal_phases_delete_title, editing.name),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.settings_goal_phases_delete_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary),
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_delete),
                onPrimary = {
                    confirmDelete = false
                    onDelete(editing)
                },
            )
        }
    }
}
