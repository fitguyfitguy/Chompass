package app.chompass.ui.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
    val settingsEntry = nav?.let { runCatching { it.getBackStackEntry(ChompassRoutes.SETTINGS) }.getOrNull() }
    val activity = LocalContext.current.findComponentActivity()
    return when {
        settingsEntry != null -> viewModel(settingsEntry, factory = factory)
        activity != null -> viewModel(activity, factory = factory)
        else -> viewModel(factory = factory)
    }
}

/** Unwrap LocalContext through ContextWrappers to the hosting ComponentActivity. */
private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}
