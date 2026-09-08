package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanEdit
import app.chompass.models.MacroPlanMode
import app.chompass.models.MacroPlanResolver
import app.chompass.models.UserProfile
import app.chompass.models.GoalJournal
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Settings → Goals → Day types editor (Codeberg #60 phase 2). Everything here
 * writes through [SettingsViewModel]'s MacroPlanEdit-backed functions; the
 * profile flow then refreshes today's goal-journal entry and the widget
 * snapshot for free (phase 1 wiring).
 *
 * Only reachable in STANDARD diet mode — the Goals-section row hides in keto,
 * and a live plan is paused on the keto switch.
 */
@Composable
fun DayTypesSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    val profile = ui.profile
    val plan = profile?.macroPlan
    val today = remember { LocalDate.now() }

    var editingProfile by remember { mutableStateOf<MacroDayProfile?>(null) }
    var addingProfile by remember { mutableStateOf(false) }
    var pickDefault by remember { mutableStateOf(false) }
    var pickWeekday by remember { mutableStateOf<DayOfWeek?>(null) }
    var pickPatternIndex by remember { mutableStateOf<Int?>(null) }
    var pickDay by remember { mutableStateOf<LocalDate?>(null) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_day_types_title),
        onBack = onBack,
        backLabel = stringResource(R.string.settings_section_goals),
    ) {
        Text(
            stringResource(R.string.settings_day_types_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )

        SectionCard(title = stringResource(R.string.settings_day_types_title)) {
            DayTypesMasterToggle(
                plan = plan,
                onChange = vm::setDayTypesEnabled,
            )
            plan?.profiles?.forEachIndexed { index, p ->
                HorizontalDivider()
                DayTypeListRow(
                    profile = p,
                    first = index == 0,
                    last = index == plan.profiles.lastIndex,
                    onClick = { editingProfile = p },
                    onMoveUp = {
                        vm.reorderDayTypeProfiles(
                            plan.profiles.map { it.id }.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                        )
                    },
                    onMoveDown = {
                        vm.reorderDayTypeProfiles(
                            plan.profiles.map { it.id }.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                        )
                    },
                )
            }
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = (plan?.profiles?.size ?: 0) < MacroPlanEdit.MAX_PROFILES) {
                        addingProfile = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = AppColors.Calorie,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.settings_day_types_add),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Calorie,
                )
            }
            if (plan != null && !plan.enabled && plan.profiles.isNotEmpty()) {
                SettingFootnote(stringResource(R.string.settings_day_types_paused_hint))
            } else if ((plan?.profiles?.size ?: 0) < MacroPlanEdit.MIN_PROFILES) {
                SettingFootnote(stringResource(R.string.settings_day_types_need_two))
            }
        }

        if (plan?.enabled == true) {
            DayTypesScheduleCard(
                plan = plan,
                onMode = vm::setDayTypesMode,
                onPickDefault = { pickDefault = true },
                onPickWeekday = { pickWeekday = it },
                onPickPatternIndex = { pickPatternIndex = it },
                onPatternAdd = {
                    vm.setDayTypesCyclePattern(plan.cyclePattern + (plan.defaultProfileId ?: plan.profiles.first().id))
                },
                onPatternRemove = {
                    vm.setDayTypesCyclePattern(plan.cyclePattern.dropLast(1))
                },
                onRestart = vm::restartDayTypesCycle,
            )

            DayTypesOverridesCard(
                profile = profile,
                plan = plan,
                today = today,
                onPickDay = { pickDay = it },
                onClearOverride = { vm.setDayTypesAssignment(it, null) },
            )
        }
    }

    if (editingProfile != null || addingProfile) {
        DayTypeProfileEditorSheet(
            existing = plan?.profiles,
            editing = editingProfile,
            base = profile?.let { MacroPlanResolver.baseTargets(it) },
            calorieFloor = profile?.let { app.chompass.models.CalorieSafety.floorKcal(it.bmr) }
                ?: app.chompass.models.CalorieSafety.ABSOLUTE_FLOOR_KCAL,
            calorieCeiling = profile?.let {
                app.chompass.models.CalorieSafety.ceilingKcal(it.tdee, app.chompass.models.CalorieSafety.floorKcal(it.bmr))
            } ?: app.chompass.models.CalorieSafety.PARSER_CEILING_KCAL,
            isReferenced = editingProfile?.let { MacroPlanEdit.isProfileReferenced(plan, it.id) } ?: false,
            replacementOptions = editingProfile
                ?.let { editing -> plan?.profiles?.filterNot { it.id == editing.id }?.map { it.name } }
                ?: emptyList(),
            onSave = { saved ->
                vm.saveDayTypeProfile(saved)
                editingProfile = null
                addingProfile = false
            },
            onDelete = { replacementName ->
                editingProfile?.let { victim ->
                    val replacementId = replacementName
                        ?.let { name -> plan?.profiles?.firstOrNull { it.name == name }?.id }
                    vm.deleteDayTypeProfile(victim.id, replacementId)
                }
                editingProfile = null
                addingProfile = false
            },
            onDismiss = {
                editingProfile = null
                addingProfile = false
            },
        )
    }

    val profiles = plan?.profiles.orEmpty()
    if (pickDefault) {
        DayTypePickerSheet(
            title = stringResource(R.string.settings_day_types_default),
            profiles = profiles,
            selectedId = plan?.defaultProfileId,
            onSelect = {
                vm.setDayTypesDefault(it.id)
                pickDefault = false
            },
            onDismiss = { pickDefault = false },
        )
    }
    pickWeekday?.let { day ->
        DayTypePickerSheet(
            title = weekdayLabel(day),
            profiles = profiles,
            selectedId = plan?.weekdayProfileIds?.get(day.name) ?: plan?.defaultProfileId,
            defaultOption = plan?.defaultProfileId?.let { id ->
                profiles.firstOrNull { it.id == id }?.name
            },
            onSelect = {
                vm.setDayTypesWeekday(day, it.id)
                pickWeekday = null
            },
            onSelectDefault = {
                vm.setDayTypesWeekday(day, null)
                pickWeekday = null
            },
            onDismiss = { pickWeekday = null },
        )
    }
    pickPatternIndex?.let { index ->
        DayTypePickerSheet(
            title = stringResource(R.string.settings_day_types_pattern_day, index + 1),
            profiles = profiles,
            selectedId = plan?.cyclePattern?.getOrNull(index),
            onSelect = { picked ->
                plan?.let { p ->
                    vm.setDayTypesCyclePattern(p.cyclePattern.toMutableList().apply { set(index, picked.id) })
                }
                pickPatternIndex = null
            },
            onDismiss = { pickPatternIndex = null },
        )
    }
    pickDay?.let { day ->
        val resolved = profile?.let { MacroPlanResolver.resolve(it.macroPlan, MacroPlanResolver.baseTargets(it), day) }
        DayTypePickerSheet(
            title = previewDateLabel(day),
            profiles = profiles,
            selectedId = resolved?.profileId ?: plan?.defaultProfileId,
            followScheduleOption = plan?.dayAssignments?.containsKey(day.toString()) == true,
            onSelect = { picked ->
                vm.setDayTypesAssignment(day, picked.id)
                pickDay = null
            },
            onSelectFollowSchedule = {
                vm.setDayTypesAssignment(day, null)
                pickDay = null
            },
            onDismiss = { pickDay = null },
        )
    }
}

