package app.chompass.services

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import app.chompass.ChompassApp
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.resolveLauncherIconTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AndroidAppIconManager {
    private const val MANIFEST_NAMESPACE = "app.chompass"

    /**
     * Night twin of the brand tile (Plum / Peach / the fixedIcon pin all share
     * the brand tile): enabled in night mode, otherwise a disabled manifest
     * default. Accent-tile aliases keep their light-tuned gradients — no dark
     * variants exist for them.
     */
    private const val BRAND_DARK_ALIAS = "LauncherBrandDark"

    /**
     * @param fixedIcon when true the brand icon alias is always used and no aliases
     *   ever move (opt-in workaround for launchers that misbehave on alias swaps, #21).
     *   Call only when the activity is not in the foreground: disabling the alias a
     *   running task was launched through makes some launchers tear the task down or
     *   force-stop the app (#13). [MainActivity] defers calls to [android.app.Activity.onStop].
     */
    fun apply(context: Context, themeColor: AppThemeColor, fixedIcon: Boolean = false) {
        val resolved = themeColor.resolveLauncherIconTheme(context, fixedIcon)
        val pm = context.packageManager
        val night = isNightLauncher(context)
        val dark = resolved.isBrandTile && night
        val currentlyEnabled = AppThemeColor.iconSelectableColors().firstOrNull { color ->
            isAliasEnabled(pm, context, color)
        }
        val darkEnabled = isBrandDarkAliasEnabled(pm, context)
        if (dark) {
            if (currentlyEnabled != null || !darkEnabled) {
                AppThemeColor.iconSelectableColors().forEach { color ->
                    pm.setComponentEnabledSetting(
                        component(context, color.launcherAliasSimpleName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
                pm.setComponentEnabledSetting(
                    component(context, BRAND_DARK_ALIAS),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } else if (currentlyEnabled != resolved || darkEnabled) {
            AppThemeColor.iconSelectableColors().forEach { color ->
                val component = component(context, color.launcherAliasSimpleName)
                val state = if (color == resolved) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
            pm.setComponentEnabledSetting(
                component(context, BRAND_DARK_ALIAS),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        // Shortcuts must target the enabled alias (same component as the live
        // task). Republish after every apply so theme switches stay in sync;
        // include the fasting toggle only when the tracker is enabled.
        val app = context.applicationContext as? ChompassApp
        if (app != null) {
            app.applicationScope.launch {
                LauncherShortcuts.publish(context, app.container.prefs.fastingEnabled.first())
            }
        } else {
            LauncherShortcuts.publish(context)
        }
    }

    /**
     * Component currently owning MAIN/LAUNCHER (themed activity-alias).
     * Explicit intents (shortcuts, shares) must use this — not [app.chompass.MainActivity] —
     * or `singleTop` / `CLEAR_TOP` can stack a second instance and drop entry results.
     */
    fun enabledLauncherComponent(context: Context): ComponentName {
        val pm = context.packageManager
        val enabled = AppThemeColor.iconSelectableColors().firstOrNull { color ->
            isAliasEnabled(pm, context, color)
        }
        val simpleName = enabled?.launcherAliasSimpleName
            // Night twin: when LauncherBrandDark owns MAIN/LAUNCHER no color
            // alias is enabled, so shortcuts must target it.
            ?: if (isBrandDarkAliasEnabled(pm, context)) {
                BRAND_DARK_ALIAS
            } else {
                AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY).launcherAliasSimpleName
            }
        return ComponentName(
            context.packageName,
            "$MANIFEST_NAMESPACE.$simpleName",
        )
    }

    private fun isAliasEnabled(
        pm: PackageManager,
        context: Context,
        color: AppThemeColor,
    ): Boolean {
        val component = component(context, color.launcherAliasSimpleName)
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // Manifest default: only the brand default alias starts enabled.
            else -> color == AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY)
        }
    }

    /** BrandDark ships disabled; only a prior night apply state-enables it. */
    private fun isBrandDarkAliasEnabled(pm: PackageManager, context: Context): Boolean =
        pm.getComponentEnabledSetting(component(context, BRAND_DARK_ALIAS)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun isNightLauncher(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val AppThemeColor.isBrandTile: Boolean
        get() = this == AppThemeColor.PLUM || this == AppThemeColor.PEACH

    private fun component(context: Context, simpleName: String) =
        ComponentName(context.packageName, "$MANIFEST_NAMESPACE.$simpleName")

    private val AppThemeColor.launcherAliasSimpleName: String
        get() = when (this) {
            // Plum and Peach are separate aliases with the same cream brand icon, so
            // flipping the default is a manifest flag swap, not an icon regeneration.
            AppThemeColor.PLUM -> "LauncherPlum"
            AppThemeColor.PEACH -> "LauncherPeach"
            AppThemeColor.TEAL -> "LauncherTeal"
            AppThemeColor.BLUE -> "LauncherBlue"
            AppThemeColor.GREEN -> "LauncherGreen"
            AppThemeColor.PURPLE -> "LauncherPurple"
            AppThemeColor.PINK -> "LauncherBabyPink"
            AppThemeColor.ORANGE -> "LauncherOrange"
            AppThemeColor.INDIGO -> "LauncherIndigo"
            AppThemeColor.NEUTRAL -> "LauncherGraphite"
            AppThemeColor.SYSTEM -> "LauncherPlum"
        }
}
