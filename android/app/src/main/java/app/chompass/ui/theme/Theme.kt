package app.chompass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** WCAG contrast ratio between two colors (both luminance values are relative). */
internal fun contrastRatio(a: Color, b: Color): Double {
    val first = a.luminance().toDouble()
    val second = b.luminance().toDouble()
    return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
}

/**
 * Content color for a filled accent: whichever of ink / blush contrasts more with the fill.
 *
 * Picking the maximum instead of thresholding on luminance keeps labels readable on mid-tone
 * fills — the dark scheme lightens the accent, where a luminance test would still choose the
 * light label and land near 2.8:1. Both brand accents and every fixed accent clear 4.5:1 here.
 */
internal fun onColorFor(fill: Color): Color {
    val ink = contrastRatio(AppColors.OnLight, fill)
    val blush = contrastRatio(AppColors.OnDark, fill)
    return if (blush >= ink) AppColors.OnDark else AppColors.OnLight
}

/** The accent as rendered in the dark scheme (lightened so it reads on plum). */
private fun AppThemeColor.darkPrimary(): Color = lerp(primary, Color.White, 0.25f)

internal fun lightColors(themeColor: AppThemeColor) = lightColorScheme(
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

internal fun darkColors(themeColor: AppThemeColor) = darkColorScheme(
    primary = themeColor.darkPrimary(),
    onPrimary = onColorFor(themeColor.darkPrimary()),
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
internal fun oledColors(themeColor: AppThemeColor) = darkColors(themeColor).copy(
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

/**
 * Root Compose theme.
 *
 * Two palette regimes:
 * - [AppThemeColor.SYSTEM] + [useDynamicColor] on Android 12+ = Material You, wallpaper-derived
 *   surfaces and primary; OLED keeps the true-black neutral override.
 * - Every fixed accent (including [AppThemeColor.PLUM], the default, and
 *   [AppThemeColor.PEACH]) = the curated cream/blush/plum neutral palette with that accent as
 *   primary. Fixed accents never borrow wallpaper neutrals — that is what System is for.
 */
@Composable
fun ChompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledTheme: Boolean = false,
    themeColor: AppThemeColor = AppThemeColor.SYSTEM,
    useDynamicColor: Boolean = true,
    glassBlur: Boolean = true,
    ambientMotion: Boolean = true,
    glassStrength: GlassStrength = GlassStrength.DEFAULT,
    ambientIntensity: AmbientIntensity = AmbientIntensity.DEFAULT,
    ambientContrast: AmbientContrast = AmbientContrast.DEFAULT,
    ambientSaturation: AmbientSaturation = AmbientSaturation.DEFAULT,
    ambientSpeed: AmbientSpeed = AmbientSpeed.DEFAULT,
    ambientPalette: AmbientPalette = AmbientPalette.DEFAULT,
    ambientTilt: Boolean = true,
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
            // A fixed accent owns its neutrals: the curated cream/blush/plum palette,
            // never wallpaper-derived surfaces (that is what System is for).
            val accent = if (themeColor.usesSystemPalette) {
                AppThemeColor.fromKey(AppThemeColor.DEFAULT_KEY)
            } else {
                themeColor
            }
            when {
                oledTheme -> oledColors(accent)
                dark -> darkColors(accent)
                else -> lightColors(accent)
            }
        }
    }
    // Mirror the scheme primary into AppColors so Calorie accents equal
    // colorScheme.primary in every mode (UI-audit 2.4).
    AppColors.setThemeColor(themeColor, colorScheme.primary)
    // Expressive-motion skip (recorded): material3 1.4.0 (BOM 2026.02.01) keeps
    // MaterialExpressiveTheme/MotionScheme internal, so the root stays on
    // MaterialTheme; custom surfaces carry the motion via AppMotion specs.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
    ) {
        CompositionLocalProvider(
            LocalChompassTraits provides ChompassTraits(
                glassBlur = glassBlur,
                ambientMotion = ambientMotion,
                glassStrength = glassStrength,
                ambientIntensity = ambientIntensity,
                ambientContrast = ambientContrast,
                ambientSaturation = ambientSaturation,
                ambientSpeed = ambientSpeed,
                ambientPalette = ambientPalette,
                ambientTilt = ambientTilt,
                oled = oledTheme,
            )
        ) {
            content()
        }
    }
}
