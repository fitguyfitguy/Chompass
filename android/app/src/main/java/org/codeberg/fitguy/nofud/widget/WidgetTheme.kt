package org.codeberg.fitguy.nofud.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider

/** Brand palette exposed to Glance. Keep in sync with ui/theme/Color.kt. */
object WidgetTheme {
    val calorieProvider = ColorProvider(day = Color(0xFF0D9488), night = Color(0xFF2DD4BF))
    val backgroundProvider = ColorProvider(day = Color(0xFFF8FAFC), night = Color(0xFF0F172A))
    val primaryTextProvider = ColorProvider(day = Color(0xFF0F172A), night = Color(0xFFE2E8F0))
    val secondaryTextProvider = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF94A3B8))

    /** Raw RGB hex from the snapshot, Fud Pink when the field is absent. */
    fun themeStart(hex: Int?): Int = hex ?: DEFAULT_THEME_START
    fun themeEnd(hex: Int?): Int = hex ?: DEFAULT_THEME_END

    /** Text color provider for the user's theme color (same in light/dark). */
    fun themeTextProvider(hex: Int?): GlanceColorProvider {
        val color = Color(0xFF000000L or themeStart(hex).toLong())
        return ColorProvider(day = color, night = color)
    }
}
