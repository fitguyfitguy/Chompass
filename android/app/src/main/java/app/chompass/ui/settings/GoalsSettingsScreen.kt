package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.components.ChompassSnackbarHost
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val ketoPausedMessage = stringResource(R.string.settings_day_types_keto_paused)
    // A keto switch pauses a live day-type plan (Q4): tell the user the plan
    // survived instead of silently losing their schedule.
    LaunchedEffect(ui.dayTypesKetoPausedTick) {
        if (ui.dayTypesKetoPausedTick > 0) snackbarHostState.showSnackbar(ketoPausedMessage)
    }
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var invalidGoalWeightMessage by remember { mutableStateOf<String?>(null) }
    var showRebalanceBlockedAlert by remember { mutableStateOf(false) }
    var showAdaptiveGoalsInfo by remember { mutableStateOf(false) }
    var showCalorieModePicker by remember { mutableStateOf(false) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_section_goals),
        onBack = onBack,
        snackbarHost = { ChompassSnackbarHost(hostState = snackbarHostState) },
    ) {
        Text(
            stringResource(R.string.settings_goals_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        SettingsGoalsSection(
            ui = ui,
            profile = ui.profile,
            vm = vm,
            nav = nav,
            onOpenSheet = { sheet = it },
            onShowAdaptiveGoalsInfo = { showAdaptiveGoalsInfo = true },
        )

        // Calorie gauge mode: moved here from Customize Home because people
        // look for the burn control under Goals. Not a profile field — the
        // card stays visible even without a profile, unlike the goals card
        // above. The full-credit toggle only exists in Add Active mode.
        val display = ui.homeDisplay
        SectionCard(title = stringResource(R.string.home_display_calorie_mode)) {
            SettingRow(
                label = stringResource(R.string.home_display_calorie_mode),
                value = stringResource(display.calorieDisplayMode.displayNameRes),
                onClick = { showCalorieModePicker = true },
            )
            Text(
                stringResource(
                    when (display.calorieDisplayMode) {
                        HomeCalorieDisplayMode.STATIC -> R.string.home_calorie_mode_static_desc
                        HomeCalorieDisplayMode.ADD_ACTIVE -> R.string.home_calorie_mode_add_active_desc
                    }
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            if (display.calorieDisplayMode == HomeCalorieDisplayMode.ADD_ACTIVE) {
                Text(
                    stringResource(R.string.home_calorie_mode_add_active_hc_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ToggleRow(
                    label = stringResource(R.string.home_calorie_full_credit),
                    checked = display.fullCreditActiveBurn,
                    onChange = vm::setHomeCalorieFullCredit,
                )
                Text(
                    stringResource(R.string.home_calorie_full_credit_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Rule C footer: destinations related to goals that live elsewhere
        // (Water goal is edited on the Water screen; the formula register is a
        // reference, not a goal).
        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_goal_phases_title)) {
                    nav.navigate(ChompassRoutes.GOAL_PHASES)
                },
                RelatedLink(label = stringResource(R.string.settings_water_title)) {
                    nav.navigate(ChompassRoutes.waterRoute("goals"))
                },
                RelatedLink(label = stringResource(R.string.settings_calc_methods)) {
                    nav.navigate(ChompassRoutes.CALCULATION_METHODS)
                },
            ),
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = { invalidGoalWeightMessage = it },
            onRebalanceBlocked = { showRebalanceBlockedAlert = true },
        )
    }

    if (showRebalanceBlockedAlert) {
        ChompassDialog(onDismissRequest = { showRebalanceBlockedAlert = false }) {
            Text(stringResource(R.string.settings_rebalance_blocked_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_rebalance_blocked_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showRebalanceBlockedAlert = false }
            )
        }
    }

    if (showAdaptiveGoalsInfo) {
        ChompassDialog(onDismissRequest = { showAdaptiveGoalsInfo = false }) {
            Text(stringResource(R.string.settings_adaptive_goals), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_adaptive_goals_info),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showAdaptiveGoalsInfo = false }
            )
        }
    }

    val adaptiveAlertTitle = ui.adaptiveGoalAlertTitle
    val adaptiveAlertMessage = ui.adaptiveGoalAlertMessage
    if (adaptiveAlertTitle != null && adaptiveAlertMessage != null) {
        ChompassDialog(onDismissRequest = { vm.dismissAdaptiveGoalAlert() }) {
            Text(adaptiveAlertTitle, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                adaptiveAlertMessage,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { vm.dismissAdaptiveGoalAlert() }
            )
        }
    }

    ui.recalcSheet?.let { sheet ->
        val pending = ui.pendingRecalc
        if (pending != null) {
            RecalcResultSheet(
                data = sheet,
                onDismiss = { vm.discardPendingRecalc() },
                editable = true,
                onEdit = { field, value -> vm.editPendingRecalc(field, value) },
                onApply = { vm.applyPendingRecalc() },
                onDiscard = { vm.discardPendingRecalc() },
            )
        } else {
            RecalcResultSheet(data = sheet, onDismiss = { vm.dismissRecalcSheet() })
        }
    }

    invalidGoalWeightMessage?.let { msg ->
        ChompassDialog(onDismissRequest = { invalidGoalWeightMessage = null }) {
            Text(stringResource(R.string.settings_invalid_goal_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary))
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { invalidGoalWeightMessage = null }
            )
        }
    }

    if (showCalorieModePicker) {
        CalorieModePickerDialog(
            selected = ui.homeDisplay.calorieDisplayMode,
            onSelect = {
                vm.setHomeCalorieDisplayMode(it)
                showCalorieModePicker = false
            },
            onDismiss = { showCalorieModePicker = false }
        )
    }
}

@Composable
private fun CalorieModePickerDialog(
    selected: HomeCalorieDisplayMode,
    onSelect: (HomeCalorieDisplayMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ChompassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.home_display_calorie_mode), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeCalorieDisplayMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(mode.displayNameRes),
                        fontWeight = if (mode == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (mode == selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        ChompassDialogActions(
            primaryText = stringResource(R.string.action_done),
            onPrimary = onDismiss,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}