/** Master on/off; the switch is inert until 2..7 profiles exist. */
@Composable
private fun DayTypesMasterToggle(plan: MacroPlan?, onChange: (Boolean) -> Unit) {
    val canEnable = (plan?.profiles?.size ?: 0) in MacroPlanEdit.MIN_PROFILES..MacroPlanEdit.MAX_PROFILES
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Event,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.settings_day_types_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Switch(checked = plan?.enabled == true, onCheckedChange = onChange, enabled = canEnable)
    }
}

/** One profile row: name, targets, reorder arrows; opens the editor. */
@Composable
private fun DayTypeListRow(
    profile: MacroDayProfile,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.day_type_targets_summary,
                    LocaleFormat.integer(profile.calories),
                    profile.proteinG,
                    profile.carbsG,
                    profile.fatG,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        if (!first) {
            val upCd = stringResource(R.string.settings_day_types_move_up, profile.name)
            IconButton(
                onClick = onMoveUp,
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = upCd },
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        if (!last) {
            val downCd = stringResource(R.string.settings_day_types_move_down, profile.name)
            IconButton(
                onClick = onMoveDown,
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = downCd },
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Mode picker (Manual / Weekdays / Cycle) + the mode's own config rows. */
@Composable
private fun DayTypesScheduleCard(
    plan: MacroPlan,
    onMode: (MacroPlanMode) -> Unit,
    onPickDefault: () -> Unit,
    onPickWeekday: (DayOfWeek) -> Unit,
    onPickPatternIndex: (Int) -> Unit,
    onPatternAdd: () -> Unit,
    onPatternRemove: () -> Unit,
    onRestart: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_day_types_schedule)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = plan.mode == MacroPlanMode.MANUAL,
                    onClick = { onMode(MacroPlanMode.MANUAL) },
                    label = { Text(stringResource(R.string.settings_day_types_mode_manual)) },
                )
                FilterChip(
                    selected = plan.mode == MacroPlanMode.WEEKDAYS,
                    onClick = { onMode(MacroPlanMode.WEEKDAYS) },
                    label = { Text(stringResource(R.string.settings_day_types_mode_weekdays)) },
                )
                FilterChip(
                    selected = plan.mode == MacroPlanMode.CYCLE,
                    onClick = { onMode(MacroPlanMode.CYCLE) },
                    label = { Text(stringResource(R.string.settings_day_types_mode_cycle)) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (plan.mode) {
                    MacroPlanMode.MANUAL -> stringResource(R.string.settings_day_types_mode_manual_hint)
                    MacroPlanMode.WEEKDAYS -> stringResource(R.string.settings_day_types_mode_weekdays_hint)
                    MacroPlanMode.CYCLE -> stringResource(R.string.settings_day_types_mode_cycle_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }

        val defaultName = plan.profileById(plan.defaultProfileId)?.name
            ?: stringResource(R.string.settings_not_set)
        HorizontalDivider()
        SettingRow(
            stringResource(R.string.settings_day_types_default),
            defaultName,
        ) { onPickDefault() }

        when (plan.mode) {
            MacroPlanMode.WEEKDAYS -> {
                DayOfWeek.entries.forEach { day ->
                    HorizontalDivider()
                    val assigned = plan.weekdayProfileIds[day.name]
                        ?.let { plan.profileById(it)?.name }
                    SettingRow(
                        weekdayLabel(day),
                        assigned ?: stringResource(R.string.settings_day_types_weekday_default, defaultName),
                    ) { onPickWeekday(day) }
                }
            }
            MacroPlanMode.CYCLE -> {
                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_day_types_pattern),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                plan.cyclePattern.forEachIndexed { index, profileId ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPickPatternIndex(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_day_types_pattern_day, index + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            plan.profileById(profileId)?.name ?: stringResource(R.string.settings_not_set),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    if (plan.cyclePattern.size < MacroPlanEdit.MAX_PROFILES) {
                        TextButton(onClick = onPatternAdd, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_day_types_pattern_add))
                        }
                    }
                    if (plan.cyclePattern.size > MacroPlanEdit.MIN_PROFILES) {
                        TextButton(onClick = onPatternRemove, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_day_types_pattern_remove))
                        }
                    }
                }
                TextButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_day_types_restart))
                }
            }
            MacroPlanMode.MANUAL -> Unit
        }
    }
}

