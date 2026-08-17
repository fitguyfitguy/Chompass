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
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
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

/**
 * SettingRow stress layout for release screenshot previews: long label + long
 * value rows (the German food-log sort row, the home-display rows) rendered in
 * a narrow column at large font scale. Pins the no-letter-stacking rule:
 * labels must wrap, values must ellipsize — never zero-width vertical text.
 */
@Composable
internal fun SettingRowStressPreviewContent() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            padding = 0.dp,
            allowBlur = false,
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                SettingRow(
                    label = stringResource(R.string.settings_food_log_sort),
                    value = stringResource(R.string.sort_standard),
                    icon = Icons.Filled.UnfoldMore,
                ) {}
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.home_display_calorie_mode),
                    value = stringResource(R.string.home_calorie_mode_add_active_hc_hint),
                ) {}
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.home_display_nutrient_cards),
                    value = stringResource(R.string.home_display_show_active_calories_desc),
                ) {}
            }
        }
    }
}

/**
 * SettingRow alignment guard for release screenshot previews: real personal + AI
 * rows with different label lengths. Locks in the rule that the value + trailing
 * icon are pinned to the row's right edge and line up at the same x across rows
 * (no mid-row floating after the dac1d20 label-wrap change).
 */
@Composable
internal fun SettingRowAlignmentPreviewContent() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            padding = 0.dp,
            allowBlur = false,
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                SettingRow(
                    label = stringResource(R.string.settings_gender),
                    value = "Männlich",
                    icon = Icons.Outlined.Person,
                    inlineMenu = true,
                ) {}
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_height),
                    value = "194 cm",
                    icon = Icons.Outlined.Height,
                ) {}
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_weight),
                    value = "93.4 kg",
                    icon = Icons.Outlined.MonitorWeight,
                ) {}
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_ai_model),
                    value = "gemini-2.5-flash",
                    icon = Icons.Outlined.Tune,
                ) {}
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_api_key),
                    value = "••••••••••••",
                    icon = Icons.Outlined.Key,
                ) {}
            }
        }
    }
}

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
