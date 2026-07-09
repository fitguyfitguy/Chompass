package org.codeberg.fitguy.nofud.services

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import org.codeberg.fitguy.nofud.ui.theme.resolveLauncherIconTheme

object AndroidAppIconManager {
    private const val MANIFEST_NAMESPACE = "org.codeberg.fitguy.nofud"

    fun apply(context: Context, themeColor: AppThemeColor) {
        val resolved = themeColor.resolveLauncherIconTheme(context)
        val pm = context.packageManager
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
