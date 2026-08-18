package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.ui.about.AboutSettingsRows
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors

@Composable
fun SettingsScreen(container: AppContainer, nav: NavHostController) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.nav_settings),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (ui.suggestions.isNotEmpty()) {
                SuggestionsCard(
                    suggestions = ui.suggestions,
                    onAction = { nav.navigate(it.targetRoute) },
                    onDismiss = vm::dismissSuggestion,
                )
            }

            FudGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppRadii.Container,
                padding = 0.dp,
                allowBlur = false,
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingsHubRow(
                        label = stringResource(R.string.settings_section_personal),
                        summary = stringResource(R.string.settings_group_personal_summary),
                        icon = Icons.Outlined.Person,
                        onClick = { nav.navigate(ChompassRoutes.SETTINGS_PERSONAL) },
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_section_goals),
                        summary = stringResource(R.string.settings_group_goals_summary),
                        icon = Icons.Outlined.Equalizer,
                        onClick = { nav.navigate(ChompassRoutes.SETTINGS_GOALS) },
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_food),
                        summary = stringResource(R.string.settings_group_food_summary),
                        icon = Icons.Outlined.Restaurant,
                        onClick = { nav.navigate(ChompassRoutes.SETTINGS_FOOD) },
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_app_display),
                        summary = stringResource(R.string.settings_group_app_summary),
                        icon = Icons.Outlined.Settings,
                        onClick = { nav.navigate(ChompassRoutes.SETTINGS_APP) },
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_ai),
                        summary = stringResource(R.string.settings_group_ai_summary),
                        icon = Icons.Outlined.SmartToy,
                        onClick = { nav.navigate(ChompassRoutes.SETTINGS_AI) },
                    )
                    HorizontalDivider()
                    SettingsHubRow(
                        label = stringResource(R.string.settings_group_data),
                        summary = stringResource(R.string.settings_group_data_summary),
                        icon = Icons.Outlined.FolderOpen,
                        onClick = { nav.navigate(ChompassRoutes.SETTINGS_DATA) },
                    )
                }
            }

            SectionCard(title = stringResource(R.string.nav_about)) {
                AboutSettingsRows(container)
            }
            Spacer(Modifier.height(BottomNavScrollPadding))
        }
    }
}

/** Dismissible hub card proposing beneficial-but-optional setups (max 3 rows). */
@Composable
private fun SuggestionsCard(
    suggestions: List<SettingsSuggestion>,
    onAction: (SettingsSuggestion) -> Unit,
    onDismiss: (String) -> Unit,
) {
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppRadii.Container,
        padding = 0.dp,
        allowBlur = false,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                stringResource(R.string.settings_suggestions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
            )
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        suggestion.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onAction(suggestion) }) {
                        Text(
                            suggestion.actionLabel,
                            color = AppColors.Calorie,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(onClick = { onDismiss(suggestion.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.settings_suggestions_dismiss),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
