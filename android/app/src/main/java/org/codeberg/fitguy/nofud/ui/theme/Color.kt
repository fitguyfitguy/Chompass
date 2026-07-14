package org.codeberg.fitguy.nofud.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.HomeTopNutrient

enum class AppThemeColor(
    val key: String,
    @param:StringRes val displayNameRes: Int,
    val primary: Color,
) {
    /** Follows the device wallpaper / Material You palette (Android 12+). */
    SYSTEM("system", R.string.theme_color_system, Color(0xFF6750A4)),
    TEAL("teal", R.string.theme_color_teal, Color(0xFF006B5E)),
    BLUE("blue", R.string.theme_color_blue, Color(0xFF0061A4)),
    GREEN("green", R.string.theme_color_green, Color(0xFF386A20)),
    PURPLE("purple", R.string.theme_color_purple, Color(0xFF6750A4)),
    PINK("pink", R.string.theme_color_pink, Color(0xFF984061)),
    ORANGE("orange", R.string.theme_color_orange, Color(0xFF8B5000)),
    INDIGO("indigo", R.string.theme_color_indigo, Color(0xFF4F378B)),
    NEUTRAL("neutral", R.string.theme_color_neutral, Color(0xFF5E5E62));

    /** Accent gradient start — kept for rings/charts. */
    val start: Color
        get() = if (this == SYSTEM) TEAL.primary else primary

    /** Accent gradient end — lighter blend of [primary]. */
    val end: Color
        get() = if (this == SYSTEM) TEAL.end else lerp(primary, Color.White, 0.28f)

    val macroPalette: MacroPalette
        get() = ThemeMacroPalettes.getValue(if (this == SYSTEM) TEAL else this)

    val usesSystemPalette: Boolean
        get() = this == SYSTEM

    companion object {
        const val DEFAULT_KEY = "system"

        private val LEGACY_KEY_MIGRATION = mapOf(
            "fudPink" to PINK,
            "babyPink" to PINK,
            "red" to PINK,
            "roseGold" to PINK,
            "coral" to PINK,
            "yellow" to ORANGE,
            "mochaBrown" to ORANGE,
            "mint" to GREEN,
            "lime" to GREEN,
            "skyCyan" to BLUE,
            "lavender" to PURPLE,
            "graphite" to NEUTRAL,
        )

        fun fromKey(key: String?): AppThemeColor {
            if (key == null) return SYSTEM
            values().firstOrNull { it.key == key }?.let { return it }
            LEGACY_KEY_MIGRATION[key]?.let { return it }
            return SYSTEM
        }

        fun migrateKey(key: String?): String = fromKey(key).key

        /** Accent colors selectable in settings (excludes [SYSTEM]). */
        fun iconSelectableColors(): List<AppThemeColor> =
            entries.filter { !it.usesSystemPalette }
    }
}

private data class LauncherIconAccent(
    val theme: AppThemeColor,
    val accent: Color,
)

/** Representative launcher-icon gradient colors from [scripts/generate_icons.py]. */
private val LauncherIconAccents: List<LauncherIconAccent> = listOf(
    LauncherIconAccent(AppThemeColor.TEAL, Color(0xFF30B0C7)),
    LauncherIconAccent(AppThemeColor.BLUE, Color(0xFF0A84FF)),
    LauncherIconAccent(AppThemeColor.GREEN, Color(0xFF34C759)),
    LauncherIconAccent(AppThemeColor.PURPLE, Color(0xFFAF52DE)),
    LauncherIconAccent(AppThemeColor.PINK, Color(0xFFFF8FAB)),
    LauncherIconAccent(AppThemeColor.ORANGE, Color(0xFFFF9500)),
    LauncherIconAccent(AppThemeColor.INDIGO, Color(0xFF5856D6)),
    LauncherIconAccent(AppThemeColor.NEUTRAL, Color(0xFF8E8E93)),
)

/** Maps a Material You / wallpaper accent to the closest pre-rendered launcher icon. */
fun nearestLauncherIconTheme(accent: Color): AppThemeColor =
    LauncherIconAccents.minBy { (accent.red - it.accent.red) * (accent.red - it.accent.red) +
        (accent.green - it.accent.green) * (accent.green - it.accent.green) +
        (accent.blue - it.accent.blue) * (accent.blue - it.accent.blue)
    }.theme

/** Theme color used for the home-screen launcher icon. */
fun AppThemeColor.resolveLauncherIconTheme(context: Context): AppThemeColor {
    if (!usesSystemPalette) return this
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return AppThemeColor.TEAL
    val (accent, _) = widgetAccentColors(context)
    return nearestLauncherIconTheme(accent)
}

