package app.chompass.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R

@Composable
internal fun SettingsCustomInstructionsSection(ui: SettingsUiState, vm: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.settings_section_custom_instructions)) {
                CustomInstructionsBlock(
                    initial = ui.userContext,
                    placeholder = stringResource(R.string.settings_custom_instructions_placeholder),
                    onSave = { vm.setUserContext(it) }
                )
                Text(
                    stringResource(R.string.settings_custom_instructions_footer),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
    }
}
