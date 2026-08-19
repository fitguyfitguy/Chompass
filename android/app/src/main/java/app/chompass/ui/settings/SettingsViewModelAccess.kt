package app.chompass.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import app.chompass.AppContainer
import app.chompass.ui.navigation.ChompassRoutes

/** Share one SettingsViewModel across the settings graph when SETTINGS is on the stack. */
@Composable
fun rememberSettingsViewModel(
    container: AppContainer,
    nav: NavHostController? = null,
): SettingsViewModel {
    val factory = remember(container) { SettingsViewModel.Factory(container) }
    val owner = nav?.let { runCatching { it.getBackStackEntry(ChompassRoutes.SETTINGS) }.getOrNull() }
    return if (owner != null) viewModel(owner, factory = factory)
    else viewModel(factory = factory)
}