/** Accent colors for widgets and other non-Compose surfaces. */
fun AppThemeColor.widgetAccentColors(context: Context): Pair<Color, Color> {
    if (!usesSystemPalette || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return start to end
    }
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val dark = nightMode == Configuration.UI_MODE_NIGHT_YES
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val primary = scheme.primary
    return primary to lerp(primary, Color.White, 0.28f)
}

object AppColors {
    private var activeThemeColor: AppThemeColor = AppThemeColor.SYSTEM
    private var dynamicPrimary: Color? = null

    fun setThemeColor(themeColor: AppThemeColor, dynamicPrimary: Color? = null) {
        activeThemeColor = themeColor
        this.dynamicPrimary = if (themeColor.usesSystemPalette) dynamicPrimary else null
    }

    val ThemeColor: AppThemeColor
        get() = activeThemeColor

    val CalorieStart: Color
        get() = dynamicPrimary ?: activeThemeColor.start

    val CalorieEnd: Color
        get() = dynamicPrimary?.let { lerp(it, Color.White, 0.28f) } ?: activeThemeColor.end

    val Calorie: Color
        get() = CalorieStart

    val Protein: Color
        get() = activeThemeColor.macroPalette.protein

    val Carbs: Color
        get() = activeThemeColor.macroPalette.carbs

    val Fat: Color
        get() = activeThemeColor.macroPalette.fat

    val Fiber: Color
        get() = activeThemeColor.macroPalette.fiber

    /** Muted tone for non-core nutrients (sodium, vitamins, etc.). */
    val SecondaryNutrient: Color = Color(0xFF79747E)

    fun nutrientColor(nutrient: HomeTopNutrient): Color =
        activeThemeColor.macroPalette.colorFor(nutrient)

    val CalorieGradient: Brush
        get() = Brush.linearGradient(listOf(CalorieStart, CalorieEnd))

    // M3 neutral surfaces
    val AppBackgroundLight = Color(0xFFFEF7FF)
    val AppBackgroundDark = Color(0xFF1C1B1F)

    val AppCardLight = Color(0xFFFFFBFE)
    val AppCardDark = Color(0xFF1C1B1F)

    val SurfaceContainerLowLight = Color(0xFFF7F2FA)
    val SurfaceContainerLowDark = Color(0xFF1D1B20)

    val SurfaceContainerHighLight = Color(0xFFECE6F0)
    val SurfaceContainerHighDark = Color(0xFF2B2930)

    val OnLight = Color(0xFF1C1B1F)
    val OnDark = Color(0xFFE6E1E5)

    val MutedLight = Color(0xFF49454F)
    val MutedDark = Color(0xFFCAC4D0)

    val DividerLight = Color(0xFFE7E0EC)
    val DividerDark = Color(0xFF49454F)

    val TranslucentSurfaceLight = SurfaceContainerLowLight.copy(alpha = 0.92f)
    val TranslucentSurfaceDark = SurfaceContainerLowDark.copy(alpha = 0.92f)

    val TranslucentFieldLight = Color(0xFFE7E0EC).copy(alpha = 0.65f)
    val TranslucentFieldDark = Color(0xFF49454F).copy(alpha = 0.55f)

    val HairlineBorderLight = Color(0xFF79747E).copy(alpha = 0.24f)
    val HairlineBorderDark = Color(0xFFCAC4D0).copy(alpha = 0.20f)

    val NavBarLight = SurfaceContainerLowLight
    val NavBarDark = SurfaceContainerLowDark

    val ActivePillLight = Color(0xFFE8DEF8)
    val ActivePillDark = Color(0xFF4A4458)

    /** Semantic warning tone (non-destructive caution states). */
    val WarningLight = Color(0xFF8B5000)
    val WarningDark = Color(0xFFFFB77C)

    /** Semantic success tone (goal-met, positive confirmation states). */
    val SuccessLight = Color(0xFF386A20)
    val SuccessDark = Color(0xFF9CD67D)
}

/** Resolves [AppColors.WarningLight]/[AppColors.WarningDark] against the active theme. */
val androidx.compose.material3.ColorScheme.warning: Color
    get() = if (background.luminance() < 0.5f) AppColors.WarningDark else AppColors.WarningLight

/** Resolves [AppColors.SuccessLight]/[AppColors.SuccessDark] against the active theme. */
val androidx.compose.material3.ColorScheme.success: Color
    get() = if (background.luminance() < 0.5f) AppColors.SuccessDark else AppColors.SuccessLight
