package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pinned 8-swatch day-type palette (UI-UX 2026-09-23 §10). Material-400
 * family, a single set for light and dark so a profile's color reads the
 * same in both themes.
 */
val DayTypePaletteKeys =
    listOf("green", "blue", "amber", "purple", "pink", "teal", "orange", "red")

private val DayTypeHex: Map<String, Color> = mapOf(
    "green" to Color(0xFF66BB6A),
    "blue" to Color(0xFF42A5F5),
    "amber" to Color(0xFFFFB300),
    "purple" to Color(0xFFAB47BC),
    "pink" to Color(0xFFEC407A),
    "teal" to Color(0xFF26A69A),
    "orange" to Color(0xFFFF7043),
    "red" to Color(0xFFEF5350),
)

/**
 * Resolves a profile's display color. Null/unknown keys fall back to a
 * stable auto-assignment: the palette index is derived from the profile id,
 * so the same profile keeps the same color across sessions and devices.
 */
fun dayTypeColor(key: String?, profileId: String): Color {
    val pinned = key?.let { DayTypeHex[it] }
    if (pinned != null) return pinned
    val n = DayTypePaletteKeys.size
    val index = ((profileId.hashCode() % n) + n) % n
    return DayTypeHex.getValue(DayTypePaletteKeys[index])
}
