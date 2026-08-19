package app.chompass.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.chompass.R
import app.chompass.models.HomeTopNutrient

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
    LauncherIconAccent(AppThemeColor.TEAL, Color(0xFF006B5E)),
    LauncherIconAccent(AppThemeColor.BLUE, Color(0xFF0A84FF)),
    LauncherIconAccent(AppThemeColor.GREEN, Color(0xFF34C759)),
    LauncherIconAccent(AppThemeColor.PURPLE, Color(0xFFAF52DE)),
    LauncherIconAccent(AppThemeColor.PINK, Color(0xFFFF8FAB)),
    LauncherIconAccent(AppThemeColor.ORANGE, Color(0xFFFF9500)),
    LauncherIconAccent(AppThemeColor.INDIGO, Color(0xFF5856D6)),
    LauncherIconAccent(AppThemeColor.NEUTRAL, Color(0xFF8E8E93)),
)

private data class Hsl(val h: Float, val s: Float, val l: Float)

/** Below this saturation a Material You accent reads as gray (light wallpapers). */
private const val GRAY_ACCENT_MAX_SATURATION = 0.12f

private fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Hsl(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }
    return Hsl(h, s, l)
}

private fun hueDistanceDegrees(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 360f
    return minOf(d, 360f - d)
}

/**
 * Maps a Material You / wallpaper accent to the closest pre-rendered launcher icon.
 * Uses hue/chroma-aware distance so warm Material You primaries do not collapse onto pink.
 */
fun nearestLauncherIconTheme(accent: Color): AppThemeColor {
    val target = accent.toHsl()
    return LauncherIconAccents.minBy { candidate ->
        val c = candidate.accent.toHsl()
        // Near-gray accents: match on saturation + lightness (and RGB), not hue.
        if (target.s < GRAY_ACCENT_MAX_SATURATION) {
            val ds = target.s - c.s
            val dl = target.l - c.l
            val dr = accent.red - candidate.accent.red
            val dg = accent.green - candidate.accent.green
            val db = accent.blue - candidate.accent.blue
            return@minBy ds * ds * 4f + dl * dl + (dr * dr + dg * dg + db * db) * 0.25f
        }
        val dh = hueDistanceDegrees(target.h, c.h) / 180f
        val ds = target.s - c.s
        val dl = target.l - c.l
        // Hue dominates; lightness is secondary so pastel Material You tones still track hue.
        dh * dh * 5f + ds * ds + dl * dl * 0.35f
    }.theme
}

/**
 * Launcher icon theme for a Material You / wallpaper accent.
 * Near-gray accents (common on light wallpapers) resolve to the brand teal icon
 * instead of the gray Graphite one, so a light wallpaper never turns the launcher
 * icon gray (#13). Saturated accents still map by hue via [nearestLauncherIconTheme].
 */
fun launcherIconThemeFor(accent: Color): AppThemeColor =
    if (accent.toHsl().s < GRAY_ACCENT_MAX_SATURATION) AppThemeColor.TEAL else nearestLauncherIconTheme(accent)

/**
 * Pure launcher-icon decision: fixed icon wins, fixed theme colors map 1:1, and
 * System resolves the wallpaper accent via [launcherIconThemeFor]. Context-free so
 * the whole decision is JVM-testable (see AppThemeColorLauncherTest).
 */
fun AppThemeColor.resolveLauncherIconTheme(accent: Color, fixedIcon: Boolean = false): AppThemeColor {
    if (fixedIcon) return AppThemeColor.TEAL
    if (!usesSystemPalette) return this
    return launcherIconThemeFor(accent)
}

/** Context-based wrapper used by [AndroidAppIconManager] (reads the Material You accent). */
fun AppThemeColor.resolveLauncherIconTheme(context: Context, fixedIcon: Boolean = false): AppThemeColor =
    resolveLauncherIconTheme(widgetAccentColors(context).first, fixedIcon)

/**
 * Resolves the app's appearance-mode string to a dark flag. "system" (or null)
 * falls back to the device's current uiMode. Used by non-Compose surfaces
 * (widgets, notifications) that must mirror the app's explicit appearance.
 */