/** Next-7-days preview (tappable → day override) + every explicit override with clear. */
@Composable
private fun DayTypesOverridesCard(
    profile: UserProfile?,
    plan: MacroPlan,
    today: LocalDate,
    onPickDay: (LocalDate) -> Unit,
    onClearOverride: (LocalDate) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_day_types_preview)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.settings_day_types_preview_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        (0L..6L).forEach { offset ->
            if (offset > 0) HorizontalDivider()
            val day = today.plusDays(offset)
            val resolved = profile?.let {
                MacroPlanResolver.resolve(it.macroPlan, MacroPlanResolver.baseTargets(it), day)
            }
            val hasOverride = plan.dayAssignments.containsKey(day.toString())
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPickDay(day) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    previewDateLabel(day),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(96.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        resolved?.profileName ?: stringResource(R.string.day_type_base_goals),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    resolved?.let {
                        Text(
                            "${LocaleFormat.integer(it.targets.calories)} ${stringResource(R.string.unit_kcal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                }
                if (hasOverride) {
                    Text(
                        stringResource(R.string.settings_day_types_override),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Calorie,
                    )
                }
            }
        }
    }

    val overrides = plan.dayAssignments.entries
        .mapNotNull { (date, id) ->
            GoalJournal.parseDateOrNull(date)?.let { d ->
                OverrideRow(d, plan.profileById(id)?.name ?: stringResource(R.string.settings_not_set))
            }
        }
        .sortedBy { it.date }
    if (overrides.isNotEmpty()) {
        SectionCard(title = stringResource(R.string.settings_day_types_overrides)) {
            overrides.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(previewDateLabel(row.date), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            row.profileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                    TextButton(onClick = { onClearOverride(row.date) }) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
            }
        }
    }
}

private data class OverrideRow(val date: LocalDate, val profileName: String)

/** "Mon, Aug 31" in the user's locale. */
@Composable
private fun previewDateLabel(day: LocalDate): String {
    val locale = LocaleFormat.first(java.util.Locale.getDefault())
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    }
    return day.format(formatter)
}

/** "Monday" localized; week starts Monday, matching the weekday-map order. */
@Composable
private fun weekdayLabel(day: DayOfWeek): String {
    val locale = LocaleFormat.first(java.util.Locale.getDefault())
    return remember(day, locale) {
        day.getDisplayName(TextStyle.FULL, locale)
    }
}
