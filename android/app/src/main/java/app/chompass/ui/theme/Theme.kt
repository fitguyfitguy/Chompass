package app.chompass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) AppColors.OnLight else AppColors.OnDark

private fun lightColors(themeColor: AppThemeColor) = lightColorScheme(
    primary = themeColor.primary,
    onPrimary = onColorFor(themeColor.primary),
    primaryContainer = lerp(themeColor.primary, Color.White, 0.82f),
    onPrimaryContainer = lerp(themeColor.primary, Color.Black, 0.35f),
    secondary = AppColors.MutedLight,
    onSecondary = AppColors.OnLight,
    secondaryContainer = AppColors.SurfaceContainerHighLight,
    onSecondaryContainer = AppColors.OnLight,
    tertiary = AppColors.Protein,
    onTertiary = Color.White,
    tertiaryContainer = lerp(AppColors.Protein, Color.White, 0.85f),
    onTertiaryContainer = AppColors.Protein,
    background = AppColors.AppBackgroundLight,
    onBackground = AppColors.OnLight,
    surface = AppColors.AppCardLight,
    onSurface = AppColors.OnLight,
    surfaceVariant = AppColors.SurfaceContainerLowLight,
    onSurfaceVariant = AppColors.MutedLight,
    surfaceContainerLow = AppColors.SurfaceContainerLowLight,
    surfaceContainer = AppColors.SurfaceContainerLowLight,
    surfaceContainerHigh = AppColors.SurfaceContainerHighLight,
    surfaceContainerHighest = AppColors.SurfaceContainerHighLight,
    outline = AppColors.DividerLight,
    outlineVariant = AppColors.HairlineBorderLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private fun darkColors(themeColor: AppThemeColor) = darkColorScheme(
    primary = lerp(themeColor.primary, Color.White, 0.25f),
    onPrimary = lerp(themeColor.primary, Color.Black, 0.25f),
    primaryContainer = lerp(themeColor.primary, Color.Black, 0.55f),
    onPrimaryContainer = lerp(themeColor.primary, Color.White, 0.75f),
    secondary = AppColors.MutedDark,
    onSecondary = AppColors.OnDark,
    secondaryContainer = AppColors.SurfaceContainerHighDark,
    onSecondaryContainer = AppColors.OnDark,
    tertiary = lerp(AppColors.Protein, Color.White, 0.2f),
    onTertiary = Color(0xFF1C1B1F),
    tertiaryContainer = lerp(AppColors.Protein, Color.Black, 0.6f),
    onTertiaryContainer = lerp(AppColors.Protein, Color.White, 0.7f),
    background = AppColors.AppBackgroundDark,
    onBackground = AppColors.OnDark,
    surface = AppColors.AppCardDark,
    onSurface = AppColors.OnDark,
    surfaceVariant = AppColors.SurfaceContainerLowDark,
    onSurfaceVariant = AppColors.MutedDark,
    surfaceContainerLow = AppColors.SurfaceContainerLowDark,
    surfaceContainer = AppColors.SurfaceContainerLowDark,
    surfaceContainerHigh = AppColors.SurfaceContainerHighDark,
    surfaceContainerHighest = AppColors.SurfaceContainerHighDark,
    outline = AppColors.DividerDark,
    outlineVariant = AppColors.HairlineBorderDark,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** OLED scheme: mirrors [darkColors] (same text/accents) but with true-black neutrals. */
private fun oledColors(themeColor: AppThemeColor) = darkColors(themeColor).copy(
    background = AppColors.AppBackgroundOled,
    onBackground = AppColors.OnOled,
    surface = AppColors.AppCardOled,
    onSurface = AppColors.OnOled,
    surfaceVariant = AppColors.SurfaceContainerLowOled,
    onSurfaceVariant = AppColors.MutedOled,
    surfaceContainerLow = AppColors.SurfaceContainerLowOled,
    surfaceContainer = AppColors.SurfaceContainerLowOled,
    surfaceContainerHigh = AppColors.SurfaceContainerHighOled,
    surfaceContainerHighest = AppColors.SurfaceContainerHighOled,
    outline = AppColors.DividerOled,
    outlineVariant = AppColors.HairlineBorderOled,
)

/** Overrides a scheme's neutral surfaces with the OLED true-black set (dynamic paths). */
private fun androidx.compose.material3.ColorScheme.withOledNeutrals() = copy(
    background = AppColors.AppBackgroundOled,
    onBackground = AppColors.OnOled,
    surface = AppColors.AppCardOled,
    onSurface = AppColors.OnOled,
    surfaceVariant = AppColors.SurfaceContainerLowOled,
    onSurfaceVariant = AppColors.MutedOled,
    surfaceContainerLow = AppColors.SurfaceContainerLowOled,
    surfaceContainer = AppColors.SurfaceContainerLowOled,
    surfaceContainerHigh = AppColors.SurfaceContainerHighOled,
    surfaceContainerHighest = AppColors.SurfaceContainerHighOled,
    outline = AppColors.DividerOled,
    outlineVariant = AppColors.HairlineBorderOled,
)

@Composable
fun ChompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledTheme: Boolean = false,
    themeColor: AppThemeColor = AppThemeColor.SYSTEM,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val usesSystemPalette = themeColor.usesSystemPalette &&
        useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // OLED is a dark variant: every dark-mode code path (dynamic scheme selection,
    // accent computation) treats it as dark; only the neutral surfaces differ.
    val dark = darkTheme || oledTheme

    val colorScheme = when {
        usesSystemPalette -> {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (oledTheme) scheme.withOledNeutrals() else scheme
        }
        else -> {
            val accent = if (themeColor.usesSystemPalette) AppThemeColor.TEAL else themeColor
            val baseScheme = when {
                oledTheme -> oledColors(accent)
                dark -> darkColors(accent)
                else -> lightColors(accent)
            }
            if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamicScheme =
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

                val primary = if (dark) {
                    lerp(accent.primary, Color.White, 0.25f)
                } else {
                    accent.primary
                }
                val onPrimary = onColorFor(primary)

                val accentOverrides = dynamicScheme.copy(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = if (dark) {
                        lerp(accent.primary, Color.Black, 0.55f)
                    } else {
                        lerp(accent.primary, Color.White, 0.82f)
                    },
                    onPrimaryContainer = if (dark) {
                        lerp(accent.primary, Color.White, 0.75f)
                    } else {
                        lerp(accent.primary, Color.Black, 0.35f)
                    },
                )
                if (oledTheme) accentOverrides.withOledNeutrals() else accentOverrides
            } else {
                baseScheme
            }
        }
    }
    // Mirror the scheme primary into AppColors so Calorie accents equal
    // colorScheme.primary in every mode (UI-audit 2.4).
    AppColors.setThemeColor(themeColor, colorScheme.primary)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
