package org.codeberg.fitguy.nofud.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider

/** Brand palette exposed to Glance. Keep in sync with ui/theme/Color.kt. */
object WidgetTheme {
    val calorieProvider = ColorProvider(day = Color(0xFF006B5E), night = Color(0xFF4DB6AC))
    val backgroundProvider = ColorProvider(day = Color(0xFFFEF7FF), night = Color(0xFF1C1B1F))
    val primaryTextProvider = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
    val secondaryTextProvider = ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))

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
}
