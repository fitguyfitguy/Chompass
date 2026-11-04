package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.CoachQuickPromptEntry
import app.chompass.ui.coach.coachBuiltinDefaultIds
import app.chompass.ui.navigation.ChompassRoutes
import kotlinx.coroutines.launch

/**
 * Coach controls: the tab toggle plus custom instructions. The toggle lives
 * on its own screen (not behind the master AI switch) so it is always
 * reachable; the master AI-features switch still gates the behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var showPromptsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Stored selection read straight from the flow (no snapshot hydration):
    // null = goal defaults, empty = explicitly hidden (#155).
    val storedPrompts by container.prefs.coachQuickPrompts.collectAsState(initial = null)
    val defaults = coachBuiltinDefaultIds(ui.profile?.goal).map { CoachQuickPromptEntry(id = it) }
    val effectivePrompts = storedPrompts?.resolveFor(defaults) ?: defaults
    val promptsSummary = if (effectivePrompts.isEmpty()) {
        stringResource(R.string.home_calorie_layout_hidden)
    } else {
        stringResource(R.string.settings_coach_quick_prompts_shown, effectivePrompts.size)
    }

    SettingsSubScreen(
        title = stringResource(R.string.settings_group_coach),
        onBack = onBack,
    ) {
        SectionCard(title = stringResource(R.string.settings_group_coach)) {
            ToggleRow(
                stringResource(R.string.settings_show_coach_tab),
                ui.coachTabEnabled,
                icon = Icons.Outlined.Forum,
                onChange = { vm.setCoachTabEnabled(it) }
            )
            SettingFootnote(stringResource(R.string.settings_show_coach_tab_footer))
            SettingRow(
                label = stringResource(R.string.settings_coach_quick_prompts),
                value = promptsSummary,
                icon = Icons.Outlined.Chat,
                onClick = { showPromptsSheet = true },
            )
            SettingFootnote(stringResource(R.string.settings_coach_quick_prompts_footer))
        }
        SettingsCustomInstructionsSection(ui = ui, vm = vm)
        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_group_ai)) {
                    nav.navigate(ChompassRoutes.SETTINGS_AI)
                },
            ),
        )
    }

    if (showPromptsSheet) {
        CoachQuickPromptsSheet(
            initial = effectivePrompts,
            onDismiss = { showPromptsSheet = false },
            onSave = { prompts ->
                showPromptsSheet = false
                scope.launch { container.prefs.setCoachQuickPrompts(prompts) }
            },
        )
    }
}
