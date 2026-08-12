package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.components.FudGlassSurface

/** Static settings hub layout for release screenshot previews. */
@Composable
internal fun SettingsScreenPreviewContent(
    @Suppress("UNUSED_PARAMETER") ui: SettingsUiState,
    @Suppress("UNUSED_PARAMETER") latestMeasurementWaistCm: Double? = null,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.nav_settings),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            FudGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                padding = 0.dp,
                allowBlur = false,
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingsHubRow(
                        label = stringResource(R.string.settings_section_personal),
                        summary = stringResource(R.string.settings_group_personal_summary),
                        icon = Icons.Outlined.Person,
                        onClick = {},
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_section_goals),
                        summary = stringResource(R.string.settings_group_goals_summary),
                        icon = Icons.Outlined.Equalizer,
                        onClick = {},
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_food),
                        summary = stringResource(R.string.settings_group_food_summary),
                        icon = Icons.Outlined.Restaurant,
                        onClick = {},
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_app_display),
                        summary = stringResource(R.string.settings_group_app_summary),
                        icon = Icons.Outlined.Settings,
                        onClick = {},
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_ai),
                        summary = stringResource(R.string.settings_group_ai_summary),
                        icon = Icons.Outlined.SmartToy,
                        onClick = {},
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_data),
                        summary = stringResource(R.string.settings_group_data_summary),
                        icon = Icons.Outlined.FolderOpen,
                        onClick = {},
                    )
                }
            }
        }
    }
}
