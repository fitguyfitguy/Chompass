package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.theme.AppTextOpacity

/**
 * Optional daily notes (Codeberg #58a): one toggle that shows the Home note
 * card. Default off; notes that already exist stay stored when toggled off,
 * so re-enabling restores them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
    from: String,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()

    SettingsSubScreen(
        title = stringResource(R.string.settings_notes_title),
        onBack = onBack,
        backLabel = settingsBackLabel(from),
    ) {
        Text(
            stringResource(R.string.settings_notes_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )

        SectionCard(title = stringResource(R.string.settings_notes_section_tracking)) {
            ToggleRow(
                stringResource(R.string.settings_notes_tracking),
                ui.dailyNotesEnabled,
                icon = Icons.Outlined.Notes,
                onChange = vm::setDailyNotesEnabled,
            )
        }

        SettingFootnote(stringResource(R.string.settings_notes_privacy_note))
    }
}
