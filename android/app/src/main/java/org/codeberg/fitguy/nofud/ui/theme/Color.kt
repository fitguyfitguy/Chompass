package org.codeberg.fitguy.nofud.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.HomeTopNutrient

enum class AppThemeColor(
    val key: String,
    @param:StringRes val displayNameRes: Int,
    val primary: Color,
) {
    TEAL("teal", R.string.theme_color_teal, Color(0xFF006B5E)),
    BLUE("blue", R.string.theme_color_blue, Color(0xFF0061A4)),
    GREEN("green", R.string.theme_color_green, Color(0xFF386A20)),
    PURPLE("purple", R.string.theme_color_purple, Color(0xFF6750A4)),
    PINK("pink", R.string.theme_color_pink, Color(0xFF984061)),
    ORANGE("orange", R.string.theme_color_orange, Color(0xFF8B5000)),
    INDIGO("indigo", R.string.theme_color_indigo, Color(0xFF4F378B)),
    NEUTRAL("neutral", R.string.theme_color_neutral, Color(0xFF5E5E62));

    /** Accent gradient start — kept for rings/charts. */
    val start: Color get() = primary

    /** Accent gradient end — lighter blend of [primary]. */
    val end: Color get() = lerp(primary, Color.White, 0.28f)

    val macroPalette: MacroPalette
        get() = ThemeMacroPalettes.getValue(this)

    companion object {
        const val DEFAULT_KEY = "teal"

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
            if (key == null) return TEAL
            values().firstOrNull { it.key == key }?.let { return it }
            LEGACY_KEY_MIGRATION[key]?.let { return it }
            return TEAL
        }

        fun migrateKey(key: String?): String = fromKey(key).key
    }
}

object AppColors {
    private var activeThemeColor: AppThemeColor = AppThemeColor.TEAL

    fun setThemeColor(themeColor: AppThemeColor) {
        activeThemeColor = themeColor
    }

    val ThemeColor: AppThemeColor
        get() = activeThemeColor

    val CalorieStart: Color
        get() = activeThemeColor.start

    val CalorieEnd: Color
        get() = activeThemeColor.end

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
}
