package app.chompass.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_group_app_display),
        onBack = onBack,
    ) {
        SettingsAppSection(
            ui = ui,
            vm = vm,
            nav = nav,
            onOpenSheet = { sheet = it },
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = {},
            onRebalanceBlocked = {},
        )
    }
}
