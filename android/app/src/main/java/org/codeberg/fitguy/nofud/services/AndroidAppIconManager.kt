package org.codeberg.fitguy.nofud.services

import android.content.Context
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor

object AndroidAppIconManager {
    /** Launcher icon is fixed to the default app icon; accent color no longer swaps icons. */
    fun apply(context: Context, themeColor: AppThemeColor) = Unit
}
