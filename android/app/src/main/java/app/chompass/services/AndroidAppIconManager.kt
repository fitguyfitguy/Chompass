package app.chompass.services

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.resolveLauncherIconTheme

object AndroidAppIconManager {
    private const val MANIFEST_NAMESPACE = "app.chompass"

    fun apply(context: Context, themeColor: AppThemeColor) {
        val resolved = themeColor.resolveLauncherIconTheme(context)
        val pm = context.packageManager
        val currentlyEnabled = AppThemeColor.iconSelectableColors().firstOrNull { color ->
            isAliasEnabled(pm, context, color)
        }
        if (currentlyEnabled != resolved) {
            AppThemeColor.iconSelectableColors().forEach { color ->
                val component = ComponentName(
                    context.packageName,
                    "$MANIFEST_NAMESPACE.${color.launcherAliasSimpleName}",
                )
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
        }
        // Shortcuts must target the enabled alias (same component as the live
        // task). Republish after every apply so theme switches stay in sync.
        LauncherShortcuts.publish(context)
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
        } ?: AppThemeColor.TEAL
        return ComponentName(
            context.packageName,
            "$MANIFEST_NAMESPACE.${enabled.launcherAliasSimpleName}",
        )
    }

    private fun isAliasEnabled(
        pm: PackageManager,
        context: Context,
        color: AppThemeColor,
    ): Boolean {
        val component = ComponentName(
            context.packageName,
            "$MANIFEST_NAMESPACE.${color.launcherAliasSimpleName}",
        )
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // Manifest default: only Teal starts enabled.
            else -> color == AppThemeColor.TEAL
        }
    }

    private val AppThemeColor.launcherAliasSimpleName: String
        get() = when (this) {
            AppThemeColor.TEAL -> "LauncherTeal"
            AppThemeColor.BLUE -> "LauncherBlue"
            AppThemeColor.GREEN -> "LauncherGreen"
            AppThemeColor.PURPLE -> "LauncherPurple"
            AppThemeColor.PINK -> "LauncherBabyPink"
            AppThemeColor.ORANGE -> "LauncherOrange"
            AppThemeColor.INDIGO -> "LauncherIndigo"
            AppThemeColor.NEUTRAL -> "LauncherGraphite"
            AppThemeColor.SYSTEM -> "LauncherTeal"
        }
}
