package app.chompass.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider

/** Brand palette exposed to Glance. Keep in sync with ui/theme/Color.kt + BrandPalette. */
object WidgetTheme {
    // Brand neutrals: cream light, plum dark, blush text, warm muted secondary.
    private val accentDay = Color(0xFF8F4B78)
    private val accentNight = Color(0xFFD6A2BE)
    private val backgroundDay = Color(0xFFFFF6EC)
    private val backgroundNight = Color(0xFF170E16)
    private val backgroundOled = Color(0xFF000000)
    private val primaryTextDay = Color(0xFF1C131A)
    private val primaryTextNight = Color(0xFFFBF0F4)
    private val secondaryTextDay = Color(0xFF6E5C63)
    private val secondaryTextNight = Color(0xFFB9A8B3)

    val calorieProvider = ColorProvider(day = accentDay, night = accentNight)
    val backgroundProvider = ColorProvider(day = backgroundDay, night = backgroundNight)
    val primaryTextProvider = ColorProvider(day = primaryTextDay, night = primaryTextNight)
    val secondaryTextProvider = ColorProvider(day = secondaryTextDay, night = secondaryTextNight)

    // OLED: true-black backgrounds, dark text/accents (same both day and night).
    val calorieOledProvider = ColorProvider(day = accentNight, night = accentNight)
    val backgroundOledProvider = ColorProvider(day = backgroundOled, night = backgroundOled)
    val primaryTextOledProvider = ColorProvider(day = primaryTextNight, night = primaryTextNight)
    val secondaryTextOledProvider = ColorProvider(day = secondaryTextNight, night = secondaryTextNight)

    val proteinProvider = ColorProvider(day = Color(0xFF4F6BED), night = Color(0xFF4F6BED))
    val carbsProvider = ColorProvider(day = Color(0xFFE8A317), night = Color(0xFFE8A317))
    val fatProvider = ColorProvider(day = Color(0xFFE46962), night = Color(0xFFE46962))

    /** Raw RGB hex from the snapshot; brand default when absent. */
    fun themeStart(hex: Int?): Int = hex ?: DEFAULT_THEME_START
    fun themeEnd(hex: Int?): Int = hex ?: DEFAULT_THEME_END

    /** Text color provider for the user's theme color (same in light/dark). */
    fun themeTextProvider(hex: Int?): GlanceColorProvider {
        val color = Color(0xFF000000L or themeStart(hex).toLong())
        return ColorProvider(day = color, night = color)
    }

    fun colorProvider(rgb: Int): GlanceColorProvider {
        val color = Color(0xFF000000L or rgb.toLong())
        return ColorProvider(day = color, night = color)
    }

    /**
     * Resolves a provider against the app's appearance mode. Explicit modes
     * ("light"/"dark"/"oled") use a fixed color both day and night so the widget
     * mirrors the app instead of the system; "system"/null follow the device.
     */
    fun backgroundProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> backgroundOledProvider
        "dark" -> ColorProvider(day = backgroundNight, night = backgroundNight)
        "light" -> ColorProvider(day = backgroundDay, night = backgroundDay)
        else -> backgroundProvider
    }

    fun primaryTextProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> primaryTextOledProvider
        "dark" -> ColorProvider(day = primaryTextNight, night = primaryTextNight)
        "light" -> ColorProvider(day = primaryTextDay, night = primaryTextDay)
        else -> primaryTextProvider
    }

    fun secondaryTextProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> secondaryTextOledProvider
        "dark" -> ColorProvider(day = secondaryTextNight, night = secondaryTextNight)
        "light" -> ColorProvider(day = secondaryTextDay, night = secondaryTextDay)
        else -> secondaryTextProvider
    }

    fun calorieProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> calorieOledProvider
        "dark" -> ColorProvider(day = accentNight, night = accentNight)
        "light" -> ColorProvider(day = accentDay, night = accentDay)
        else -> calorieProvider
    }
}
