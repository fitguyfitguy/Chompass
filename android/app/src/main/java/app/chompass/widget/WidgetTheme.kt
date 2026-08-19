package app.chompass.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider

/** Brand palette exposed to Glance. Keep in sync with ui/theme/Color.kt. */
object WidgetTheme {
    val calorieProvider = ColorProvider(day = Color(0xFF006B5E), night = Color(0xFF4DB6AC))
    val backgroundProvider = ColorProvider(day = Color(0xFFFEF7FF), night = Color(0xFF1C1B1F))
    val primaryTextProvider = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
    val secondaryTextProvider = ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))

    // OLED: true-black backgrounds, dark text/accents (same both day and night).
    val calorieOledProvider = ColorProvider(day = Color(0xFF4DB6AC), night = Color(0xFF4DB6AC))
    val backgroundOledProvider = ColorProvider(day = Color(0xFF000000), night = Color(0xFF000000))
    val primaryTextOledProvider = ColorProvider(day = Color(0xFFE6E1E5), night = Color(0xFFE6E1E5))
    val secondaryTextOledProvider = ColorProvider(day = Color(0xFFCAC4D0), night = Color(0xFFCAC4D0))

    val proteinProvider = ColorProvider(day = Color(0xFF4F6BED), night = Color(0xFF4F6BED))
    val carbsProvider = ColorProvider(day = Color(0xFFE8A317), night = Color(0xFFE8A317))
    val fatProvider = ColorProvider(day = Color(0xFFE46962), night = Color(0xFFE46962))

    /** Raw RGB hex from the snapshot; teal default when absent. */
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
        "dark" -> ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFF1C1B1F))
        "light" -> ColorProvider(day = Color(0xFFFEF7FF), night = Color(0xFFFEF7FF))
        else -> backgroundProvider
    }

    fun primaryTextProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> primaryTextOledProvider
        "dark" -> ColorProvider(day = Color(0xFFE6E1E5), night = Color(0xFFE6E1E5))
        "light" -> ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFF1C1B1F))
        else -> primaryTextProvider
    }

    fun secondaryTextProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> secondaryTextOledProvider
        "dark" -> ColorProvider(day = Color(0xFFCAC4D0), night = Color(0xFFCAC4D0))
        "light" -> ColorProvider(day = Color(0xFF49454F), night = Color(0xFF49454F))
        else -> secondaryTextProvider
    }

    fun calorieProvider(appearance: String?): GlanceColorProvider = when (appearance) {
        "oled" -> calorieOledProvider
        "dark" -> ColorProvider(day = Color(0xFF4DB6AC), night = Color(0xFF4DB6AC))
        "light" -> ColorProvider(day = Color(0xFF006B5E), night = Color(0xFF006B5E))
        else -> calorieProvider
    }
}
