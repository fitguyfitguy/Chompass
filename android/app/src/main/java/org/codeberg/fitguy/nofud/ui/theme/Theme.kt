package org.codeberg.fitguy.nofud.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance

private fun lightColors(themeColor: AppThemeColor) = lightColorScheme(
    primary = themeColor.start,
    onPrimary = AppColors.OnDark,
    secondary = themeColor.start,
    onSecondary = AppColors.OnDark,
    tertiary = themeColor.start,
    onTertiary = AppColors.OnDark,
    background = AppColors.AppBackgroundLight,
    onBackground = AppColors.OnLight,
    surface = AppColors.AppCardLight,
    onSurface = AppColors.OnLight,
    surfaceVariant = AppColors.AppCardLight,
    onSurfaceVariant = AppColors.MutedLight,
    outline = AppColors.DividerLight
)

private fun darkColors(themeColor: AppThemeColor) = darkColorScheme(
    primary = themeColor.start,
    onPrimary = AppColors.OnDark,
    secondary = themeColor.start,
    onSecondary = AppColors.OnDark,
    tertiary = themeColor.start,
    onTertiary = AppColors.OnDark,
    background = AppColors.AppBackgroundDark,
    onBackground = AppColors.OnDark,
    surface = AppColors.AppCardDark,
    onSurface = AppColors.OnDark,
    surfaceVariant = AppColors.AppCardDark,
    onSurfaceVariant = AppColors.MutedDark,
    outline = AppColors.DividerDark
)

@Composable
fun NoFUDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: AppThemeColor = AppThemeColor.TEAL,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    AppColors.setThemeColor(themeColor)
    val baseScheme = if (darkTheme) darkColors(themeColor) else lightColors(themeColor)
    val context = LocalContext.current

    val colorScheme =
        if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicScheme =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            val primary = themeColor.start
            // Choose a readable foreground color for the overridden primary.
            val onPrimary =
                if (primary.luminance() > 0.5f) AppColors.OnLight else AppColors.OnDark

            dynamicScheme.copy(
                primary = primary,
                secondary = primary,
                tertiary = primary,
                onPrimary = onPrimary,
                onSecondary = onPrimary,
                onTertiary = onPrimary,
            )
        } else {
            baseScheme
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
