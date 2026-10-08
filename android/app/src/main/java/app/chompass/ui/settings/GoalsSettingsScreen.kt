package app.chompass.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.navigation.ChompassRoutes
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

    SettingsSubScreen(
        title = stringResource(R.string.settings_section_goals),
        onBack = onBack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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

        // Rule C footer: destinations related to goals that live elsewhere
        // (Water goal is edited on the Water screen; the formula register is a
        // reference, not a goal).
        RelatedLinks(
            rows = listOf(
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
        RecalcResultSheet(data = sheet, onDismiss = { vm.dismissRecalcSheet() })
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
}
