package org.codeberg.fitguy.nofud.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

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

/**
 * Controls whether glass surfaces try to use a blur effect (API 31+). Kept as a
 * CompositionLocal so `FudGlass*` components can stay mostly dumb.
 */
val LocalGlassBlurEnabled = staticCompositionLocalOf { true }

@Composable
fun NoFUDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: AppThemeColor = AppThemeColor.TEAL,
    glassBlurEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    AppColors.setThemeColor(themeColor)
    val baseScheme = if (darkTheme) darkColors(themeColor) else lightColors(themeColor)
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Prefer Material You (Monet) for the background/surfaces, but keep the app's
        // accent colors driven by the user's selected theme color.
        val dynamicScheme =
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dynamicScheme.copy(
            primary = themeColor.start,
            onPrimary = AppColors.OnDark,
            secondary = themeColor.start,
            onSecondary = AppColors.OnDark,
            tertiary = themeColor.start,
            onTertiary = AppColors.OnDark,
        )
    } else {
        baseScheme
    }

    CompositionLocalProvider(LocalGlassBlurEnabled provides glassBlurEnabled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