fun appearanceIsDark(appearance: String?, systemDark: Boolean): Boolean = when (appearance) {
    "light" -> false
    "dark", "oled" -> true
    else -> systemDark
}

/** Context-based wrapper of [appearanceIsDark] (reads the device uiMode for "system"). */
fun appearanceIsDark(appearance: String?, context: Context): Boolean {
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return appearanceIsDark(appearance, nightMode == Configuration.UI_MODE_NIGHT_YES)
}

/** Accent colors for widgets and other non-Compose surfaces, resolved against an explicit dark flag. */
fun AppThemeColor.widgetAccentColors(context: Context, dark: Boolean): Pair<Color, Color> {
    if (!usesSystemPalette || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return start to end
    }
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val primary = scheme.primary
    return primary to lerp(primary, Color.White, 0.28f)
}

/** Accent colors for widgets and other non-Compose surfaces (follows the device dark mode). */
fun AppThemeColor.widgetAccentColors(context: Context): Pair<Color, Color> =
    widgetAccentColors(context, appearanceIsDark(null, context))

object AppColors {
    private var activeThemeColor: AppThemeColor = AppThemeColor.SYSTEM
    private var primaryOverride: Color? = null

    /**
     * Sets the active theme and the scheme primary (UI-audit 2.4): [Calorie]/[CalorieStart]
     * mirror `colorScheme.primary` in every mode (dynamic, fixed light, fixed dark — where
     * the scheme lightens the accent), so Compose surfaces and widget/notification reads
     * cannot drift. Called once per [ChompassTheme] composition (idempotent write).
     */
    fun setThemeColor(themeColor: AppThemeColor, primary: Color? = null) {
        activeThemeColor = themeColor
        primaryOverride = primary
    }

    val ThemeColor: AppThemeColor
        get() = activeThemeColor

    val CalorieStart: Color
        get() = primaryOverride ?: activeThemeColor.start

    val CalorieEnd: Color
        get() = primaryOverride?.let { lerp(it, Color.White, 0.28f) } ?: activeThemeColor.end

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

    // OLED mode: true-black neutrals so OLED panels can turn pixels off.
    // Text and accent colors reuse the Dark values (unchanged).
    val AppBackgroundOled = Color(0xFF000000)
    val AppCardOled = Color(0xFF000000)
    val SurfaceContainerLowOled = Color(0xFF0A0A0A)
    val SurfaceContainerHighOled = Color(0xFF141414)
    val NavBarOled = Color(0xFF000000)
    val ActivePillOled = Color(0xFF3A3A3A)
    val OnOled = OnDark
    val MutedOled = MutedDark
    val DividerOled = DividerDark
    val HairlineBorderOled = HairlineBorderDark
    val WarningOled = WarningDark
    val SuccessOled = SuccessDark
}

/** Resolves [AppColors.WarningLight]/[AppColors.WarningDark] against the active theme. */
val androidx.compose.material3.ColorScheme.warning: Color
    get() = if (background.luminance() < 0.5f) AppColors.WarningDark else AppColors.WarningLight

/** Resolves [AppColors.SuccessLight]/[AppColors.SuccessDark] against the active theme. */
val androidx.compose.material3.ColorScheme.success: Color
    get() = if (background.luminance() < 0.5f) AppColors.SuccessDark else AppColors.SuccessLight

/** Muted tone for non-core nutrients (sodium, vitamins…), theme/dark aware (UI-audit 2.4). */
val androidx.compose.material3.ColorScheme.mutedNutrient: Color
    get() = if (background.luminance() < 0.5f) AppColors.MutedDark else Color(0xFF79747E)

/**
 * Accent for a nutrient row: palette color for the four core macros, scheme-muted
 * otherwise (UI-audit 2.4 — the old hardcoded 0xFF79747E was not dark aware).
 */
@Composable
fun nutrientAccentColor(nutrient: HomeTopNutrient): Color = when (nutrient) {
    HomeTopNutrient.PROTEIN, HomeTopNutrient.CARBS, HomeTopNutrient.FAT, HomeTopNutrient.FIBER ->
        AppColors.nutrientColor(nutrient)
    else -> MaterialTheme.colorScheme.mutedNutrient
}
