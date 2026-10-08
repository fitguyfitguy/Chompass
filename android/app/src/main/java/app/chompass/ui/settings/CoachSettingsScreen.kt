package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.navigation.ChompassRoutes

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
}
